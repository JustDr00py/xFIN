package com.xfin.data.iptv.epg

import org.junit.Assume.assumeTrue
import org.junit.Test
import org.kxml2.io.KXmlParser
import java.io.File
import java.util.zip.GZIPInputStream

/** Parses a real guide when XFIN_EPG_SAMPLE points at one; skipped otherwise. */
class RealGuideSmokeTest {
    @Test
    fun `parses a real xmltv guide`() {
        val path = System.getenv("XFIN_EPG_SAMPLE")
        assumeTrue(path != null && File(path).exists())
        var channels = 0
        var programmes = 0
        val started = System.nanoTime()
        GZIPInputStream(File(path!!).inputStream()).bufferedReader().use { reader ->
            val parser = KXmlParser().apply { setInput(reader) }
            XmltvParser.parse(parser, { channels++ }, { _, _, _ -> true }) { programmes++ }
        }
        println("XMLTV sample: $channels channels, $programmes programmes in ${(System.nanoTime() - started) / 1_000_000} ms")
        check(channels > 0 && programmes > 0)
    }
}
