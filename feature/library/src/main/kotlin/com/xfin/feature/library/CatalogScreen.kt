package com.xfin.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.xfin.core.designsystem.component.FocusSurface
import com.xfin.core.designsystem.component.shimmer
import com.xfin.core.model.LibraryCategory
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
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
import androidx.compose.material3.LocalContentColor
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.xfin.core.designsystem.component.EmptyState
import com.xfin.core.designsystem.component.HeaderIconButton
import com.xfin.core.designsystem.component.LocalIsTv
import com.xfin.core.designsystem.theme.XfinColors
import com.xfin.core.model.MediaItem
import com.xfin.core.model.MediaKind
import com.xfin.core.model.Source
import com.xfin.data.jellyfin.LibrarySort
import com.xfin.data.library.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

data class Shelves(
    val sourceId: Long,
    val loading: Boolean,
    val latest: List<MediaItem> = emptyList(),
    val categories: List<LibraryCategory> = emptyList(),
    val error: String? = null,
)

/**
 * Every movie or every show of a server: rows by category (the provider's categories, or genres
 * on Jellyfin), each with "View all"; the heart switches to a grid of favorites.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CatalogViewModel @Inject constructor(private val jellyfin: LibraryRepository) : ViewModel() {
    private val kind = MutableStateFlow(MediaKind.MOVIE)
    private val _sort = MutableStateFlow(LibrarySort.ADDED)
    val sort: StateFlow<LibrarySort> = _sort.asStateFlow()
    private val _server = MutableStateFlow<Long?>(null)
    private val _favoritesOnly = MutableStateFlow(false)
    val favoritesOnly: StateFlow<Boolean> = _favoritesOnly.asStateFlow()

    val servers: StateFlow<List<Source>?> = jellyfin.observeServers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The chosen server, defaulting to the first one. */
    val server: StateFlow<Long?> = combine(_server, servers) { chosen, list ->
        chosen?.takeIf { id -> list?.any { it.id == id } == true } ?: list?.firstOrNull()?.id
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val items: Flow<PagingData<MediaItem>> = combine(server, kind, _sort, _favoritesOnly) { s, k, o, f -> Query(s, k, o, f) }
        .flatMapLatest { q ->
            if (q.server == null) emptyFlow() else kotlinx.coroutines.flow.flow { emitAll(jellyfin.pagedAll(q.server, q.kind, q.sort, q.favoritesOnly)) }
        }
        .cachedIn(viewModelScope)

    private data class Query(val server: Long?, val kind: MediaKind, val sort: LibrarySort, val favoritesOnly: Boolean)

    private val reload = MutableStateFlow(0)

    /** The rows: recently added, then one per category (provider category or Jellyfin genre). */
    val shelves: StateFlow<Shelves?> = combine(server, kind, reload) { s, k, _ -> s to k }
        .flatMapLatest { (s, k) ->
            if (s == null) {
                kotlinx.coroutines.flow.flowOf<Shelves?>(null)
            } else {
                flow<Shelves?> {
                    emit(Shelves(s, loading = true))
                    emit(
                        try {
                            coroutineScope {
                                val latest = async { runCatching { jellyfin.latestOf(s, k) }.getOrDefault(emptyList()) }
                                val categories = async { jellyfin.categories(s, k) }
                                Shelves(s, loading = false, latest = latest.await(), categories = categories.await())
                            }
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Shelves(s, loading = false, error = e.message ?: "Something went wrong")
                        },
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val rows = HashMap<String, MutableStateFlow<List<MediaItem>?>>()

    /** A category row's first titles in the current sort, loaded the first time the row is shown. */
    fun row(sourceId: Long, categoryId: String): StateFlow<List<MediaItem>?> {
        val k = kind.value
        val sort = _sort.value
        return rows.getOrPut("$sourceId|$k|$sort|$categoryId") {
            MutableStateFlow<List<MediaItem>?>(null).also { flow ->
                viewModelScope.launch {
                    flow.value = runCatching { jellyfin.categoryItems(sourceId, k, categoryId, sort) }.getOrDefault(emptyList())
                }
            }
        }
    }

    fun retry() {
        rows.clear()
        reload.value++
    }

    fun toggleFavorites() {
        _favoritesOnly.value = !_favoritesOnly.value
    }

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
    onOpenCategory: (CategoryGridRoute) -> Unit,
    onSearch: () -> Unit,
    onConnect: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: CatalogViewModel = hiltViewModel(key = kind.name),
) {
    remember(kind) { viewModel.setKind(kind) }
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val server by viewModel.server.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val favoritesOnly by viewModel.favoritesOnly.collectAsStateWithLifecycle()
    val items = viewModel.items.collectAsLazyPagingItems()
    // Favorites may have changed on a title's page (or in another Jellyfin app).
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if (favoritesOnly) items.refresh() }
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
            HeaderIconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, "Search") }
            HeaderIconButton(onClick = viewModel::toggleFavorites) {
                Icon(
                    if (favoritesOnly) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    if (favoritesOnly) "Show all" else "Show favorites",
                    tint = if (favoritesOnly) XfinColors.Favorite else LocalContentColor.current,
                )
            }
            SortMenu(sort, viewModel::sortBy)
            HeaderIconButton(onClick = onOpenSettings) { Icon(Icons.Rounded.Settings, "Settings") }
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
        if (!favoritesOnly) {
            ShelfList(
                viewModel = viewModel,
                kind = kind,
                sort = sort,
                onOpenItem = onOpenItem,
                onOpenCategory = onOpenCategory,
            )
            return@Column
        }
        LazyVerticalGrid(
            // TV: ~6 posters a row, so a whole row and its titles fit under the header.
            columns = GridCells.Adaptive(if (isTv) 124.dp else 112.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(if (isTv) 24.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (isTv) 24.dp else 16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (favoritesOnly && items.itemCount == 0 && items.loadState.refresh is LoadState.NotLoading) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "No favorite ${title.lowercase()} yet. Open one and choose Add to favorites.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
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
        HeaderIconButton(onClick = { open = true }) { Icon(Icons.AutoMirrored.Rounded.Sort, "Sort") }
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

@Composable
private fun ShelfList(
    viewModel: CatalogViewModel,
    kind: MediaKind,
    sort: LibrarySort,
    onOpenItem: (sourceId: Long, itemId: String) -> Unit,
    onOpenCategory: (CategoryGridRoute) -> Unit,
) {
    val shelves by viewModel.shelves.collectAsStateWithLifecycle()
    val isTv = LocalIsTv.current
    val gutter = if (isTv) 40.dp else 20.dp
    val posterWidth = if (isTv) 124.dp else 112.dp
    val current = shelves
    when {
        current == null || current.loading -> LoadingShelves(gutter, posterWidth)
        current.error != null -> EmptyState(
            icon = Icons.Rounded.CloudOff,
            title = "Couldn't load ${if (kind == MediaKind.SERIES) "series" else "movies"}",
            message = current.error,
            action = { TextButton(onClick = viewModel::retry) { Text("Try again") } },
            modifier = Modifier.fillMaxSize(),
        )
        else -> LazyColumn(
            contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(if (isTv) 28.dp else 22.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            val sourceId = current.sourceId
            if (current.latest.isNotEmpty()) {
                item(key = "latest") {
                    Shelf(
                        title = "Recently added",
                        count = null,
                        items = current.latest,
                        gutter = gutter,
                        posterWidth = posterWidth,
                        onOpenItem = onOpenItem,
                        onViewAll = {
                            onOpenCategory(CategoryGridRoute(sourceId, kind.name, null, "Recently added", LibrarySort.ADDED.name))
                        },
                    )
                }
            }
            items(current.categories, key = { "category-${it.id}" }) { category ->
                // Keyed by sort so a new sort reloads the row.
                val items by remember(sourceId, category.id, sort) { viewModel.row(sourceId, category.id) }.collectAsStateWithLifecycle()
                Shelf(
                    title = category.name,
                    count = category.count,
                    items = items,
                    gutter = gutter,
                    posterWidth = posterWidth,
                    onOpenItem = onOpenItem,
                    onViewAll = { onOpenCategory(CategoryGridRoute(sourceId, kind.name, category.id, category.name, sort.name)) },
                )
            }
        }
    }
}

/** One row: title (and count), posters scrolling sideways, and a "View all" card at the end. */
@Composable
private fun Shelf(
    title: String,
    count: Int?,
    items: List<MediaItem>?,
    gutter: Dp,
    posterWidth: Dp,
    onOpenItem: (sourceId: Long, itemId: String) -> Unit,
    onViewAll: () -> Unit,
) {
    if (items != null && items.isEmpty()) return
    val isTv = LocalIsTv.current
    Column {
        Row(
            // Phones can tap the header; on TV the end card is the way in, so D-pad focus stays on posters.
            Modifier
                .fillMaxWidth()
                .then(if (isTv) Modifier else Modifier.clickable(onClick = onViewAll))
                .padding(start = gutter, end = gutter, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            count?.let {
                Text(
                    "%,d".format(it),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
            if (!isTv) {
                Spacer(Modifier.weight(1f))
                Text("View all", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
        if (items == null) {
            PosterPlaceholders(gutter, posterWidth)
        } else {
            LazyRow(
                // Coming back to a row puts focus on the poster last focused in it.
                modifier = Modifier.focusRestorer(),
                contentPadding = PaddingValues(horizontal = gutter),
                horizontalArrangement = Arrangement.spacedBy(if (isTv) 20.dp else 12.dp),
            ) {
                items(items, key = { it.id }) { item ->
                    PosterCard(item, posterWidth, onClick = { onOpenItem(item.sourceId, item.id) })
                }
                if (count == null || count > items.size) {
                    item(key = "view-all") { ViewAllCard(posterWidth, onViewAll) }
                }
            }
        }
    }
}

@Composable
private fun ViewAllCard(width: Dp, onClick: () -> Unit) {
    Column(Modifier.width(width)) {
        FocusSurface(onClick = onClick, shape = MaterialTheme.shapes.medium) {
            Box(
                Modifier.fillMaxWidth().aspectRatio(2f / 3f).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        "View all",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PosterPlaceholders(gutter: Dp, posterWidth: Dp) {
    Row(Modifier.padding(start = gutter), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(7) {
            Box(Modifier.width(posterWidth).aspectRatio(2f / 3f).clip(MaterialTheme.shapes.medium).shimmer())
        }
    }
}

@Composable
private fun LoadingShelves(gutter: Dp, posterWidth: Dp) {
    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
        repeat(2) {
            Column {
                Box(Modifier.padding(start = gutter).width(160.dp).height(18.dp).clip(MaterialTheme.shapes.extraSmall).shimmer())
                Spacer(Modifier.height(12.dp))
                PosterPlaceholders(gutter, posterWidth)
            }
        }
    }
}
