package com.xfin.core.model

/** Kinds of on-demand items the library shows. */
enum class MediaKind { MOVIE, SERIES, SEASON, EPISODE, COLLECTION, OTHER }

/** A movie, show, season or episode from a library source (Jellyfin today). */
data class MediaItem(
    val id: String,
    val sourceId: Long,
    val kind: MediaKind,
    val name: String,
    val overview: String? = null,
    val year: Int? = null,
    val runtimeMs: Long? = null,
    val positionMs: Long = 0,
    val played: Boolean = false,
    val isFavorite: Boolean = false,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    /** Landscape still for episodes and "continue watching" cards. */
    val thumbUrl: String? = null,
    val logoUrl: String? = null,
    val seriesId: String? = null,
    val seriesName: String? = null,
    val seasonId: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val communityRating: Float? = null,
    val officialRating: String? = null,
    val genres: List<String> = emptyList(),
    val unplayedCount: Int? = null,
    /** Alternative files for the same title (e.g. 4K and 1080p); empty when not fetched. */
    val versions: List<MediaVersion> = emptyList(),
) {
    /** Fraction watched, for progress bars; 0 when unknown or not started. */
    val progress: Float
        get() = if (runtimeMs == null || runtimeMs <= 0) 0f else (positionMs.toFloat() / runtimeMs).coerceIn(0f, 1f)

    val canResume: Boolean get() = positionMs > 0 && !played

    /** "S1 E5" for episodes. */
    val episodeLabel: String?
        get() = if (kind != MediaKind.EPISODE) null else listOfNotNull(
            seasonNumber?.let { "S$it" },
            episodeNumber?.let { "E$it" },
        ).joinToString(" ").ifEmpty { null }
}

/** One playable file of a title, as offered by the version picker. */
data class MediaVersion(
    val id: String,
    /** Server-provided name, usually from the file name ("4K", "Director's Cut"). */
    val name: String,
    /** Resolution, codec, HDR, audio, size and bitrate, e.g. "4K · HEVC · Dolby Vision · 18.2 GB". */
    val details: String,
)

enum class SegmentType { INTRO, RECAP, OUTRO }

/** A part of a title the player offers to skip, from the server's intro and credits markers. */
data class MediaSegment(val type: SegmentType, val startMs: Long, val endMs: Long)

/** A top-level library ("Movies", "Shows") on a server. */
data class LibraryView(
    val id: String,
    val sourceId: Long,
    val name: String,
    /** Server collection type, e.g. "movies", "tvshows". */
    val collectionType: String?,
    val imageUrl: String?,
)

data class ExternalSubtitle(val url: String, val mimeType: String, val language: String?, val label: String)

/** Everything the VOD player needs to play an item and report progress. */
data class VodStream(
    val itemId: String,
    val sourceId: Long,
    val url: String,
    val headers: Map<String, String>,
    val mediaSourceId: String?,
    val playSessionId: String?,
    /** DirectPlay, DirectStream or Transcode. */
    val playMethod: String,
    val isHls: Boolean,
    val subtitles: List<ExternalSubtitle>,
    val startMs: Long,
)
