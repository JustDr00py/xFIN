package com.xfin.data.sources

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.xfin.core.database.dao.ChannelDao
import com.xfin.core.database.dao.SourceDao
import com.xfin.core.database.entity.FavoriteEntity
import com.xfin.core.database.entity.HiddenGroupEntity
import com.xfin.core.database.entity.HistoryEntity
import com.xfin.core.model.Channel
import com.xfin.core.model.ChannelFilter
import com.xfin.core.model.ChannelGroup
import com.xfin.core.model.PlayableStream
import com.xfin.core.model.SourceType
import com.xfin.core.model.XtreamOutput
import com.xfin.data.iptv.xtream.XtreamClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Source-agnostic access to live channels: browsing, zapping order, favorites and stream URLs. */
@Singleton
class ChannelRepository @Inject internal constructor(
    private val channelDao: ChannelDao,
    private val sourceDao: SourceDao,
    private val credentials: SourceCredentials,
) {
    /** Paged channels for browsing; a non-blank [query] searches every visible channel. */
    fun pagedChannels(filter: ChannelFilter, query: String = ""): Flow<PagingData<Channel>> {
        val match = ftsQuery(query)
        if (query.isNotBlank() && match == null) return flowOf(PagingData.empty())
        return Pager(PagingConfig(pageSize = 60, prefetchDistance = 120, enablePlaceholders = true)) {
            when {
                match != null -> channelDao.pageSearch(match)
                filter == ChannelFilter.All -> channelDao.pageAll()
                filter == ChannelFilter.Favorites -> channelDao.pageFavorites()
                filter == ChannelFilter.Recent -> channelDao.pageRecent()
                filter is ChannelFilter.Group -> channelDao.pageGroup(filter.name)
                else -> channelDao.pageAll()
            }
        }.flow.map { paging -> paging.map { it.toModel() } }
    }

    /** Zapping lineup as ids only (100k channels is under 1 MB); same order as [pagedChannels]. */
    suspend fun lineup(filter: ChannelFilter): LongArray = when (filter) {
        ChannelFilter.All -> channelDao.idsAll()
        ChannelFilter.Favorites -> channelDao.idsFavorites()
        ChannelFilter.Recent -> channelDao.idsRecent()
        is ChannelFilter.Group -> channelDao.idsGroup(filter.name)
    }.toLongArray()

    suspend fun channel(id: Long): Channel? = channelDao.get(id)?.toModel()

    suspend fun idsWithNumber(number: Int): List<Long> = channelDao.idsWithNumber(number)

    /** The first [limit] channels matching [query], for global search. */
    suspend fun search(query: String, limit: Int = 40): List<Channel> {
        val match = ftsQuery(query) ?: return emptyList()
        return channelDao.search(match, limit).map { it.toModel() }
    }

    /** Channels by id, returned in the order of [ids]. */
    suspend fun channels(ids: List<Long>): List<Channel> {
        if (ids.isEmpty()) return emptyList()
        val byId = ids.chunked(500).flatMap { channelDao.getAll(it) }.associateBy { it.channel.id }
        return ids.mapNotNull { byId[it]?.toModel() }
    }

    fun observeChannel(id: Long): Flow<Channel?> = channelDao.observe(id).map { it?.toModel() }

    fun observeGroups(): Flow<List<ChannelGroup>> = channelDao.observeGroups().map { rows -> rows.map { it.toModel() } }

    fun observeHasFavorites(): Flow<Boolean> = channelDao.observeFavoriteCount().map { it > 0 }

    fun observeHasHistory(): Flow<Boolean> = channelDao.observeHistoryCount().map { it > 0 }

    suspend fun setFavorite(channel: Channel, favorite: Boolean) {
        if (favorite) {
            channelDao.addFavorite(FavoriteEntity(channel.sourceId, channel.key))
        } else {
            channelDao.removeFavorite(channel.sourceId, channel.key)
        }
    }

    suspend fun setGroupHidden(name: String, hidden: Boolean) {
        if (hidden) channelDao.hideGroup(HiddenGroupEntity(name)) else channelDao.unhideGroup(name)
    }

    suspend fun recordWatched(channel: Channel) {
        channelDao.recordWatched(HistoryEntity(channel.sourceId, channel.key, System.currentTimeMillis()))
    }

    suspend fun resolveStream(channelId: Long): PlayableStream {
        val row = channelDao.get(channelId)?.channel ?: throw IOException("Channel no longer exists")
        val source = sourceDao.get(row.sourceId) ?: throw IOException("Source no longer exists")
        return when (SourceType.valueOf(source.type)) {
            SourceType.XTREAM -> {
                val output = runCatching { XtreamOutput.valueOf(source.xtreamOutput) }.getOrDefault(XtreamOutput.TS)
                PlayableStream(
                    url = XtreamClient.liveStreamUrl(credentials.xtream(source), row.stream, output.extension),
                    userAgent = source.userAgent,
                )
            }
            else -> PlayableStream(
                url = row.stream,
                userAgent = row.userAgent ?: source.userAgent,
                referrer = row.referrer,
            )
        }
    }
}

/**
 * Turns user input into an FTS4 prefix query: "espn sp" -> "espn* sp*". Only letters and digits
 * survive, so user input can never form FTS operators. Null when nothing searchable remains.
 */
internal fun ftsQuery(input: String): String? {
    val tokens = input.lowercase()
        .split { !it.isLetterOrDigit() }
        .filter { it.isNotEmpty() }
        .take(8)
    return if (tokens.isEmpty()) null else tokens.joinToString(" ") { "$it*" }
}

private fun String.split(isSeparator: (Char) -> Boolean): List<String> {
    val parts = ArrayList<String>()
    val current = StringBuilder()
    for (ch in this) {
        if (isSeparator(ch)) {
            if (current.isNotEmpty()) parts += current.toString()
            current.clear()
        } else {
            current.append(ch)
        }
    }
    if (current.isNotEmpty()) parts += current.toString()
    return parts
}
