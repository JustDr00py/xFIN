package com.xfin.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xfin.data.sources.SourceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.xfin.core.model.SourceType
import javax.inject.Inject

/** What the app has to show: decides the first screen. */
data class SourceSummary(val any: Boolean, val iptv: Boolean, val library: Boolean)

@HiltViewModel
class MainViewModel @Inject constructor(sources: SourceRepository) : ViewModel() {
    /** Null until the database answered; the splash screen stays up meanwhile. */
    val sources: StateFlow<SourceSummary?> = sources.observeSources()
        .map { list ->
            SourceSummary(
                any = list.isNotEmpty(),
                iptv = list.any { it.type != SourceType.JELLYFIN },
                library = list.any { it.type == SourceType.JELLYFIN },
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
