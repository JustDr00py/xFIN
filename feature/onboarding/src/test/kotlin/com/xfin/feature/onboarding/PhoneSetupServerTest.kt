package com.xfin.feature.onboarding

import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL

class PhoneSetupServerTest {
    private val received = mutableListOf<PhoneSetupForm>()
    private var opened = false
    private lateinit var server: PhoneSetupServer
    private var port = 0

    @Before
    fun setUp() {
        server = PhoneSetupServer(
            page = "<html>form</html>",
            prefill = { PhoneSetupPrefill(kind = "XTREAM", editing = true, url = "http://p.tv", username = "bob", hasPassword = true) },
            onOpened = { opened = true },
            onSubmit = { form ->
                received += form
                if (form.password == "wrong") PhoneSetupReply(false, "Bad login") else PhoneSetupReply(true, "Saved")
            },
        )
        port = server.start(preferredPort = 0)
    }

    @After
    fun tearDown() = server.stop()

    private fun call(path: String, body: String? = null): Pair<Int, String> {
        val conn = URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
        if (body != null) {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toByteArray()) }
        }
        val code = conn.responseCode
        val text = (if (code < 400) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        return code to text
    }

    @Test
    fun `requests without the token are refused`() {
        assertEquals(403, call("/").first)
        assertEquals(403, call("/info?t=nope").first)
        assertEquals(403, call("/submit?t=nope", """{"password":"x"}""").first)
        assertFalse(opened)
        assertTrue(received.isEmpty())
    }

    @Test
    fun `serves the page and the prefill without the password`() {
        val (code, page) = call("/?t=${server.token}")
        assertEquals(200, code)
        assertEquals("<html>form</html>", page)
        assertTrue(opened)

        val (_, info) = call("/info?t=${server.token}")
        val prefill = Json.decodeFromString(PhoneSetupPrefill.serializer(), info)
        assertEquals("bob", prefill.username)
        assertTrue(prefill.hasPassword)
        assertFalse(info.contains("password\""))
    }

    @Test
    fun `passes the submitted form on and returns the answer`() {
        val (code, reply) = call("/submit?t=${server.token}", """{"url":"http://new.tv","username":"amy","password":"wrong","extra":1}""")
        assertEquals(200, code)
        assertEquals(PhoneSetupReply(false, "Bad login"), Json.decodeFromString(PhoneSetupReply.serializer(), reply))
        assertEquals(PhoneSetupForm(url = "http://new.tv", username = "amy", password = "wrong"), received.single())

        val (_, ok) = call("/submit?t=${server.token}", """{"url":"http://new.tv","username":"amy","password":"right"}""")
        assertTrue(Json.decodeFromString(PhoneSetupReply.serializer(), ok).ok)
    }

    @Test
    fun `stops answering once stopped`() {
        server.stop()
        val failed = runCatching { call("/?t=${server.token}") }.isFailure
        assertTrue(failed)
    }

    @Test
    fun `parses query strings and bodies`() {
        val raw = "POST /submit?t=a%2Bb&x HTTP/1.1\r\nHost: tv\r\ncontent-length: 4\r\n\r\nbody"
        val request = PhoneSetupServer.readRequest(ByteArrayInputStream(raw.toByteArray()))!!
        assertEquals("POST", request.method)
        assertEquals("/submit", request.path)
        assertEquals("a+b", request.query["t"])
        assertEquals("body", request.body)
    }

    @Test
    fun `rejects oversized bodies`() {
        val raw = "POST /submit HTTP/1.1\r\nContent-Length: 999999\r\n\r\n"
        assertEquals(null, PhoneSetupServer.readRequest(ByteArrayInputStream(raw.toByteArray())))
    }
}
