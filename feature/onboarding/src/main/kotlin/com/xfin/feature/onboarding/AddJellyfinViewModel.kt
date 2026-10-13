package com.xfin.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xfin.data.jellyfin.JellyfinConnector
import com.xfin.data.jellyfin.JellyfinRepository
import com.xfin.data.jellyfin.JellyfinServer
import com.xfin.data.jellyfin.JellyfinSession
import com.xfin.data.jellyfin.QuickConnectCode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddJellyfinState(
    val address: String = "",
    val server: JellyfinServer? = null,
    val username: String = "",
    val password: String = "",
    val quickConnect: QuickConnectCode? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val done: Boolean = false,
)

@HiltViewModel
class AddJellyfinViewModel @Inject constructor(
    private val connector: JellyfinConnector,
    private val jellyfin: JellyfinRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(AddJellyfinState())
    val state: StateFlow<AddJellyfinState> = _state.asStateFlow()
    private var pollJob: Job? = null

    fun setAddress(value: String) = _state.update { it.copy(address = value, error = null) }
    fun setUsername(value: String) = _state.update { it.copy(username = value, error = null) }
    fun setPassword(value: String) = _state.update { it.copy(password = value, error = null) }

    fun connect() {
        val address = _state.value.address
        if (address.isBlank()) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val server = connector.probe(address)
                _state.update { it.copy(server = server, busy = false) }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "Couldn't connect") }
            }
        }
    }

    fun changeServer() {
        cancelQuickConnect()
        _state.update { it.copy(server = null, error = null) }
    }

    fun signIn() {
        val s = _state.value
        val server = s.server ?: return
        if (s.username.isBlank()) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                finish(connector.login(server, s.username, s.password))
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "Sign-in failed") }
            }
        }
    }

    /** Shows a code to approve from any signed-in Jellyfin app (Settings › Quick Connect). */
    fun startQuickConnect() {
        val server = _state.value.server ?: return
        _state.update { it.copy(busy = true, error = null) }
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            try {
                val code = connector.startQuickConnect(server)
                _state.update { it.copy(quickConnect = code, busy = false) }
                while (isActive) {
                    delay(POLL_MS)
                    if (connector.isQuickConnectApproved(server, code)) {
                        _state.update { it.copy(busy = true) }
                        finish(connector.finishQuickConnect(server, code))
                        return@launch
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, quickConnect = null, error = e.message ?: "Quick Connect failed") }
            }
        }
    }

    fun cancelQuickConnect() {
        pollJob?.cancel()
        _state.update { it.copy(quickConnect = null, busy = false) }
    }

    private suspend fun finish(session: JellyfinSession) {
        jellyfin.addSource(session)
        _state.update { it.copy(busy = false, done = true) }
    }

    private companion object {
        const val POLL_MS = 3_000L
    }
}
