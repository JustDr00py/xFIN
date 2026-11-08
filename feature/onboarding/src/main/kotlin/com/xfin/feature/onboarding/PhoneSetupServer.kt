package com.xfin.feature.onboarding

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom

/** What the phone form starts with. The password is never sent back out; [hasPassword] says one is saved. */
@Serializable
internal data class PhoneSetupPrefill(
    val kind: String,
    val editing: Boolean,
    val name: String = "",
    val url: String = "",
    val username: String = "",
    val epgUrl: String = "",
    val hasPassword: Boolean = false,
)

/** What the phone sends. A blank password while editing keeps the saved one. */
@Serializable
internal data class PhoneSetupForm(
    val name: String = "",
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val epgUrl: String = "",
)

/** The TV's answer to a submitted form. */
@Serializable
internal data class PhoneSetupReply(val ok: Boolean, val message: String)

/**
 * A tiny HTTP server for typing a source's login on a phone instead of with the remote.
 *
 * It only answers requests carrying [token], which the TV shows inside the QR code, so other devices on the
 * network can't read the form or post to it. It runs only while the setup dialog is open.
 */
internal class PhoneSetupServer(
    private val page: String,
    private val prefill: () -> PhoneSetupPrefill,
    private val onOpened: () -> Unit,
    private val onSubmit: suspend (PhoneSetupForm) -> PhoneSetupReply,
    val token: String = newToken(),
) {
    @Volatile private var socket: ServerSocket? = null
    private val submitLock = Mutex()

    /**
     * Requests are served in a scope of their own rather than the caller's, so a reply still reaches the phone
     * when a successful save closes the screen (and its view model) while the request is in flight.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Starts listening and returns the port. */
    fun start(preferredPort: Int = DEFAULT_PORT): Int {
        val server = ServerSocket().apply { reuseAddress = true }
        try {
            server.bind(InetSocketAddress(preferredPort), BACKLOG)
        } catch (e: IOException) {
            server.bind(InetSocketAddress(0), BACKLOG)
        }
        socket = server
        scope.launch {
            while (true) {
                val client = try {
                    server.accept()
                } catch (e: IOException) {
                    break // closed by stop()
                }
                launch { client.use { handle(it) } }
            }
        }
        return server.localPort
    }

    /** Stops taking new requests. One already being answered finishes, bounded by the socket timeout. */
    fun stop() {
        runCatching { socket?.close() }
        socket = null
    }

    private suspend fun handle(client: Socket) {
        client.soTimeout = READ_TIMEOUT_MS
        val out = client.getOutputStream()
        val request = try {
            readRequest(BufferedInputStream(client.getInputStream()))
        } catch (e: IOException) {
            return
        } ?: return respond(out, 400, "text/plain", "Bad request")

        if (request.path == "/favicon.ico") return respond(out, 404, "text/plain", "")
        if (!tokenMatches(request.query["t"])) {
            return respond(out, 403, "text/html", EXPIRED_PAGE)
        }
        when {
            request.method == "GET" && request.path == "/" -> {
                onOpened()
                respond(out, 200, "text/html", page)
            }
            request.method == "GET" && request.path == "/info" ->
                respond(out, 200, "application/json", json.encodeToString(PhoneSetupPrefill.serializer(), prefill()))
            request.method == "POST" && request.path == "/submit" -> {
                val form = runCatching { json.decodeFromString(PhoneSetupForm.serializer(), request.body) }.getOrNull()
                    ?: return respond(out, 400, "application/json", reply(false, "The form couldn't be read. Reload the page."))
                // One login check at a time; a second phone tap waits for the first answer.
                val result = submitLock.withLock { onSubmit(form) }
                respond(out, 200, "application/json", json.encodeToString(PhoneSetupReply.serializer(), result))
            }
            else -> respond(out, 404, "text/plain", "Not found")
        }
    }

    private fun tokenMatches(candidate: String?): Boolean =
        candidate != null && MessageDigest.isEqual(candidate.toByteArray(), token.toByteArray())

    private fun reply(ok: Boolean, message: String) = json.encodeToString(PhoneSetupReply.serializer(), PhoneSetupReply(ok, message))

    private fun respond(out: OutputStream, status: Int, type: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val reason = when (status) {
            200 -> "OK"
            400 -> "Bad Request"
            403 -> "Forbidden"
            else -> "Not Found"
        }
        val head = buildString {
            append("HTTP/1.1 $status $reason\r\n")
            append("Content-Type: $type; charset=utf-8\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Cache-Control: no-store\r\n")
            append("Referrer-Policy: no-referrer\r\n")
            append("Connection: close\r\n\r\n")
        }
        try {
            out.write(head.toByteArray(Charsets.US_ASCII))
            out.write(bytes)
            out.flush()
        } catch (e: IOException) {
            // The phone went away; nothing to do.
        }
    }

    internal data class Request(val method: String, val path: String, val query: Map<String, String>, val body: String)

    companion object {
        const val DEFAULT_PORT = 8765
        private const val BACKLOG = 8
        private const val READ_TIMEOUT_MS = 15_000
        private const val MAX_HEADER_BYTES = 8 * 1024
        private const val MAX_BODY_BYTES = 16 * 1024
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        private const val EXPIRED_PAGE = """<!doctype html><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Link expired</title>
<body style="font-family:system-ui,sans-serif;padding:24px;max-width:480px;margin:auto">
<h2>This link has expired</h2><p>Choose <b>Set up from phone</b> on the TV again and scan the new code.</p></body>"""

        fun newToken(): String {
            val bytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
            return bytes.joinToString("") { "%02x".format(it) }
        }

        /** Parses one HTTP/1.1 request; null when it isn't one. */
        internal fun readRequest(input: InputStream): Request? {
            val requestLine = readLine(input) ?: return null
            val parts = requestLine.split(' ')
            if (parts.size < 3) return null
            var contentLength = 0
            var headerBytes = 0
            while (true) {
                val line = readLine(input) ?: return null
                if (line.isEmpty()) break
                headerBytes += line.length
                if (headerBytes > MAX_HEADER_BYTES) return null
                val colon = line.indexOf(':')
                if (colon > 0 && line.substring(0, colon).trim().equals("Content-Length", ignoreCase = true)) {
                    contentLength = line.substring(colon + 1).trim().toIntOrNull() ?: return null
                }
            }
            if (contentLength !in 0..MAX_BODY_BYTES) return null
            val body = ByteArray(contentLength)
            var read = 0
            while (read < contentLength) {
                val n = input.read(body, read, contentLength - read)
                if (n < 0) return null
                read += n
            }
            val target = parts[1]
            val q = target.indexOf('?')
            val path = if (q >= 0) target.substring(0, q) else target
            val query = if (q >= 0) parseQuery(target.substring(q + 1)) else emptyMap()
            return Request(parts[0].uppercase(), path, query, String(body, Charsets.UTF_8))
        }

        private fun parseQuery(query: String): Map<String, String> = query.split('&')
            .filter { it.isNotEmpty() }
            .associate {
                val eq = it.indexOf('=')
                val key = if (eq >= 0) it.substring(0, eq) else it
                val value = if (eq >= 0) it.substring(eq + 1) else ""
                runCatching { URLDecoder.decode(key, "UTF-8") to URLDecoder.decode(value, "UTF-8") }.getOrElse { key to value }
            }

        private fun readLine(input: InputStream): String? {
            val buffer = ByteArrayOutputStream()
            while (true) {
                val b = input.read()
                if (b < 0) return if (buffer.size() == 0) null else buffer.toString("US-ASCII")
                if (b == '\n'.code) break
                if (b != '\r'.code) buffer.write(b)
                if (buffer.size() > MAX_HEADER_BYTES) return null
            }
            return buffer.toString("US-ASCII")
        }
    }
}

/** The TV's LAN address, preferring Wi‑Fi and Ethernet over VPNs and the like. */
internal fun localIpv4Address(): String? = runCatching {
    NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        .filter { it.isUp && !it.isLoopback && !it.isVirtual }
        .sortedBy { if (it.name.startsWith("wlan") || it.name.startsWith("eth")) 0 else 1 }
        .flatMap { it.inetAddresses.toList() }
        .filterIsInstance<Inet4Address>()
        .firstOrNull { it.isSiteLocalAddress }
        ?.hostAddress
}.getOrNull()
