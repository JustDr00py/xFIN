package com.xfin.data.sources

import com.xfin.core.database.entity.EpgChannelEntity
import com.xfin.data.iptv.epg.channelNameKey
import com.xfin.data.iptv.epg.epgIdKey

/** Resolves a playlist channel to a guide channel: exact tvg-id first, then normalized name. */
internal class EpgLinker(epgChannels: List<EpgChannelEntity>) {
    private val ids = epgChannels.mapTo(HashSet()) { it.epgId }
    private val byName = HashMap<String, String>().apply {
        epgChannels.forEach { if (it.nameKey.isNotEmpty()) putIfAbsent(it.nameKey, it.epgId) }
    }

    fun resolve(tvgId: String?, name: String): String? =
        tvgId?.let(::epgIdKey)?.takeIf { it in ids }
            ?: byName[channelNameKey(name)]
            ?: tvgId?.let { byName[channelNameKey(it)] }
}
