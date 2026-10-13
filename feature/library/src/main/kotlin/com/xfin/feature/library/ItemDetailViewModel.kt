package com.xfin.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.xfin.core.model.MediaItem
import com.xfin.core.model.MediaKind
import com.xfin.data.jellyfin.JellyfinRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ItemDetailState(
    val item: MediaItem? = null,
    /** For series: seasons, the selected one, its episodes and the episode to offer first. */
    val seasons: List<MediaItem> = emptyList(),
    val selectedSeasonId: String? = null,
    val episodes: List<MediaItem> = emptyList(),
    val episodesLoading: Boolean = false,
    val upNext: MediaItem? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class ItemDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val jellyfin: JellyfinRepository,
) : ViewModel() {
    private val route = savedState.toRoute<ItemRoute>()
    val sourceId: Long = route.sourceId

    private val _state = MutableStateFlow(ItemDetailState())
    val state: StateFlow<ItemDetailState> = _state.asStateFlow()

    init {
        load()
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
                _state.update { it.copy(item = item, loading = false, error = null) }
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

    fun togglePlayed() {
        val item = _state.value.item ?: return
        val played = !item.played
        _state.update { it.copy(item = item.copy(played = played, positionMs = if (played) 0 else item.positionMs)) }
        viewModelScope.launch {
            runCatching { jellyfin.setPlayed(sourceId, item.id, played) }
            load()
        }
    }
}
