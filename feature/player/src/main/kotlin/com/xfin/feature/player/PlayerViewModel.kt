package com.xfin.feature.player

import androidx.annotation.OptIn
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.navigation.toRoute
import com.xfin.core.model.Channel
import com.xfin.core.model.decodeChannelFilter
import com.xfin.core.player.LivePlayer
import com.xfin.core.player.LivePlayerState
import com.xfin.core.model.NowNext
import com.xfin.data.sources.ChannelRepository
import com.xfin.data.sources.EpgRepository
import com.xfin.data.sources.minuteTicks
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlayerUiState(
    val channel: Channel? = null,
    val position: Int = 0,
    val lineupSize: Int = 0,
    /** +1 when zapping forward, -1 backward; drives the info banner animation. */
    val direction: Int = 0,
    val numberEntry: String = "",
    val resolveError: String? = null,
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
    private var lineup: List<Channel> = emptyList()
        set(value) {
            field = value
            _lineup.value = value
        }
    private val _lineup = MutableStateFlow<List<Channel>>(emptyList())

    /** The zapping lineup, shown by the in-player mini guide. */
    val lineupState: StateFlow<List<Channel>> = _lineup.asStateFlow()
    private var tuneJob: Job? = null
    private var numberJob: Job? = null

    private val _ui = MutableStateFlow(PlayerUiState())
    val ui: StateFlow<PlayerUiState> = _ui.asStateFlow()
    val playback: StateFlow<LivePlayerState> = livePlayer.state

    val now: StateFlow<Long> = minuteTicks().stateIn(viewModelScope, SharingStarted.Eagerly, System.currentTimeMillis())

    /** Now/next for the channel on screen, refreshed each minute. */
    val nowNext: StateFlow<NowNext> = combine(
        _ui.map { it.channel }.distinctUntilChanged { a, b -> a?.id == b?.id },
        now,
    ) { channel, time -> channel to time }
        .mapLatest { (channel, time) -> channel?.let { epg.nowNext(it, time) } ?: NowNext(null, null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, NowNext(null, null))

    init {
        viewModelScope.launch {
            lineup = channels.channels(filter)
            var index = lineup.indexOfFirst { it.id == route.channelId }
            if (index < 0) {
                // Channel not in the lineup (e.g. opened from search): play it standalone.
                lineup = listOfNotNull(channels.channel(route.channelId))
                index = 0
            }
            if (lineup.isEmpty()) {
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
        val index = lineup.indexOfFirst { it.number == number }.takeIf { it >= 0 }
            ?: (number - 1).takeIf { it in lineup.indices }
            ?: return
        if (index == _ui.value.position) return
        show(index, direction = if (index > _ui.value.position) 1 else -1)
        tune(delayMs = 0)
    }

    fun toggleFavorite() {
        val channel = _ui.value.channel ?: return
        val updated = channel.copy(isFavorite = !channel.isFavorite)
        lineup = lineup.map { if (it.id == channel.id) updated else it }
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

    /** Refreshes "on now" for the mini guide without disturbing the lineup order. */
    fun refreshLineup() {
        viewModelScope.launch {
            val fresh = channels.channels(filter).associateBy { it.id }
            lineup = lineup.map { old -> fresh[old.id]?.copy(isFavorite = old.isFavorite) ?: old }
        }
    }

    fun peek(delta: Int): Channel? =
        if (lineup.size < 2) null else lineup[(_ui.value.position + delta).mod(lineup.size)]

    private fun show(index: Int, direction: Int) {
        _ui.update {
            it.copy(
                channel = lineup[index],
                position = index,
                lineupSize = lineup.size,
                direction = direction,
                resolveError = null,
            )
        }
    }

    private fun tune(delayMs: Long) {
        tuneJob?.cancel()
        tuneJob = viewModelScope.launch {
            if (delayMs > 0) delay(delayMs)
            val channel = _ui.value.channel ?: return@launch
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
    }
}
