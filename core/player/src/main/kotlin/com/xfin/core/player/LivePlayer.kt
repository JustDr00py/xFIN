package com.xfin.core.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.analytics.AnalyticsListener
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
import androidx.media3.extractor.DefaultExtractorsFactory
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
    val playing: Boolean = false,
    /** Stream facts for the info chips; 0/null when unknown. */
    val frameRate: Float = 0f,
    val audioChannels: Int = 0,
    val audioLanguage: String? = null,
    val videoCodec: String? = null,
    /** A finite stream (catch-up archive) played to its end. */
    val ended: Boolean = false,
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
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(
                dataSources,
                // IPTV restreams rarely declare their captions in the stream's tables, so a TS
                // stream would offer none; assume broadcast CC (CEA-608) is carried in the video.
                DefaultExtractorsFactory().setTsSubtitleFormats(
                    listOf(Format.Builder().setSampleMimeType(MimeTypes.APPLICATION_CEA608).build()),
                ),
            ),
        )
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
            // Formats are only final once the decoders see them (frame rate, channels, language).
            addAnalyticsListener(
                object : AnalyticsListener {
                    override fun onVideoInputFormatChanged(
                        eventTime: AnalyticsListener.EventTime,
                        format: Format,
                        decoderReuseEvaluation: DecoderReuseEvaluation?,
                    ) {
                        _state.update {
                            it.copy(
                                frameRate = format.frameRate.takeIf { r -> r > 0 } ?: it.frameRate,
                                videoCodec = format.sampleMimeType?.let(::codecName) ?: it.videoCodec,
                            )
                        }
                    }

                    override fun onAudioInputFormatChanged(
                        eventTime: AnalyticsListener.EventTime,
                        format: Format,
                        decoderReuseEvaluation: DecoderReuseEvaluation?,
                    ) {
                        _state.update {
                            it.copy(
                                audioChannels = format.channelCount.takeIf { c -> c > 0 } ?: it.audioChannels,
                                audioLanguage = format.language?.takeUnless { l -> l == "und" || l.isBlank() } ?: it.audioLanguage,
                            )
                        }
                    }
                },
            )
        }

    fun play(stream: PlayableStream) {
        retryJob?.cancel()
        current = stream
        forcedHls = false
        retries = 0
        _state.value = LivePlayerState(buffering = true)
        start(stream)
    }

    /** Position within the current stream; for archives, time since the archive's start. */
    val positionMs: Long get() = player.currentPosition.coerceAtLeast(0)

    /** Length of the current stream when known (archives), else null. */
    val durationMs: Long? get() = player.duration.takeIf { it != C.TIME_UNSET && it > 0 }

    /** Whether [seekTo] works in place; archives without byte-range support must be re-requested. */
    val isSeekable: Boolean get() = player.isCurrentMediaItemSeekable && durationMs != null

    fun seekTo(positionMs: Long) = player.seekTo(positionMs)

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

    /**
     * For multiview: several players play at once, so none may take audio focus (it would pause
     * the others); the UI mutes all but one instead.
     */
    fun useForMultiview() {
        player.setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
            /* handleAudioFocus = */ false,
        )
    }

    fun setMuted(muted: Boolean) {
        player.volume = if (muted) 0f else 1f
    }

    fun togglePlay() {
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    private fun codecName(mime: String): String? = when (mime) {
        MimeTypes.VIDEO_H264 -> "H.264"
        MimeTypes.VIDEO_H265 -> "HEVC"
        MimeTypes.VIDEO_AV1 -> "AV1"
        MimeTypes.VIDEO_VP9 -> "VP9"
        MimeTypes.VIDEO_MPEG2 -> "MPEG-2"
        else -> null
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
            _state.update { it.copy(buffering = playbackState == Player.STATE_BUFFERING, ended = playbackState == Player.STATE_ENDED) }
            if (playbackState == Player.STATE_READY) retries = 0
        }

        override fun onTracksChanged(tracks: Tracks) {
            val video = player.videoFormat
            val audio = player.audioFormat
            _state.update {
                it.copy(
                    audioTracks = tracks.trackOptions(C.TRACK_TYPE_AUDIO),
                    textTracks = tracks.trackOptions(C.TRACK_TYPE_TEXT),
                    frameRate = video?.frameRate?.takeIf { r -> r > 0 } ?: 0f,
                    videoCodec = video?.sampleMimeType?.let(::codecName),
                    audioChannels = audio?.channelCount?.takeIf { c -> c > 0 } ?: 0,
                    audioLanguage = audio?.language?.takeUnless { l -> l == "und" || l.isBlank() },
                )
            }
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            val rate = player.videoFormat?.frameRate?.takeIf { it > 0 }
            _state.update { it.copy(videoHeight = videoSize.height, frameRate = rate ?: it.frameRate) }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(playing = isPlaying) }
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
                // Non-standard codes IPTV panels use when the account's connection limit is hit.
                429, 456, 458, 460, 461 -> "The provider refused the stream ($status). Your account may be at its connection " +
                    "limit, e.g. another device is watching."
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
