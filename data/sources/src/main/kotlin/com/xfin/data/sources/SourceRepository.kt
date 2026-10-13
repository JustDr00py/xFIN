package com.xfin.data.sources

import androidx.room.withTransaction
import com.xfin.core.database.XfinDatabase
import com.xfin.core.database.crypto.SecretCipher
import com.xfin.core.database.dao.ChannelDao
import com.xfin.core.database.dao.ProgramDao
import com.xfin.core.database.dao.SourceDao
import com.xfin.core.database.entity.ChannelEntity
import com.xfin.core.database.entity.SourceEntity
import com.xfin.core.model.RefreshState
import com.xfin.core.model.Source
import com.xfin.core.model.SourceType
import com.xfin.core.model.XtreamOutput
import com.xfin.data.iptv.m3u.PlaylistLoader
import com.xfin.data.iptv.xtream.XtreamCategory
import com.xfin.data.iptv.xtream.XtreamClient
import com.xfin.data.iptv.xtream.XtreamCredentials
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SourceRepository @Inject internal constructor(
    private val db: XfinDatabase,
    private val sourceDao: SourceDao,
    private val channelDao: ChannelDao,
    private val programDao: ProgramDao,
    private val cipher: SecretCipher,
    private val credentials: SourceCredentials,
    private val playlistLoader: PlaylistLoader,
    private val xtream: XtreamClient,
    private val epg: EpgRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _refreshState = MutableStateFlow<RefreshState>(RefreshState.Idle)
    val refreshState: StateFlow<RefreshState> = _refreshState.asStateFlow()

    fun observeSources(): Flow<List<Source>> = sourceDao.observeAll().map { list -> list.map { it.toModel() } }

    fun observeHasSources(): Flow<Boolean> = sourceDao.observeCount().map { it > 0 }

    /** Adds an M3U playlist and imports it; the source is removed again if the first import fails. */
    suspend fun addM3u(name: String, location: String, epgUrl: String?, userAgent: String?): Long =
        scope.async { addM3uSource(name, location, epgUrl, userAgent) }.await()

    private suspend fun addM3uSource(name: String, location: String, epgUrl: String?, userAgent: String?): Long {
        val id = sourceDao.insert(
            SourceEntity(
                name = name.ifBlank { defaultName(location) },
                type = SourceType.M3U.name,
                url = location.trim(),
                epgUrl = epgUrl?.trim()?.ifBlank { null },
                userAgent = userAgent?.trim()?.ifBlank { null },
            ),
        )
        return importOrRollback(id)
    }

    /** Validates Xtream credentials, then adds the source and imports live channels. */
    suspend fun addXtream(name: String, server: String, username: String, password: String, output: XtreamOutput): Long {
        val creds = XtreamCredentials.normalize(server, username, password)
            ?: throw IllegalArgumentException("Enter a valid server URL, username and password")
        xtream.authenticate(creds)
        return scope.async { addXtreamSource(name, creds, output) }.await()
    }

    private suspend fun addXtreamSource(name: String, creds: XtreamCredentials, output: XtreamOutput): Long {
        val id = sourceDao.insert(
            SourceEntity(
                name = name.ifBlank { defaultName(creds.baseUrl) },
                type = SourceType.XTREAM.name,
                url = creds.baseUrl,
                username = creds.username,
                passwordEnc = cipher.encrypt(creds.password),
                xtreamOutput = output.name,
            ),
        )
        return importOrRollback(id)
    }

    /** Re-imports the playlist, then the guide. */
    suspend fun refresh(sourceId: Long, withGuide: Boolean = true) {
        try {
            import(sourceId)
            if (withGuide) epg.launchRefresh(sourceId)
        } catch (e: Exception) {
            val message = e.message ?: e::class.simpleName.orEmpty()
            sourceDao.markFailed(sourceId, message)
            _refreshState.value = RefreshState.Failed(sourceId, message)
            throw e
        }
    }

    /** Re-imports playlists older than [maxAgeMs]; used by the background worker. */
    suspend fun refreshStale(maxAgeMs: Long) {
        val now = System.currentTimeMillis()
        sourceDao.getAll()
            .filter { it.type != SourceType.JELLYFIN.name && (it.lastRefreshedAt ?: 0) < now - maxAgeMs }
            .forEach { source -> runCatching { refresh(source.id, withGuide = false) } }
    }

    suspend fun rename(sourceId: Long, name: String) {
        sourceDao.get(sourceId)?.let { sourceDao.update(it.copy(name = name)) }
    }

    suspend fun delete(sourceId: Long) = sourceDao.delete(sourceId)

    private suspend fun importOrRollback(id: Long): Long {
        try {
            import(id)
        } catch (e: Exception) {
            sourceDao.delete(id)
            throw e
        }
        // The guide can be large; load it in the background so the user lands on channels at once.
        epg.launchRefresh(id)
        return id
    }

    /**
     * Replaces all channels of a source atomically. The playlist is downloaded to a temp file first
     * (outside any transaction, so the UI keeps reading), then parsed on an IO thread and fed through
     * a bounded channel into batched inserts: memory stays flat for huge playlists and a failure
     * rolls back to the previous channel list.
     *
     * Runs in the repository's own scope so leaving the screen that started it does not cancel it.
     */
    private suspend fun import(sourceId: Long) = scope.async { importNow(sourceId) }.await()

    private suspend fun importNow(sourceId: Long) {
        val source = sourceDao.get(sourceId) ?: return
        _refreshState.value = RefreshState.Running(sourceId, 0)
        val linker = EpgLinker(programDao.epgChannels(sourceId))
        try {
            when (SourceType.valueOf(source.type)) {
                SourceType.M3U -> playlistLoader.stage(source.url, source.userAgent).use { staged ->
                    var discoveredEpg: String? = null
                    replaceChannels(source) { emit -> produceM3u(source, staged, linker, emit) { discoveredEpg = it } }
                    if (source.epgUrl == null && discoveredEpg != null) {
                        sourceDao.get(sourceId)?.let { sourceDao.update(it.copy(epgUrl = discoveredEpg)) }
                    }
                }
                SourceType.XTREAM -> {
                    val creds = credentials.xtream(source)
                    val categories = xtream.liveCategories(creds)
                    val file = xtream.downloadLiveStreams(creds)
                    try {
                        replaceChannels(source) { emit -> produceXtream(source, categories, file, linker, emit) }
                    } finally {
                        file.delete()
                    }
                }
                SourceType.JELLYFIN -> Unit
            }
        } finally {
            if (_refreshState.value is RefreshState.Running) _refreshState.value = RefreshState.Idle
        }
    }

    private suspend fun replaceChannels(source: SourceEntity, produce: ((ChannelEntity) -> Unit) -> Unit) {
        var count = 0
        db.withTransaction {
            channelDao.deleteForSource(source.id)
            coroutineScope {
                val pipe = Channel<ChannelEntity>(capacity = BATCH_SIZE * 2)
                launch(Dispatchers.IO) {
                    try {
                        produce { pipe.trySendBlocking(it).getOrThrow() }
                        pipe.close()
                    } catch (e: Throwable) {
                        pipe.close(e)
                        throw e
                    }
                }
                val batch = ArrayList<ChannelEntity>(BATCH_SIZE)
                for (entity in pipe) {
                    batch += entity
                    if (batch.size == BATCH_SIZE) {
                        channelDao.insertAll(batch.toList())
                        count += batch.size
                        batch.clear()
                        _refreshState.value = RefreshState.Running(source.id, count)
                    }
                }
                if (batch.isNotEmpty()) channelDao.insertAll(batch)
                count += batch.size
            }
            if (count == 0) throw IllegalStateException("No channels found")
            sourceDao.markRefreshed(source.id, count, System.currentTimeMillis())
        }
    }

    private fun produceM3u(
        source: SourceEntity,
        staged: PlaylistLoader.Staged,
        linker: EpgLinker,
        emit: (ChannelEntity) -> Unit,
        onEpg: (String) -> Unit,
    ) {
        val keys = KeyAllocator()
        var index = 0
        staged.parse(onHeader = { header -> header.epgUrls.firstOrNull()?.let(onEpg) }) { entry ->
            emit(
                ChannelEntity(
                    sourceId = source.id,
                    channelKey = keys.allocate("${entry.tvgId.orEmpty()}|${entry.name}"),
                    name = entry.name,
                    number = entry.number,
                    logoUrl = entry.logoUrl,
                    groupName = entry.group ?: UNCATEGORIZED,
                    tvgId = entry.tvgId,
                    stream = entry.url,
                    catchupMode = entry.catchupMode,
                    catchupSource = entry.catchupSource,
                    catchupDays = entry.catchupDays,
                    userAgent = entry.userAgent,
                    referrer = entry.referrer,
                    sortIndex = index++,
                    epgId = linker.resolve(entry.tvgId, entry.name),
                ),
            )
        }
    }

    private fun produceXtream(
        source: SourceEntity,
        categoryList: List<XtreamCategory>,
        file: File,
        linker: EpgLinker,
        emit: (ChannelEntity) -> Unit,
    ) {
        val categories = categoryList.associate { it.id to it.name }
        // Keep the provider's category order rather than alphabetical.
        val categoryOrder = categoryList.withIndex().associate { it.value.id to it.index }
        var index = 0
        XtreamClient.parseLiveStreams(file) { stream ->
            emit(
                ChannelEntity(
                    sourceId = source.id,
                    channelKey = stream.streamId.toString(),
                    name = stream.name,
                    number = stream.number,
                    logoUrl = stream.icon,
                    groupName = stream.categoryId?.let(categories::get) ?: UNCATEGORIZED,
                    tvgId = stream.epgChannelId,
                    stream = stream.streamId.toString(),
                    catchupMode = if (stream.archiveDays > 0) "xc" else null,
                    catchupDays = stream.archiveDays,
                    sortIndex = (categoryOrder[stream.categoryId] ?: categoryOrder.size) * CATEGORY_STRIDE + index++,
                    epgId = linker.resolve(stream.epgChannelId, stream.name),
                ),
            )
        }
    }

    private fun defaultName(location: String): String =
        location.substringAfter("://").substringBefore('/').substringBefore('?').ifBlank { "Playlist" }

    private companion object {
        const val BATCH_SIZE = 500
        const val CATEGORY_STRIDE = 1_000_000
    }
}
