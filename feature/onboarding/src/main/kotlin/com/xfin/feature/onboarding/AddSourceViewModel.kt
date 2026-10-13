package com.xfin.feature.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.xfin.core.model.RefreshState
import com.xfin.core.model.XtreamOutput
import com.xfin.data.sources.SourceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddSourceUiState(
    val kind: SourceKind,
    val name: String = "",
    val url: String = "",
    val fileName: String? = null,
    val epgUrl: String = "",
    val userAgent: String = "",
    val username: String = "",
    val password: String = "",
    val output: XtreamOutput = XtreamOutput.TS,
    val importing: Boolean = false,
    val imported: Int = 0,
    val error: String? = null,
    val done: Boolean = false,
) {
    val canSubmit: Boolean
        get() = !importing && when (kind) {
            SourceKind.M3U -> url.isNotBlank()
            SourceKind.XTREAM -> url.isNotBlank() && username.isNotBlank() && password.isNotBlank()
        }
}

@HiltViewModel
class AddSourceViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sources: SourceRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(
        AddSourceUiState(kind = SourceKind.valueOf(savedStateHandle.toRoute<AddSourceRoute>().kind)),
    )
    val state: StateFlow<AddSourceUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            sources.refreshState.collect { refresh ->
                if (refresh is RefreshState.Running) _state.update { it.copy(imported = refresh.itemsImported) }
            }
        }
    }

    fun update(transform: AddSourceUiState.() -> AddSourceUiState) = _state.update { it.transform().copy(error = null) }

    fun pickedFile(uri: String, displayName: String?) = update { copy(url = uri, fileName = displayName ?: "Playlist file") }

    fun submit() {
        val s = _state.value
        if (!s.canSubmit) return
        _state.update { it.copy(importing = true, imported = 0, error = null) }
        viewModelScope.launch {
            try {
                when (s.kind) {
                    SourceKind.M3U -> sources.addM3u(s.name, s.url, s.epgUrl, s.userAgent)
                    SourceKind.XTREAM -> sources.addXtream(s.name, s.url, s.username, s.password, s.output)
                }
                _state.update { it.copy(importing = false, done = true) }
            } catch (e: Exception) {
                _state.update { it.copy(importing = false, error = e.toUserMessage()) }
            }
        }
    }

    private fun Exception.toUserMessage(): String = when (this) {
        is java.net.UnknownHostException -> "Server not found. Check the address."
        is java.net.SocketTimeoutException -> "The server took too long to respond."
        is java.net.ConnectException -> "Couldn't connect to the server."
        is javax.net.ssl.SSLException -> "Secure connection failed. Try http:// instead of https://."
        else -> message ?: "Something went wrong"
    }
}
