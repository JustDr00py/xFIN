package com.xfin.data.iptv.epg

private val QUALITY_TOKENS = hashSetOf(
    "uhd", "fhd", "hd", "sd", "4k", "8k", "hevc", "h265", "h264",
    "1080", "1080p", "1080i", "720p", "576", "576p", "576i", "480p", "50fps", "60fps", "backup", "alt",
)

/**
 * Normalizes a channel or guide display name for fuzzy matching:
 * "US| CNN HD (1080p)" and "CNN" both become "cnn".
 *
 * Hand-written rather than regex based: it runs for every channel of every import, and regexes
 * are slow enough on Android to dominate a 100k-channel import.
 */
fun channelNameKey(name: String): String {
    // 1. Lowercase and drop bracketed parts: "(1080p)", "[Backup]".
    val text = StringBuilder(name.length)
    var depth = 0
    for (raw in name) {
        when (raw) {
            '(', '[' -> depth++
            ')', ']' -> if (depth > 0) {
                depth--
                text.append(' ')
            }
            else -> if (depth == 0) text.append(raw.lowercaseChar())
        }
    }
    // 2. Skip a country prefix such as "us| " or "fr: ".
    var start = 0
    while (start < text.length && text[start].isWhitespace()) start++
    var letters = 0
    while (start + letters < text.length && text[start + letters] in 'a'..'z' && letters < 4) letters++
    if (letters in 2..3) {
        var i = start + letters
        while (i < text.length && text[i].isWhitespace()) i++
        if (i < text.length && (text[i] == '|' || text[i] == ':')) start = i + 1
    }
    // 3. Keep letter/digit tokens that are not quality markers ("h.265" arrives as "h" + "265").
    val out = StringBuilder(text.length)
    var i = start
    var pendingH = false
    while (i < text.length) {
        if (!text[i].isLetterOrDigit()) {
            i++
            continue
        }
        val tokenStart = i
        while (i < text.length && text[i].isLetterOrDigit()) i++
        val token = text.substring(tokenStart, i)
        if (pendingH) {
            pendingH = false
            if (token == "264" || token == "265") continue
            out.append('h')
        }
        when {
            token == "h" && i < text.length && text[i] == '.' -> pendingH = true
            token in QUALITY_TOKENS -> Unit
            else -> out.append(token)
        }
    }
    if (pendingH) out.append('h')
    return out.toString()
}

fun epgIdKey(id: String): String = id.trim().lowercase()
