package com.xfin.feature.guide

import android.util.LruCache
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.xfin.core.model.Channel
import com.xfin.core.model.ChannelFilter
import com.xfin.core.model.EpgRefreshState
import com.xfin.core.model.Program
import com.xfin.core.model.decodeChannelFilter
import com.xfin.core.model.encode
import com.xfin.data.sources.ChannelRepository
import com.xfin.data.sources.EpgRepository
import com.xfin.data.sources.LastFilter
import com.xfin.data.sources.minuteTicks
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class GuideTab(val filter: ChannelFilter, val label: String)

/** The time span the grid covers, in epoch millis. */
data class GuideWindow(val start: Long, val end: Long)

data class ProgramSelection(val channel: Channel, val program: Program?)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GuideViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val channels: ChannelRepository,
    private val epg: EpgRepository,
    private val lastFilter: LastFilter,
) : ViewModel() {

    val now: StateFlow<Long> = minuteTicks().stateIn(viewModelScope, SharingStarted.Eagerly, System.currentTimeMillis())

    val window: GuideWindow = run {
        val halfHour = TimeUnit.MINUTES.toMillis(30)
        // A day back (as much past as the guide keeps) so past programs can be watched via catch-up.
        val now = System.currentTimeMillis() / halfHour * halfHour
        GuideWindow(now - TimeUnit.DAYS.toMillis(1), now + TimeUnit.DAYS.toMillis(3))
    }

    private val _filter = MutableStateFlow(savedState.get<String>(KEY_FILTER)?.let(::decodeChannelFilter))
    val filter: StateFlow<ChannelFilter?> = _filter.asStateFlow()

    val tabs: StateFlow<List<GuideTab>> = combine(
        channels.observeGroups(),
        channels.observeHasFavorites(),
    ) { groups, hasFavorites ->
        buildList {
            if (hasFavorites) add(GuideTab(ChannelFilter.Favorites, "Favorites"))
            add(GuideTab(ChannelFilter.All, "All channels"))
            groups.filterNot { it.hidden }.forEach { add(GuideTab(ChannelFilter.Group(it.name), it.name)) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val channelPages: Flow<PagingData<Channel>> = _filter.filterNotNull()
        .flatMapLatest { channels.pagedChannels(it, query = "") }
        .cachedIn(viewModelScope)

    private val cache = LruCache<Long, List<Program>>(CACHE_ROWS)

    /** Bumps when a new guide is swapped in; rows reload their programs when it changes. */
    val guideVersion: StateFlow<Int> = epg.observeGuideVersion()
        .onEach { cache.evictAll() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** Null while unknown; false when no channel is linked to guide data yet. */
    val hasGuide: StateFlow<Boolean?> = epg.observeHasGuide().map { it > 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val refresh: StateFlow<EpgRefreshState> = epg.state

    private val _selection = MutableStateFlow<ProgramSelection?>(null)
    val selection: StateFlow<ProgramSelection?> = _selection.asStateFlow()

    init {
        if (_filter.value == null) {
            viewModelScope.launch {
                val restored = lastFilter.restore(SCREEN)
                if (_filter.value == null) {
                    _filter.value = restored
                    savedState[KEY_FILTER] = restored.encode()
                }
            }
        }
    }

    fun select(filter: ChannelFilter) {
        _filter.value = filter
        savedState[KEY_FILTER] = filter.encode()
        lastFilter.save(SCREEN, filter)
    }

    suspend fun programs(channel: Channel): List<Program> {
        cache.get(channel.id)?.let { return it }
        return epg.programs(channel, window.start, window.end).also { cache.put(channel.id, it) }
    }

    /** Already-loaded programs, so a row scrolled into view can draw them in its first frame. */
    fun cachedPrograms(channel: Channel): List<Program>? = cache.get(channel.id)

    private val prefetching = HashSet<Long>()

    /** Loads the rows around the visible ones in one query, ahead of scrolling. */
    fun prefetch(rows: List<Channel>) {
        val missing = rows.filter { cache.get(it.id) == null && it.id !in prefetching }
        if (missing.isEmpty()) return
        prefetching += missing.map { it.id }
        viewModelScope.launch {
            try {
                epg.programsFor(missing, window.start, window.end).forEach { (id, programs) -> cache.put(id, programs) }
            } finally {
                prefetching -= missing.map { it.id }.toSet()
            }
        }
    }

    fun open(channel: Channel, program: Program?) {
        _selection.value = ProgramSelection(channel, program)
    }

    fun dismiss() {
        _selection.value = null
    }

    private companion object {
        const val KEY_FILTER = "filter"
        const val SCREEN = "guide"
        const val CACHE_ROWS = 300
    }
}
