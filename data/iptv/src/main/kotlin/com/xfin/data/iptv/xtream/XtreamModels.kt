package com.xfin.data.iptv.xtream

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class XtreamCredentials(val baseUrl: String, val username: String, val password: String) {
    companion object {
        /**
         * Normalizes whatever the user typed: `host:port`, a trailing slash, a `player_api.php`
         * URL, or a full `get.php?username=..&password=..` playlist link (credentials extracted).
         */
        fun normalize(server: String, username: String, password: String): XtreamCredentials? {
            val withScheme = server.trim().let {
                if (it.startsWith("http://", true) || it.startsWith("https://", true)) it else "http://$it"
            }
            val url = withScheme.toHttpUrlOrNull() ?: return null
            val user = username.ifBlank { url.queryParameter("username").orEmpty() }
            val pass = password.ifBlank { url.queryParameter("password").orEmpty() }
            if (user.isBlank() || pass.isBlank()) return null
            return XtreamCredentials(url.origin(), user.trim(), pass.trim())
        }

        private fun HttpUrl.origin(): String {
            val defaultPort = HttpUrl.defaultPort(scheme)
            return if (port == defaultPort) "$scheme://$host" else "$scheme://$host:$port"
        }
    }
}

data class XtreamAccount(
    val status: String?,
    val expiresAt: Long?,
    val maxConnections: Int?,
    val activeConnections: Int?,
    /** The provider's server timezone (e.g. "Europe/London"); archive start times use it. */
    val timezone: String? = null,
)

data class XtreamCategory(val id: String, val name: String)

data class XtreamLiveStream(
    val streamId: Long,
    val number: Int?,
    val name: String,
    val icon: String?,
    val epgChannelId: String?,
    val categoryId: String?,
    val archiveDays: Int,
    val directSource: String?,
)

class XtreamAuthException(message: String) : Exception(message)
