package com.xfin.feature.settings

/**
 * Matches channel group names against what the user types.
 *
 * Words match at word starts, so "us" finds "US| NEWS" and "+6H USA" but not "AUSTRIA" or "MUSIC".
 * Several words must all match ("us sport"); commas separate alternatives ("us, ca, uk").
 * With [startsWith], an alternative must match the start of the name ("us" → only "US| …").
 */
internal object GroupFilter {

    fun matches(name: String, query: String, startsWith: Boolean): Boolean {
        val alternatives = query.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (alternatives.isEmpty()) return true
        val lower = name.lowercase()
        return alternatives.any { alt ->
            val terms = alt.lowercase().split(Regex("\\s+"))
            if (startsWith) {
                lower.startsWith(terms.first()) && terms.drop(1).all { lower.hasWordStartingWith(it) }
            } else {
                terms.all { lower.hasWordStartingWith(it) }
            }
        }
    }

    private fun String.hasWordStartingWith(term: String): Boolean {
        var from = 0
        while (true) {
            val i = indexOf(term, from)
            if (i < 0) return false
            if (i == 0 || !this[i - 1].isLetterOrDigit()) return true
            from = i + 1
        }
    }
}
