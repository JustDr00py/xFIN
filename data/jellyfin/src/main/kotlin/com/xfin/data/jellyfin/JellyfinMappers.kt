package com.xfin.data.jellyfin

import com.xfin.core.model.LibraryView
import com.xfin.core.model.MediaItem
import com.xfin.core.model.MediaKind
import com.xfin.core.model.MediaVersion
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.VideoRangeType
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import java.util.UUID

internal const val TICKS_PER_MS = 10_000L

/** Image sizes requested from the server; it resizes, so lists never download full artwork. */
private const val POSTER_HEIGHT = 600
private const val THUMB_WIDTH = 640
private const val BACKDROP_WIDTH = 1920

internal fun BaseItemDto.toMediaItem(api: ApiClient, sourceId: Long): MediaItem {
    val kind = when (type) {
        BaseItemKind.MOVIE -> MediaKind.MOVIE
        BaseItemKind.SERIES -> MediaKind.SERIES
        BaseItemKind.SEASON -> MediaKind.SEASON
        BaseItemKind.EPISODE -> MediaKind.EPISODE
        BaseItemKind.BOX_SET, BaseItemKind.FOLDER, BaseItemKind.COLLECTION_FOLDER -> MediaKind.COLLECTION
        else -> MediaKind.OTHER
    }
    val primaryTag = imageTags?.get(ImageType.PRIMARY)
    fun image(id: UUID?, type: ImageType, tag: String?, height: Int? = null, width: Int? = null, index: Int? = null) =
        if (id == null || tag == null) {
            null
        } else {
            api.imageApi.getItemImageUrl(
                itemId = id,
                imageType = type,
                fillHeight = height,
                fillWidth = width,
                quality = 90,
                tag = tag,
                imageIndex = index,
            )
        }

    val poster = when (kind) {
        // Episodes and seasons show their series' poster in poster rows.
        MediaKind.EPISODE -> image(seriesId, ImageType.PRIMARY, seriesPrimaryImageTag, height = POSTER_HEIGHT)
        else -> image(id, ImageType.PRIMARY, primaryTag, height = POSTER_HEIGHT)
    } ?: image(id, ImageType.PRIMARY, primaryTag, height = POSTER_HEIGHT)

    val backdrop = backdropImageTags?.firstOrNull()?.let { image(id, ImageType.BACKDROP, it, width = BACKDROP_WIDTH, index = 0) }
        ?: parentBackdropImageTags?.firstOrNull()?.let {
            image(parentBackdropItemId, ImageType.BACKDROP, it, width = BACKDROP_WIDTH, index = 0)
        }

    val thumb = when (kind) {
        MediaKind.EPISODE -> image(id, ImageType.PRIMARY, primaryTag, width = THUMB_WIDTH)
        else -> imageTags?.get(ImageType.THUMB)?.let { image(id, ImageType.THUMB, it, width = THUMB_WIDTH) }
    } ?: parentThumbImageTag?.let { image(parentThumbItemId, ImageType.THUMB, it, width = THUMB_WIDTH) }
        ?: backdropImageTags?.firstOrNull()?.let { image(id, ImageType.BACKDROP, it, width = THUMB_WIDTH, index = 0) }
        ?: parentBackdropImageTags?.firstOrNull()?.let {
            image(parentBackdropItemId, ImageType.BACKDROP, it, width = THUMB_WIDTH, index = 0)
        }

    val logo = imageTags?.get(ImageType.LOGO)?.let { image(id, ImageType.LOGO, it, width = 800) }
        ?: parentLogoImageTag?.let { image(parentLogoItemId, ImageType.LOGO, it, width = 800) }

    return MediaItem(
        id = id.toString(),
        sourceId = sourceId,
        kind = kind,
        name = name.orEmpty(),
        overview = overview,
        year = productionYear,
        runtimeMs = runTimeTicks?.div(TICKS_PER_MS),
        positionMs = (userData?.playbackPositionTicks ?: 0) / TICKS_PER_MS,
        played = userData?.played == true,
        isFavorite = userData?.isFavorite == true,
        posterUrl = poster,
        backdropUrl = backdrop,
        thumbUrl = thumb,
        logoUrl = logo,
        seriesId = seriesId?.toString(),
        seriesName = seriesName,
        seasonId = seasonId?.toString(),
        seasonNumber = if (kind == MediaKind.EPISODE) parentIndexNumber else indexNumber,
        episodeNumber = if (kind == MediaKind.EPISODE) indexNumber else null,
        communityRating = communityRating,
        officialRating = officialRating,
        genres = genres.orEmpty(),
        unplayedCount = userData?.unplayedItemCount,
        versions = mediaSources.orEmpty().mapIndexedNotNull { index, source -> source.toVersion(index) },
    )
}

