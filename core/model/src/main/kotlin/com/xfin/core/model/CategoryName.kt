package com.xfin.core.model

/** A category name split into a leading emoji (if any) and the text to show beside it. */
data class CategoryName(val emoji: String?, val label: String) {
    companion object {
        /**
         * Splits "⚽ Sports" into ("⚽", "Sports"). Names without a leading emoji, or that are only
         * an emoji, come back unchanged with a null [emoji].
         */
        fun of(name: String): CategoryName {
            val end = leadingEmojiEnd(name)
            if (end == 0) return CategoryName(null, name)
            val rest = name.substring(end).trimStart(' ', '\t', '|', '-', ':', '•', '·', ' ')
            return if (rest.isBlank()) CategoryName(null, name) else CategoryName(name.substring(0, end), rest)
        }

        /** Length of the emoji cluster at the start of [text], or 0 when it does not start with one. */
        private fun leadingEmojiEnd(text: String): Int {
            if (text.isEmpty()) return 0
            val first = text.codePointAt(0)
            var i = Character.charCount(first)
            when {
                // Keycaps: 1️⃣ #️⃣ *️⃣
                first in '0'.code..'9'.code || first == '#'.code || first == '*'.code -> {
                    if (i < text.length && text.codePointAt(i) == VS16) i++
                    return if (i < text.length && text.codePointAt(i) == KEYCAP) i + 1 else 0
                }
                // Flags are a pair of regional indicators.
                first in REGIONAL -> {
                    return if (i < text.length && text.codePointAt(i) in REGIONAL) i + 2 else i
                }
                !isEmojiBase(first) -> {
                    // ©, ®, ™ and similar only count as emoji when followed by the emoji selector.
                    val symbol = Character.getType(first) == Character.OTHER_SYMBOL.toInt()
                    if (!symbol || i >= text.length || text.codePointAt(i) != VS16) return 0
                }
            }
            // Modifiers, selectors, tags and ZWJ sequences (👨‍👩‍👧, 🏳️‍🌈, 🏴󠁧󠁢󠁥󠁮󠁧󠁿).
            while (i < text.length) {
                val cp = text.codePointAt(i)
                when {
                    cp == VS16 || cp == KEYCAP || cp in SKIN_TONES || cp in TAGS -> i += Character.charCount(cp)
                    cp == ZWJ -> {
                        i += 1
                        if (i < text.length) i += Character.charCount(text.codePointAt(i))
                    }
                    else -> break
                }
            }
            return i
        }

        private fun isEmojiBase(cp: Int): Boolean =
            cp in 0x1F000..0x1FAFF || cp in 0x2600..0x27BF || cp in 0x2300..0x23FF ||
                cp in 0x2B00..0x2BFF || cp == 0x3030 || cp == 0x303D || cp == 0x3297 || cp == 0x3299

        private const val VS16 = 0xFE0F
        private const val ZWJ = 0x200D
        private const val KEYCAP = 0x20E3
        private val REGIONAL = 0x1F1E6..0x1F1FF
        private val SKIN_TONES = 0x1F3FB..0x1F3FF
        private val TAGS = 0xE0020..0xE007F
    }
}
