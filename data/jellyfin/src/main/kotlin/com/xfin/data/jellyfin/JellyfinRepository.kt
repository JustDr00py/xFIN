package com.xfin.data.jellyfin

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import android.content.Context
import com.xfin.core.database.crypto.SecretCipher
import dagger.hilt.android.qualifiers.ApplicationContext
import com.xfin.core.database.dao.SourceDao
import com.xfin.core.database.entity.SourceEntity
import com.xfin.core.model.ExternalSubtitle
import com.xfin.core.model.LibraryView
import com.xfin.core.model.MediaItem
import com.xfin.core.model.MediaKind
import com.xfin.core.model.Source
import com.xfin.core.model.SourceType
import com.xfin.core.model.VodStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.mediaInfoApi
import org.jellyfin.sdk.api.client.extensions.sessionApi
import org.jellyfin.sdk.api.client.extensions.showApi
import org.jellyfin.sdk.api.client.extensions.userDataApi
import org.jellyfin.sdk.api.client.extensions.userViewApi
import org.jellyfin.sdk.api.client.extensions.videoApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PlaybackInfoDto
import org.jellyfin.sdk.model.api.PlaybackOrder
import org.jellyfin.sdk.model.api.PlaybackProgressInfo
import org.jellyfin.sdk.model.api.PlaybackStartInfo
import org.jellyfin.sdk.model.api.PlaybackStopInfo
import org.jellyfin.sdk.model.api.RepeatMode
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.SubtitleDeliveryMethod
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

enum class LibrarySort(val by: ItemSortBy, val order: SortOrder) {
    NAME(ItemSortBy.SORT_NAME, SortOrder.ASCENDING),
    ADDED(ItemSortBy.DATE_CREATED, SortOrder.DESCENDING),
    YEAR(ItemSortBy.PRODUCTION_YEAR, SortOrder.DESCENDING),
    RATING(ItemSortBy.COMMUNITY_RATING, SortOrder.DESCENDING),
}

