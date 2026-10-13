package com.xfin.data.sources

import androidx.collection.LruCache
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.xfin.core.database.dao.SourceDao
import com.xfin.core.database.dao.VodDao
import com.xfin.core.database.entity.SourceEntity
import com.xfin.core.database.entity.VodItemEntity
import com.xfin.core.database.entity.VodProgressEntity
import com.xfin.core.model.MediaItem
import com.xfin.core.model.MediaKind
import com.xfin.core.model.SourceType
import com.xfin.core.model.VodStream
import com.xfin.data.iptv.xtream.XtreamClient
import com.xfin.data.iptv.xtream.XtreamMovieInfo
import com.xfin.data.iptv.xtream.XtreamSeriesInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel as Pipe
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class VodSort { NAME, ADDED, YEAR, RATING }

/**
 * Movies and series from Xtream providers. The catalogue is imported into the database (so it
 * browses and searches offline and fast); series episodes and extra details are fetched on demand.
 *
 * Item ids: movie `xm:<id>`, series `xs:<id>`, season `xs:<id>:<n>`,
 * episode `xe:<seriesId>:<season>:<episodeId>:<ext>`.
 */
@Singleton
class VodRepository @Inject internal constructor(
    private val sourceDao: SourceDao,
    private val vodDao: VodDao,
    private val xtream: XtreamClient,
    private val credentials: SourceCredentials,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val importLock = Mutex()
    private val movieInfo = LruCache<String, XtreamMovieInfo>(64)
    private val seriesInfo = LruCache<String, XtreamSeriesInfo>(32)

    fun observeProgressVersion(): Flow<Long> = vodDao.observeProgressVersion()

    // --- Import ---

    fun launchRefresh(sourceId: Long) {
        scope.launch { runCatching { refresh(sourceId) } }
    }

    suspend fun refresh(sourceId: Long) = scope.async { importLock.withLock { import(sourceId) } }.await()

    suspend fun refreshStale(maxAgeMs: Long) {
        val now = System.currentTimeMillis()
        sourceDao.getAll()
            .filter { it.type == SourceType.XTREAM.name && (it.vodUpdatedAt ?: 0) < now - maxAgeMs }
            .forEach { runCatching { refresh(it.id) } }
    }

    private suspend fun import(sourceId: Long) {
        val source = sourceDao.get(sourceId)?.takeIf { it.type == SourceType.XTREAM.name } ?: return
        val creds = credentials.xtream(source)
        val generation = source.vodGeneration + 1
        vodDao.deleteGeneration(sourceId, generation)
        val movieCats = runCatching { xtream.vodCategories(creds) }.getOrDefault(emptyList()).associate { it.id to it.name }
        val seriesCats = runCatching { xtream.seriesCategories(creds) }.getOrDefault(emptyList()).associate { it.id to it.name }

        val movies = Perf.time("vod movies #$sourceId") {
            importList(runCatching { xtream.download(creds, "get_vod_streams") }.getOrNull()) { file, emit ->
                XtreamClient.parseMovies(file) { m ->
                    emit(
                        VodItemEntity(
                            sourceId = sourceId,
                            generation = generation,
                            kind = KIND_MOVIE,
                            providerId = m.streamId,
                            name = m.name,
                            poster = m.icon,
                            category = m.categoryId?.let(movieCats::get),
                            rating = m.rating,
                            year = m.year,
                            added = m.added,
                            extension = m.extension,
                        ),
                    )
                }
            }
        }
        val series = Perf.time("vod series #$sourceId") {
            importList(runCatching { xtream.download(creds, "get_series") }.getOrNull()) { file, emit ->
                XtreamClient.parseSeries(file) { s ->
                    emit(
                        VodItemEntity(
                            sourceId = sourceId,
                            generation = generation,
                            kind = KIND_SERIES,
                            providerId = s.seriesId,
                            name = s.name,
                            poster = s.cover,
                            category = s.categoryId?.let(seriesCats::get),
                            rating = s.rating,
                            year = s.year,
                            added = s.added,
                            plot = s.plot,
                            genre = s.genre,
                            backdrop = s.backdrop,
                        ),
                    )
                }
            }
        }
        Perf.log("vod #$sourceId: $movies movies, $series series")
        vodDao.swap(sourceId, generation, movies, series, System.currentTimeMillis())
        while (vodDao.deleteOtherGenerations(sourceId, generation, 5_000) > 0) Unit
    }

    /** Parses a downloaded list on a background thread into batched inserts; returns the count. */
    private suspend fun importList(
        file: File?,
        parse: (File, (VodItemEntity) -> Unit) -> Unit,
    ): Int {
        if (file == null) return 0
        var count = 0
        try {
            coroutineScope {
                val pipe = Pipe<VodItemEntity>(capacity = BATCH * 2)
                launch(ImportDispatcher) {
                    try {
                        parse(file) { pipe.trySendBlocking(it).getOrThrow() }
                        pipe.close()
                    } catch (e: Throwable) {
                        pipe.close(e)
                        throw e
                    }
                }
                val batch = ArrayList<VodItemEntity>(BATCH)
                for (item in pipe) {
                    batch += item
                    if (batch.size == BATCH) {
                        vodDao.insertAll(batch.toList())
                        count += batch.size
                        batch.clear()
                    }
                }
                if (batch.isNotEmpty()) vodDao.insertAll(batch)
                count += batch.size
            }
        } finally {
            file.delete()
        }
        return count
    }

    // --- Browsing ---

    fun paged(sourceId: Long, kind: MediaKind, sort: VodSort): Flow<PagingData<MediaItem>> {
        val k = if (kind == MediaKind.SERIES) KIND_SERIES else KIND_MOVIE
        return Pager(PagingConfig(pageSize = 60, enablePlaceholders = true)) {
            when (sort) {
                VodSort.NAME -> vodDao.pageByName(sourceId, k)
                VodSort.ADDED -> vodDao.pageByAdded(sourceId, k)
                VodSort.YEAR -> vodDao.pageByYear(sourceId, k)
                VodSort.RATING -> vodDao.pageByRating(sourceId, k)
            }
        }.flow.map { page -> page.map { it.toItem() } }
    }

    suspend fun latest(sourceId: Long, kind: MediaKind, limit: Int): List<MediaItem> =
        vodDao.latest(sourceId, if (kind == MediaKind.SERIES) KIND_SERIES else KIND_MOVIE, limit).map { it.toItem() }

    suspend fun search(sourceId: Long, term: String, limit: Int = 60): List<MediaItem> =
        vodDao.search(sourceId, term.trim(), limit).map { it.toItem() }

    /** Titles started but not finished, newest first. */
    suspend fun inProgress(sourceId: Long, limit: Int): List<MediaItem> = vodDao.inProgress(sourceId, limit).map { p ->
        MediaItem(
            id = p.itemId,
            sourceId = sourceId,
            kind = if (p.kind == KIND_EPISODE) MediaKind.EPISODE else MediaKind.MOVIE,
            name = p.name,
            runtimeMs = p.durationMs.takeIf { it > 0 },
            positionMs = p.positionMs,
            played = p.played,
            posterUrl = p.posterUrl,
            thumbUrl = p.thumbUrl ?: p.posterUrl,
            seriesId = p.seriesId,
            seriesName = p.seriesName,
            seasonNumber = p.seasonNumber,
            episodeNumber = p.episodeNumber,
        )
    }

    // --- Details ---

    suspend fun item(sourceId: Long, itemId: String): MediaItem {
        val id = VodId.parse(itemId)
        return when (id) {
            is VodId.Movie -> {
                val row = vodDao.get(sourceId, KIND_MOVIE, id.streamId) ?: throw IllegalStateException("This movie is no longer available")
                val info = movieInfo(sourceId, id.streamId)
                withProgress(sourceId, row.toItem(info))
            }
            is VodId.Series, is VodId.Season -> {
                val seriesId = (id as? VodId.Series)?.seriesId ?: (id as VodId.Season).seriesId
                val row = vodDao.get(sourceId, KIND_SERIES, seriesId) ?: throw IllegalStateException("This show is no longer available")
                val info = seriesInfo(sourceId, seriesId)
                row.toItem().copy(
                    overview = info?.plot ?: row.plot,
                    backdropUrl = info?.backdrop ?: row.backdrop,
                    genres = (info?.genre ?: row.genre).splitGenres(),
                    year = row.year ?: info?.year,
                )
            }
            is VodId.Episode -> episodes(sourceId, "xs:${id.seriesId}", "xs:${id.seriesId}:${id.season}")
                .firstOrNull { it.id == itemId } ?: throw IllegalStateException("This episode is no longer available")
        }
    }

    suspend fun seasons(sourceId: Long, seriesItemId: String): List<MediaItem> {
        val seriesId = (VodId.parse(seriesItemId) as? VodId.Series)?.seriesId ?: return emptyList()
        val row = vodDao.get(sourceId, KIND_SERIES, seriesId)
        val info = seriesInfo(sourceId, seriesId) ?: return emptyList()
        return info.seasons.map { s ->
            MediaItem(
                id = "xs:$seriesId:${s.number}",
                sourceId = sourceId,
                kind = MediaKind.SEASON,
                name = s.name,
                posterUrl = s.cover ?: row?.poster,
                seriesId = seriesItemId,
                seriesName = row?.name,
                seasonNumber = s.number,
            )
        }
    }

    suspend fun episodes(sourceId: Long, seriesItemId: String, seasonItemId: String): List<MediaItem> {
        val seriesId = (VodId.parse(seriesItemId) as? VodId.Series)?.seriesId ?: return emptyList()
        val season = (VodId.parse(seasonItemId) as? VodId.Season)?.season
        val row = vodDao.get(sourceId, KIND_SERIES, seriesId)
        val info = seriesInfo(sourceId, seriesId) ?: return emptyList()
        val items = info.episodes.filter { season == null || it.season == season }.map { e ->
            MediaItem(
                id = "xe:$seriesId:${e.season}:${e.id}:${e.extension}",
                sourceId = sourceId,
                kind = MediaKind.EPISODE,
                name = e.title,
                overview = e.plot,
                runtimeMs = e.durationMs,
                posterUrl = row?.poster,
                thumbUrl = e.image ?: info.backdrop ?: row?.backdrop,
                backdropUrl = info.backdrop ?: row?.backdrop,
                seriesId = seriesItemId,
                seriesName = row?.name,
                seasonId = "xs:$seriesId:${e.season}",
                seasonNumber = e.season,
                episodeNumber = e.number,
            )
        }
        return withProgress(sourceId, items)
    }

    /** The episode to offer for a series: one in progress, else the first unwatched, else the first. */
    suspend fun nextEpisodeOf(sourceId: Long, seriesItemId: String): MediaItem? {
        val all = episodes(sourceId, seriesItemId, seriesItemId)
        return all.firstOrNull { it.canResume } ?: all.firstOrNull { !it.played } ?: all.firstOrNull()
    }

    suspend fun nextEpisodeAfter(sourceId: Long, seriesItemId: String, episodeItemId: String): MediaItem? {
        val all = episodes(sourceId, seriesItemId, seriesItemId)
        val index = all.indexOfFirst { it.id == episodeItemId }
        return if (index >= 0) all.getOrNull(index + 1) else null
    }

    // --- Playback & progress ---

    suspend fun stream(sourceId: Long, itemId: String, startMs: Long): VodStream {
        val source = sourceDao.get(sourceId) ?: throw IllegalStateException("This source was removed")
        val creds = credentials.xtream(source)
        val url = when (val id = VodId.parse(itemId)) {
            is VodId.Movie -> {
                val ext = vodDao.get(sourceId, KIND_MOVIE, id.streamId)?.extension ?: movieInfo(sourceId, id.streamId)?.extension ?: "mp4"
                XtreamClient.movieUrl(creds, id.streamId, ext)
            }
            is VodId.Episode -> XtreamClient.episodeUrl(creds, id.episodeId, id.extension)
            else -> throw IllegalStateException("Choose an episode to play")
        }
        return VodStream(
            itemId = itemId,
            sourceId = sourceId,
            url = url,
            headers = source.userAgent?.let { mapOf("User-Agent" to it) }.orEmpty(),
            mediaSourceId = null,
            playSessionId = null,
            playMethod = "DirectPlay",
            isHls = url.substringBefore('?').endsWith(".m3u8"),
            subtitles = emptyList(),
            startMs = startMs,
        )
    }

    suspend fun saveProgress(item: MediaItem, positionMs: Long, durationMs: Long) {
        val duration = durationMs.takeIf { it > 0 } ?: item.runtimeMs ?: 0
        val played = duration > 0 && positionMs >= duration * 0.92
        vodDao.saveProgress(
            VodProgressEntity(
                sourceId = item.sourceId,
                itemId = item.id,
                positionMs = if (played) 0 else positionMs,
                durationMs = duration,
                played = played,
                updatedAt = System.currentTimeMillis(),
                kind = if (item.kind == MediaKind.EPISODE) KIND_EPISODE else KIND_MOVIE,
                name = item.name,
                seriesId = item.seriesId,
                seriesName = item.seriesName,
                posterUrl = item.posterUrl,
                thumbUrl = item.thumbUrl ?: item.backdropUrl,
                seasonNumber = item.seasonNumber,
                episodeNumber = item.episodeNumber,
            ),
        )
    }

    suspend fun setPlayed(item: MediaItem, played: Boolean) {
        val duration = item.runtimeMs ?: 0
        saveProgress(item, if (played) duration.coerceAtLeast(1) else 0, if (played) duration.coerceAtLeast(1) else duration)
    }

    private suspend fun withProgress(sourceId: Long, item: MediaItem) = withProgress(sourceId, listOf(item)).first()

    private suspend fun withProgress(sourceId: Long, items: List<MediaItem>): List<MediaItem> {
        if (items.isEmpty()) return items
        val progress = items.map { it.id }.chunked(500).flatMap { vodDao.progressFor(sourceId, it) }.associateBy { it.itemId }
        return items.map { item ->
            progress[item.id]?.let { p ->
                item.copy(positionMs = p.positionMs, played = p.played, runtimeMs = item.runtimeMs ?: p.durationMs.takeIf { it > 0 })
            } ?: item
        }
    }

    private suspend fun movieInfo(sourceId: Long, streamId: Long): XtreamMovieInfo? {
        val key = "$sourceId:$streamId"
        movieInfo[key]?.let { return it }
        val source = sourceDao.get(sourceId) ?: return null
        return runCatching { xtream.movieInfo(credentials.xtream(source), streamId) }.getOrNull()?.also { movieInfo.put(key, it) }
    }

    private suspend fun seriesInfo(sourceId: Long, seriesId: Long): XtreamSeriesInfo? {
        val key = "$sourceId:$seriesId"
        seriesInfo[key]?.let { return it }
        val source: SourceEntity = sourceDao.get(sourceId) ?: return null
        return runCatching { xtream.seriesInfo(credentials.xtream(source), seriesId) }.getOrNull()?.also { seriesInfo.put(key, it) }
    }

    private fun VodItemEntity.toItem(info: XtreamMovieInfo? = null) = MediaItem(
        id = if (kind == KIND_SERIES) "xs:$providerId" else "xm:$providerId",
        sourceId = sourceId,
        kind = if (kind == KIND_SERIES) MediaKind.SERIES else MediaKind.MOVIE,
        name = name,
        overview = info?.plot ?: plot,
        year = year ?: info?.year,
        runtimeMs = info?.durationMs,
        posterUrl = poster,
        backdropUrl = info?.backdrop ?: backdrop,
        thumbUrl = info?.backdrop ?: backdrop,
        communityRating = rating ?: info?.rating,
        genres = (info?.genre ?: genre).splitGenres(),
    )

    private fun String?.splitGenres(): List<String> =
        this?.split(',', '/', '|')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

    private companion object {
        const val KIND_MOVIE = "movie"
        const val KIND_SERIES = "series"
        const val KIND_EPISODE = "episode"
        const val BATCH = 1_000
    }
}

/** Parsed VOD item ids (see [VodRepository]). */
sealed interface VodId {
    data class Movie(val streamId: Long) : VodId
    data class Series(val seriesId: Long) : VodId
    data class Season(val seriesId: Long, val season: Int) : VodId
    data class Episode(val seriesId: Long, val season: Int, val episodeId: Long, val extension: String) : VodId

    companion object {
        fun isVod(itemId: String) = itemId.startsWith("xm:") || itemId.startsWith("xs:") || itemId.startsWith("xe:")

        fun parse(itemId: String): VodId {
            val parts = itemId.split(':')
            return when (parts.firstOrNull()) {
                "xm" -> Movie(parts[1].toLong())
                "xs" -> if (parts.size >= 3) Season(parts[1].toLong(), parts[2].toInt()) else Series(parts[1].toLong())
                "xe" -> Episode(parts[1].toLong(), parts[2].toInt(), parts[3].toLong(), parts.getOrElse(4) { "mp4" })
                else -> throw IllegalArgumentException("Not a VOD id: $itemId")
            }
        }
    }
}
