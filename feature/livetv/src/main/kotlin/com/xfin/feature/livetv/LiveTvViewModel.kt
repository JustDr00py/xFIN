package com.xfin.feature.livetv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xfin.core.model.ChannelFilter
import com.xfin.core.model.RefreshState
import com.xfin.data.sources.ChannelRepository
import com.xfin.data.sources.SourceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** A tile on the Live TV page; opening one shows the guide for those channels. */
data class Category(val filter: ChannelFilter, val label: String, val count: Int? = null)

@HiltViewModel
class LiveTvViewModel @Inject constructor(
    channels: ChannelRepository,
    sources: SourceRepository,
) : ViewModel() {

    /** Favorites and Recent first (when there are any), then every channel, then each visible group. */
    val categories: StateFlow<List<Category>?> = combine(
        channels.observeGroups(),
        channels.observeHasFavorites(),
        channels.observeHasHistory(),
    ) { groups, hasFavorites, hasHistory ->
        val visible = groups.filterNot { it.hidden }
        buildList {
            if (hasFavorites) add(Category(ChannelFilter.Favorites, "Favorites"))
            if (hasHistory) add(Category(ChannelFilter.Recent, "Recent"))
            add(Category(ChannelFilter.All, "All channels", visible.sumOf { it.channelCount }))
            visible.forEach { add(Category(ChannelFilter.Group(it.name), it.name, it.channelCount)) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val refreshState: StateFlow<RefreshState> = sources.refreshState
}
