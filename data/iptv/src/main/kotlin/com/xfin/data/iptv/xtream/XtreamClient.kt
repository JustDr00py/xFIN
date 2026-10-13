package com.xfin.data.iptv.xtream

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.decodeToSequence
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject

@OptIn(ExperimentalSerializationApi::class)
class XtreamClient @Inject constructor(private val http: OkHttpClient) {

    suspend fun authenticate(creds: XtreamCredentials): XtreamAccount =
        get(creds, action = null) { XtreamParsing.account(XtreamParsing.json.decodeFromStream<JsonElement>(it)) }

    suspend fun liveCategories(creds: XtreamCredentials): List<XtreamCategory> =
        get(creds, "get_live_categories") {
            XtreamParsing.categories(XtreamParsing.json.decodeFromStream<JsonElement>(it))
        }

    /** Downloads the (often multi-megabyte) live stream list to a temp file; parse with [parseLiveStreams]. */
    suspend fun downloadLiveStreams(creds: XtreamCredentials): File = get(creds, "get_live_streams") { input ->
        val file = File.createTempFile("xtream_live", ".json")
        try {
            file.outputStream().use { input.copyTo(it) }
        } catch (e: Throwable) {
            file.delete()
            throw e
        }
        file
    }

    suspend fun vodCategories(creds: XtreamCredentials): List<XtreamCategory> =
        get(creds, "get_vod_categories") { XtreamParsing.categories(XtreamParsing.json.decodeFromStream<JsonElement>(it)) }

    suspend fun seriesCategories(creds: XtreamCredentials): List<XtreamCategory> =
        get(creds, "get_series_categories") { XtreamParsing.categories(XtreamParsing.json.decodeFromStream<JsonElement>(it)) }

    /** Downloads a large list action (get_vod_streams, get_series) to a temp file. */
    suspend fun download(creds: XtreamCredentials, action: String): File = get(creds, action) { input ->
        val file = File.createTempFile("xtream_$action", ".json")
        try {
            file.outputStream().use { input.copyTo(it) }
        } catch (e: Throwable) {
            file.delete()
            throw e
        }
        file
    }

    suspend fun movieInfo(creds: XtreamCredentials, streamId: Long): XtreamMovieInfo? =
        get(creds, "get_vod_info", mapOf("vod_id" to streamId.toString())) {
            XtreamVodParsing.movieInfo(XtreamParsing.json.decodeFromStream<JsonElement>(it))
        }

    suspend fun seriesInfo(creds: XtreamCredentials, seriesId: Long): XtreamSeriesInfo? =
        get(creds, "get_series_info", mapOf("series_id" to seriesId.toString())) {
            XtreamVodParsing.seriesInfo(XtreamParsing.json.decodeFromStream<JsonElement>(it))
        }

    private suspend fun <T> get(
        creds: XtreamCredentials,
        action: String?,
        extra: Map<String, String> = emptyMap(),
        read: (InputStream) -> T,
    ): T =

        withContext(Dispatchers.IO) {
            val url = apiUrl(creds, action).newBuilder().apply { extra.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
            val request = Request.Builder().url(url).build()
            http.newCall(request).execute().use { response ->
                response.requireSuccess()
                response.body.byteStream().use(read)
            }
        }

    private fun Response.requireSuccess() {
        when {
            code == 401 || code == 403 -> throw XtreamAuthException("Invalid username or password")
            !isSuccessful -> throw IOException("Server returned HTTP $code")
        }
    }

    companion object {
        fun parseMovies(file: File, onMovie: (XtreamMovie) -> Unit) = parseArray(file) { XtreamVodParsing.movie(it)?.let(onMovie) }

        fun parseSeries(file: File, onSeries: (XtreamSeries) -> Unit) = parseArray(file) { XtreamVodParsing.series(it)?.let(onSeries) }

        private fun parseArray(file: File, onElement: (JsonElement) -> Unit) {
            file.inputStream().buffered().use { input ->
                XtreamParsing.json.decodeToSequence<JsonElement>(input).forEach(onElement)
            }
        }

        fun movieUrl(creds: XtreamCredentials, streamId: Long, extension: String): String =
            "${creds.baseUrl}/movie/${creds.username.encodePath()}/${creds.password.encodePath()}/$streamId.$extension"

        fun episodeUrl(creds: XtreamCredentials, episodeId: Long, extension: String): String =
            "${creds.baseUrl}/series/${creds.username.encodePath()}/${creds.password.encodePath()}/$episodeId.$extension"

        /** Streams a downloaded live list element by element, in constant memory. */
        fun parseLiveStreams(file: File, onStream: (XtreamLiveStream) -> Unit) {
            file.inputStream().buffered().use { input ->
                XtreamParsing.json.decodeToSequence<JsonElement>(input).forEach { element ->
                    XtreamParsing.liveStream(element)?.let(onStream)
                }
            }
        }

        fun apiUrl(creds: XtreamCredentials, action: String?): HttpUrl =
            "${creds.baseUrl}/player_api.php".toHttpUrl().newBuilder()
                .addQueryParameter("username", creds.username)
                .addQueryParameter("password", creds.password)
                .apply { if (action != null) addQueryParameter("action", action) }
                .build()

        fun xmltvUrl(creds: XtreamCredentials): String =
            "${creds.baseUrl}/xmltv.php".toHttpUrl().newBuilder()
                .addQueryParameter("username", creds.username)
                .addQueryParameter("password", creds.password)
                .build()
                .toString()

        fun liveStreamUrl(creds: XtreamCredentials, streamId: String, extension: String): String =
            "${creds.baseUrl}/live/${creds.username.encodePath()}/${creds.password.encodePath()}/$streamId.$extension"

        private fun String.encodePath(): String =
            HttpUrl.Builder().scheme("http").host("x").addPathSegment(this).build().encodedPathSegments.first()
    }
}
