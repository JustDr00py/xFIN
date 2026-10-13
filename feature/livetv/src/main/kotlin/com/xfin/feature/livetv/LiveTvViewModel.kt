package com.xfin.feature.livetv

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.cachedIn
import com.xfin.core.model.Channel
import com.xfin.core.model.ChannelFilter
import com.xfin.core.model.RefreshState
import com.xfin.core.model.decodeChannelFilter
import com.xfin.core.model.encode
import com.xfin.data.sources.ChannelRepository
import com.xfin.data.sources.SourceRepository
import com.xfin.data.sources.minuteTicks
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FilterTab(val filter: ChannelFilter, val label: String, val count: Int? = null)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class LiveTvViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val channels: ChannelRepository,
    sources: SourceRepository,
) : ViewModel() {

    private val _filter = MutableStateFlow(savedState.get<String>(KEY_FILTER)?.let(::decodeChannelFilter))
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val tabs: StateFlow<List<FilterTab>> = combine(
        channels.observeGroups(),
        channels.observeHasFavorites(),
        channels.observeHasHistory(),
    ) { groups, hasFavorites, hasHistory ->
        buildList {
            if (hasFavorites) add(FilterTab(ChannelFilter.Favorites, "Favorites"))
            if (hasHistory) add(FilterTab(ChannelFilter.Recent, "Recent"))
            add(FilterTab(ChannelFilter.All, "All channels", groups.filterNot { it.hidden }.sumOf { it.channelCount }))
            groups.filterNot { it.hidden }.forEach { add(FilterTab(ChannelFilter.Group(it.name), it.name, it.channelCount)) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Selected filter; defaults to Favorites when the user has some, otherwise All. */
    val filter: StateFlow<ChannelFilter?> = _filter.asStateFlow()

    /** Wall clock, ticking each minute, so "on now" and progress bars stay current. */
    val now: StateFlow<Long> = minuteTicks().stateIn(viewModelScope, SharingStarted.Eagerly, System.currentTimeMillis())

    @Volatile
    private var liveSource: PagingSource<Int, *>? = null

    val channelPages: Flow<PagingData<Channel>> = combine(
        _filter,
        _query.debounce { if (it.isEmpty()) 0 else 250 }.distinctUntilChanged(),
    ) { filter, query -> filter to query }
        .flatMapLatest { (filter, query) ->
            // An active search always spans every channel.
            channels.pagedChannels(
                filter = if (query.isNotBlank()) ChannelFilter.All else filter ?: ChannelFilter.All,
                query = query,
                onSource = { liveSource = it },
            )
        }
        .cachedIn(viewModelScope)

    val refreshState: StateFlow<RefreshState> = sources.refreshState

    init {
        // Re-query in place each minute so "on now" moves on without losing the scroll position.
        viewModelScope.launch { now.drop(1).collect { liveSource?.invalidate() } }
        if (_filter.value == null) {
            viewModelScope.launch {
                val hasFavorites = channels.observeHasFavorites().first()
                if (_filter.value == null) select(if (hasFavorites) ChannelFilter.Favorites else ChannelFilter.All)
            }
        }
    }

    fun select(filter: ChannelFilter) {
        _filter.value = filter
        savedState[KEY_FILTER] = filter.encode()
    }

    fun search(query: String) {
        _query.value = query
    }

    fun toggleFavorite(channel: Channel) {
        viewModelScope.launch { channels.setFavorite(channel, !channel.isFavorite) }
    }

    /** The zapping context for the player: the search results play within "All". */
    fun playbackFilter(): ChannelFilter =
        if (_query.value.isNotBlank()) ChannelFilter.All else _filter.value ?: ChannelFilter.All

    private companion object {
        const val KEY_FILTER = "filter"
    }
}