private fun MediaSourceInfo.toVersion(index: Int): MediaVersion? {
    val id = id ?: return null
    val video = mediaStreams?.firstOrNull { it.type == MediaStreamType.VIDEO }
    val audio = mediaStreams?.firstOrNull { it.type == MediaStreamType.AUDIO && it.isDefault }
        ?: mediaStreams?.firstOrNull { it.type == MediaStreamType.AUDIO }
    val resolution = video?.let { v ->
        val w = v.width ?: 0
        val h = v.height ?: 0
        when {
            w >= 3200 || h >= 2000 -> "4K"
            w >= 2200 || h >= 1300 -> "1440p"
            w >= 1800 || h >= 1000 -> "1080p"
            w >= 1200 || h >= 700 -> "720p"
            h > 0 -> "${h}p"
            else -> null
        }
    }
    val range = when (video?.videoRangeType) {
        VideoRangeType.DOVI, VideoRangeType.DOVI_WITH_EL, VideoRangeType.DOVI_WITH_HDR10, VideoRangeType.DOVI_WITH_HLG,
        VideoRangeType.DOVI_WITH_SDR, VideoRangeType.DOVI_WITH_HDR10_PLUS, VideoRangeType.DOVI_WITH_ELHDR10_PLUS,
        -> "Dolby Vision"
        VideoRangeType.HDR10_PLUS -> "HDR10+"
        VideoRangeType.HDR10 -> "HDR10"
        VideoRangeType.HLG -> "HLG"
        else -> null
    }
    val audioLabel = audio?.let { a ->
        listOfNotNull(
            a.codec?.uppercase(),
            when (a.channels) {
                null -> null
                1 -> "Mono"
                2 -> "Stereo"
                6 -> "5.1"
                8 -> "7.1"
                else -> "${a.channels}ch"
            },
        ).joinToString(" ").ifEmpty { null }
    }
    val label = name?.ifBlank { null } ?: "Version ${index + 1}"
    // Many servers already put resolution, size and bitrate in the version name; don't repeat them.
    val lower = label.lowercase()
    fun mentioned(vararg words: String) = words.any { lower.contains(it) }
    val details = listOfNotNull(
        resolution?.takeUnless { mentioned("2160", "4k", "1440", "1080", "720", "480", "uhd") },
        video?.codec?.uppercase()?.replace("H264", "H.264")?.takeUnless { mentioned("hevc", "h.265", "h265", "x265", "h.264", "h264", "x264", "avc", "av1") },
        range?.takeUnless { mentioned("hdr", "dv", "dolby", "hlg") },
        audioLabel?.takeUnless { mentioned("truehd", "atmos", "dts", "ac3", "eac3", "aac", "flac") },
        size?.let(::formatSize)?.takeUnless { mentioned("gb", "mb") },
        bitrate?.let { "%.0f Mbps".format(it / 1_000_000.0) }?.takeUnless { mentioned("bps") },
    ).joinToString(" · ")
    return MediaVersion(id = id, name = label, details = details)
}

private fun formatSize(bytes: Long): String =
    if (bytes >= 1_000_000_000) "%.1f GB".format(bytes / 1_000_000_000.0) else "%.0f MB".format(bytes / 1_000_000.0)

internal fun BaseItemDto.toLibraryView(api: ApiClient, sourceId: Long) = LibraryView(
    id = id.toString(),
    sourceId = sourceId,
    name = name.orEmpty(),
    collectionType = collectionType?.serialName,
    imageUrl = imageTags?.get(ImageType.PRIMARY)?.let {
        api.imageApi.getItemImageUrl(itemId = id, imageType = ImageType.PRIMARY, fillWidth = THUMB_WIDTH, quality = 90, tag = it)
    },
)
