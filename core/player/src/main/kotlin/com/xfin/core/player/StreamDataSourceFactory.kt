package com.xfin.core.player

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.xfin.core.model.PlayableStream
import com.xfin.core.network.DEFAULT_USER_AGENT
import okhttp3.OkHttpClient

/**
 * Creates OkHttp data sources carrying the headers of the stream currently being played.
 * IPTV providers often require a specific User-Agent or Referer per playlist or channel.
 */
@UnstableApi
internal class StreamDataSourceFactory(private val http: OkHttpClient) : DataSource.Factory {
    @Volatile
    var stream: PlayableStream? = null

    override fun createDataSource(): DataSource {
        val current = stream
        val headers = buildMap {
            current?.referrer?.let { put("Referer", it) }
            current?.headers?.let(::putAll)
        }
        return OkHttpDataSource.Factory(http)
            .setUserAgent(current?.userAgent ?: DEFAULT_USER_AGENT)
            .setDefaultRequestProperties(headers)
            .createDataSource()
    }
}
