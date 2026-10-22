package com.xfin.core.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.xfin.core.model.PlayableStream
import com.xfin.core.model.VodStream
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject

data class VodPlayerState(
    val playing: Boolean = false,
    val buffering: Boolean = true,
    val ended: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val bufferedMs: Long = 0,
    val error: String? = null,
    /** The device could not decode the stream; a server transcode may still work. */
    val decodeFailed: Boolean = false,
    /** The server answered a request with an error status; asking for a new stream may work. */
    val httpFailed: Boolean = false,
    /** The server couldn't be reached at all; another version of the title won't help. */
    val networkFailed: Boolean = false,
    val audioTracks: List<TrackOption> = emptyList(),
    val textTracks: List<TrackOption> = emptyList(),
)

/** ExoPlayer for movies and episodes: seeking, resume, side-loaded subtitles and FFmpeg audio. */
@UnstableApi
class VodPlayer @Inject constructor(
    @ApplicationContext context: Context,
    http: OkHttpClient,
) {
    private val dataSources = StreamDataSourceFactory(http)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(VodPlayerState())
    val state: StateFlow<VodPlayerState> = _state.asStateFlow()

    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setRenderersFactory(
            DefaultRenderersFactory(context)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
                .setEnableDecoderFallback(true),
        )
        .setMediaSourceFactory(DefaultMediaSourceFactory(dataSources))
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
            /* handleAudioFocus = */ true,
        )
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .setSeekBackIncrementMs(SEEK_STEP_MS)
        .setSeekForwardIncrementMs(SEEK_STEP_MS)
        .build()
        .apply { addListener(Listener()) }

    init {
        // Position ticks for the seek bar.
        scope.launch {
            while (isActive) {
                _state.update {
                    it.copy(
                        positionMs = player.currentPosition.coerceAtLeast(0),
                        durationMs = player.duration.takeIf { d -> d > 0 } ?: it.durationMs,
                        bufferedMs = player.bufferedPosition,
                    )
                }
                delay(500)
            }
        }
    }

    fun play(stream: VodStream) {
        dataSources.stream = PlayableStream(url = stream.url, headers = stream.headers)
        val item = MediaItem.Builder()
            .setUri(stream.url)
            .apply { if (stream.isHls) setMimeType(MimeTypes.APPLICATION_M3U8) }
            .setSubtitleConfigurations(
                stream.subtitles.map { sub ->
                    MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.url))
                        .setMimeType(sub.mimeType)
                        .setLanguage(sub.language)
                        .setLabel(sub.label)
                        .build()
                },
            )
            .build()
        _state.value = VodPlayerState(buffering = true)
        // A transcode already starts at the requested offset; direct play seeks.
        val seekTo = if (stream.isHls) 0 else stream.startMs
        player.setMediaItem(item, seekTo)
        player.prepare()
        player.play()
    }

    fun togglePlay() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun seekBy(deltaMs: Long) {
        val target = (player.currentPosition + deltaMs).coerceIn(0, player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE)
        player.seekTo(target)
        _state.update { it.copy(positionMs = target) }
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
        _state.update { it.copy(positionMs = positionMs) }
    }

    fun selectTrack(option: TrackOption) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(option.group.type, false)
            .setOverrideForType(TrackSelectionOverride(option.group.mediaTrackGroup, option.index))
            .build()
    }

    fun disableSubtitles() {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
    }

    fun release() {
        scope.cancel()
        player.release()
    }

    private inner class Listener : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(playing = isPlaying) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _state.update {
                it.copy(
                    buffering = playbackState == Player.STATE_BUFFERING,
                    ended = playbackState == Player.STATE_ENDED,
                    durationMs = player.duration.takeIf { d -> d > 0 } ?: it.durationMs,
                )
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            _state.update {
                it.copy(audioTracks = tracks.trackOptions(C.TRACK_TYPE_AUDIO), textTracks = tracks.trackOptions(C.TRACK_TYPE_TEXT))
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val status = (error.cause as? androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)?.responseCode
            val message = when {
                status == 404 -> "This video isn't available on the server. Its file may be missing."
                status == 401 || status == 403 -> "The server refused access ($status). Try signing in again."
                error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                    error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Can't reach the server. Check your connection."
                error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ||
                    error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> "This device can't decode this video."
                status != null -> "The server couldn't send this video (HTTP $status)."
                else -> "Playback failed: ${error.errorCodeName}"
            }
            val decodeFailed = error.errorCode in PlaybackException.ERROR_CODE_DECODER_INIT_FAILED..PlaybackException.ERROR_CODE_DECODING_RESOURCES_RECLAIMED
            val httpFailed = error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
            val networkFailed = error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
            _state.update {
                it.copy(buffering = false, error = message, decodeFailed = decodeFailed, httpFailed = httpFailed, networkFailed = networkFailed)
            }
        }
    }

    companion object {
        const val SEEK_STEP_MS = 10_000L
    }
}
