package com.xfin.data.library

import androidx.paging.PagingData
import com.xfin.core.model.LibraryView
import com.xfin.core.model.MediaItem
import com.xfin.core.model.MediaKind
import com.xfin.core.model.Source
import com.xfin.core.model.SourceType
import com.xfin.core.model.VodStream
import com.xfin.data.jellyfin.JellyfinRepository
import com.xfin.data.jellyfin.LibrarySort
import com.xfin.data.sources.SourceRepository
import com.xfin.data.sources.VodRepository
import com.xfin.data.sources.VodSort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One library over every on-demand source: Jellyfin servers and Xtream providers' movies and
 * series. Screens talk only to this; each call is routed by the source's type.
 */
@Singleton
class LibraryRepository @Inject constructor(
    private val jellyfin: JellyfinRepository,
    private val vod: VodRepository,
    private val sources: SourceRepository,
) {
    private val types = ConcurrentHashMap<Long, SourceType>()

    /** Sources with something to browse: Jellyfin servers and Xtream providers that have VOD. */
    fun observeServers(): Flow<List<Source>> = sources.observeSources().map { list ->
        list.onEach { types[it.id] = it.type }
            .filter { it.type == SourceType.JELLYFIN || it.type == SourceType.XTREAM && it.vodMovieCount + it.vodSeriesCount > 0 }
    }

    fun observeProgressVersion(): Flow<Long> = vod.observeProgressVersion()

    suspend fun views(sourceId: Long): List<LibraryView> = if (isVod(sourceId)) {
        val source = sources.observeSources().first().firstOrNull { it.id == sourceId }
        buildList {
            if ((source?.vodMovieCount ?: 0) > 0) add(LibraryView(VIEW_MOVIES, sourceId, "Movies", "movies", null))
            if ((source?.vodSeriesCount ?: 0) > 0) add(LibraryView(VIEW_SERIES, sourceId, "Series", "tvshows", null))
        }
    } else {
        jellyfin.views(sourceId)
    }

    suspend fun resume(sourceId: Long, limit: Int = 20): List<MediaItem> =
        if (isVod(sourceId)) vod.inProgress(sourceId, limit) else jellyfin.resume(sourceId, limit)

    suspend fun nextUp(sourceId: Long, limit: Int = 20): List<MediaItem> =
        if (isVod(sourceId)) emptyList() else jellyfin.nextUp(sourceId, limit)

    suspend fun latest(sourceId: Long, viewId: String, limit: Int = 24): List<MediaItem> =
        if (isVod(sourceId)) vod.latest(sourceId, viewKind(viewId), limit) else jellyfin.latest(sourceId, viewId, limit)

    fun pagedItems(sourceId: Long, view: LibraryView, sort: LibrarySort): Flow<PagingData<MediaItem>> =
        if (view.id == VIEW_MOVIES || view.id == VIEW_SERIES) {
            vod.paged(sourceId, viewKind(view.id), sort.toVod())
        } else {
            jellyfin.pagedItems(sourceId, view, sort)
        }

    /** All movies or all shows of a source; [favoritesOnly] narrows it to the user's favorites. */
    suspend fun pagedAll(sourceId: Long, kind: MediaKind, sort: LibrarySort, favoritesOnly: Boolean = false): Flow<PagingData<MediaItem>> =
        when {
            !isVod(sourceId) -> jellyfin.pagedAll(sourceId, kind, sort, favoritesOnly)
            favoritesOnly -> vod.pagedFavorites(sourceId, kind)
            else -> vod.paged(sourceId, kind, sort.toVod())
        }

    /** Favorite movies and shows: Jellyfin's own (synced with its other apps), or kept on the device for Xtream. */
    suspend fun favorites(sourceId: Long, limit: Int = 24): List<MediaItem> =
        if (isVod(sourceId)) vod.favorites(sourceId, limit) else jellyfin.favorites(sourceId, limit)

    suspend fun setFavorite(item: MediaItem, favorite: Boolean) =
        if (isVod(item.sourceId)) vod.setFavorite(item, favorite) else jellyfin.setFavorite(item.sourceId, item.id, favorite)

    suspend fun search(sourceId: Long, term: String): List<MediaItem> =
        if (isVod(sourceId)) vod.search(sourceId, term) else jellyfin.search(sourceId, term)

    suspend fun item(sourceId: Long, itemId: String): MediaItem =
        if (isVod(sourceId)) vod.item(sourceId, itemId) else jellyfin.item(sourceId, itemId)

    suspend fun seasons(sourceId: Long, seriesId: String): List<MediaItem> =
        if (isVod(sourceId)) vod.seasons(sourceId, seriesId) else jellyfin.seasons(sourceId, seriesId)

    suspend fun episodes(sourceId: Long, seriesId: String, seasonId: String): List<MediaItem> =
        if (isVod(sourceId)) vod.episodes(sourceId, seriesId, seasonId) else jellyfin.episodes(sourceId, seriesId, seasonId)

    suspend fun seriesNextEpisode(sourceId: Long, seriesId: String): MediaItem? =
        if (isVod(sourceId)) vod.nextEpisodeOf(sourceId, seriesId) else jellyfin.seriesNextEpisode(sourceId, seriesId)

    suspend fun nextEpisode(sourceId: Long, seriesId: String, episodeId: String): MediaItem? =
        if (isVod(sourceId)) vod.nextEpisodeAfter(sourceId, seriesId, episodeId) else jellyfin.nextEpisode(sourceId, seriesId, episodeId)

    suspend fun setPlayed(item: MediaItem, played: Boolean) =
        if (isVod(item.sourceId)) vod.setPlayed(item, played) else jellyfin.setPlayed(item.sourceId, item.id, played)

    suspend fun playback(
        sourceId: Long,
        itemId: String,
        startMs: Long,
        allowDirect: Boolean = true,
        mediaSourceId: String? = null,
    ): VodStream = if (isVod(sourceId)) {
        vod.stream(sourceId, itemId, startMs)
    } else {
        jellyfin.playback(sourceId, itemId, startMs, allowDirect = allowDirect, mediaSourceId = mediaSourceId)
    }

    /** Whether a failed direct play can be retried as a server transcode. */
    suspend fun canTranscode(sourceId: Long): Boolean = !isVod(sourceId)

    suspend fun reportStart(stream: VodStream, positionMs: Long) {
        if (!isVod(stream.sourceId)) jellyfin.reportStart(stream, positionMs)
    }

    suspend fun reportProgress(stream: VodStream, item: MediaItem?, positionMs: Long, durationMs: Long, paused: Boolean) {
        if (isVod(stream.sourceId)) {
            if (item != null && positionMs > 0) vod.saveProgress(item, positionMs, durationMs)
        } else {
            jellyfin.reportProgress(stream, positionMs, paused)
        }
    }

    suspend fun reportStopped(stream: VodStream, item: MediaItem?, positionMs: Long, durationMs: Long) {
        if (isVod(stream.sourceId)) {
            if (item != null && positionMs > 0) vod.saveProgress(item, positionMs, durationMs)
        } else {
            jellyfin.reportStopped(stream, positionMs)
        }
    }

    private suspend fun isVod(sourceId: Long): Boolean {
        val type = types[sourceId] ?: sources.observeSources().first().onEach { types[it.id] = it.type }
            .firstOrNull { it.id == sourceId }?.type
        return type == SourceType.XTREAM || type == SourceType.M3U
    }

    private fun viewKind(viewId: String) = if (viewId == VIEW_SERIES) MediaKind.SERIES else MediaKind.MOVIE

    private fun LibrarySort.toVod() = when (this) {
        LibrarySort.NAME -> VodSort.NAME
        LibrarySort.ADDED -> VodSort.ADDED
        LibrarySort.YEAR -> VodSort.YEAR
        LibrarySort.RATING -> VodSort.RATING
    }

    private companion object {
        const val VIEW_MOVIES = "xtream-movies"
        const val VIEW_SERIES = "xtream-series"
    }
}
