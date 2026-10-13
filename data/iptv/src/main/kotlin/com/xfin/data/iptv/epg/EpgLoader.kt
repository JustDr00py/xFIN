package com.xfin.data.iptv.epg

import android.content.Context
import android.util.Xml
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.GZIPInputStream
import javax.inject.Inject

/** Downloads XMLTV guides to a temp file and streams them through [XmltvParser]. */
class EpgLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val http: OkHttpClient,
) {
    suspend fun download(url: String, userAgent: String?): File = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url)
            .apply { if (!userAgent.isNullOrBlank()) header("User-Agent", userAgent) }
            .build()
        val file = File.createTempFile("epg", ".xml", context.cacheDir)
        try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Guide request failed: HTTP ${response.code}")
                file.outputStream().use { response.body.byteStream().copyTo(it) }
            }
        } catch (e: Throwable) {
            file.delete()
            throw e
        }
        file
    }

    fun parse(
        file: File,
        onChannel: (XmltvChannel) -> Unit,
        accept: (channelId: String, start: Long, stop: Long) -> Boolean,
        onProgram: (XmltvProgram) -> Unit,
    ) {
        openMaybeGzip(file).use { input ->
            val parser = Xml.newPullParser().apply {
                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                setInput(input, null)
            }
            XmltvParser.parse(parser, onChannel, accept, onProgram)
        }
    }

    /** Providers serve gzip with all sorts of names and content types, so sniff the magic bytes. */
    private fun openMaybeGzip(file: File): InputStream {
        val input = BufferedInputStream(file.inputStream(), 64 * 1024)
        input.mark(2)
        val gzip = input.read() == 0x1f && input.read() == 0x8b
        input.reset()
        return if (gzip) BufferedInputStream(GZIPInputStream(input, 64 * 1024), 64 * 1024) else input
    }
}
