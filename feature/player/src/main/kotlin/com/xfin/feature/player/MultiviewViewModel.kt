package com.xfin.feature.player

import androidx.annotation.OptIn
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.navigation.toRoute
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.xfin.core.model.Channel
import com.xfin.core.model.ProgramSlot
import com.xfin.core.model.decodeChannelFilter
import com.xfin.core.player.LivePlayer
import com.xfin.data.sources.ChannelRepository
import com.xfin.data.sources.EpgRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Provider

/** One multiview tile: a channel with its own player. */
@OptIn(UnstableApi::class)
class Tile(val channel: Channel, val player: LivePlayer)

data class MultiviewState(
    val tiles: List<Tile> = emptyList(),
    /** Index of the tile whose audio is heard. */
    val audio: Int = 0,
)

@OptIn(UnstableApi::class)
@HiltViewModel
class MultiviewViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val channels: ChannelRepository,
    private val epg: EpgRepository,
    private val players: Provider<LivePlayer>,
) : ViewModel() {
    private val route = savedState.toRoute<MultiviewRoute>()
    val filterKey: String = route.filter

    private val _state = MutableStateFlow(MultiviewState())
    val state: StateFlow<MultiviewState> = _state.asStateFlow()

    /** Channels to add from, in the same order as the lineup the user came from. */
    val picker: Flow<PagingData<Channel>> = channels.pagedChannels(decodeChannelFilter(route.filter)).cachedIn(viewModelScope)

    init {
        add(route.channelId)
    }

    fun add(channelId: Long) {
        if (_state.value.tiles.size >= MAX_TILES || _state.value.tiles.any { it.channel.id == channelId }) return
        viewModelScope.launch {
            val channel = channels.channel(channelId) ?: return@launch
            val player = players.get().apply { useForMultiview() }
            _state.update { it.copy(tiles = it.tiles + Tile(channel, player)) }
            applyAudio()
            runCatching { player.play(channels.resolveStream(channelId)) }
        }
    }

    fun remove(index: Int) {
        val tiles = _state.value.tiles
        if (index !in tiles.indices || tiles.size == 1) return
        tiles[index].player.release()
        val audio = _state.value.audio
        _state.update {
            it.copy(
                tiles = tiles.filterIndexed { i, _ -> i != index },
                audio = when {
                    index == audio -> 0
                    index < audio -> audio - 1
                    else -> audio
                },
            )
        }
        applyAudio()
    }

    fun listenTo(index: Int) {
        if (index !in _state.value.tiles.indices) return
        _state.update { it.copy(audio = index) }
        applyAudio()
    }

    suspend fun nowPlaying(channel: Channel, now: Long): ProgramSlot? = epg.nowPlaying(channel, now)

    private fun applyAudio() {
        val s = _state.value
        s.tiles.forEachIndexed { i, tile -> tile.player.setMuted(i != s.audio) }
    }

    override fun onCleared() {
        _state.value.tiles.forEach { it.player.release() }
    }

    companion object {
        const val MAX_TILES = 4
    }
}
