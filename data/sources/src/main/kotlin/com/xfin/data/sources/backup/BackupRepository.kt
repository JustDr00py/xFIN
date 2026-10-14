package com.xfin.data.sources.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.xfin.core.database.XfinDatabase
import com.xfin.core.database.crypto.SecretCipher
import com.xfin.core.database.dao.BackupDao
import com.xfin.core.database.dao.SourceDao
import com.xfin.core.database.entity.FavoriteEntity
import com.xfin.core.database.entity.HiddenGroupEntity
import com.xfin.core.database.entity.HistoryEntity
import com.xfin.core.database.entity.SourceEntity
import com.xfin.core.database.entity.VodProgressEntity
import com.xfin.core.model.SourceType
import com.xfin.data.sources.SourceRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class BackupSummary(val sources: Int, val favorites: Int)

data class RestoreSummary(val sourcesAdded: Int, val sourcesKept: Int, val favorites: Int)

/**
 * Saves the user's sources (with their logins), favorites, history, hidden groups, watch progress
 * and remembered choices to a password-encrypted file, and merges such a file back in.
 * Channels, guides and catalogues are not saved; restored sources download them again.
 */
@Singleton
class BackupRepository @Inject internal constructor(
    @ApplicationContext private val context: Context,
    private val db: XfinDatabase,
    private val sourceDao: SourceDao,
    private val backupDao: BackupDao,
    private val cipher: SecretCipher,
    private val sources: SourceRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    suspend fun export(uri: Uri, password: String): BackupSummary = withContext(Dispatchers.IO) {
        val data = snapshot()
        val bytes = BackupCodec.encode(data, password)
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
            ?: throw BackupException("Couldn't write the backup file")
        BackupSummary(sources = data.sources.size, favorites = data.favorites.size)
    }

    suspend fun restore(uri: Uri, password: String): RestoreSummary = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw BackupException("Couldn't open the backup file")
        val data = BackupCodec.decode(bytes, password)
        val merged = merge(data)
        restorePreferences(data.preferences)
        // Channels, guides and catalogues come from the providers again, one source at a time.
        scope.launch { merged.toImport.forEach { id -> runCatching { sources.refresh(id) } } }
        merged.summary
    }

    private suspend fun snapshot(): BackupData {
        val all = sourceDao.getAll()
        return BackupData(
            createdAt = System.currentTimeMillis(),
            sources = all.map { s ->
                BackupSource(
                    id = s.id,
                    name = s.name,
                    type = s.type,
                    url = s.url,
                    username = s.username,
                    secret = s.passwordEnc?.let { runCatching { cipher.decrypt(it) }.getOrNull() },
                    epgUrl = s.epgUrl,
                    userAgent = s.userAgent,
                    xtreamOutput = s.xtreamOutput,
                    createdAt = s.createdAt,
                    userId = s.userId,
                    serverId = s.serverId,
                )
            },
            favorites = backupDao.favorites().map { BackupChannelRef(it.sourceId, it.channelKey, it.addedAt) },
            history = backupDao.history().map { BackupChannelRef(it.sourceId, it.channelKey, it.watchedAt) },
            hiddenGroups = backupDao.hiddenGroups().map { it.name },
            vodProgress = backupDao.vodProgress().map { it.toBackup() },
            preferences = PREFERENCE_FILES.associateWith { name ->
                context.getSharedPreferences(name, Context.MODE_PRIVATE).all
                    .mapNotNull { (key, value) -> (value as? String)?.let { key to it } }
                    .toMap()
            },
        )
    }

    private class Merged(val summary: RestoreSummary, val toImport: List<Long>)

    /** Adds missing sources and the rows that belong to them. A source already on the device is reused. */
    private suspend fun merge(data: BackupData): Merged = db.withTransaction {
        val existing = sourceDao.getAll()
        val ids = HashMap<Long, Long>()
        val toImport = ArrayList<Long>()
        var added = 0
        var kept = 0
        for (s in data.sources) {
            if (runCatching { SourceType.valueOf(s.type) }.isFailure) continue
            val match = existing.firstOrNull {
                it.type == s.type && it.url == s.url && it.username == s.username && it.userId == s.userId
            }
            if (match != null) {
                ids[s.id] = match.id
                kept++
                continue
            }
            val id = sourceDao.insert(
                SourceEntity(
                    name = s.name,
                    type = s.type,
                    url = s.url,
                    username = s.username,
                    passwordEnc = s.secret?.let(cipher::encrypt),
                    epgUrl = s.epgUrl,
                    userAgent = s.userAgent,
                    xtreamOutput = s.xtreamOutput,
                    createdAt = s.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
                    userId = s.userId,
                    serverId = s.serverId,
                ),
            )
            ids[s.id] = id
            added++
            if (s.type != SourceType.JELLYFIN.name) toImport += id
        }
        val favorites = data.favorites.mapNotNull { f -> ids[f.sourceId]?.let { FavoriteEntity(it, f.channelKey, f.at) } }
        favorites.chunked(CHUNK).forEach { backupDao.insertFavorites(it) }
        data.history.mapNotNull { h -> ids[h.sourceId]?.let { HistoryEntity(it, h.channelKey, h.at) } }
            .chunked(CHUNK).forEach { backupDao.insertHistory(it) }
        data.hiddenGroups.map(::HiddenGroupEntity).chunked(CHUNK).forEach { backupDao.insertHiddenGroups(it) }
        data.vodProgress.mapNotNull { p -> ids[p.sourceId]?.let { p.toEntity(it) } }
            .chunked(CHUNK).forEach { backupDao.insertVodProgress(it) }
        Merged(RestoreSummary(sourcesAdded = added, sourcesKept = kept, favorites = favorites.size), toImport)
    }

    /** Remembered choices from the backup fill in what this device doesn't have yet. */
    private fun restorePreferences(preferences: Map<String, Map<String, String>>) {
        for ((name, values) in preferences) {
            if (name !in PREFERENCE_FILES) continue
            val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            val editor = prefs.edit()
            values.forEach { (key, value) -> if (!prefs.contains(key)) editor.putString(key, value) }
            editor.apply()
        }
    }

    private companion object {
        const val CHUNK = 500

        /** Small SharedPreferences files worth carrying over (see LastFilter, VersionPreferences). */
        val PREFERENCE_FILES = listOf("last_filter", "jellyfin_versions")
    }
}

private fun VodProgressEntity.toBackup() = BackupVodProgress(
    sourceId = sourceId,
    itemId = itemId,
    positionMs = positionMs,
    durationMs = durationMs,
    played = played,
    updatedAt = updatedAt,
    kind = kind,
    name = name,
    seriesId = seriesId,
    seriesName = seriesName,
    posterUrl = posterUrl,
    thumbUrl = thumbUrl,
    seasonNumber = seasonNumber,
    episodeNumber = episodeNumber,
)

private fun BackupVodProgress.toEntity(sourceId: Long) = VodProgressEntity(
    sourceId = sourceId,
    itemId = itemId,
    positionMs = positionMs,
    durationMs = durationMs,
    played = played,
    updatedAt = updatedAt,
    kind = kind,
    name = name,
    seriesId = seriesId,
    seriesName = seriesName,
    posterUrl = posterUrl,
    thumbUrl = thumbUrl,
    seasonNumber = seasonNumber,
    episodeNumber = episodeNumber,
)
