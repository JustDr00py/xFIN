package com.xfin.data.jellyfin

import com.xfin.core.model.LibraryView
import com.xfin.core.model.MediaItem
import com.xfin.core.model.MediaKind
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
    )
}

internal fun BaseItemDto.toLibraryView(api: ApiClient, sourceId: Long) = LibraryView(
    id = id.toString(),
    sourceId = sourceId,
    name = name.orEmpty(),
    collectionType = collectionType?.serialName,
    imageUrl = imageTags?.get(ImageType.PRIMARY)?.let {
        api.imageApi.getItemImageUrl(itemId = id, imageType = ImageType.PRIMARY, fillWidth = THUMB_WIDTH, quality = 90, tag = it)
    },
)
