package com.xfin.core.model

enum class SourceType { M3U, XTREAM, JELLYFIN }

/** Container format requested from Xtream servers for live streams. */
enum class XtreamOutput(val extension: String) { TS("ts"), HLS("m3u8") }

data class Source(
    val id: Long,
    val name: String,
    val type: SourceType,
    /** Playlist URL / content URI (M3U) or server base URL (Xtream, Jellyfin). */
    val url: String,
    val username: String? = null,
    val epgUrl: String? = null,
    val userAgent: String? = null,
    val xtreamOutput: XtreamOutput = XtreamOutput.TS,
    val channelCount: Int = 0,
    val lastRefreshedAt: Long? = null,
    val lastError: String? = null,
    val epg: EpgStatus = EpgStatus(null, 0, null),
)

sealed interface RefreshState {
    data object Idle : RefreshState
    data class Running(val sourceId: Long, val itemsImported: Int) : RefreshState
    data class Failed(val sourceId: Long, val message: String) : RefreshState
}
