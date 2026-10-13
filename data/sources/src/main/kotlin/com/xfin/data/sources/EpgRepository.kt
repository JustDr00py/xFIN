package com.xfin.data.sources

import androidx.room.withTransaction
import com.xfin.core.database.XfinDatabase
import com.xfin.core.database.dao.ChannelDao
import com.xfin.core.database.dao.ProgramDao
import com.xfin.core.database.dao.SourceDao
import com.xfin.core.database.entity.EpgChannelEntity
import com.xfin.core.database.entity.ProgramEntity
import com.xfin.core.database.entity.SourceEntity
import com.xfin.core.model.Channel
import com.xfin.core.model.EpgRefreshState
import com.xfin.core.model.NowNext
import com.xfin.core.model.Program
import com.xfin.core.model.SourceType
import com.xfin.data.iptv.epg.EpgLoader
import com.xfin.data.iptv.epg.channelNameKey
import com.xfin.data.iptv.epg.epgIdKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel as Pipe
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Guide data. Imports are written under a new generation while the old guide stays readable,
 * then swapped in by a tiny transaction, so the UI is never blocked by a large XMLTV import.
 */
@Singleton
class EpgRepository @Inject internal constructor(
    private val db: XfinDatabase,
    private val sourceDao: SourceDao,
    private val channelDao: ChannelDao,
    private val programDao: ProgramDao,
    private val loader: EpgLoader,
    private val credentials: SourceCredentials,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val importLock = Mutex()

    private val _state = MutableStateFlow<EpgRefreshState>(EpgRefreshState.Idle)
    val state: StateFlow<EpgRefreshState> = _state.asStateFlow()

    fun observeGuideVersion(): Flow<Int> = programDao.observeGuideVersion()

    fun observeHasGuide(): Flow<Int> = programDao.observeLinkedChannelCount()

    suspend fun programs(channel: Channel, from: Long, to: Long): List<Program> {
        val epgId = channel.epgId ?: return emptyList()
        return programDao.programs(channel.sourceId, epgId, from, to).map { it.toModel() }
    }

    suspend fun nowNext(channel: Channel, now: Long = System.currentTimeMillis()): NowNext {
        val epgId = channel.epgId ?: return NowNext(null, null)
        val rows = programDao.nowNext(channel.sourceId, epgId, now).map { it.toModel() }
        val current = rows.firstOrNull()?.takeIf { it.isAiring(now) }
        val next = if (current != null) rows.getOrNull(1) else rows.firstOrNull()
        return NowNext(current, next)
    }

    suspend fun program(id: Long): Program? = programDao.get(id)?.toModel()

    suspend fun setEpgUrl(sourceId: Long, url: String?) {
        sourceDao.setEpgUrl(sourceId, url?.trim()?.ifEmpty { null })
        launchRefresh(sourceId)
    }

    /** Fire-and-forget refresh; failures are recorded on the source. */
    fun launchRefresh(sourceId: Long) {
        scope.launch { runCatching { refresh(sourceId) } }
    }

    suspend fun refresh(sourceId: Long) = scope.async { importLock.withLock { import(sourceId) } }.await()

    /** Refreshes guides older than [maxAgeMs]; used by the background worker. */
    suspend fun refreshStale(maxAgeMs: Long = TimeUnit.HOURS.toMillis(12)) {
        val now = System.currentTimeMillis()
        sourceDao.getAll()
            .filter { it.type != SourceType.JELLYFIN.name }
            .filter { (it.epgUpdatedAt ?: 0) < now - maxAgeMs }
            .forEach { runCatching { refresh(it.id) } }
    }

    private suspend fun import(sourceId: Long) {
        val source = sourceDao.get(sourceId) ?: return
        val url = credentials.epgUrl(source) ?: return
        _state.value = EpgRefreshState.Running(sourceId, 0)
        try {
            val file = loader.download(url, source.userAgent)
            val result = try {
                writeGeneration(source, file)
            } finally {
                file.delete()
            }
            if (result.count == 0) throw IllegalStateException("The guide has no programmes for your channels")
            swap(source, result)
            // Old generations are already invisible; remove them in small chunks.
            while (programDao.deleteOtherGenerations(sourceId, result.generation, 5_000) > 0) Unit
        } catch (e: Exception) {
            sourceDao.markEpgFailed(sourceId, e.message ?: e::class.simpleName.orEmpty())
            throw e
        } finally {
            _state.value = EpgRefreshState.Idle
        }
    }

    private class ImportResult(val generation: Int, val count: Int, val epgChannels: List<EpgChannelEntity>)

    private suspend fun writeGeneration(source: SourceEntity, file: java.io.File): ImportResult {
        val links = channelDao.linkRows(source.id)
        val wantedIds = links.mapNotNullTo(HashSet()) { it.tvgId?.let(::epgIdKey) }
        val wantedNames = links.mapTo(HashSet()) { channelNameKey(it.name) }.apply { remove("") }

        val now = System.currentTimeMillis()
        val windowStart = now - TimeUnit.DAYS.toMillis(PAST_DAYS)
        val windowEnd = now + TimeUnit.DAYS.toMillis(FUTURE_DAYS)

        val generation = source.epgGeneration + 1
        programDao.deleteGeneration(source.id, generation) // leftovers from an interrupted import

        val accepted = HashSet<String>()
        val epgChannels = ArrayList<EpgChannelEntity>()
        var count = 0

        coroutineScope {
            val pipe = Pipe<ProgramEntity>(capacity = BATCH_SIZE * 2)
            launch(Dispatchers.IO) {
                try {
                    loader.parse(
                        file = file,
                        onChannel = { channel ->
                            val key = epgIdKey(channel.id)
                            val names = channel.displayNames.map(::channelNameKey).filter { it.isNotEmpty() }
                            if (key in wantedIds || names.any { it in wantedNames }) {
                                if (accepted.add(key)) {
                                    epgChannels += EpgChannelEntity(source.id, key, "")
                                    names.distinct().forEach { epgChannels += EpgChannelEntity(source.id, key, it) }
                                }
                            }
                        },
                        accept = { channelId, start, stop ->
                            val key = epgIdKey(channelId)
                            if (stop < windowStart || start > windowEnd) {
                                false
                            } else if (key in accepted) {
                                true
                            } else if (key in wantedIds) {
                                // Programme for a channel the feed never declared.
                                accepted += key
                                epgChannels += EpgChannelEntity(source.id, key, "")
                                true
                            } else {
                                false
                            }
                        },
                        onProgramme = { p ->
                            val entity = ProgramEntity(
                                sourceId = source.id,
                                generation = generation,
                                channelId = epgIdKey(p.channelId),
                                startAt = p.start,
                                endAt = p.stop,
                                title = p.title,
                                subtitle = p.subtitle,
                                description = p.description,
                                category = p.category,
                                iconUrl = p.iconUrl,
                                episode = p.episode,
                            )
                            pipe.trySendBlocking(entity).getOrThrow()
                        },
                    )
                    pipe.close()
                } catch (e: Throwable) {
                    pipe.close(e)
                    throw e
                }
            }
            val batch = ArrayList<ProgramEntity>(BATCH_SIZE)
            for (program in pipe) {
                batch += program
                if (batch.size == BATCH_SIZE) {
                    // Each batch is its own short transaction, so readers interleave freely.
                    programDao.insertAll(batch.toList())
                    count += batch.size
                    batch.clear()
                    _state.value = EpgRefreshState.Running(source.id, count)
                }
            }
            if (batch.isNotEmpty()) programDao.insertAll(batch)
            count += batch.size
        }
        return ImportResult(generation, count, epgChannels)
    }

    private suspend fun swap(source: SourceEntity, result: ImportResult) {
        db.withTransaction {
            programDao.deleteEpgChannels(source.id)
            result.epgChannels.chunked(BATCH_SIZE).forEach { programDao.insertEpgChannels(it) }
            val linker = EpgLinker(result.epgChannels)
            channelDao.linkRows(source.id).forEach { row ->
                val epgId = linker.resolve(row.tvgId, row.name)
                if (epgId != row.epgId) channelDao.setEpgId(row.id, epgId)
            }
            sourceDao.markEpgSwapped(source.id, result.generation, result.count, System.currentTimeMillis())
        }
    }

    private companion object {
        const val BATCH_SIZE = 1_000
        const val PAST_DAYS = 1L
        const val FUTURE_DAYS = 8L
    }
}

internal fun ProgramEntity.toModel() = Program(
    id = id,
    title = title,
    start = startAt,
    end = endAt,
    subtitle = subtitle,
    description = description,
    category = category,
    iconUrl = iconUrl,
    episode = episode,
)
