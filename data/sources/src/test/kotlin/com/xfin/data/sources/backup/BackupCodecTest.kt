package com.xfin.data.sources.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupCodecTest {
    private val data = BackupData(
        createdAt = 1_791_950_000_000,
        sources = listOf(
            BackupSource(id = 5, name = "Provider", type = "XTREAM", url = "http://provider.example:8080", username = "user", secret = "s3cret!"),
            BackupSource(id = 6, name = "Playlist", type = "M3U", url = "https://example.com/list.m3u", epgUrl = "https://example.com/epg.xml.gz"),
        ),
        favorites = listOf(BackupChannelRef(5, "stream:42", 1_791_000_000_000)),
        history = listOf(BackupChannelRef(6, "url:abc", 1_791_000_100_000)),
        hiddenGroups = listOf("UK| SPORTS"),
        vodProgress = listOf(BackupVodProgress(5, "movie:9", 60_000, 5_400_000, false, 1_791_000_200_000, "movie", "A Film")),
        preferences = mapOf("last_filter" to mapOf("guide" to "group:News")),
    )

    @Test
    fun roundTrips() {
        val bytes = BackupCodec.encode(data, "correct horse", iterations = 1_000)
        assertEquals(data, BackupCodec.decode(bytes, "correct horse"))
    }

    @Test
    fun secretsAreNotStoredInPlainText() {
        val text = BackupCodec.encode(data, "correct horse", iterations = 1_000).toString(Charsets.UTF_8)
        assertFalse(text.contains("s3cret!"))
        assertFalse(text.contains("provider.example"))
    }

    @Test
    fun wrongPasswordIsReported() {
        val bytes = BackupCodec.encode(data, "correct horse", iterations = 1_000)
        val e = assertThrows(BackupException::class.java) { BackupCodec.decode(bytes, "battery staple") }
        assertEquals("Wrong password, or the file is damaged", e.message)
    }

    @Test
    fun otherFilesAreRejected() {
        val e = assertThrows(BackupException::class.java) { BackupCodec.decode("#EXTM3U\n".toByteArray(), "x") }
        assertEquals("This isn't an xFIN backup file", e.message)
    }

    @Test
    fun absurdIterationCountsAreRejected() {
        val text = BackupCodec.encode(data, "pw", iterations = 1_000).toString(Charsets.UTF_8)
            .replace("\"iterations\":1000", "\"iterations\":2000000000")
        assertThrows(BackupException::class.java) { BackupCodec.decode(text.toByteArray(), "pw") }
    }
}
