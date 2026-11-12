package com.xfin.data.iptv.xtream

import java.text.Normalizer

/**
 * Recognises the same movie or show listed several times by a provider ("Mamma Mia! - 2008",
 * "EN | Mamma Mia!", "EN - Mamma Mia! 4K"), so the copies can be offered as versions of one title.
 */
object VodTitles {
    /** "[EN] ", "|EN| ", "(EN) " at the start of a name. */
    private val LANG_BRACKET_PREFIX = Regex("""^\s*[|\[(]\s*[A-Z]{2,3}\s*[|\])]\s*""")
    /** "EN | ", "EN ▎ ", "EN - ", "EN: " at the start of a name: any symbol separates the code. */
    private val LANG_PREFIX = Regex("""^\s*[A-Z]{2,3}\s*[^\p{L}\p{N}\s'"(\[]+\s*""")
    // Android compiles these with ICU, which (unlike the JVM) rejects an unescaped "}": escape
    // every literal bracket and brace.
    private val BRACKETS = Regex("""\[[^\]]*\]|\{[^\}]*\}""")
    private val TRAILING_YEAR = Regex("""\s*[-–(\[]?\s*((?:19|20)\d{2})\s*[)\]]?\s*$""")
    private val QUALITY = Regex(
        """(?i)\b(?:4k|8k|uhd|fhd|hd|sd|hdr10\+?|hdr|dolby\s*vision|hevc|h\.?26[45]|x26[45]|2160p|1080p|720p|480p|multi[\s-]*subs?|multi|vostfr|dual[\s-]*audio)\b""",
    )
    private val MARKS = Regex("""\p{Mn}+""")
    private val NON_ALNUM = Regex("""[^a-z0-9]+""")
    private val LEADING_THE = Regex("""^the """)
    private val WHITESPACE = Regex("""\s+""")
    private val EMPTY_PARENS = Regex("""\(\s*\)""")
    private val RESOLUTIONS = setOf("8K", "4K", "UHD", "2160P", "FHD", "1080P", "HD", "720P", "SD")

    /**
     * Copies with the same key are versions of one title. A year only matches the same year; a key
     * without one ("title|") can still join a title's only dated key at import (see VodDao).
     */
    fun groupKey(name: String, year: Int?): String {
        val (title, nameYear) = clean(name)
        val normalized = Normalizer.normalize(title, Normalizer.Form.NFD)
            .replace(MARKS, "")
            .lowercase()
            .replace("&", " and ")
            .replace(NON_ALNUM, " ")
            .trim()
            .replace(LEADING_THE, "")
        return "${normalized.ifEmpty { name.trim().lowercase() }}|${year ?: nameYear ?: ""}"
    }

    /**
     * The name to show: without the language prefix, tags ("[MULTI-SUB]", "4K") and trailing year,
     * which the app shows on its own ("EN ▎ Mamma Mia! - 2008 [MULTI-SUB]" → "Mamma Mia!").
     */
    fun displayTitle(name: String): String = clean(name).first.ifEmpty { name.trim() }

    /** A short quality tag for naming a version ("4K", "FHD"), from its name or else its category. */
    fun qualityLabel(name: String, category: String?): String? {
        for (text in listOfNotNull(name, category)) {
            val best = QUALITY.findAll(text).map { it.value.uppercase() }.firstOrNull { it in RESOLUTIONS } ?: continue
            return when (best) {
                "2160P", "UHD" -> "4K"
                "1080P" -> "FHD"
                "720P" -> "HD"
                else -> best
            }
        }
        return null
    }

    /** The name without language prefix, tags and trailing year, plus that year. */
    private fun clean(name: String): Pair<String, Int?> {
        var s = name.replace(LANG_BRACKET_PREFIX, "").replace(LANG_PREFIX, "")
        s = s.replace(BRACKETS, " ").replace(QUALITY, " ").replace(WHITESPACE, " ").trim()
        var year: Int? = null
        TRAILING_YEAR.find(s)?.let { m ->
            val stripped = s.substring(0, m.range.first)
            // A title that is just a year ("1917", "2012") keeps it.
            if (stripped.isNotBlank()) {
                year = m.groupValues[1].toInt()
                s = stripped
            }
        }
        s = s.replace(EMPTY_PARENS, " ").replace(WHITESPACE, " ").trim().trimEnd('-', '–', '|', ':').trim()
        return s to year
    }
}