/** Browsing, details, playback and progress for Jellyfin sources. */
@Singleton
class JellyfinRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sdk: JellyfinSdk,
    private val sourceDao: SourceDao,
    private val cipher: SecretCipher,
    private val connector: JellyfinConnector,
) {
    private class Connection(val api: ApiClient, val userId: UUID, val token: String, val baseUrl: String)

    private val connections = ConcurrentHashMap<Long, Connection>()

    fun observeServers(): Flow<List<Source>> = sourceDao.observeAll().map { rows ->
        rows.filter { it.type == SourceType.JELLYFIN.name }.map { it.toSource() }
    }

    /** Saves a signed-in session as a source; signing in again to the same server/user updates it. */
    suspend fun addSource(session: JellyfinSession): Long {
        val existing = sourceDao.getAll().firstOrNull {
            it.type == SourceType.JELLYFIN.name && it.serverId == session.server.id && it.userId == session.userId
        }
        val entity = SourceEntity(
            id = existing?.id ?: 0,
            name = session.server.name,
            type = SourceType.JELLYFIN.name,
            url = session.server.url,
            username = session.userName,
            passwordEnc = cipher.encrypt(session.accessToken),
            userId = session.userId,
            serverId = session.server.id,
            lastRefreshedAt = System.currentTimeMillis(),
        )
        return if (existing != null) {
            sourceDao.update(entity.copy(createdAt = existing.createdAt))
            connections.remove(existing.id)
            existing.id
        } else {
            sourceDao.insert(entity)
        }
    }

    /**
     * Renames a server or points it at a new address (e.g. a new domain). The address must reach the
     * same server, so the saved sign-in stays valid.
     */
    suspend fun updateServer(sourceId: Long, name: String, address: String) {
        val source = sourceDao.get(sourceId) ?: throw JellyfinException("This server was removed")
        var url = source.url
        if (address.trim().trimEnd('/') != source.url.trimEnd('/')) {
            val server = connector.probe(address)
            if (source.serverId != null && server.id != source.serverId) {
                throw JellyfinException("That address is a different Jellyfin server (${server.name}). Add it as a new server instead.")
            }
            url = server.url
        }
        sourceDao.update(source.copy(name = name.ifBlank { source.name }, url = url))
        connections.remove(sourceId)
    }

    suspend fun views(sourceId: Long): List<LibraryView> = call(sourceId) { c ->
        c.api.userViewApi.getUserViews(userId = c.userId).content.items
            .filter { it.collectionType == CollectionType.MOVIES || it.collectionType == CollectionType.TVSHOWS || it.collectionType == null || it.collectionType == CollectionType.HOMEVIDEOS || it.collectionType == CollectionType.BOXSETS }
            .map { it.toLibraryView(c.api, sourceId) }
    }

    suspend fun resume(sourceId: Long, limit: Int = 20): List<MediaItem> = call(sourceId) { c ->
        c.api.libraryApi.getResumeItems(
            userId = c.userId,
            limit = limit,
            mediaTypes = listOf(MediaType.VIDEO),
            fields = ROW_FIELDS,
            enableUserData = true,
        ).content.items.map { it.toMediaItem(c.api, sourceId) }
    }

    /** Next episodes to watch, skipping specials (season 0), which Next Up otherwise offers first. */
    suspend fun nextUp(sourceId: Long, limit: Int = 20): List<MediaItem> = call(sourceId) { c ->
        c.api.showApi.getNextUp(userId = c.userId, limit = limit, fields = ROW_FIELDS, enableUserData = true)
            .content.items.map { it.toMediaItem(c.api, sourceId) }
            .filterNot { it.seasonNumber == 0 }
    }

    suspend fun latest(sourceId: Long, viewId: String, limit: Int = 24): List<MediaItem> = call(sourceId) { c ->
        c.api.libraryApi.getLatestMedia(
            userId = c.userId,
            parentId = UUID.fromString(viewId),
            fields = ROW_FIELDS,
            limit = limit,
            enableUserData = true,
        ).content.map { it.toMediaItem(c.api, sourceId) }
    }

    fun pagedItems(sourceId: Long, view: LibraryView, sort: LibrarySort): Flow<PagingData<MediaItem>> =
        Pager(PagingConfig(pageSize = 60, initialLoadSize = 60, enablePlaceholders = true)) {
            ItemsPagingSource(sourceId, view.id, typesFor(view.collectionType), sort)
        }.flow

    /** Every movie (or every show) the user can see on a server, across all its libraries. */
    fun pagedAll(sourceId: Long, kind: MediaKind, sort: LibrarySort, favoritesOnly: Boolean = false): Flow<PagingData<MediaItem>> =
        Pager(PagingConfig(pageSize = 60, initialLoadSize = 60, enablePlaceholders = true)) {
            ItemsPagingSource(
                sourceId,
                parentId = null,
                types = listOf(if (kind == MediaKind.SERIES) BaseItemKind.SERIES else BaseItemKind.MOVIE),
                sort = sort,
                isFavorite = favoritesOnly.takeIf { it },
            )
        }.flow

    /** The user's favorite movies and shows on a server. */
    suspend fun favorites(sourceId: Long, limit: Int = 24): List<MediaItem> = call(sourceId) { c ->
        c.api.libraryApi.getItems(
            userId = c.userId,
            includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
            recursive = true,
            isFavorite = true,
            sortBy = listOf(ItemSortBy.SORT_NAME),
            limit = limit,
            fields = ROW_FIELDS,
            enableUserData = true,
        ).content.items.map { it.toMediaItem(c.api, sourceId) }
    }

    suspend fun setFavorite(sourceId: Long, itemId: String, favorite: Boolean) = call(sourceId) { c ->
        val id = UUID.fromString(itemId)
        if (favorite) c.api.userDataApi.markFavoriteItem(itemId = id, userId = c.userId)
        else c.api.userDataApi.unmarkFavoriteItem(itemId = id, userId = c.userId)
        Unit
    }

    /** Movies, shows and episodes matching [term], best matches first. */
    suspend fun search(sourceId: Long, term: String, limit: Int = 60): List<MediaItem> = call(sourceId) { c ->
        c.api.libraryApi.getItems(
            userId = c.userId,
            searchTerm = term,
            includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES, BaseItemKind.EPISODE),
            recursive = true,
            limit = limit,
            fields = ROW_FIELDS,
            enableUserData = true,
        ).content.items.map { it.toMediaItem(c.api, sourceId) }
    }

    suspend fun item(sourceId: Long, itemId: String): MediaItem = call(sourceId) { c ->
        c.api.libraryApi.getItem(itemId = UUID.fromString(itemId), userId = c.userId).content.toMediaItem(c.api, sourceId)
    }

    suspend fun seasons(sourceId: Long, seriesId: String): List<MediaItem> = call(sourceId) { c ->
        c.api.showApi.getSeasons(seriesId = UUID.fromString(seriesId), userId = c.userId, isMissing = false, enableUserData = true)
            .content.items.map { it.toMediaItem(c.api, sourceId) }
    }

    suspend fun episodes(sourceId: Long, seriesId: String, seasonId: String): List<MediaItem> = call(sourceId) { c ->
        c.api.showApi.getEpisodes(
            seriesId = UUID.fromString(seriesId),
            seasonId = UUID.fromString(seasonId),
            userId = c.userId,
            // "Missing" episodes exist only as metadata; they have no file to play.
            isMissing = false,
            // Media sources are included so the version picker can open instantly.
            fields = listOf(ItemFields.OVERVIEW, ItemFields.MEDIA_SOURCES),
            enableUserData = true,
        ).content.items.map { it.toMediaItem(c.api, sourceId) }
    }

    /**
     * The episode to offer for a series: one in progress, else the first unwatched regular episode,
     * else the first regular episode. Specials and missing episodes are skipped.
     */
    suspend fun seriesNextEpisode(sourceId: Long, seriesId: String): MediaItem? = call(sourceId) { c ->
        val episodes = c.api.showApi.getEpisodes(
            seriesId = UUID.fromString(seriesId),
            userId = c.userId,
            isMissing = false,
            fields = listOf(ItemFields.MEDIA_SOURCES),
            enableUserData = true,
        ).content.items.map { it.toMediaItem(c.api, sourceId) }
        val regular = episodes.filter { it.seasonNumber != 0 }.ifEmpty { episodes }
        regular.firstOrNull { it.canResume } ?: regular.firstOrNull { !it.played } ?: regular.firstOrNull()
    }

    /**
     * The next episode in the same series after [episodeId], for "Up next" at the end of playback.
     * After a regular episode, specials (season 0) the server places in between are skipped.
     */
    suspend fun nextEpisode(sourceId: Long, seriesId: String, episodeId: String): MediaItem? = call(sourceId) { c ->
        val items = c.api.showApi.getEpisodes(
            seriesId = UUID.fromString(seriesId),
            userId = c.userId,
            startItemId = UUID.fromString(episodeId),
            isMissing = false,
            limit = 50,
            enableUserData = true,
        ).content.items
        val current = items.firstOrNull() ?: return@call null
        val following = items.drop(1)
        val next = if (current.parentIndexNumber == 0) following.firstOrNull()
            else following.firstOrNull { it.parentIndexNumber != 0 }
        next?.toMediaItem(c.api, sourceId)
    }

    suspend fun setPlayed(sourceId: Long, itemId: String, played: Boolean) = call(sourceId) { c ->
        val id = UUID.fromString(itemId)
        if (played) c.api.userDataApi.markPlayedItem(itemId = id, userId = c.userId)
        else c.api.userDataApi.markUnplayedItem(itemId = id, userId = c.userId)
        Unit
    }

    /**
     * Asks the server how to play [itemId] on this device (direct play when the device profile
     * allows, otherwise an HLS transcode) and builds the stream for the player.
     */
    suspend fun playback(
        sourceId: Long,
        itemId: String,
        startMs: Long,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
        /** False forces a server transcode, e.g. after the device failed to decode the original. */
        allowDirect: Boolean = true,
        /** A specific version from the version picker; null lets the server choose. */
        mediaSourceId: String? = null,
    ): VodStream = call(sourceId) { c ->
        val id = UUID.fromString(itemId)
        val info = c.api.mediaInfoApi.getPostedPlaybackInfo(
            itemId = id,
            data = PlaybackInfoDto(
                userId = c.userId,
                maxStreamingBitrate = DeviceProfiles.MAX_BITRATE,
                startTimeTicks = startMs * TICKS_PER_MS,
                audioStreamIndex = audioStreamIndex,
                subtitleStreamIndex = subtitleStreamIndex,
                mediaSourceId = mediaSourceId,
                deviceProfile = DeviceProfiles.profile(context),
                enableDirectPlay = allowDirect,
                enableDirectStream = allowDirect,
                enableTranscoding = true,
                allowVideoStreamCopy = true,
                allowAudioStreamCopy = true,
                autoOpenLiveStream = true,
            ),
        ).content
        info.errorCode?.let { throw JellyfinException("The server can't play this item ($it)") }
        val source = info.mediaSources.firstOrNull { it.id == mediaSourceId } ?: info.mediaSources.firstOrNull() ?: throw JellyfinException("No playable media for this item")
        val transcodeUrl = source.transcodingUrl
        val (url, method) = when {
            allowDirect && (source.supportsDirectPlay || source.supportsDirectStream && transcodeUrl == null) -> c.api.videoApi.getVideoStreamUrl(
                itemId = id,
                static = true,
                mediaSourceId = source.id,
                playSessionId = info.playSessionId,
            ) to if (source.supportsDirectPlay) "DirectPlay" else "DirectStream"
            transcodeUrl != null -> absolute(c.baseUrl, transcodeUrl) to "Transcode"
            else -> throw JellyfinException("This item can't be played on this device")
        }
        val subtitles = source.mediaStreams.orEmpty()
            .filter { it.type == MediaStreamType.SUBTITLE && it.deliveryMethod == SubtitleDeliveryMethod.EXTERNAL }
            .mapNotNull { stream ->
                val path = stream.deliveryUrl ?: return@mapNotNull null
                val mime = subtitleMime(stream.codec) ?: return@mapNotNull null
                ExternalSubtitle(
                    url = absolute(c.baseUrl, path),
                    mimeType = mime,
                    language = stream.language,
                    label = stream.displayTitle ?: stream.title ?: stream.language ?: "Subtitles",
                )
            }
        VodStream(
            itemId = itemId,
            sourceId = sourceId,
            url = url,
            headers = mapOf("Authorization" to sdk.authorizationHeader(c.token)),
            mediaSourceId = source.id,
            playSessionId = info.playSessionId,
            playMethod = method,
            isHls = method == "Transcode" || url.contains(".m3u8"),
            subtitles = subtitles,
            startMs = startMs,
        )
    }

    suspend fun reportStart(stream: VodStream, positionMs: Long) = report(stream.sourceId) { c ->
        c.api.sessionApi.reportPlaybackStart(
            PlaybackStartInfo(
                canSeek = true,
                itemId = UUID.fromString(stream.itemId),
                mediaSourceId = stream.mediaSourceId,
                isPaused = false,
                isMuted = false,
                positionTicks = positionMs * TICKS_PER_MS,
                playMethod = stream.method(),
                playSessionId = stream.playSessionId,
                repeatMode = RepeatMode.REPEAT_NONE,
                playbackOrder = PlaybackOrder.DEFAULT,
            ),
        )
    }

    suspend fun reportProgress(stream: VodStream, positionMs: Long, paused: Boolean) = report(stream.sourceId) { c ->
        c.api.sessionApi.reportPlaybackProgress(
            PlaybackProgressInfo(
                canSeek = true,
                itemId = UUID.fromString(stream.itemId),
                mediaSourceId = stream.mediaSourceId,
                isPaused = paused,
                isMuted = false,
                positionTicks = positionMs * TICKS_PER_MS,
                playMethod = stream.method(),
                playSessionId = stream.playSessionId,
                repeatMode = RepeatMode.REPEAT_NONE,
                playbackOrder = PlaybackOrder.DEFAULT,
            ),
        )
    }

    suspend fun reportStopped(stream: VodStream, positionMs: Long) = report(stream.sourceId) { c ->
        c.api.sessionApi.reportPlaybackStopped(
            PlaybackStopInfo(
                itemId = UUID.fromString(stream.itemId),
                mediaSourceId = stream.mediaSourceId,
                positionTicks = positionMs * TICKS_PER_MS,
                playSessionId = stream.playSessionId,
                failed = false,
            ),
        )
    }

    private fun typesFor(collectionType: String?) = when (collectionType) {
        "movies" -> listOf(BaseItemKind.MOVIE)
        "tvshows" -> listOf(BaseItemKind.SERIES)
        "boxsets" -> listOf(BaseItemKind.BOX_SET)
        else -> emptyList()
    }

    private inner class ItemsPagingSource(
        private val sourceId: Long,
        private val parentId: String?,
        private val types: List<BaseItemKind>,
        private val sort: LibrarySort,
        private val isFavorite: Boolean? = null,
    ) : PagingSource<Int, MediaItem>() {
        override fun getRefreshKey(state: PagingState<Int, MediaItem>): Int? =
            state.anchorPosition?.let { (it - state.config.initialLoadSize / 2).coerceAtLeast(0) }

        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MediaItem> = try {
            val start = params.key ?: 0
            val result = call(sourceId) { c ->
                c.api.libraryApi.getItems(
                    userId = c.userId,
                    parentId = parentId?.let(UUID::fromString),
                    includeItemTypes = types,
                    recursive = types.isNotEmpty(),
                    isFavorite = isFavorite,
                    sortBy = listOf(sort.by, ItemSortBy.SORT_NAME),
                    sortOrder = listOf(sort.order, SortOrder.ASCENDING),
                    startIndex = start,
                    limit = params.loadSize,
                    fields = ROW_FIELDS,
                    enableUserData = true,
                    enableTotalRecordCount = true,
                ).content
            }
            val items = result.items.map { item -> call(sourceId) { c -> item.toMediaItem(c.api, sourceId) } }
            val total = result.totalRecordCount
            val end = start + items.size
            LoadResult.Page(
                data = items,
                prevKey = if (start == 0) null else (start - params.loadSize).coerceAtLeast(0),
                nextKey = if (items.isEmpty() || end >= total) null else end,
                itemsBefore = start,
                itemsAfter = (total - end).coerceAtLeast(0),
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    private fun VodStream.method() = when (playMethod) {
        "DirectPlay" -> PlayMethod.DIRECT_PLAY
        "DirectStream" -> PlayMethod.DIRECT_STREAM
        else -> PlayMethod.TRANSCODE
    }

    /** Progress reports are best effort: never fail playback because the server is unreachable. */
    private suspend fun report(sourceId: Long, block: suspend (Connection) -> Unit) {
        runCatching { call(sourceId, block) }
    }

    private suspend fun <T> call(sourceId: Long, block: suspend (Connection) -> T): T = withContext(Dispatchers.IO) {
        block(connection(sourceId))
    }

    private suspend fun connection(sourceId: Long): Connection {
        connections[sourceId]?.let { return it }
        val source = sourceDao.get(sourceId) ?: throw JellyfinException("This server was removed")
        val token = source.passwordEnc?.let(cipher::decrypt) ?: throw JellyfinException("Not signed in to ${source.name}")
        val userId = source.userId?.let(UUID::fromString) ?: throw JellyfinException("Not signed in to ${source.name}")
        return Connection(sdk.api(source.url, token), userId, token, source.url).also { connections[sourceId] = it }
    }

    private fun absolute(base: String, path: String) = if (path.startsWith("http")) path else base + path

    private fun subtitleMime(codec: String?): String? = when (codec?.lowercase()) {
        "srt", "subrip" -> "application/x-subrip"
        "vtt", "webvtt" -> "text/vtt"
        "ass", "ssa" -> "text/x-ssa"
        "ttml" -> "application/ttml+xml"
        else -> null
    }

    private fun SourceEntity.toSource() = Source(
        id = id,
        name = name,
        type = SourceType.JELLYFIN,
        url = url,
        username = username,
        lastRefreshedAt = lastRefreshedAt,
    )

    private companion object {
        val ROW_FIELDS = listOf(ItemFields.PRIMARY_IMAGE_ASPECT_RATIO)
    }
}
