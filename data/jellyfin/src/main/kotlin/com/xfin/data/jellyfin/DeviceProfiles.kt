package com.xfin.data.jellyfin

import android.content.Context
import android.hardware.display.DisplayManager
import android.media.MediaCodecInfo
import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaCodecList
import android.view.Display
import org.jellyfin.sdk.model.api.CodecProfile
import org.jellyfin.sdk.model.api.CodecType
import org.jellyfin.sdk.model.api.DeviceProfile
import org.jellyfin.sdk.model.api.DirectPlayProfile
import org.jellyfin.sdk.model.api.DlnaProfileType
import org.jellyfin.sdk.model.api.EncodingContext
import org.jellyfin.sdk.model.api.MediaStreamProtocol
import org.jellyfin.sdk.model.api.ProfileCondition
import org.jellyfin.sdk.model.api.ProfileConditionType
import org.jellyfin.sdk.model.api.ProfileConditionValue
import org.jellyfin.sdk.model.api.SubtitleDeliveryMethod
import org.jellyfin.sdk.model.api.SubtitleProfile
import org.jellyfin.sdk.model.api.TranscodingProfile
import org.jellyfin.sdk.model.api.VideoRangeType

/**
 * Tells the server exactly what this device can play natively, so it direct-plays when it can and
 * transcodes when it must. Codecs, maximum resolution and 10-bit support come from the device's
 * real decoders; HDR and Dolby Vision are only accepted when the display can show them (otherwise
 * the server tone-maps). Audio relies on Media3 plus the FFmpeg extension, so it is broad.
 */
internal object DeviceProfiles {

    @Volatile
    private var cached: DeviceProfile? = null

    fun profile(context: Context): DeviceProfile = cached ?: build(context).also { cached = it }

    private class VideoSupport(val codec: String, val maxWidth: Int, val maxHeight: Int, val tenBit: Boolean)

