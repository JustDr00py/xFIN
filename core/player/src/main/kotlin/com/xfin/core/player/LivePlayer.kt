package com.xfin.core.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.UnrecognizedInputFormatException
import com.xfin.core.model.PlayableStream
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject

data class TrackOption(
    val group: Tracks.Group,
    val index: Int,
    val label: String,
    val selected: Boolean,
)

data class LivePlayerState(
    val buffering: Boolean = true,
    val error: String? = null,
    val audioTracks: List<TrackOption> = emptyList(),
    val textTracks: List<TrackOption> = emptyList(),
    val videoHeight: Int = 0,
)

/**
 * ExoPlayer tuned for live IPTV: fast start, automatic recovery from the failures IPTV streams
 * commonly produce (stale live windows, mislabeled HLS, transient network drops), FFmpeg audio
 * fallback for AC3/EAC3/MP2, and per-stream request headers.
 */
@UnstableApi
class LivePlayer @Inject constructor(
    @ApplicationContext context: Context,
    http: OkHttpClient,
) {
    private val dataSources = StreamDataSourceFactory(http)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var retryJob: Job? = null
    private var current: PlayableStream? = null
    private var forcedHls = false
    private var retries = 0

    private val _state = MutableStateFlow(LivePlayerState())
    val state: StateFlow<LivePlayerState> = _state.asStateFlow()

    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setRenderersFactory(
            DefaultRenderersFactory(context)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
                .setEnableDecoderFallback(true),
        )
        .setMediaSourceFactory(DefaultMediaSourceFactory(dataSources))
        .setLoadControl(
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(15_000, 50_000, 1_000, 2_500)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build(),
        )
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
            /* handleAudioFocus = */ true,
        )
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .build()
        .apply {
            playWhenReady = true
            addListener(Listener())
        }

    fun play(stream: PlayableStream) {
        retryJob?.cancel()
        current = stream
        forcedHls = false
        retries = 0
        _state.value = LivePlayerState(buffering = true)
        start(stream)
    }

    fun retry() {
        current?.let {
            retries = 0
            _state.update { s -> s.copy(error = null, buffering = true) }
            start(it)
        }
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

    private fun start(stream: PlayableStream) {
        dataSources.stream = stream
        val item = MediaItem.Builder()
            .setUri(stream.url)
            .apply { if (forcedHls || stream.url.looksLikeHls()) setMimeType(MimeTypes.APPLICATION_M3U8) }
            .setLiveConfiguration(MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(8_000).build())
            .build()
        player.setMediaItem(item)
        player.prepare()
        player.play()
    }

    private fun String.looksLikeHls(): Boolean {
        val path = substringBefore('?').lowercase()
        return path.endsWith(".m3u8") || path.endsWith(".m3u") || contains("type=m3u8", ignoreCase = true)
    }

    private inner class Listener : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            _state.update { it.copy(buffering = playbackState == Player.STATE_BUFFERING) }
            if (playbackState == Player.STATE_READY) retries = 0
        }

        override fun onTracksChanged(tracks: Tracks) {
            _state.update {
                it.copy(
                    audioTracks = tracks.trackOptions(C.TRACK_TYPE_AUDIO),
                    textTracks = tracks.trackOptions(C.TRACK_TYPE_TEXT),
                )
            }
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            _state.update { it.copy(videoHeight = videoSize.height) }
        }

        override fun onPlayerError(error: PlaybackException) {
            val stream = current ?: return
            when {
                error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> {
                    player.seekToDefaultPosition()
                    player.prepare()
                }
                // Many providers serve HLS from extension-less URLs; progressive extraction then fails.
                !forcedHls && error.hasCause<UnrecognizedInputFormatException>() -> {
                    forcedHls = true
                    start(stream)
                }
                // A broken or expired certificate will not fix itself; fail fast instead of retrying.
                error.hasCause<javax.net.ssl.SSLException>() || error.hasCause<java.security.cert.CertificateException>() ->
                    _state.update {
                        it.copy(buffering = false, error = "This stream's security certificate is invalid or expired.")
                    }
                error.isTransient() && retries < MAX_RETRIES -> {
                    retries++
                    retryJob = scope.launch {
                        delay(1_000L * retries)
                        start(stream)
                    }
                }
                else -> _state.update { it.copy(buffering = false, error = error.describe()) }
            }
        }
    }

    private inline fun <reified T : Throwable> Throwable.hasCause(): Boolean {
        var e: Throwable? = this
        while (e != null) {
            if (e is T) return true
            e = e.cause
        }
        return false
    }

    private fun PlaybackException.isTransient() = errorCode in setOf(
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        PlaybackException.ERROR_CODE_TIMEOUT,
    )

    private fun PlaybackException.describe(): String = when (errorCode) {
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> {
            val status = (cause as? androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)?.responseCode
            when (status) {
                401, 403 -> "The stream refused the connection ($status). It may be region-locked, or your subscription expired or hit its connection limit."
                404 -> "This channel is currently unavailable (404)."
                else -> "The provider returned an error${status?.let { " ($it)" }.orEmpty()}."
            }
        }
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Can't reach the stream. Check your connection."
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> "This device can't decode this stream's format."
        else -> "Playback failed: $errorCodeName"
    }

    private companion object {
        const val MAX_RETRIES = 3
    }
}
