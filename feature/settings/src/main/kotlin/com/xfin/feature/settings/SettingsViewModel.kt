package com.xfin.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xfin.core.model.ChannelGroup
import com.xfin.core.model.RefreshState
import com.xfin.core.model.Source
import com.xfin.data.sources.ChannelRepository
import com.xfin.data.sources.EpgRepository
import com.xfin.data.sources.SourceRepository
import com.xfin.data.sources.backup.BackupRepository
import com.xfin.core.model.EpgRefreshState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val sources: SourceRepository,
    private val channels: ChannelRepository,
    private val epg: EpgRepository,
    private val backups: BackupRepository,
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

    private val _backupState = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val backupState: StateFlow<BackupUiState> = _backupState.asStateFlow()

    fun backup(uri: Uri, password: String) = runBackup("Saving backup…") {
        val result = backups.export(uri, password)
        "Saved ${plural(result.sources, "source")} and ${plural(result.favorites, "favorite")}. " +
            "Keep the password: the file can't be opened without it."
    }

    fun restore(uri: Uri, password: String) = runBackup("Restoring…") {
        val result = backups.restore(uri, password)
        buildString {
            append("Added ${plural(result.sourcesAdded, "source")}")
            if (result.sourcesKept > 0) append(" (${plural(result.sourcesKept, "source")} already here)")
            append(" and ${plural(result.favorites, "favorite")}.")
            if (result.sourcesAdded > 0) append(" Channels and guides are loading in the background.")
        }
    }

    fun dismissBackupResult() {
        _backupState.value = BackupUiState.Idle
    }

    private fun runBackup(working: String, block: suspend () -> String) {
        if (_backupState.value is BackupUiState.Working) return
        _backupState.value = BackupUiState.Working(working)
        viewModelScope.launch {
            _backupState.value = try {
                BackupUiState.Done(block())
            } catch (e: Exception) {
                BackupUiState.Failed(e.message ?: "Something went wrong")
            }
        }
    }

    private fun plural(n: Int, word: String) = "%,d %s".format(n, if (n == 1) word else word + "s")
}

sealed interface BackupUiState {
    data object Idle : BackupUiState
    data class Working(val message: String) : BackupUiState
    data class Done(val message: String) : BackupUiState
    data class Failed(val message: String) : BackupUiState
}
