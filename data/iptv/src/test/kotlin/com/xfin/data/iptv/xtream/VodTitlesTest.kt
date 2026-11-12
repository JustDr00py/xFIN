package com.xfin.data.iptv.xtream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VodTitlesTest {
    @Test
    fun `provider spellings of one movie share a key`() {
        val a = VodTitles.groupKey("Mamma Mia! - 2008", null)
        assertEquals(a, VodTitles.groupKey("EN | Mamma Mia!", 2008))
        assertEquals(a, VodTitles.groupKey("EN - Mamma Mia! (2008)", null))
        assertEquals(a, VodTitles.groupKey("EN| Mamma Mia! 4K", 2008))
        assertEquals(a, VodTitles.groupKey("[EN] Mamma Mia! [MULTI-SUB]", 2008))
        assertEquals(a, VodTitles.groupKey("EN ▎ Mamma Mia!", 2008))
        assertEquals(a, VodTitles.groupKey("EN - Mamma Mia! - 2008 [MULTI-SUB]", null))
        assertEquals(
            VodTitles.groupKey("EN ▎ Mamma Mia! I Have A Dream", 2023),
            VodTitles.groupKey("EN - Mamma Mia! I Have A Dream (2023)", 2023),
        )
    }

    @Test
    fun `sequels and remakes stay separate`() {
        assertNotEquals(
            VodTitles.groupKey("EN | Mamma Mia!", 2008),
            VodTitles.groupKey("EN | Mamma Mia! Here We Go Again", 2018),
        )
        assertNotEquals(
            VodTitles.groupKey("EN | The Magnificent Seven", 1960),
            VodTitles.groupKey("EN | The Magnificent Seven", 2016),
        )
    }

    @Test
    fun `a year only matches the same year`() {
        assertNotEquals(VodTitles.groupKey("Dune", null), VodTitles.groupKey("Dune", 2021))
        assertEquals(VodTitles.groupKey("EN - Dune", null), VodTitles.groupKey("Dune [MULTI-SUB]", null))
    }

    @Test
    fun `ordinary titles keep their start`() {
        assertEquals("up|2009", VodTitles.groupKey("Up", 2009))
        assertEquals("e t the extra terrestrial|1982", VodTitles.groupKey("E.T. the Extra-Terrestrial", 1982))
        assertEquals("it|2017", VodTitles.groupKey("EN | It", 2017))
    }

    @Test
    fun `a title that is a year keeps it`() {
        assertEquals("1917|2019", VodTitles.groupKey("EN | 1917", 2019))
        assertEquals(VodTitles.groupKey("1917 - 2019", null), VodTitles.groupKey("EN | 1917", 2019))
    }

    @Test
    fun `accents case and punctuation are ignored`() {
        assertEquals(VodTitles.groupKey("Amélie", 2001), VodTitles.groupKey("EN - AMELIE", 2001))
        assertEquals(VodTitles.groupKey("Fast & Furious", 2009), VodTitles.groupKey("Fast and Furious", 2009))
    }

    @Test
    fun `display titles drop provider decoration`() {
        assertEquals("Mamma Mia!", VodTitles.displayTitle("EN ▎ Mamma Mia!"))
        assertEquals("Mamma Mia!", VodTitles.displayTitle("EN - Mamma Mia! - 2008 [MULTI-SUB]"))
        assertEquals("Mamma Mia!", VodTitles.displayTitle("EN - Mamma Mia! 4K [MULTI-SUB] - 2008"))
        assertEquals("Mamma Mia! I Have A Dream", VodTitles.displayTitle("EN - Mamma Mia! I Have A Dream (2023)"))
        assertEquals("The Magnificent Seven", VodTitles.displayTitle("EN | The Magnificent Seven"))
        assertEquals("Spider-Man: No Way Home", VodTitles.displayTitle("Spider-Man: No Way Home"))
        assertEquals("1917", VodTitles.displayTitle("EN | 1917"))
        assertEquals("Up", VodTitles.displayTitle("Up"))
        assertEquals("[4K]", VodTitles.displayTitle("[4K]"))
    }

    @Test
    fun `quality labels`() {
        assertEquals("4K", VodTitles.qualityLabel("EN | David", "EN | 4K MOVIES"))
        assertEquals("FHD", VodTitles.qualityLabel("Dune 1080p", null))
        assertEquals("4K", VodTitles.qualityLabel("Dune UHD", "Movies"))
        assertNull(VodTitles.qualityLabel("EN | Mamma Mia!", "EN | APPLE TV+"))
    }
}
