package com.xfin.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.xfin.core.designsystem.component.EmptyState
import com.xfin.core.designsystem.component.LocalIsTv
import com.xfin.core.model.MediaItem
import com.xfin.core.model.MediaKind
import com.xfin.core.model.Source
import com.xfin.data.library.LibraryRepository
import com.xfin.data.jellyfin.LibrarySort
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Every movie or every show across a server's libraries, like UHF's Movies and TV Shows tabs. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CatalogViewModel @Inject constructor(private val jellyfin: LibraryRepository) : ViewModel() {
    private val kind = MutableStateFlow(MediaKind.MOVIE)
    private val _sort = MutableStateFlow(LibrarySort.ADDED)
    val sort: StateFlow<LibrarySort> = _sort.asStateFlow()
    private val _server = MutableStateFlow<Long?>(null)

    val servers: StateFlow<List<Source>?> = jellyfin.observeServers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The chosen server, defaulting to the first one. */
    val server: StateFlow<Long?> = combine(_server, servers) { chosen, list ->
        chosen?.takeIf { id -> list?.any { it.id == id } == true } ?: list?.firstOrNull()?.id
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val items: Flow<PagingData<MediaItem>> = combine(server, kind, _sort) { s, k, o -> Triple(s, k, o) }
        .flatMapLatest { (s, k, o) -> if (s == null) emptyFlow() else kotlinx.coroutines.flow.flow { emitAll(jellyfin.pagedAll(s, k, o)) } }
        .cachedIn(viewModelScope)

    fun setKind(value: MediaKind) {
        kind.value = value
    }

    fun sortBy(value: LibrarySort) {
        _sort.value = value
    }

    fun selectServer(id: Long) {
        _server.value = id
    }
}

@Composable
internal fun CatalogScreen(
    kind: MediaKind,
    onOpenItem: (sourceId: Long, itemId: String) -> Unit,
    onSearch: () -> Unit,
    onConnect: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: CatalogViewModel = hiltViewModel(key = kind.name),
) {
    remember(kind) { viewModel.setKind(kind) }
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val server by viewModel.server.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val items = viewModel.items.collectAsLazyPagingItems()
    val isTv = LocalIsTv.current
    val title = if (kind == MediaKind.SERIES) "Series" else "Movies"

    val list = servers ?: return
    if (list.isEmpty()) {
        EmptyState(
            icon = Icons.Rounded.VideoLibrary,
            title = "No $title yet",
            message = "Connect a Jellyfin server to browse your ${title.lowercase()}.",
            modifier = Modifier.fillMaxSize(),
            action = { Button(onClick = onConnect) { Text("Connect Jellyfin") } },
        )
        return
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = if (isTv) 40.dp else 20.dp, end = 8.dp, top = if (isTv) 24.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, "Search") }
            SortMenu(sort, viewModel::sortBy)
            IconButton(onClick = onOpenSettings) { Icon(Icons.Rounded.Settings, "Settings") }
        }
        if (list.size > 1) {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list, key = { it.id }) { s ->
                    FilterChip(
                        selected = s.id == server,
                        onClick = { viewModel.selectServer(s.id) },
                        label = { Text(s.name) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        border = null,
                        shape = MaterialTheme.shapes.large,
                    )
                }
            }
        }
        LazyVerticalGrid(
            // TV: ~6 posters a row, so a whole row and its titles fit under the header.
            columns = GridCells.Adaptive(if (isTv) 124.dp else 112.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(if (isTv) 24.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (isTv) 24.dp else 16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(items.itemCount, key = items.itemKey { "${it.sourceId}-${it.id}" }) { index ->
                val item = items[index]
                if (item != null) {
                    PosterCard(item, width = androidx.compose.ui.unit.Dp.Unspecified, onClick = { onOpenItem(item.sourceId, item.id) })
                } else {
                    Box(Modifier.padding(4.dp))
                }
            }
        }
    }
}

@Composable
internal fun SortMenu(sort: LibrarySort, onSort: (LibrarySort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.AutoMirrored.Rounded.Sort, "Sort") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            LibrarySort.entries.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            when (option) {
                                LibrarySort.NAME -> "Name"
                                LibrarySort.ADDED -> "Recently added"
                                LibrarySort.YEAR -> "Release year"
                                LibrarySort.RATING -> "Rating"
                            },
                            color = if (option == sort) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    onClick = {
                        onSort(option)
                        open = false
                    },
                )
            }
        }
    }
}
