package com.xfin.feature.player

import androidx.annotation.OptIn
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.navigation.toRoute
import com.xfin.core.model.MediaItem
import com.xfin.core.model.MediaKind
import com.xfin.core.model.VodStream
import com.xfin.core.player.VodPlayer
import com.xfin.core.player.VodPlayerState
import com.xfin.data.jellyfin.JellyfinRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class VodUiState(
    val item: MediaItem? = null,
    val upNext: MediaItem? = null,
    val error: String? = null,
)

@OptIn(UnstableApi::class)
@HiltViewModel
class VodPlayerViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val jellyfin: JellyfinRepository,
    val player: VodPlayer,
) : ViewModel() {
    private val route = savedState.toRoute<VodPlayerRoute>()
    private var stream: VodStream? = null

    private val _ui = MutableStateFlow(VodUiState())
    val ui: StateFlow<VodUiState> = _ui.asStateFlow()
    val playback: StateFlow<VodPlayerState> = player.state

    init {
        viewModelScope.launch {
            try {
                val item = jellyfin.item(route.sourceId, route.itemId)
                _ui.update { it.copy(item = item) }
                val s = jellyfin.playback(route.sourceId, route.itemId, route.startMs, mediaSourceId = route.mediaSourceId)
                stream = s
                android.util.Log.i("xFIN-vod", "${s.playMethod} ${item.kind} ${s.url} subs=${s.subtitles.map { it.url }}")
                player.play(s)
                jellyfin.reportStart(s, route.startMs)
                if (item.kind == MediaKind.EPISODE && item.seriesId != null) {
                    launch { _ui.update { it.copy(upNext = runCatching { jellyfin.nextEpisode(route.sourceId, item.seriesId!!, item.id) }.getOrNull()) } }
                }
            } catch (e: Exception) {
                android.util.Log.w("xFIN-vod", "playback setup failed", e)
                _ui.update { it.copy(error = e.message ?: "Couldn't play this title") }
            }
        }
        // Keep the server's "continue watching" in sync: every 10 s, and on pause/resume.
        viewModelScope.launch {
            while (isActive) {
                delay(PROGRESS_INTERVAL_MS)
                report()
            }
        }
        viewModelScope.launch {
            player.state.map { it.playing }.distinctUntilChanged().drop(1).collect { report() }
        }
        viewModelScope.launch {
            player.state.map { it.ended }.distinctUntilChanged().filter { it }.collect { report() }
        }
        // Safety net: if this device can't decode the original, ask the server to transcode it.
        viewModelScope.launch {
            player.state.map { it.decodeFailed }.distinctUntilChanged().filter { it }.collect {
                val s = stream ?: return@collect
                if (s.playMethod == "Transcode") return@collect
                val position = player.state.value.positionMs.takeIf { it > 0 } ?: s.startMs
                android.util.Log.i("xFIN-vod", "decode failed with ${s.playMethod}; retrying as a transcode")
                try {
                    val transcode = jellyfin.playback(route.sourceId, route.itemId, position, allowDirect = false, mediaSourceId = s.mediaSourceId)
                    stream = transcode
                    player.play(transcode)
                    jellyfin.reportStart(transcode, position)
                } catch (e: Exception) {
                    _ui.update { it.copy(error = e.message ?: "Couldn't play this title") }
                }
            }
        }
    }

    fun retry() {
        _ui.update { it.copy(error = null) }
        val s = stream ?: return
        player.play(s.copy(startMs = player.state.value.positionMs))
    }

    private suspend fun report() {
        val s = stream ?: return
        jellyfin.reportProgress(s, player.state.value.positionMs, paused = !player.state.value.playing)
    }

    override fun onCleared() {
        val s = stream
        val position = player.state.value.positionMs
        player.release()
        // The view model scope is gone; finish the stop report on its own.
        if (s != null) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                withContext(NonCancellable) { jellyfin.reportStopped(s, position) }
            }
        }
    }

    private companion object {
        const val PROGRESS_INTERVAL_MS = 10_000L
    }
}
