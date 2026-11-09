package com.xfin.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryNameTest {

    @Test
    fun `leading emoji is split from the label`() {
        assertEquals(CategoryName("⚽", "Sports"), CategoryName.of("⚽ Sports"))
        assertEquals(CategoryName("🎬", "Movies"), CategoryName.of("🎬Movies"))
        assertEquals(CategoryName("📺", "News"), CategoryName.of("📺 | News"))
    }

    @Test
    fun `multi code point emoji stay whole`() {
        assertEquals(CategoryName("🇺🇸", "USA"), CategoryName.of("🇺🇸 USA"))
        assertEquals(CategoryName("👨‍👩‍👧", "Family"), CategoryName.of("👨‍👩‍👧 Family"))
        assertEquals(CategoryName("❤️", "Love"), CategoryName.of("❤️ Love"))
        assertEquals(CategoryName("1️⃣", "One"), CategoryName.of("1️⃣ One"))
        assertEquals(CategoryName("👍🏽", "Top"), CategoryName.of("👍🏽 Top"))
    }

    @Test
    fun `plain names are unchanged`() {
        assertEquals(CategoryName(null, "Sports"), CategoryName.of("Sports"))
        assertEquals(CategoryName(null, "1 Sports"), CategoryName.of("1 Sports"))
        assertEquals(CategoryName(null, "#Trending"), CategoryName.of("#Trending"))
        assertEquals(CategoryName(null, "© Classics"), CategoryName.of("© Classics"))
        assertEquals(CategoryName(null, "Ünïcode"), CategoryName.of("Ünïcode"))
        assertEquals(CategoryName(null, ""), CategoryName.of(""))
    }

    @Test
    fun `emoji only name keeps its text`() {
        assertEquals(CategoryName(null, "⚽"), CategoryName.of("⚽"))
    }
}
