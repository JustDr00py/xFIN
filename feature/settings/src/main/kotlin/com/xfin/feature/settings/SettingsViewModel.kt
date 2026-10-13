package com.xfin.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xfin.core.model.ChannelGroup
import com.xfin.core.model.RefreshState
import com.xfin.core.model.Source
import com.xfin.data.sources.ChannelRepository
import com.xfin.data.sources.EpgRepository
import com.xfin.data.sources.SourceRepository
import com.xfin.core.model.EpgRefreshState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val sources: SourceRepository,
    private val channels: ChannelRepository,
    private val epg: EpgRepository,
) : ViewModel() {

    val epgState: StateFlow<EpgRefreshState> = epg.state

    fun setEpgUrl(source: Source, url: String) {
        viewModelScope.launch { epg.setEpgUrl(source.id, url) }
    }

    fun refreshGuide(source: Source) = epg.launchRefresh(source.id)

    val sourceList: StateFlow<List<Source>> =
        sources.observeSources().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val groups: StateFlow<List<ChannelGroup>> =
        channels.observeGroups().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val refreshState: StateFlow<RefreshState> = sources.refreshState

    fun refresh(source: Source) {
        viewModelScope.launch { runCatching { sources.refresh(source.id) } }
    }

    fun delete(source: Source) {
        viewModelScope.launch { sources.delete(source.id) }
    }

    fun setGroupVisible(group: ChannelGroup, visible: Boolean) {
        viewModelScope.launch { channels.setGroupHidden(group.name, !visible) }
    }
}
