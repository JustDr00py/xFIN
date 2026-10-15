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
import com.xfin.core.model.ChannelFilter
import com.xfin.core.model.encode
import kotlinx.coroutines.flow.flatMapLatest
import com.xfin.core.player.LivePlayer
import com.xfin.core.player.LivePlayerState
import com.xfin.data.sources.ChannelRepository
import com.xfin.data.sources.EpgRepository
import com.xfin.data.sources.SourceRepository
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
import kotlinx.coroutines.flow.flow
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
    /** Name of the playlist/provider the channel comes from. */
    val sourceName: String? = null,
    /** Set while watching the archive instead of live. */
    val catchup: CatchupPlayback? = null,
)

/** Archive playback of [start]..[end]; the current stream starts at [playFrom] (whole minutes). */
data class CatchupPlayback(val title: String?, val start: Long, val end: Long, val playFrom: Long)

@OptIn(UnstableApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val channels: ChannelRepository,
    private val epg: EpgRepository,
    sources: SourceRepository,
    val livePlayer: LivePlayer,
) : ViewModel() {
    private val sourceNames: StateFlow<Map<Long, String>> = sources.observeSources()
        .map { list -> list.associate { it.id to it.name } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val route = savedState.toRoute<PlayerRoute>()
    /** The category being zapped through; changes when a channel is picked from another one. */
    private var filter = decodeChannelFilter(route.filter)

    /** The encoded lineup filter, passed on to multiview so it can offer the same channels. */
    val filterKey: String get() = filter.encode()

    /** The category shown in the mini guide, which can be browsed without changing playback. */
    private val _browse = MutableStateFlow(filter)
    val browseFilter: StateFlow<ChannelFilter> = _browse.asStateFlow()

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

    private val _ui = MutableStateFlow(
        PlayerUiState(
            catchup = route.catchupStart.takeIf { it > 0 }?.let {
                CatchupPlayback(route.catchupTitle, it, route.catchupEnd, floorMinute(it))
            },
        ),
    )
    val ui: StateFlow<PlayerUiState> = _ui.asStateFlow()
    val playback: StateFlow<LivePlayerState> = livePlayer.state

    val now: StateFlow<Long> = minuteTicks().stateIn(viewModelScope, SharingStarted.Eagerly, System.currentTimeMillis())

    val guideVersion: StateFlow<Int> = epg.observeGuideVersion()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Where in the archive playback is (epoch ms), or null when live. Ticks twice a second. */
    val catchupPosition: StateFlow<Long?> = flow {
        while (true) {
            emit(_ui.value.catchup?.let { it.playFrom + livePlayer.positionMs })
            delay(500)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(1_000), null)

    /** Now/next for the channel on screen at the time being watched (live or archive). */
    val nowNext: StateFlow<NowNext> = combine(
        _ui.map { it.channel }.distinctUntilChanged { a, b -> a?.id == b?.id },
        now,
        catchupPosition.map { it?.let(::floorMinute) }.distinctUntilChanged(),
    ) { channel, time, archiveTime -> channel to (archiveTime ?: time) }
        .mapLatest { (channel, time) -> channel?.let { epg.nowNext(it, time) } ?: NowNext(null, null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, NowNext(null, null))

    /** The lineup as pages for the in-player mini guide (same order as [lineup]). */
    val miniGuide: Flow<PagingData<Channel>> = _browse.flatMapLatest { channels.pagedChannels(it) }.cachedIn(viewModelScope)

    /** Categories for the mini guide's side column (Favorites and Recent first, as on Live TV). */
    val categories: StateFlow<List<PlayerCategory>> = combine(
        channels.observeGroups(),
        channels.observeHasFavorites(),
        channels.observeHasHistory(),
    ) { groups, hasFavorites, hasHistory ->
        buildList {
            if (hasFavorites) add(PlayerCategory(ChannelFilter.Favorites, "Favorites"))
            if (hasHistory) add(PlayerCategory(ChannelFilter.Recent, "Recent"))
            add(PlayerCategory(ChannelFilter.All, "All channels"))
            groups.filterNot { it.hidden }.forEach { add(PlayerCategory(ChannelFilter.Group(it.name), it.name)) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Whether the mini guide shows the lineup being played (so its current row can be marked). */
    val browsingCurrent: Boolean get() = _browse.value == filter

    fun browse(category: ChannelFilter) {
        _browse.value = category
    }

    /** Back to the playing category, e.g. when the mini guide closes. */
    fun resetBrowse() {
        _browse.value = filter
    }

    /** Picks row [index] of the mini guide; from another category, that category becomes the lineup. */
    fun tuneFromGuide(index: Int) {
        val browsing = _browse.value
        if (browsing == filter) {
            tuneTo(index)
            return
        }
        viewModelScope.launch {
            val ids = channels.lineup(browsing)
            if (index !in ids.indices) return@launch
            filter = browsing
            lineup = ids
            positionOf = null
            _ui.update { it.copy(catchup = null, hasMiniGuide = true) }
            show(index, direction = 0)
            tune(delayMs = 0)
        }
    }

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
        // An archive that plays to its end continues live.
        viewModelScope.launch {
            livePlayer.state.map { it.ended }.distinctUntilChanged().collect { ended ->
                if (ended && _ui.value.catchup != null) goLive()
            }
        }
    }

    // --- Catch-up ---

    /** Whether the program airing now can be restarted from the archive. */
    fun canStartOver(): Boolean {
        val program = nowNext.value.now ?: return false
        return _ui.value.catchup == null && _ui.value.channel?.catchup?.covers(program.start, System.currentTimeMillis()) == true
    }

    fun startOver() {
        val program = nowNext.value.now ?: return
        watchArchive(program.title, program.start, program.end, from = program.start)
    }

    /** Plays the archive from [time] (epoch ms) within the program on screen, live or archived. */
    fun seekTo(time: Long) {
        val now = System.currentTimeMillis()
        val target = time.coerceAtMost(now - LIVE_EDGE_MS)
        val current = _ui.value.catchup
        if (current == null) {
            val program = nowNext.value.now ?: return
            if (_ui.value.channel?.catchup?.covers(program.start, now) != true) return
            watchArchive(program.title, program.start, program.end, from = target.coerceAtLeast(program.start))
            return
        }
        val offset = target - current.playFrom
        val loaded = livePlayer.durationMs
        if (livePlayer.isSeekable && loaded != null && offset in 0 until loaded) {
            livePlayer.seekTo(offset)
        } else {
            _ui.update { it.copy(catchup = current.copy(playFrom = floorMinute(target.coerceAtLeast(current.start)))) }
            tune(delayMs = SEEK_SETTLE_MS)
        }
    }

    fun skip(deltaMs: Long) {
        val position = catchupPosition.value ?: return
        seekTo(position + deltaMs)
    }

    fun goLive() {
        if (_ui.value.catchup == null) return
        _ui.update { it.copy(catchup = null) }
        tune(delayMs = 0)
    }

    private fun watchArchive(title: String?, start: Long, end: Long, from: Long) {
        _ui.update { it.copy(catchup = CatchupPlayback(title, start, end, floorMinute(from))) }
        tune(delayMs = 0)
    }

    /** Moves through the lineup; playback starts once the user stops pressing, saving connections. */
    fun zap(delta: Int) {
        if (lineup.size < 2) return
        _ui.update { it.copy(catchup = null) }
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
        _ui.update { it.copy(catchup = null) }
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
                sourceName = (details[id] ?: it.channel)?.sourceId?.let(sourceNames.value::get),
                resolveError = null,
            )
        }
        showJob?.cancel()
        showJob = viewModelScope.launch {
            val channel = load(id)
            if (_ui.value.position != index) return@launch
            _ui.update { it.copy(channel = channel ?: it.channel, sourceName = channel?.sourceId?.let(sourceNames.value::get) ?: it.sourceName) }
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
                val archive = _ui.value.catchup
                if (archive != null) {
                    livePlayer.play(channels.resolveCatchup(channel.id, archive.playFrom, archive.end))
                } else {
                    livePlayer.play(channels.resolveStream(channel.id))
                    channels.recordWatched(channel)
                }
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
        const val SEEK_SETTLE_MS = 300L
        /** Archives lag live a little; don't seek closer than this to now. */
        const val LIVE_EDGE_MS = 90_000L
    }
}

private fun floorMinute(time: Long) = time / 60_000 * 60_000

data class PlayerCategory(val filter: ChannelFilter, val label: String)
