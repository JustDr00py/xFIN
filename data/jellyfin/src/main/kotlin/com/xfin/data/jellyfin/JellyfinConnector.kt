package com.xfin.data.jellyfin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.extensions.authenticationApi
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.model.api.AuthenticateUserByName
import org.jellyfin.sdk.model.api.AuthenticationResult
import org.jellyfin.sdk.model.api.QuickConnectDto
import java.io.IOException
import javax.inject.Inject

data class JellyfinServer(
    val url: String,
    val id: String,
    val name: String,
    val version: String,
    val quickConnectEnabled: Boolean,
)

data class JellyfinSession(
    val server: JellyfinServer,
    val userId: String,
    val userName: String,
    val accessToken: String,
)

data class QuickConnectCode(val code: String, val secret: String)

class JellyfinException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** Finding a server and signing in, before a source exists. */
class JellyfinConnector @Inject constructor(private val sdk: JellyfinSdk) {

    suspend fun probe(input: String): JellyfinServer = io {
        val url = normalizeServerUrl(input)
        val candidates = buildList {
            add(url)
            // Typed without a scheme and https failed: many home servers only speak http.
            if (!input.trim().startsWith("http", ignoreCase = true)) add(url.replaceFirst("https://", "http://"))
        }
        var last: Throwable? = null
        for (candidate in candidates) {
            try {
                val api = sdk.api(candidate)
                val info = api.systemApi.getPublicSystemInfo().content
                val quickConnect = runCatching { api.authenticationApi.getQuickConnectEnabled().content }.getOrDefault(false)
                return@io JellyfinServer(
                    url = candidate,
                    id = info.id.orEmpty(),
                    name = info.serverName?.ifBlank { null } ?: "Jellyfin",
                    version = info.version.orEmpty(),
                    quickConnectEnabled = quickConnect,
                )
            } catch (e: Exception) {
                last = e
            }
        }
        throw JellyfinException(friendly(last, "Couldn't reach a Jellyfin server at $url"), last)
    }

    suspend fun login(server: JellyfinServer, username: String, password: String): JellyfinSession = io {
        try {
            val result = sdk.api(server.url).authenticationApi
                .authenticateUserByName(AuthenticateUserByName(username = username.trim(), pw = password))
                .content
            session(server, result)
        } catch (e: InvalidStatusException) {
            throw JellyfinException(if (e.status == 401) "Wrong username or password" else "Sign-in failed (${e.status})", e)
        }
    }

    suspend fun startQuickConnect(server: JellyfinServer): QuickConnectCode = io {
        val result = sdk.api(server.url).authenticationApi.initiateQuickConnect().content
        QuickConnectCode(result.code, result.secret)
    }

    suspend fun isQuickConnectApproved(server: JellyfinServer, code: QuickConnectCode): Boolean = io {
        sdk.api(server.url).authenticationApi.getQuickConnectState(code.secret).content.authenticated
    }

    suspend fun finishQuickConnect(server: JellyfinServer, code: QuickConnectCode): JellyfinSession = io {
        val result = sdk.api(server.url).authenticationApi
            .authenticateWithQuickConnect(QuickConnectDto(secret = code.secret))
            .content
        session(server, result)
    }

    private fun session(server: JellyfinServer, result: AuthenticationResult): JellyfinSession {
        val user = result.user ?: throw JellyfinException("Server did not return a user")
        val token = result.accessToken ?: throw JellyfinException("Server did not return an access token")
        return JellyfinSession(
            server = server.copy(id = result.serverId ?: server.id),
            userId = user.id.toString(),
            userName = user.name.orEmpty(),
            accessToken = token,
        )
    }

    private fun friendly(e: Throwable?, fallback: String): String = when (e) {
        is java.net.UnknownHostException -> "Server not found. Check the address."
        is javax.net.ssl.SSLException -> "Secure connection failed. Check the address or certificate."
        is java.net.SocketTimeoutException -> "The server took too long to respond."
        is InvalidStatusException -> "That address doesn't look like a Jellyfin server (${e.status})."
        else -> fallback
    }

    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }
}