    private fun build(context: Context): DeviceProfile {
        val decoders = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filterNot { it.isEncoder }
        val video = listOfNotNull(
            support(decoders, "video/avc", "h264", tenBitProfile = CodecProfileLevel.AVCProfileHigh10),
            support(decoders, "video/hevc", "hevc", tenBitProfile = CodecProfileLevel.HEVCProfileMain10),
            support(decoders, "video/x-vnd.on2.vp9", "vp9", tenBitProfile = CodecProfileLevel.VP9Profile2),
            support(decoders, "video/av01", "av1", tenBitProfile = CodecProfileLevel.AV1ProfileMain10),
            support(decoders, "video/mpeg2", "mpeg2video", tenBitProfile = null),
            support(decoders, "video/mp4v-es", "mpeg4", tenBitProfile = null),
        )
        val hdrTypes = displayHdrTypes(context)
        val dolbyVision = decoders.any { info -> info.supportedTypes.any { it.equals("video/dolby-vision", true) } } &&
            Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION in hdrTypes
        val hdr10 = Display.HdrCapabilities.HDR_TYPE_HDR10 in hdrTypes
        val hlg = Display.HdrCapabilities.HDR_TYPE_HLG in hdrTypes
        val ranges = buildList {
            add(VideoRangeType.SDR)
            add(VideoRangeType.DOVI_WITH_SDR)
            if (hdr10) addAll(listOf(VideoRangeType.HDR10, VideoRangeType.HDR10_PLUS, VideoRangeType.DOVI_WITH_HDR10, VideoRangeType.DOVI_WITH_HDR10_PLUS))
            if (hlg) addAll(listOf(VideoRangeType.HLG, VideoRangeType.DOVI_WITH_HLG))
            if (dolbyVision) addAll(listOf(VideoRangeType.DOVI, VideoRangeType.DOVI_WITH_EL, VideoRangeType.DOVI_WITH_ELHDR10_PLUS))
        }.joinToString("|") { it.serialName }

        val audio = "aac,mp3,mp2,ac3,eac3,flac,alac,opus,vorbis,dts,truehd,pcm_s16le,pcm_s24le"
        val text = listOf("srt", "subrip", "vtt", "webvtt", "ass", "ssa", "ttml")
        val bitmap = listOf("pgs", "pgssub", "dvdsub", "dvbsub")
        val hevc = video.firstOrNull { it.codec == "hevc" }

        return DeviceProfile(
            name = CLIENT_NAME,
            maxStreamingBitrate = MAX_BITRATE,
            maxStaticBitrate = MAX_BITRATE,
            musicStreamingTranscodingBitrate = 384_000,
            directPlayProfiles = listOf(
                DirectPlayProfile(
                    container = "mp4,m4v,mov,mkv,webm,ts,mpegts,m2ts,avi",
                    videoCodec = video.joinToString(",") { it.codec },
                    audioCodec = audio,
                    type = DlnaProfileType.VIDEO,
                ),
                DirectPlayProfile(container = "mp3,aac,m4a,flac,ogg,opus,wav", type = DlnaProfileType.AUDIO),
            ),
            transcodingProfiles = listOf(
                TranscodingProfile(
                    container = "ts",
                    type = DlnaProfileType.VIDEO,
                    videoCodec = if (hevc != null) "hevc,h264" else "h264",
                    audioCodec = "aac,ac3,eac3,mp3",
                    protocol = MediaStreamProtocol.HLS,
                    context = EncodingContext.STREAMING,
                    maxAudioChannels = "6",
                    minSegments = 1,
                    breakOnNonKeyFrames = true,
                    conditions = emptyList(),
                ),
            ),
            containerProfiles = emptyList(),
            codecProfiles = video.map { v ->
                CodecProfile(
                    type = CodecType.VIDEO,
                    codec = v.codec,
                    conditions = listOf(
                        condition(ProfileConditionType.LESS_THAN_EQUAL, ProfileConditionValue.WIDTH, v.maxWidth.toString()),
                        condition(ProfileConditionType.LESS_THAN_EQUAL, ProfileConditionValue.HEIGHT, v.maxHeight.toString()),
                        condition(ProfileConditionType.LESS_THAN_EQUAL, ProfileConditionValue.VIDEO_BIT_DEPTH, if (v.tenBit) "10" else "8"),
                        condition(ProfileConditionType.EQUALS_ANY, ProfileConditionValue.VIDEO_RANGE_TYPE, ranges),
                    ),
                    applyConditions = emptyList(),
                )
            },
            subtitleProfiles = text.map { SubtitleProfile(format = it, method = SubtitleDeliveryMethod.EXTERNAL) } +
                (text + bitmap).map { SubtitleProfile(format = it, method = SubtitleDeliveryMethod.EMBED) },
        )
    }

    private fun condition(type: ProfileConditionType, property: ProfileConditionValue, value: String) =
        ProfileCondition(condition = type, property = property, value = value, isRequired = true)

    /** Largest common frame size any decoder for [mime] handles in real time, or null if none. */
    private fun support(decoders: List<MediaCodecInfo>, mime: String, codec: String, tenBitProfile: Int?): VideoSupport? {
        val capabilities = decoders.mapNotNull { info ->
            info.supportedTypes.firstOrNull { it.equals(mime, true) }?.let { runCatching { info.getCapabilitiesForType(it) }.getOrNull() }
        }
        if (capabilities.isEmpty()) return null
        val sizes = listOf(3840 to 2160, 2560 to 1440, 1920 to 1080, 1280 to 720)
        val (width, height) = sizes.firstOrNull { (w, h) ->
            capabilities.any { caps -> caps.videoCapabilities?.areSizeAndRateSupported(w, h, 24.0) == true }
        } ?: (1280 to 720)
        val tenBit = tenBitProfile != null && capabilities.any { caps -> caps.profileLevels.any { it.profile == tenBitProfile } }
        return VideoSupport(codec, width, height, tenBit)
    }

    private fun displayHdrTypes(context: Context): Set<Int> {
        val display = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY) ?: return emptySet()
        @Suppress("DEPRECATION")
        return display.hdrCapabilities?.supportedHdrTypes?.toSet().orEmpty()
    }

    const val MAX_BITRATE = 120_000_000
}
