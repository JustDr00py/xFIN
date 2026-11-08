package com.xfin.feature.onboarding

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.xfin.core.model.RefreshState
import com.xfin.core.model.XtreamOutput
import com.xfin.data.jellyfin.JellyfinRepository
import com.xfin.data.sources.SourceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** The "Set up from phone" dialog: [address] goes in the QR code; [opened] once the phone loads the form. */
data class PhoneSetupUiState(val address: String, val opened: Boolean = false)

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
    val phone: PhoneSetupUiState? = null,
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
    @param:ApplicationContext private val context: Context,
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
        viewModelScope.launch { save(s) }
    }

    /** Adds or updates the source from [s]; returns the error shown to the user, or null once saved. */
    private suspend fun save(s: AddSourceUiState): String? {
        _state.update { it.copy(importing = true, imported = 0, error = null) }
        return try {
            val id = route.sourceId
            when {
                !s.editing && s.kind == SourceKind.M3U -> sources.addM3u(s.name, s.url, s.epgUrl, s.userAgent)
                !s.editing && s.kind == SourceKind.XTREAM -> sources.addXtream(s.name, s.url, s.username, s.password, s.output)
                s.kind == SourceKind.M3U -> sources.updateM3u(id, s.name, s.url, s.epgUrl, s.userAgent)
                s.kind == SourceKind.XTREAM -> sources.updateXtream(id, s.name, s.url, s.username, s.password, s.output, s.epgUrl)
                s.kind == SourceKind.JELLYFIN -> jellyfin.updateServer(id, s.name, s.url)
            }
            _state.update { it.copy(importing = false, done = true) }
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = e.toUserMessage()
            _state.update { it.copy(importing = false, error = message) }
            message
        }
    }

    private var phoneServer: PhoneSetupServer? = null
    private var phoneTimeout: Job? = null

    /** Starts the local web form behind the QR code. */
    fun startPhoneSetup() {
        if (phoneServer != null || _state.value.loading) return
        val ip = localIpv4Address()
        if (ip == null) {
            _state.update { it.copy(error = "Connect the TV to Wi‑Fi or Ethernet to set up from a phone.") }
            return
        }
        val page = context.resources.openRawResource(R.raw.phone_setup).bufferedReader().use { it.readText() }
        val server = PhoneSetupServer(
            page = page,
            prefill = ::phonePrefill,
            onOpened = { _state.update { it.copy(phone = it.phone?.copy(opened = true)) } },
            onSubmit = ::submitFromPhone,
        )
        val port = try {
            server.start()
        } catch (e: IOException) {
            _state.update { it.copy(error = "Couldn't start phone setup: ${e.message}") }
            return
        }
        phoneServer = server
        _state.update { it.copy(error = null, phone = PhoneSetupUiState("http://$ip:$port/?t=${server.token}")) }
        phoneTimeout = viewModelScope.launch {
            delay(PHONE_SETUP_LIFETIME)
            stopPhoneSetup()
        }
    }

    fun stopPhoneSetup() {
        phoneServer?.stop()
        phoneServer = null
        phoneTimeout?.cancel()
        phoneTimeout = null
        _state.update { it.copy(phone = null) }
    }

    private fun phonePrefill(): PhoneSetupPrefill = _state.value.let {
        PhoneSetupPrefill(
            kind = it.kind.name,
            editing = it.editing,
            name = it.name,
            url = if (it.fileName != null) "" else it.url,
            username = it.username,
            epgUrl = it.epgUrl,
            hasPassword = it.editing && it.password.isNotEmpty(),
        )
    }

    private suspend fun submitFromPhone(form: PhoneSetupForm): PhoneSetupReply {
        val current = _state.value
        if (current.importing) return PhoneSetupReply(false, "The TV is busy saving. Try again in a moment.")
        val xtream = current.kind == SourceKind.XTREAM
        val next = current.copy(
            name = form.name.trim(),
            url = form.url.trim(),
            fileName = null,
            username = if (xtream) form.username.trim() else current.username,
            // While editing, a blank password keeps the saved one; the phone never sees it.
            password = when {
                !xtream -> current.password
                form.password.isEmpty() && current.editing -> current.password
                else -> form.password
            },
            epgUrl = if (!xtream || current.editing) form.epgUrl.trim() else current.epgUrl,
            error = null,
        )
        _state.value = next
        if (!next.canSubmit) {
            return PhoneSetupReply(false, if (xtream) "Fill in the server URL, username and password." else "Fill in the playlist URL.")
        }
        val saving = viewModelScope.async { save(next) }
        val outcome = try {
            withTimeoutOrNull(PHONE_REPLY_WAIT) { PhoneOutcome(saving.await()) }
        } catch (e: CancellationException) {
            // The screen closed under us, which only happens once the save went through.
            PhoneOutcome(if (_state.value.done) null else "The setup screen on the TV was closed.")
        }
        return when {
            outcome == null -> PhoneSetupReply(true, "The TV is still loading channels. Check the TV to see when it's done.")
            outcome.error != null -> PhoneSetupReply(false, outcome.error)
            next.editing -> PhoneSetupReply(true, "Saved. Your TV is using the new details.")
            else -> PhoneSetupReply(true, "All set! Your channels are on the TV.")
        }
    }

    private class PhoneOutcome(val error: String?)

    override fun onCleared() {
        phoneServer?.stop()
    }

    private fun Exception.toUserMessage(): String = when (this) {
        is java.net.UnknownHostException -> "Server not found. Check the address."
        is java.net.SocketTimeoutException -> "The server took too long to respond."
        is java.net.ConnectException -> "Couldn't connect to the server."
        is javax.net.ssl.SSLException -> "Secure connection failed. Try http:// instead of https://."
        else -> message ?: "Something went wrong"
    }

    private companion object {
        val PHONE_SETUP_LIFETIME = 10.minutes
        val PHONE_REPLY_WAIT = 25.seconds
    }
}
