package com.xfin.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xfin.data.sources.SourceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(sources: SourceRepository) : ViewModel() {
    /** Null until the database answered; the splash screen stays up meanwhile. */
    val hasSources: StateFlow<Boolean?> =
        sources.observeHasSources().stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
