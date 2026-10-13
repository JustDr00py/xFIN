package com.xfin.data.iptv.m3u

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class M3uParserTest {

    private fun parse(text: String): Pair<M3uHeader?, List<M3uEntry>> {
        var header: M3uHeader? = null
        val entries = mutableListOf<M3uEntry>()
        M3uParser.parse(text.trimIndent().reader().buffered(), { header = it }) { entries += it }
        return header to entries
    }

    @Test
    fun `parses standard attributes and title`() {
        val (_, entries) = parse(
            """
            #EXTM3U
            #EXTINF:-1 tvg-id="bbc1.uk" tvg-name="BBC One" tvg-logo="http://logo/bbc1.png" group-title="UK",BBC One HD
            http://example.com/live/1.ts
            """,
        )
        val e = entries.single()
        assertEquals("BBC One HD", e.name)
        assertEquals("bbc1.uk", e.tvgId)
        assertEquals("http://logo/bbc1.png", e.logoUrl)
        assertEquals("UK", e.group)
        assertEquals("http://example.com/live/1.ts", e.url)
    }

    @Test
    fun `comma inside quoted attribute does not split title`() {
        val (_, entries) = parse(
            """
            #EXTINF:-1 group-title="News, Weather",Channel, With Comma
            http://x/1
            """,
        )
        assertEquals("News, Weather", entries.single().group)
        assertEquals("Channel, With Comma", entries.single().name)
    }

    @Test
    fun `header epg and catchup are inherited`() {
        val (header, entries) = parse(
            """
            #EXTM3U url-tvg="http://epg/a.xml.gz,http://epg/b.xml" catchup="shift" catchup-days="3"
            #EXTINF:-1,One
            http://x/1
            #EXTINF:-1 catchup="append" catchup-source="?utc={utc}" catchup-days="7",Two
            http://x/2
            """,
        )
        assertEquals(listOf("http://epg/a.xml.gz", "http://epg/b.xml"), header!!.epgUrls)
        assertEquals("shift", entries[0].catchupMode)
        assertEquals(3, entries[0].catchupDays)
        assertEquals("append", entries[1].catchupMode)
        assertEquals("?utc={utc}", entries[1].catchupSource)
        assertEquals(7, entries[1].catchupDays)
    }

    @Test
    fun `extgrp vlcopt and pipe headers`() {
        val (_, entries) = parse(
            """
            #EXTINF:-1,Grp Channel
            #EXTGRP:Sports
            #EXTVLCOPT:http-user-agent=MyAgent
            http://x/1
            #EXTINF:-1,Pipe Channel
            http://x/2|User-Agent=PipeAgent&Referer=http://ref
            """,
        )
        assertEquals("Sports", entries[0].group)
        assertEquals("MyAgent", entries[0].userAgent)
        assertEquals("http://x/2", entries[1].url)
        assertEquals("PipeAgent", entries[1].userAgent)
        assertEquals("http://ref", entries[1].referrer)
        assertNull(entries[1].group)
    }

    @Test
    fun `tolerates missing header bom blank lines and bare urls`() {
        val (header, entries) = parse(
            "﻿#EXTINF:-1 tvg-chno=12,Twelve\r\n\r\nhttp://x/12\r\nhttp://x/bare.m3u8\r\n",
        )
        assertNull(header)
        assertEquals(2, entries.size)
        assertEquals(12, entries[0].number)
        assertEquals("bare.m3u8", entries[1].name)
    }

    @Test
    fun `unquoted attribute values`() {
        val attrs = M3uParser.parseAttributes("""tvg-id=abc tvg-chno=5 group-title="A B"""")
        assertEquals("abc", attrs["tvg-id"])
        assertEquals("5", attrs["tvg-chno"])
        assertEquals("A B", attrs["group-title"])
    }

    @Test
    fun `parses large playlist in streaming fashion`() {
        val count = 50_000
        val text = buildString {
            append("#EXTM3U\n")
            repeat(count) { append("#EXTINF:-1 group-title=\"G${it % 20}\",Ch $it\nhttp://x/$it.ts\n") }
        }
        var n = 0
        M3uParser.parse(text.reader().buffered()) { n++ }
        assertEquals(count, n)
    }
}
