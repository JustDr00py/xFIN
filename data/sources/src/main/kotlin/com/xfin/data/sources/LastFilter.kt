package com.xfin.data.sources

import android.content.Context
import com.xfin.core.model.ChannelFilter
import com.xfin.core.model.decodeChannelFilter
import com.xfin.core.model.encode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers which channel list (favorites, a group…) each screen showed last, across restarts.
 * A remembered list that has since gone (group hidden or dropped by the provider, favorites
 * cleared) falls back to Favorites, else All.
 */
@Singleton
class LastFilter @Inject internal constructor(
    @ApplicationContext context: Context,
    private val channels: ChannelRepository,
) {
    private val prefs = context.getSharedPreferences("last_filter", Context.MODE_PRIVATE)

    suspend fun restore(screen: String): ChannelFilter {
        val saved = prefs.getString(screen, null)?.let { runCatching { decodeChannelFilter(it) }.getOrNull() }
        val hasFavorites = channels.observeHasFavorites().first()
        val available = when (saved) {
            null -> false
            ChannelFilter.All -> true
            ChannelFilter.Favorites -> hasFavorites
            ChannelFilter.Recent -> channels.observeHasHistory().first()
            is ChannelFilter.Group -> channels.observeGroups().first().any { it.name == saved.name && !it.hidden }
        }
        return if (available && saved != null) saved else if (hasFavorites) ChannelFilter.Favorites else ChannelFilter.All
    }

    fun save(screen: String, filter: ChannelFilter) {
        prefs.edit().putString(screen, filter.encode()).apply()
    }
}
