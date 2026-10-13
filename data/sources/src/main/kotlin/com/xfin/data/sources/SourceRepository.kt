package com.xfin.data.sources

import androidx.room.withTransaction
import com.xfin.core.database.XfinDatabase
import com.xfin.core.database.crypto.SecretCipher
import com.xfin.core.database.dao.ChannelDao
import com.xfin.core.database.dao.ProgramDao
import com.xfin.core.database.dao.SourceDao
import com.xfin.core.database.entity.StagedChannelEntity
import com.xfin.core.database.entity.SourceEntity
import com.xfin.core.model.RefreshState
import com.xfin.core.model.Source
import com.xfin.core.model.SourceType
import com.xfin.core.model.XtreamOutput
import com.xfin.data.iptv.epg.channelNameKey
import com.xfin.data.iptv.epg.epgIdKey
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
    private val vod: VodRepository,
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
            vod.launchRefresh(sourceId)
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

    /**
     * Removes a source. Its guide and channels are deleted in small slices first; a single
     * cascading delete of 100k channels and 700k programmes would hold the database for seconds.
     */
    suspend fun delete(sourceId: Long) = scope.async {
        Perf.time("delete source #$sourceId") {
            while (programDao.deleteOtherGenerations(sourceId, keep = -1, limit = 5_000) > 0) Unit
            while (channelDao.deleteSlice(sourceId, limit = 2_000) > 0) Unit
            sourceDao.delete(sourceId)
        }
    }.await()

    private suspend fun importOrRollback(id: Long): Long {
        try {
            import(id)
        } catch (e: Exception) {
            sourceDao.delete(id)
            throw e
        }
        // The guide and movie catalogue can be large; load them in the background.
        epg.launchRefresh(id)
        vod.launchRefresh(id)
        return id
    }

    /**
     * Replaces all channels of a source. The playlist is downloaded to a temp file and parsed on an
     * IO thread into a staging table in many short transactions, so the UI keeps reading the old
     * channels throughout. Only the final swap (copy, guide linking) runs in one transaction; a
     * failure anywhere before it leaves the previous channel list untouched.
     *
     * Runs in the repository's own scope so leaving the screen that started it does not cancel it.
     */
    private suspend fun import(sourceId: Long) = scope.async { importNow(sourceId) }.await()

    private suspend fun importNow(sourceId: Long) {
        val source = sourceDao.get(sourceId) ?: return
        _refreshState.value = RefreshState.Running(sourceId, 0)
        try {
            when (SourceType.valueOf(source.type)) {
                SourceType.M3U -> Perf.time("playlist download #$sourceId") { playlistLoader.stage(source.url, source.userAgent) }.use { staged ->
                    var discoveredEpg: String? = null
                    replaceChannels(source) { emit -> produceM3u(source, staged, emit) { discoveredEpg = it } }
                    if (source.epgUrl == null && discoveredEpg != null) {
                        sourceDao.get(sourceId)?.let { sourceDao.update(it.copy(epgUrl = discoveredEpg)) }
                    }
                }
                SourceType.XTREAM -> {
                    val creds = credentials.xtream(source)
                    val categories = xtream.liveCategories(creds)
                    val file = Perf.time("xtream download #$sourceId") { xtream.downloadLiveStreams(creds) }
                    try {
                        replaceChannels(source) { emit -> produceXtream(source, categories, file, emit) }
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

    private suspend fun replaceChannels(source: SourceEntity, produce: ((StagedChannelEntity) -> Unit) -> Unit) {
        channelDao.clearStaging(source.id)
        try {
            var staged = 0
            var stageNanos = 0L
            Perf.time("playlist parse+stage #${source.id}") {
                coroutineScope {
                    val pipe = Channel<StagedChannelEntity>(capacity = BATCH_SIZE * 2)
                    launch(ImportDispatcher) {
                        try {
                            produce { pipe.trySendBlocking(it).getOrThrow() }
                            pipe.close()
                        } catch (e: Throwable) {
                            pipe.close(e)
                            throw e
                        }
                    }
                    val batch = ArrayList<StagedChannelEntity>(BATCH_SIZE)
                    for (entity in pipe) {
                        batch += entity
                        if (batch.size == BATCH_SIZE) {
                            val t = System.nanoTime()
                            channelDao.stage(batch.toList())
                            stageNanos += System.nanoTime() - t
                            staged += batch.size
                            batch.clear()
                            _refreshState.value = RefreshState.Running(source.id, staged)
                        }
                    }
                    if (batch.isNotEmpty()) channelDao.stage(batch)
                    staged += batch.size
                }
            }
            Perf.log("playlist #${source.id}: staging inserts took ${stageNanos / 1_000_000} ms")
            if (staged == 0) throw IllegalStateException("No channels found")
            val count = Perf.time("playlist swap #${source.id}") {
                db.withTransaction {
                    val removed = Perf.time("  delete removed") { channelDao.deleteMissing(source.id) }
                    val changed = Perf.time("  update changed") { channelDao.updateChanged(source.id) }
                    Perf.time("  insert new") { channelDao.insertNew(source.id) }
                    Perf.log("playlist #${source.id}: $removed removed, $changed changed")
                    val linked = Perf.time("  link guide") { channelDao.linkGuide(source.id) }
                    val count = channelDao.count(source.id)
                    sourceDao.markRefreshed(source.id, count, System.currentTimeMillis())
                    Perf.log("playlist #${source.id}: $count channels, $linked linked to the guide")
                    count
                }
            }
            if (count == 0) throw IllegalStateException("No channels found")
        } finally {
            channelDao.clearStaging(source.id)
        }
    }

    private fun produceM3u(
        source: SourceEntity,
        staged: PlaylistLoader.Staged,
        emit: (StagedChannelEntity) -> Unit,
        onEpg: (String) -> Unit,
    ) {
        val keys = KeyAllocator()
        var index = 0L
        staged.parse(onHeader = { header -> header.epgUrls.firstOrNull()?.let(onEpg) }) { entry ->
            emit(
                StagedChannelEntity(
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
                    nameKey = channelNameKey(entry.name),
                    tvgKey = entry.tvgId?.let(::epgIdKey),
                ),
            )
        }
    }

    private fun produceXtream(
        source: SourceEntity,
        categoryList: List<XtreamCategory>,
        file: File,
        emit: (StagedChannelEntity) -> Unit,
    ) {
        val categories = categoryList.associate { it.id to it.name }
        // Keep the provider's category order rather than alphabetical.
        val categoryOrder = categoryList.withIndex().associate { it.value.id to it.index }
        var index = 0L
        XtreamClient.parseLiveStreams(file) { stream ->
            emit(
                StagedChannelEntity(
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
                    sortIndex = (categoryOrder[stream.categoryId] ?: categoryOrder.size).toLong() * CATEGORY_STRIDE + index++,
                    nameKey = channelNameKey(stream.name),
                    tvgKey = stream.epgChannelId?.let(::epgIdKey),
                ),
            )
        }
    }

    private fun defaultName(location: String): String =
        location.substringAfter("://").substringBefore('/').substringBefore('?').ifBlank { "Playlist" }

    private companion object {
        const val BATCH_SIZE = 2_000
        const val CATEGORY_STRIDE = 10_000_000L
    }
}
