package com.xfin.core.model

/** A live channel from any source. Everything above the data layer is source-agnostic. */
data class Channel(
    val id: Long,
    val sourceId: Long,
    /** Stable identity within a source; survives playlist refreshes (favorites, history). */
    val key: String,
    val name: String,
    val number: Int?,
    val logoUrl: String?,
    val group: String,
    val tvgId: String?,
    val catchup: Catchup?,
    val isFavorite: Boolean = false,
    /** Resolved guide channel id (by tvg-id or name), null when the channel has no guide data. */
    val epgId: String? = null,
    /** The program airing when this channel was loaded. */
    val nowPlaying: ProgramSlot? = null,
)

/** Lightweight "what's on" summary carried with channel lists. */
data class ProgramSlot(val title: String, val start: Long, val end: Long) {
    fun progress(now: Long): Float =
        if (end <= start) 0f else ((now - start).toFloat() / (end - start)).coerceIn(0f, 1f)
}

data class Catchup(
    /** default | append | shift | flussonic | xc */
    val mode: String,
    val source: String?,
    val days: Int,
) {
    /** Whether a program that started at [start] can be played from the archive at [now]. */
    fun covers(start: Long, now: Long): Boolean =
        start < now && (days <= 0 || start >= now - days * 86_400_000L)
}

data class ChannelGroup(
    val name: String,
    val channelCount: Int,
    val hidden: Boolean = false,
)

/** Which list of channels the user is browsing; also defines zapping order in the player. */
sealed interface ChannelFilter {
    data object All : ChannelFilter
    data object Favorites : ChannelFilter
    data object Recent : ChannelFilter
    data class Group(val name: String) : ChannelFilter
}

/** Everything the player needs to open a stream. */
data class PlayableStream(
    val url: String,
    val userAgent: String? = null,
    val referrer: String? = null,
    val headers: Map<String, String> = emptyMap(),
)

/** Compact string form for navigation arguments. */
fun ChannelFilter.encode(): String = when (this) {
    ChannelFilter.All -> "all"
    ChannelFilter.Favorites -> "fav"
    ChannelFilter.Recent -> "recent"
    is ChannelFilter.Group -> "group:$name"
}

fun decodeChannelFilter(value: String): ChannelFilter = when {
    value == "fav" -> ChannelFilter.Favorites
    value == "recent" -> ChannelFilter.Recent
    value.startsWith("group:") -> ChannelFilter.Group(value.removePrefix("group:"))
    else -> ChannelFilter.All
}
