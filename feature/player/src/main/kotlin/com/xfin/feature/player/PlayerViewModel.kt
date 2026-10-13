package com.xfin.feature.player

import androidx.annotation.OptIn
import androidx.collection.LruCache
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.navigation.toRoute
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.xfin.core.model.Channel
import com.xfin.core.model.NowNext
import com.xfin.core.model.ProgramSlot
import com.xfin.core.model.decodeChannelFilter
import com.xfin.core.player.LivePlayer
import com.xfin.core.player.LivePlayerState
import com.xfin.data.sources.ChannelRepository
import com.xfin.data.sources.EpgRepository
import com.xfin.data.sources.Perf
import com.xfin.data.sources.minuteTicks
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlayerUiState(
    val channel: Channel? = null,
    val position: Int = 0,
    val lineupSize: Int = 0,
    /** Neighbours shown as zap hints; null until loaded. */
    val next: Channel? = null,
    val previous: Channel? = null,
    /** +1 when zapping forward, -1 backward; drives the info banner animation. */
    val direction: Int = 0,
    val numberEntry: String = "",
    val resolveError: String? = null,
    val hasMiniGuide: Boolean = false,
)

@OptIn(UnstableApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val channels: ChannelRepository,
    private val epg: EpgRepository,
    val livePlayer: LivePlayer,
) : ViewModel() {

    private val route = savedState.toRoute<PlayerRoute>()
    private val filter = decodeChannelFilter(route.filter)

    /**
     * The zapping lineup as channel ids only, so even 100k-channel playlists open instantly;
     * channel details are loaded for the current position and its neighbours on demand.
     */
    private var lineup = LongArray(0)
    private var positionOf: HashMap<Long, Int>? = null
    private val details = LruCache<Long, Channel>(64)
    private var tuneJob: Job? = null
    private var numberJob: Job? = null
    private var showJob: Job? = null

    private val _ui = MutableStateFlow(PlayerUiState())
    val ui: StateFlow<PlayerUiState> = _ui.asStateFlow()
    val playback: StateFlow<LivePlayerState> = livePlayer.state

    val now: StateFlow<Long> = minuteTicks().stateIn(viewModelScope, SharingStarted.Eagerly, System.currentTimeMillis())

    val guideVersion: StateFlow<Int> = epg.observeGuideVersion()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Now/next for the channel on screen, refreshed each minute. */
    val nowNext: StateFlow<NowNext> = combine(
        _ui.map { it.channel }.distinctUntilChanged { a, b -> a?.id == b?.id },
        now,
    ) { channel, time -> channel to time }
        .mapLatest { (channel, time) -> channel?.let { epg.nowNext(it, time) } ?: NowNext(null, null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, NowNext(null, null))

    /** The lineup as pages for the in-player mini guide (same order as [lineup]). */
    val miniGuide: Flow<PagingData<Channel>> = channels.pagedChannels(filter).cachedIn(viewModelScope)

    init {
        viewModelScope.launch {
            lineup = Perf.time("player lineup ($filter)") { channels.lineup(filter) }
            Perf.log("player lineup size ${lineup.size}")
            var index = lineup.indexOf(route.channelId)
            _ui.update { it.copy(hasMiniGuide = index >= 0) }
            if (index < 0) {
                // Channel not in the lineup (e.g. opened from search): play it standalone.
                lineup = longArrayOf(route.channelId)
                index = 0
            }
            if (load(route.channelId) == null) {
                _ui.update { it.copy(resolveError = "This channel no longer exists.") }
                return@launch
            }
            show(index, direction = 0)
            tune(delayMs = 0)
        }
    }

    /** Moves through the lineup; playback starts once the user stops pressing, saving connections. */
    fun zap(delta: Int) {
        if (lineup.size < 2) return
        show((_ui.value.position + delta).mod(lineup.size), direction = delta.coerceIn(-1, 1))
        tune(delayMs = ZAP_SETTLE_MS)
    }

    fun digit(d: Int) {
        val entry = (_ui.value.numberEntry + d).takeLast(4)
        _ui.update { it.copy(numberEntry = entry) }
        numberJob?.cancel()
        numberJob = viewModelScope.launch {
            delay(NUMBER_COMMIT_MS)
            commitNumber()
        }
    }

    fun commitNumber() {
        numberJob?.cancel()
        val number = _ui.value.numberEntry.toIntOrNull()
        _ui.update { it.copy(numberEntry = "") }
        if (number == null) return
        viewModelScope.launch {
            val positions = positionOf ?: HashMap<Long, Int>(lineup.size * 2).also { map ->
                lineup.forEachIndexed { i, id -> map[id] = i }
                positionOf = map
            }
            val index = channels.idsWithNumber(number).mapNotNull { positions[it] }.minOrNull()
                ?: (number - 1).takeIf { it in lineup.indices }
                ?: return@launch
            tuneTo(index)
        }
    }

    fun toggleFavorite() {
        val channel = _ui.value.channel ?: return
        val updated = channel.copy(isFavorite = !channel.isFavorite)
        details.put(updated.id, updated)
        _ui.update { it.copy(channel = updated) }
        viewModelScope.launch { channels.setFavorite(channel, updated.isFavorite) }
    }

    fun retry() {
        if (_ui.value.resolveError != null) tune(delayMs = 0) else livePlayer.retry()
    }

    fun tuneTo(index: Int) {
        if (index !in lineup.indices || index == _ui.value.position) return
        show(index, direction = if (index > _ui.value.position) 1 else -1)
        tune(delayMs = 0)
    }

    suspend fun nowPlaying(channel: Channel, now: Long): ProgramSlot? = epg.nowPlaying(channel, now)

    private fun show(index: Int, direction: Int) {
        val id = lineup[index]
        // Zapping must feel instant: use cached details when we have them, load otherwise.
        _ui.update {
            it.copy(
                channel = details[id] ?: it.channel?.takeIf { c -> c.id == id },
                position = index,
                lineupSize = lineup.size,
                next = neighbour(index, +1)?.let(details::get),
                previous = neighbour(index, -1)?.let(details::get),
                direction = direction,
                resolveError = null,
            )
        }
        showJob?.cancel()
        showJob = viewModelScope.launch {
            val channel = load(id)
            if (_ui.value.position != index) return@launch
            _ui.update { it.copy(channel = channel ?: it.channel) }
            prefetchAround(index)
            if (_ui.value.position != index) return@launch
            _ui.update {
                it.copy(
                    next = neighbour(index, +1)?.let(details::get),
                    previous = neighbour(index, -1)?.let(details::get),
                )
            }
        }
    }

    private fun neighbour(index: Int, delta: Int): Long? =
        if (lineup.size < 2) null else lineup[(index + delta).mod(lineup.size)]

    private suspend fun prefetchAround(index: Int) {
        val ids = (-PREFETCH..PREFETCH).mapNotNull { neighbour(index, it) }.distinct().filter { details[it] == null }
        channels.channels(ids).forEach { details.put(it.id, it) }
    }

    private suspend fun load(id: Long): Channel? =
        details[id] ?: channels.channel(id)?.also { details.put(id, it) }

    private fun tune(delayMs: Long) {
        tuneJob?.cancel()
        tuneJob = viewModelScope.launch {
            if (delayMs > 0) delay(delayMs)
            val id = lineup.getOrNull(_ui.value.position) ?: return@launch
            val channel = load(id) ?: return@launch
            try {
                livePlayer.play(channels.resolveStream(channel.id))
                channels.recordWatched(channel)
            } catch (e: Exception) {
                _ui.update { it.copy(resolveError = e.message ?: "Couldn't open this channel") }
            }
        }
    }

    override fun onCleared() {
        livePlayer.release()
    }

    private companion object {
        const val ZAP_SETTLE_MS = 450L
        const val NUMBER_COMMIT_MS = 1_500L
        const val PREFETCH = 3
    }
}
