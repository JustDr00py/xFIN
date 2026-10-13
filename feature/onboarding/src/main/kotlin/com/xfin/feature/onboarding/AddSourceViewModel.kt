package com.xfin.feature.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.xfin.core.model.RefreshState
import com.xfin.core.model.XtreamOutput
import com.xfin.data.jellyfin.JellyfinRepository
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
    /** Editing an existing source rather than adding one. */
    val editing: Boolean = false,
    val loading: Boolean = false,
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
        get() = !importing && !loading && when (kind) {
            SourceKind.M3U -> url.isNotBlank()
            SourceKind.XTREAM -> url.isNotBlank() && username.isNotBlank() && password.isNotBlank()
            SourceKind.JELLYFIN -> editing && url.isNotBlank()
        }
}

@HiltViewModel
class AddSourceViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sources: SourceRepository,
    private val jellyfin: JellyfinRepository,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<AddSourceRoute>()
    private val _state = MutableStateFlow(
        AddSourceUiState(kind = SourceKind.valueOf(route.kind), editing = route.isEdit, loading = route.isEdit),
    )
    val state: StateFlow<AddSourceUiState> = _state.asStateFlow()

    init {
        if (route.isEdit) {
            viewModelScope.launch {
                val source = sources.editable(route.sourceId)
                if (source == null) {
                    _state.update { it.copy(loading = false, error = "This source was removed") }
                    return@launch
                }
                _state.update {
                    it.copy(
                        loading = false,
                        name = source.name,
                        url = source.url,
                        fileName = if (source.url.startsWith("content://")) "Playlist file" else null,
                        epgUrl = source.epgUrl.orEmpty(),
                        userAgent = source.userAgent.orEmpty(),
                        username = source.username.orEmpty(),
                        password = source.password.orEmpty(),
                        output = source.output,
                    )
                }
            }
        }
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
                val id = route.sourceId
                when {
                    !s.editing && s.kind == SourceKind.M3U -> sources.addM3u(s.name, s.url, s.epgUrl, s.userAgent)
                    !s.editing && s.kind == SourceKind.XTREAM -> sources.addXtream(s.name, s.url, s.username, s.password, s.output)
                    s.kind == SourceKind.M3U -> sources.updateM3u(id, s.name, s.url, s.epgUrl, s.userAgent)
                    s.kind == SourceKind.XTREAM -> sources.updateXtream(id, s.name, s.url, s.username, s.password, s.output, s.epgUrl)
                    s.kind == SourceKind.JELLYFIN -> jellyfin.updateServer(id, s.name, s.url)
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
