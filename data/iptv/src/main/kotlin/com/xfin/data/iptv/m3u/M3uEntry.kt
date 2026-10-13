package com.xfin.data.iptv.m3u

data class M3uEntry(
    val name: String,
    val url: String,
    val tvgId: String? = null,
    val tvgName: String? = null,
    val logoUrl: String? = null,
    val group: String? = null,
    val number: Int? = null,
    val catchupMode: String? = null,
    val catchupSource: String? = null,
    val catchupDays: Int = 0,
    val userAgent: String? = null,
    val referrer: String? = null,
)

data class M3uHeader(
    /** EPG URLs advertised by the playlist (url-tvg / x-tvg-url), may be comma separated. */
    val epgUrls: List<String> = emptyList(),
    val catchupMode: String? = null,
    val catchupSource: String? = null,
    val catchupDays: Int = 0,
)
