package com.xfin.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class GroupFilterTest {
    private val groups = listOf(
        "US| NEWS ᴴᴰ/ᴿᴬᵂ ⁶⁰ᶠᵖˢ",
        "US| SPORT ᴴᴰ/ᴿᴬᵂ ⁶⁰ᶠᵖˢ",
        "AR| EGYPT +6H USA ⎋",
        "AT| AUSTRIA HD/4K",
        "AR| MUSIC 4K ▶ موسيقى",
        "CA| MUSIC FR",
        "UK| SPORTS",
    )

    private fun find(query: String, startsWith: Boolean = false) = groups.filter { GroupFilter.matches(it, query, startsWith) }

    @Test fun matchesWordStartsOnly() =
        assertEquals(listOf("US| NEWS ᴴᴰ/ᴿᴬᵂ ⁶⁰ᶠᵖˢ", "US| SPORT ᴴᴰ/ᴿᴬᵂ ⁶⁰ᶠᵖˢ", "AR| EGYPT +6H USA ⎋"), find("us"))

    @Test fun startsWithAnchorsToTheName() =
        assertEquals(listOf("US| NEWS ᴴᴰ/ᴿᴬᵂ ⁶⁰ᶠᵖˢ", "US| SPORT ᴴᴰ/ᴿᴬᵂ ⁶⁰ᶠᵖˢ"), find("US", startsWith = true))

    @Test fun commasAreAlternatives() =
        assertEquals(listOf("US| NEWS ᴴᴰ/ᴿᴬᵂ ⁶⁰ᶠᵖˢ", "US| SPORT ᴴᴰ/ᴿᴬᵂ ⁶⁰ᶠᵖˢ", "CA| MUSIC FR", "UK| SPORTS"), find("us, ca ,uk", startsWith = true))

    @Test fun spacesNarrow() = assertEquals(listOf("US| SPORT ᴴᴰ/ᴿᴬᵂ ⁶⁰ᶠᵖˢ", "UK| SPORTS"), find("sport"))

    @Test fun allWordsMustMatch() = assertEquals(listOf("US| SPORT ᴴᴰ/ᴿᴬᵂ ⁶⁰ᶠᵖˢ"), find("us sport", startsWith = true))

    @Test fun emptyMatchesEverything() = assertEquals(groups, find("  , "))
}
