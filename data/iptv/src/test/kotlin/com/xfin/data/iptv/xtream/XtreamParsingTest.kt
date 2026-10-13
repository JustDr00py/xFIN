package com.xfin.data.iptv.xtream

import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XtreamParsingTest {

    private fun json(text: String) = XtreamParsing.json.parseToJsonElement(text)

    @Test
    fun `account accepts string typed numbers`() {
        val account = XtreamParsing.account(
            json("""{"user_info":{"auth":1,"status":"Active","exp_date":"1767225600","max_connections":"2"}}"""),
        )
        assertEquals(1767225600000L, account.expiresAt)
        assertEquals(2, account.maxConnections)
    }

    @Test(expected = XtreamAuthException::class)
    fun `auth zero is rejected`() {
        XtreamParsing.account(json("""{"user_info":{"auth":0}}"""))
    }

    @Test(expected = XtreamAuthException::class)
    fun `expired account is rejected`() {
        XtreamParsing.account(json("""{"user_info":{"auth":1,"status":"Expired"}}"""))
    }

    @Test
    fun `live stream maps lenient fields`() {
        val stream = XtreamParsing.liveStream(
            json(
                """{"num":"3","name":" News ","stream_id":"101","stream_icon":"","epg_channel_id":null,
                "category_id":"7","tv_archive":"1","tv_archive_duration":"5"}""",
            ),
        )!!
        assertEquals(101L, stream.streamId)
        assertEquals(3, stream.number)
        assertEquals("News", stream.name)
        assertNull(stream.icon)
        assertNull(stream.epgChannelId)
        assertEquals(5, stream.archiveDays)
    }

    @Test
    fun `stream without id is skipped`() {
        assertNull(XtreamParsing.liveStream(json("""{"name":"x"}""") as JsonElement))
    }

    @Test
    fun `credentials are normalized from playlist link`() {
        val creds = XtreamCredentials.normalize("example.com:8080/get.php?username=u&password=p&type=m3u", "", "")!!
        assertEquals("http://example.com:8080", creds.baseUrl)
        assertEquals("u", creds.username)
        assertEquals("p", creds.password)
    }

    @Test
    fun `stream url encodes credentials`() {
        val creds = XtreamCredentials("http://h:80", "user", "p/w")
        assertEquals("http://h:80/live/user/p%2Fw/9.ts", XtreamClient.liveStreamUrl(creds, "9", "ts"))
    }
}
