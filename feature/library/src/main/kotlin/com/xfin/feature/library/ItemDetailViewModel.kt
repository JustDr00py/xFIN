package com.xfin.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.xfin.core.model.MediaItem
import com.xfin.core.model.MediaKind
import com.xfin.core.model.MediaVersion
import com.xfin.core.model.SourceType
import com.xfin.data.jellyfin.VersionPreferences
import com.xfin.data.library.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ItemDetailState(
    val item: MediaItem? = null,
    /** For series: seasons, the selected one, its episodes and the episode to offer first. */
    val seasons: List<MediaItem> = emptyList(),
    val selectedSeasonId: String? = null,
    val episodes: List<MediaItem> = emptyList(),
    val episodesLoading: Boolean = false,
    val upNext: MediaItem? = null,
    /** e.g. "blockbustr · Jellyfin" or "mmp · IPTV". */
    val source: String? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

/** Playback of [item] failed with [failedVersionId]: reopen the picker, starting at [startMs]. */
data class VersionRetryRequest(val item: MediaItem, val failedVersionId: String, val startMs: Long)

@HiltViewModel
class ItemDetailViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val jellyfin: LibraryRepository,
    private val versions: VersionPreferences,
) : ViewModel() {
    /**
     * The version Play will use: the last one that played, else the server's first. The player saves
     * it once playback starts, so a pick that fails never becomes the default.
     */
    fun versionFor(item: MediaItem): MediaVersion? {
        if (item.versions.isEmpty()) return null
        val id = versions.get(item.id)
        return item.versions.firstOrNull { it.id == id } ?: item.versions.first()
    }

    private val _versionRetry = MutableStateFlow<VersionRetryRequest?>(null)
    val versionRetry: StateFlow<VersionRetryRequest?> = _versionRetry.asStateFlow()

    fun versionRetryShown() {
        _versionRetry.value = null
    }

    private val route = savedState.toRoute<ItemRoute>()
    val sourceId: Long = route.sourceId

    private val _state = MutableStateFlow(ItemDetailState())
    val state: StateFlow<ItemDetailState> = _state.asStateFlow()

    init {
        load()
        // Sent back by the player when the user asks to try another version.
        viewModelScope.launch {
            savedState.getStateFlow<String?>(RETRY_ITEM, null).filterNotNull().collect { itemId ->
                val failed = savedState.get<String>(RETRY_FAILED_VERSION)
                val startMs = savedState.get<Long>(RETRY_START_MS) ?: 0
                savedState[RETRY_ITEM] = null
                if (failed == null) return@collect
                // Usually an episode of this series rather than the page's own title.
                val item = _state.value.item?.takeIf { it.id == itemId }
                    ?: runCatching { jellyfin.item(sourceId, itemId) }.getOrNull()
                    ?: return@collect
                if (item.versions.size > 1) _versionRetry.value = VersionRetryRequest(item, failed, startMs)
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            try {
                var item = jellyfin.item(sourceId, route.itemId)
                var preselectSeason: String? = null
                // A season on its own is shown as its series with that season selected.
                if (item.kind == MediaKind.SEASON && item.seriesId != null) {
                    preselectSeason = item.id
                    item = jellyfin.item(sourceId, item.seriesId!!)
                }
                val source = jellyfin.observeServers().first().firstOrNull { it.id == sourceId }
                    ?.let { "${it.name} · ${if (it.type == SourceType.JELLYFIN) "Jellyfin" else "IPTV"}" }
                _state.update { it.copy(item = item, source = source, loading = false, error = null) }
                if (item.kind == MediaKind.SERIES) {
                    val seasons = jellyfin.seasons(sourceId, item.id)
                    val upNext = runCatching { jellyfin.seriesNextEpisode(sourceId, item.id) }.getOrNull()
                    val season = preselectSeason ?: _state.value.selectedSeasonId
                        ?: upNext?.seasonId
                        ?: seasons.firstOrNull { it.seasonNumber != 0 }?.id
                        ?: seasons.firstOrNull()?.id
                    _state.update { it.copy(seasons = seasons, upNext = upNext) }
                    season?.let { selectSeason(it) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "Couldn't load this title") }
            }
        }
    }

    fun selectSeason(seasonId: String) {
        val seriesId = _state.value.item?.id ?: return
        _state.update { it.copy(selectedSeasonId = seasonId, episodesLoading = true) }
        viewModelScope.launch {
            val episodes = runCatching { jellyfin.episodes(sourceId, seriesId, seasonId) }.getOrDefault(emptyList())
            if (_state.value.selectedSeasonId == seasonId) _state.update { it.copy(episodes = episodes, episodesLoading = false) }
        }
    }

    fun toggleFavorite() {
        val item = _state.value.item ?: return
        val favorite = !item.isFavorite
        _state.update { it.copy(item = item.copy(isFavorite = favorite)) }
        viewModelScope.launch {
            runCatching { jellyfin.setFavorite(item, favorite) }
                .onFailure { _state.update { s -> s.copy(item = s.item?.copy(isFavorite = !favorite)) } }
        }
    }

    fun togglePlayed() {
        val item = _state.value.item ?: return
        val played = !item.played
        _state.update { it.copy(item = item.copy(played = played, positionMs = if (played) 0 else item.positionMs)) }
        viewModelScope.launch {
            runCatching { jellyfin.setPlayed(item, played) }
            load()
        }
    }
}
