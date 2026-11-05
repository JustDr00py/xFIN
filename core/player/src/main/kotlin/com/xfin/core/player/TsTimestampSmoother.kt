package com.xfin.core.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.DataReader
import androidx.media3.common.Format
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.text.SubtitleParser
import androidx.media3.extractor.ts.TsExtractor

/**
 * Keeps sample timestamps continuous across a jump in the source clock.
 *
 * IPTV panels fail over to a backup feed inside the same MPEG-TS connection without marking it,
 * so the clock jumps (often tens of seconds back). Media3 passes the jump through: audio
 * resyncs, but video frames then look far too old or too new and the picture freezes while
 * sound plays on. FFmpeg-based players (VLC, mpv, KSPlayer) smooth such jumps over; this does
 * the same.
 *
 * A jump seen on an audio or video track starts a new epoch whose offset maps the new clock to
 * just after that track's last sample. The other tracks adopt the same offset when they jump
 * too, so A/V sync is kept. Sparse tracks (captions) never start an epoch; they follow the
 * current one.
 */
internal class TimestampSmoother {
    private var epoch = 0
    private var offsetUs = 0L

    fun track(drivesEpochs: Boolean) = Track(drivesEpochs)

    inner class Track internal constructor(private val drivesEpochs: Boolean) {
        private var trackEpoch = 0
        private var trackOffsetUs = 0L
        private var lastRawUs = C.TIME_UNSET
        private var maxOutUs = C.TIME_UNSET
        private var lastStepUs = DEFAULT_STEP_US

        fun adjust(rawUs: Long): Long {
            val last = lastRawUs
            if (!drivesEpochs) {
                // Captions are sparse: a long gap is normal, so just follow the current epoch.
                trackEpoch = epoch
                trackOffsetUs = offsetUs
            } else if (last != C.TIME_UNSET && Math.abs(rawUs - last) > JUMP_THRESHOLD_US) {
                if (trackEpoch == epoch) {
                    // First track to see this jump: continue right after its last sample.
                    epoch++
                    offsetUs = maxOutUs + lastStepUs - rawUs
                }
                trackEpoch = epoch
                trackOffsetUs = offsetUs
            } else if (last != C.TIME_UNSET) {
                // Positive steps only: video arrives in decode order, so PTS steps back on B-frames.
                val step = rawUs - last
                if (step in 1..MAX_STEP_US) lastStepUs = step
            }
            lastRawUs = rawUs
            val out = rawUs + trackOffsetUs
            if (maxOutUs == C.TIME_UNSET || out > maxOutUs) maxOutUs = out
            return out
        }
    }

    companion object {
        /** Far beyond B-frame reordering or a missing frame, far below a feed switch's jump. */
        const val JUMP_THRESHOLD_US = 3_000_000L
        private const val DEFAULT_STEP_US = 40_000L
        private const val MAX_STEP_US = 100_000L
    }
}

/** [DefaultExtractorsFactory] whose MPEG-TS extractors smooth over source clock jumps. */
@UnstableApi
internal class SmoothingExtractorsFactory(private val inner: DefaultExtractorsFactory) : ExtractorsFactory {
    override fun createExtractors(): Array<Extractor> = inner.createExtractors().map(::wrap).toTypedArray()

    override fun createExtractors(uri: Uri, responseHeaders: Map<String, List<String>>): Array<Extractor> =
        inner.createExtractors(uri, responseHeaders).map(::wrap).toTypedArray()

    // DefaultMediaSourceFactory configures caption handling through these; pass them on.
    override fun setSubtitleParserFactory(subtitleParserFactory: SubtitleParser.Factory): ExtractorsFactory =
        apply { inner.setSubtitleParserFactory(subtitleParserFactory) }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun experimentalSetTextTrackTranscodingEnabled(textTrackTranscodingEnabled: Boolean): ExtractorsFactory =
        apply { inner.experimentalSetTextTrackTranscodingEnabled(textTrackTranscodingEnabled) }

    override fun experimentalSetCodecsToParseWithinGopSampleDependencies(codecsToParseWithinGopSampleDependencies: Int): ExtractorsFactory =
        apply { inner.experimentalSetCodecsToParseWithinGopSampleDependencies(codecsToParseWithinGopSampleDependencies) }

    private fun wrap(extractor: Extractor): Extractor =
        if (extractor.underlyingImplementation is TsExtractor) SmoothingExtractor(extractor) else extractor
}

@UnstableApi
private class SmoothingExtractor(private val inner: Extractor) : Extractor {
    private var smoother = TimestampSmoother()

    override fun sniff(input: ExtractorInput): Boolean = inner.sniff(input)

    override fun init(output: ExtractorOutput) = inner.init(
        object : ExtractorOutput {
            override fun track(id: Int, type: Int): TrackOutput = SmoothingTrackOutput(
                output.track(id, type),
                drivesEpochs = type == C.TRACK_TYPE_AUDIO || type == C.TRACK_TYPE_VIDEO,
                smoother = { smoother },
            )

            override fun endTracks() = output.endTracks()

            override fun seekMap(seekMap: SeekMap) = output.seekMap(seekMap)
        },
    )

    override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int = inner.read(input, seekPosition)

    override fun seek(position: Long, timeUs: Long) {
        smoother = TimestampSmoother()
        inner.seek(position, timeUs)
    }

    override fun release() = inner.release()

    override fun getUnderlyingImplementation(): Extractor = inner.underlyingImplementation
}

@UnstableApi
private class SmoothingTrackOutput(
    private val inner: TrackOutput,
    private val drivesEpochs: Boolean,
    private val smoother: () -> TimestampSmoother,
) : TrackOutput {
    private var owner: TimestampSmoother? = null
    private var track: TimestampSmoother.Track? = null

    override fun format(format: Format) = inner.format(format)

    override fun sampleData(input: DataReader, length: Int, allowEndOfInput: Boolean, sampleDataPart: Int): Int =
        inner.sampleData(input, length, allowEndOfInput, sampleDataPart)

    override fun sampleData(data: ParsableByteArray, length: Int, sampleDataPart: Int) =
        inner.sampleData(data, length, sampleDataPart)

    override fun durationUs(durationUs: Long) = inner.durationUs(durationUs)

    override fun sampleMetadata(timeUs: Long, flags: Int, size: Int, offset: Int, cryptoData: TrackOutput.CryptoData?) {
        val adjusted = if (timeUs == C.TIME_UNSET) timeUs else currentTrack().adjust(timeUs)
        inner.sampleMetadata(adjusted, flags, size, offset, cryptoData)
    }

    /** Starts afresh after the extractor seeks, which replaces its smoother. */
    private fun currentTrack(): TimestampSmoother.Track {
        val current = smoother()
        if (current !== owner) {
            owner = current
            track = current.track(drivesEpochs)
        }
        return track!!
    }
}
