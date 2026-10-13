package com.xfin.data.iptv.m3u

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.GZIPInputStream
import javax.inject.Inject

/**
 * Loads M3U playlists from http(s) or a SAF content URI. Remote playlists are first downloaded to
 * a temp file so slow providers never hold a database transaction open during an import.
 */
class PlaylistLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val http: OkHttpClient,
) {
    /** A playlist ready for fast local parsing; [close] deletes any temp file. */
    inner class Staged internal constructor(private val location: String, private val file: File?) : AutoCloseable {
        fun parse(onHeader: (M3uHeader) -> Unit = {}, onEntry: (M3uEntry) -> Unit) {
            val input = file?.inputStream()
                ?: context.contentResolver.openInputStream(Uri.parse(location))
                ?: throw IOException("Cannot open $location")
            input.maybeGunzip(location).bufferedReader().use { M3uParser.parse(it, onHeader, onEntry) }
        }

        override fun close() {
            file?.delete()
        }
    }

    suspend fun stage(location: String, userAgent: String?): Staged = withContext(Dispatchers.IO) {
        when (Uri.parse(location).scheme?.lowercase()) {
            "content", "file" -> Staged(location, file = null)
            else -> {
                val request = Request.Builder().url(location)
                    .apply { if (!userAgent.isNullOrBlank()) header("User-Agent", userAgent) }
                    .build()
                val file = File.createTempFile("playlist", ".m3u", context.cacheDir)
                try {
                    http.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) throw IOException("Playlist request failed: HTTP ${response.code}")
                        file.outputStream().use { response.body.byteStream().copyTo(it) }
                    }
                } catch (e: Throwable) {
                    file.delete()
                    throw e
                }
                Staged(location, file)
            }
        }
    }

    private fun InputStream.maybeGunzip(location: String): InputStream =
        if (location.substringBefore('?').endsWith(".gz", ignoreCase = true)) GZIPInputStream(this) else this
}
