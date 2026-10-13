package com.xfin.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xfin.core.model.LibraryView
import com.xfin.core.model.MediaItem
import com.xfin.core.model.Source
import com.xfin.data.jellyfin.JellyfinRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class RowStyle { THUMB, POSTER }

data class HomeRow(val key: String, val title: String, val items: List<MediaItem>, val style: RowStyle)

data class ServerHome(
    val server: Source,
    val libraries: List<LibraryView> = emptyList(),
    val rows: List<HomeRow> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

data class LibraryHomeState(
    /** Null until the source list is known. */
    val servers: List<ServerHome>? = null,
)

@HiltViewModel
class LibraryHomeViewModel @Inject constructor(private val jellyfin: JellyfinRepository) : ViewModel() {

    private val _state = MutableStateFlow(LibraryHomeState())
    val state: StateFlow<LibraryHomeState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            jellyfin.observeServers().map { list -> list.map { it.id } to list }
                .distinctUntilChanged { a, b -> a.first == b.first }
                .collect { (_, servers) ->
                    _state.value = LibraryHomeState(servers.map { ServerHome(it) })
                    servers.forEach { load(it) }
                }
        }
    }

    /** Re-fetch rows, e.g. after returning from the player so progress is current. */
    fun refresh() {
        _state.value.servers?.forEach { load(it.server, keepContent = true) }
    }

    private fun load(server: Source, keepContent: Boolean = false) {
        viewModelScope.launch {
            update(server.id) { if (keepContent) it.copy(error = null) else it.copy(loading = true, error = null) }
            try {
                val home = coroutineScope {
                    val resume = async { jellyfin.resume(server.id) }
                    val nextUp = async { jellyfin.nextUp(server.id) }
                    val views = jellyfin.views(server.id)
                    val latest = views.map { view -> async { view to runCatching { jellyfin.latest(server.id, view.id) }.getOrDefault(emptyList()) } }
                    val rows = buildList {
                        resume.await().takeIf { it.isNotEmpty() }?.let { add(HomeRow("resume", "Continue watching", it, RowStyle.THUMB)) }
                        // Next up often repeats what is already in "continue watching".
                        val resumeIds = resume.await().map { it.id }.toSet()
                        nextUp.await().filterNot { it.id in resumeIds }.takeIf { it.isNotEmpty() }
                            ?.let { add(HomeRow("nextup", "Next up", it, RowStyle.THUMB)) }
                        latest.awaitAll().forEach { (view, items) ->
                            if (items.isNotEmpty()) add(HomeRow("latest-${view.id}", "Recently added in ${view.name}", items, RowStyle.POSTER))
                        }
                    }
                    views to rows
                }
                update(server.id) { it.copy(libraries = home.first, rows = home.second, loading = false) }
            } catch (e: Exception) {
                update(server.id) { it.copy(loading = false, error = e.message ?: "Couldn't load ${server.name}") }
            }
        }
    }

    private fun update(sourceId: Long, transform: (ServerHome) -> ServerHome) {
        _state.value = _state.value.copy(
            servers = _state.value.servers?.map { if (it.server.id == sourceId) transform(it) else it },
        )
    }
}
