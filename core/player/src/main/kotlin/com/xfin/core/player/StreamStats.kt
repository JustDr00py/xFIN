package com.xfin.core.player

import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData

/** One reading for the "Under the Hood" panel; null/0 when unknown. */
data class StreamStats(
    val resolution: String?,
    val frameRate: Float,
    val videoCodec: String?,
    val videoBitrate: Int,
    val videoDecoder: String?,
    val audioCodec: String?,
    val audioChannels: Int,
    val sampleRate: Int,
    val audioLanguage: String?,
    val audioDecoder: String?,
    val bufferMs: Long,
    val droppedFrames: Int,
    val bandwidthBps: Long,
    val downloadedBytes: Long,
    val state: String,
    val host: String?,
)

/** Collects what ExoPlayer only reports as events (decoders, dropped frames, network). */
@UnstableApi
class StreamStatsTracker : AnalyticsListener {
    @Volatile private var videoDecoder: String? = null
    @Volatile private var audioDecoder: String? = null
    @Volatile private var dropped = 0
    @Volatile private var bandwidth = 0L
    @Volatile private var bytes = 0L

    /** A new stream starts from zero. */
    fun reset() {
        dropped = 0
        bytes = 0
    }

    override fun onVideoDecoderInitialized(
        eventTime: AnalyticsListener.EventTime,
        decoderName: String,
        initializedTimestampMs: Long,
        initializationDurationMs: Long,
    ) {
        videoDecoder = decoderName
    }

    override fun onAudioDecoderInitialized(
        eventTime: AnalyticsListener.EventTime,
        decoderName: String,
        initializedTimestampMs: Long,
        initializationDurationMs: Long,
    ) {
        audioDecoder = decoderName
    }

    override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) {
        dropped += droppedFrames
    }

    override fun onBandwidthEstimate(
        eventTime: AnalyticsListener.EventTime,
        totalLoadTimeMs: Int,
        totalBytesLoaded: Long,
        bitrateEstimate: Long,
    ) {
        bandwidth = bitrateEstimate
    }

    override fun onLoadCompleted(eventTime: AnalyticsListener.EventTime, loadEventInfo: LoadEventInfo, mediaLoadData: MediaLoadData) {
        bytes += loadEventInfo.bytesLoaded
    }

    // Live TS is one endless load that only ends when canceled; count what it delivered.
    override fun onLoadCanceled(eventTime: AnalyticsListener.EventTime, loadEventInfo: LoadEventInfo, mediaLoadData: MediaLoadData) {
        bytes += loadEventInfo.bytesLoaded
    }

    /** Call on the main thread (ExoPlayer's getters require it). */
    fun snapshot(player: ExoPlayer): StreamStats {
        val video: Format? = player.videoFormat
        val audio: Format? = player.audioFormat
        val size = player.videoSize
        return StreamStats(
            resolution = if (size.width > 0) "${size.width}×${size.height}" else video?.takeIf { it.width > 0 }?.let { "${it.width}×${it.height}" },
            frameRate = video?.frameRate?.takeIf { it > 0 } ?: 0f,
            videoCodec = video?.codecs ?: video?.sampleMimeType,
            videoBitrate = video?.bitrate?.takeIf { it != Format.NO_VALUE } ?: 0,
            videoDecoder = videoDecoder,
            audioCodec = audio?.codecs ?: audio?.sampleMimeType,
            audioChannels = audio?.channelCount?.takeIf { it > 0 } ?: 0,
            sampleRate = audio?.sampleRate?.takeIf { it > 0 } ?: 0,
            audioLanguage = audio?.language?.takeUnless { it == "und" || it.isBlank() },
            audioDecoder = audioDecoder,
            bufferMs = player.totalBufferedDuration.coerceAtLeast(0),
            droppedFrames = dropped,
            bandwidthBps = bandwidth,
            downloadedBytes = bytes,
            state = when (player.playbackState) {
                Player.STATE_BUFFERING -> "Buffering"
                Player.STATE_READY -> if (player.isPlaying) "Playing" else "Paused"
                Player.STATE_ENDED -> "Ended"
                else -> "Idle"
            },
            host = player.currentMediaItem?.localConfiguration?.uri?.host,
        )
    }
}
