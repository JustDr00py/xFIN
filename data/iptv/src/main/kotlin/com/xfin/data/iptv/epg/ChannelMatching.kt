package com.xfin.data.iptv.epg

private val QUALITY = Regex("""\b(uhd|fhd|hd|sd|4k|8k|hevc|h\.?265|h\.?264|1080[pi]?|720p|576[pi]?|480p|50fps|60fps|backup|alt)\b""")
private val BRACKETS = Regex("""[\[(][^\])]*[\])]""")
private val COUNTRY_PREFIX = Regex("""^[a-z]{2,3}\s*[|:]\s*""")
private val NON_ALNUM = Regex("""[^\p{L}\p{N}]+""")

/**
 * Normalizes a channel or guide display name for fuzzy matching:
 * "US| CNN HD (1080p)" and "CNN" both become "cnn".
 */
fun channelNameKey(name: String): String =
    name.lowercase()
        .replace(BRACKETS, " ")
        .replace(COUNTRY_PREFIX, "")
        .replace(QUALITY, " ")
        .replace(NON_ALNUM, "")

fun epgIdKey(id: String): String = id.trim().lowercase()
