package com.xfin.data.sources

import com.xfin.core.database.entity.ChannelRow
import com.xfin.core.database.entity.GroupRow
import com.xfin.core.database.entity.SourceEntity
import com.xfin.core.model.Catchup
import com.xfin.core.model.Channel
import com.xfin.core.model.ChannelGroup
import com.xfin.core.model.EpgStatus
import com.xfin.core.model.ProgramSlot
import com.xfin.core.model.Source
import com.xfin.core.model.SourceType
import com.xfin.core.model.XtreamOutput

internal fun SourceEntity.toModel() = Source(
    id = id,
    name = name,
    type = SourceType.valueOf(type),
    url = url,
    username = username,
    epgUrl = epgUrl,
    userAgent = userAgent,
    xtreamOutput = runCatching { XtreamOutput.valueOf(xtreamOutput) }.getOrDefault(XtreamOutput.TS),
    channelCount = channelCount,
    lastRefreshedAt = lastRefreshedAt,
    lastError = lastError,
    epg = EpgStatus(updatedAt = epgUpdatedAt, programCount = epgProgramCount, error = epgError),
)

internal fun ChannelRow.toModel() = Channel(
    id = channel.id,
    sourceId = channel.sourceId,
    key = channel.channelKey,
    name = channel.name,
    number = channel.number,
    logoUrl = channel.logoUrl,
    group = channel.groupName,
    tvgId = channel.tvgId,
    catchup = channel.catchupMode?.let { Catchup(it, channel.catchupSource, channel.catchupDays) },
    isFavorite = isFavorite,
    epgId = channel.epgId,
    nowPlaying = run {
        val title = nowTitle
        val start = nowStart
        val end = nowEnd
        if (title != null && start != null && end != null) ProgramSlot(title, start, end) else null
    },
)

internal fun GroupRow.toModel() = ChannelGroup(name = name, channelCount = channelCount, hidden = hidden)

internal const val UNCATEGORIZED = "Uncategorized"

/** Produces stable, unique channel keys within one import. */
internal class KeyAllocator {
    private val seen = HashMap<String, Int>()

    fun allocate(base: String): String {
        val n = seen.merge(base, 1, Int::plus)!!
        return if (n == 1) base else "$base#$n"
    }
}
