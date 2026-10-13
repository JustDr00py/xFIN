package com.xfin.data.iptv.epg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.kxml2.io.KXmlParser
import java.time.Instant

class XmltvParserTest {

    private val xml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE tv SYSTEM "xmltv.dtd">
        <tv generator-info-name="test">
          <channel id="cnn.us">
            <display-name>CNN</display-name>
            <display-name>CNN HD</display-name>
            <icon src="http://logo/cnn.png"/>
          </channel>
          <channel id="bbc1.uk"><display-name>BBC One</display-name></channel>
          <programme start="20261012180000 +0000" stop="20261012190000 +0000" channel="cnn.us">
            <title lang="en">Evening News</title>
            <sub-title>Top stories</sub-title>
            <desc lang="en">The day's headlines &amp; analysis.</desc>
            <category>News</category>
            <episode-num system="xmltv_ns">1.4.</episode-num>
            <icon src="http://img/news.jpg"/>
            <credits><actor>Someone</actor></credits>
          </programme>
          <programme start="20261012190000 +0200" channel="bbc1.uk">
            <title><![CDATA[Doctor Who]]></title>
          </programme>
          <programme start="20261012200000" stop="20261012210000" channel="skip.me">
            <title>Skipped</title><desc>nested <b>markup</b></desc>
          </programme>
          <programme start="bad" stop="20261012210000" channel="cnn.us"><title>Bad time</title></programme>
        </tv>
    """.trimIndent()

    private fun parse(accept: (String) -> Boolean = { true }): Pair<List<XmltvChannel>, List<XmltvProgramme>> {
        val parser = KXmlParser().apply { setInput(xml.reader()) }
        val channels = mutableListOf<XmltvChannel>()
        val programmes = mutableListOf<XmltvProgramme>()
        XmltvParser.parse(parser, channels::add, { id, _, _ -> accept(id) }, programmes::add)
        return channels to programmes
    }

    @Test
    fun `reads channels with all display names`() {
        val (channels, _) = parse()
        assertEquals(listOf("cnn.us", "bbc1.uk"), channels.map { it.id })
        assertEquals(listOf("CNN", "CNN HD"), channels[0].displayNames)
        assertEquals("http://logo/cnn.png", channels[0].iconUrl)
    }

    @Test
    fun `reads programme fields and skips unknown children`() {
        val (_, programmes) = parse()
        val news = programmes.first { it.title == "Evening News" }
        assertEquals(Instant.parse("2026-10-12T18:00:00Z").toEpochMilli(), news.start)
        assertEquals(Instant.parse("2026-10-12T19:00:00Z").toEpochMilli(), news.stop)
        assertEquals("Top stories", news.subtitle)
        assertEquals("The day's headlines & analysis.", news.description)
        assertEquals("News", news.category)
        assertEquals("S2 E5", news.episode)
        assertEquals("http://img/news.jpg", news.iconUrl)
    }

    @Test
    fun `offset is applied and missing stop defaults to 30 minutes`() {
        val (_, programmes) = parse()
        val who = programmes.first { it.title == "Doctor Who" }
        assertEquals(Instant.parse("2026-10-12T17:00:00Z").toEpochMilli(), who.start)
        assertEquals(who.start + 30 * 60_000, who.stop)
    }

    @Test
    fun `rejected and malformed programmes are skipped without breaking the stream`() {
        val (_, programmes) = parse { it != "skip.me" }
        assertEquals(listOf("Evening News", "Doctor Who"), programmes.map { it.title })
    }

    @Test
    fun `time without offset is utc and short times are rejected`() {
        assertEquals(Instant.parse("2026-01-02T03:04:00Z").toEpochMilli(), XmltvParser.parseTime("202601020304"))
        assertEquals(Instant.parse("2026-01-02T08:34:05Z").toEpochMilli(), XmltvParser.parseTime("20260102030405 -0530"))
        assertNull(XmltvParser.parseTime("2026"))
    }

    @Test
    fun `channel names normalize for matching`() {
        assertEquals("cnn", channelNameKey("US| CNN HD (1080p)"))
        assertEquals("cnn", channelNameKey("CNN"))
        assertEquals("bbcone", channelNameKey("BBC One FHD"))
        assertEquals("tf1", channelNameKey("FR: TF1 [Backup]"))
    }
}

class ChannelNameKeyTest {
    @Test
    fun `quality markers prefixes and punctuation are removed`() {
        assertEquals("espn2", channelNameKey("US| ESPN 2 FHD"))
        assertEquals("skysportsmainevent", channelNameKey("UK: Sky Sports Main Event H.265"))
        assertEquals("canalplus", channelNameKey("Canal+ Plus [VIP] (Backup)"))
        assertEquals("discovery", channelNameKey("  DE| Discovery 1080p 50fps"))
        assertEquals("tf1", channelNameKey("TF1"))
        assertEquals("rtlh2", channelNameKey("RTL h 2"))
        assertEquals("caféfr", channelNameKey("Café FR"))
    }
}
