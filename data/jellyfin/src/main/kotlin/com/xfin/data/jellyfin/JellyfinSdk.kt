package com.xfin.data.jellyfin

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import org.jellyfin.sdk.android.androidDevice
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.util.AuthorizationHeaderBuilder
import org.jellyfin.sdk.api.okhttp.OkHttpFactory
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import javax.inject.Inject
import javax.inject.Singleton

internal const val CLIENT_NAME = "xFIN"
internal const val CLIENT_VERSION = "0.1.0"

/** One Jellyfin SDK instance for the app, sharing the app's OkHttp connection pool. */
@Singleton
class JellyfinSdk @Inject constructor(@ApplicationContext context: Context, http: OkHttpClient) {
    private val device = androidDevice(context)
    private val factory = OkHttpFactory(http)

    private val jellyfin = createJellyfin {
        this.context = context
        clientInfo = ClientInfo(CLIENT_NAME, CLIENT_VERSION)
        deviceInfo = device
        apiClientFactory = factory
        socketConnectionFactory = factory
    }

    fun api(baseUrl: String, accessToken: String? = null): ApiClient = jellyfin.createApi(baseUrl, accessToken)

    /** `Authorization` header for requests made outside the SDK (ExoPlayer streams). */
    fun authorizationHeader(accessToken: String): String =
        AuthorizationHeaderBuilder.buildHeader(CLIENT_NAME, CLIENT_VERSION, device.id, device.name, accessToken)
}

/**
 * Accepts what people paste: `host`, `host:8096`, `https://host/`, or a browser URL like
 * `https://host/web/#/home.html`.
 */
fun normalizeServerUrl(input: String): String {
    var url = input.trim()
    if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
        url = "https://$url"
    }
    url = url.substringBefore('#').substringBefore('?')
    val web = url.indexOf("/web", startIndex = url.indexOf("://") + 3)
    if (web > 0) url = url.substring(0, web)
    return url.trimEnd('/')
}
