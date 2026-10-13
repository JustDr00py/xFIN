package com.xfin.data.sources

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
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
    /**
     * Paged channels for browsing. [clock] selects the "on now" programme each page carries;
     * call [PagingSource.invalidate] on the source passed to [onSource] to refresh it in place
     * (keeps the scroll position, unlike building a new pager).
     */
    fun pagedChannels(
        filter: ChannelFilter,
        query: String,
        clock: () -> Long = System::currentTimeMillis,
        onSource: (PagingSource<Int, *>) -> Unit = {},
    ): Flow<PagingData<Channel>> {
        val q = query.trim()
        return Pager(PagingConfig(pageSize = 60, prefetchDistance = 120, enablePlaceholders = true)) {
            val now = clock()
            when (filter) {
                ChannelFilter.All -> channelDao.pageAll(q, now)
                ChannelFilter.Favorites -> channelDao.pageFavorites(q, now)
                ChannelFilter.Recent -> channelDao.pageRecent(q, now)
                is ChannelFilter.Group -> channelDao.pageGroup(filter.name, q, now)
            }.also(onSource)
        }.flow.map { paging -> paging.map { it.toModel() } }
    }

    /** Full ordered list used for zapping; same order as [pagedChannels]. */
    suspend fun channels(filter: ChannelFilter, now: Long = System.currentTimeMillis()): List<Channel> = when (filter) {
        ChannelFilter.All -> channelDao.listAll(now)
        ChannelFilter.Favorites -> channelDao.listFavorites(now)
        ChannelFilter.Recent -> channelDao.listRecent(now)
        is ChannelFilter.Group -> channelDao.listGroup(filter.name, now)
    }.map { it.toModel() }

    suspend fun channel(id: Long): Channel? = channelDao.get(id, System.currentTimeMillis())?.toModel()

    fun observeChannel(id: Long): Flow<Channel?> = channelDao.observe(id, System.currentTimeMillis()).map { it?.toModel() }

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
        val row = channelDao.get(channelId, System.currentTimeMillis())?.channel ?: throw IOException("Channel no longer exists")
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
