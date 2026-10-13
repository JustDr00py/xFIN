package com.xfin.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.xfin.core.designsystem.component.EmptyState
import com.xfin.core.designsystem.component.LocalIsTv
import com.xfin.core.model.MediaItem
import com.xfin.core.model.MediaKind
import com.xfin.data.jellyfin.JellyfinRepository
import com.xfin.data.sources.ChannelRepository
import com.xfin.data.sources.EpgRepository
import com.xfin.core.model.Channel
import com.xfin.core.model.ProgramSlot
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.navigation.toRoute
import javax.inject.Inject

data class SearchResults(
    val channels: List<Channel> = emptyList(),
    val movies: List<MediaItem> = emptyList(),
    val shows: List<MediaItem> = emptyList(),
    val episodes: List<MediaItem> = emptyList(),
) {
    val isEmpty get() = channels.isEmpty() && movies.isEmpty() && shows.isEmpty() && episodes.isEmpty()
}

enum class SearchScope(val label: String) { LIBRARY("Movies & shows"), LIVE("Live TV"), ALL("All") }

data class SearchState(
    val query: String = "",
    val scope: SearchScope = SearchScope.LIBRARY,
    val searching: Boolean = false,
    val results: SearchResults? = null,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    savedState: androidx.lifecycle.SavedStateHandle,
    private val jellyfin: JellyfinRepository,
    private val channels: ChannelRepository,
    private val epg: EpgRepository,
) : ViewModel() {
    private val _query = MutableStateFlow("")
    private val _scope = MutableStateFlow(
        runCatching { SearchScope.valueOf(savedState.toRoute<SearchRoute>().scope) }.getOrDefault(SearchScope.LIBRARY),
    )
    private val _state = MutableStateFlow(SearchState(scope = _scope.value))
    val state: StateFlow<SearchState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(_query.map { it.trim() }.debounce(300).distinctUntilChanged(), _scope) { q, s -> q to s }
                .collectLatest { (term, scope) ->
                if (term.length < 2) {
                    _state.value = _state.value.copy(searching = false, results = null)
                    return@collectLatest
                }
                _state.value = _state.value.copy(searching = true)
                val servers = if (scope == SearchScope.LIVE) emptyList() else jellyfin.observeServers().first()
                val (found, live) = coroutineScope {
                    val live = async {
                        if (scope == SearchScope.LIBRARY) emptyList() else runCatching { channels.search(term) }.getOrDefault(emptyList())
                    }
                    val media = servers.map { server -> async { runCatching { jellyfin.search(server.id, term) }.getOrDefault(emptyList()) } }
                    media.awaitAll().flatten() to live.await()
                }
                _state.value = _state.value.copy(
                    searching = false,
                    results = SearchResults(
                        channels = live,
                        movies = found.filter { it.kind == MediaKind.MOVIE },
                        shows = found.filter { it.kind == MediaKind.SERIES },
                        episodes = found.filter { it.kind == MediaKind.EPISODE },
                    ),
                )
            }
        }
    }

    suspend fun nowPlaying(channel: Channel, now: Long): ProgramSlot? = epg.nowPlaying(channel, now)

    fun setScope(scope: SearchScope) {
        _scope.value = scope
        _state.value = _state.value.copy(scope = scope)
    }

    fun setQuery(query: String) {
        _query.value = query
        _state.value = _state.value.copy(query = query)
    }
}

@Composable
internal fun SearchScreen(
    onOpenItem: (sourceId: Long, itemId: String) -> Unit,
    onPlayChannel: (channelId: Long) -> Unit,
    onBack: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isTv = LocalIsTv.current
    val focus = remember { FocusRequester() }
    val gutter = if (isTv) 40.dp else 16.dp

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            TextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                placeholder = {
                    Text(
                        when (state.scope) {
                            SearchScope.LIBRARY -> "Movies, shows, episodes"
                            SearchScope.LIVE -> "Live channels"
                            SearchScope.ALL -> "Channels, movies, shows"
                        },
                    )
                },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) { Icon(Icons.Rounded.Close, "Clear") }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                shape = MaterialTheme.shapes.large,
                colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                modifier = Modifier.weight(1f).focusRequester(focus),
            )
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(SearchScope.entries) { scope ->
                androidx.compose.material3.FilterChip(
                    selected = scope == state.scope,
                    onClick = { viewModel.setScope(scope) },
                    label = { Text(scope.label) },
                    colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    border = null,
                    shape = MaterialTheme.shapes.large,
                )
            }
        }
        val results = state.results
        when {
            state.searching && results == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            results == null -> EmptyState(
                icon = Icons.Rounded.Search,
                title = when (state.scope) {
                    SearchScope.LIBRARY -> "Search your library"
                    SearchScope.LIVE -> "Search live TV"
                    SearchScope.ALL -> "Search everything"
                },
                message = when (state.scope) {
                    SearchScope.LIBRARY -> "Find movies, shows and episodes."
                    SearchScope.LIVE -> "Find channels across your playlists."
                    SearchScope.ALL -> "Find live channels, movies, shows and episodes in one place."
                },
                modifier = Modifier.fillMaxSize(),
            )
            results.isEmpty -> EmptyState(
                icon = Icons.Rounded.SearchOff,
                title = "No matches",
                message = "Nothing matches “${state.query.trim()}”.",
                modifier = Modifier.fillMaxSize(),
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                if (results.channels.isNotEmpty()) {
                    item(key = "Live TV") {
                        Column {
                            Text("Live TV", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = gutter, bottom = 10.dp))
                            LazyRow(contentPadding = PaddingValues(horizontal = gutter), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(results.channels, key = { "ch-${it.id}" }) { channel ->
                                    ChannelResult(channel, if (isTv) 220.dp else 168.dp, viewModel::nowPlaying) { onPlayChannel(channel.id) }
                                }
                            }
                        }
                    }
                }
                section("Movies", results.movies, gutter) { item -> PosterCard(item, if (isTv) 160.dp else 116.dp, onClick = { onOpenItem(item.sourceId, item.id) }) }
                section("Shows", results.shows, gutter) { item -> PosterCard(item, if (isTv) 160.dp else 116.dp, onClick = { onOpenItem(item.sourceId, item.id) }) }
                section("Episodes", results.episodes, gutter) { item -> ThumbCard(item, if (isTv) 280.dp else 220.dp, onClick = { onOpenItem(item.sourceId, item.id) }) }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: String,
    items: List<MediaItem>,
    gutter: androidx.compose.ui.unit.Dp,
    card: @Composable (MediaItem) -> Unit,
) {
    if (items.isEmpty()) return
    item(key = title) {
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = gutter, bottom = 10.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = gutter), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(items, key = { "${it.sourceId}-${it.id}" }) { card(it) }
            }
        }
    }
}

/** A live channel result: logo tile, name and what's on now. */
@Composable
private fun ChannelResult(
    channel: Channel,
    width: androidx.compose.ui.unit.Dp,
    nowPlaying: suspend (Channel, Long) -> ProgramSlot?,
    onClick: () -> Unit,
) {
    val now = remember { System.currentTimeMillis() }
    val playing = com.xfin.core.designsystem.component.rememberNowPlaying(channel, now, 0, nowPlaying)
    Column(Modifier.width(width)) {
        com.xfin.core.designsystem.component.FocusSurface(onClick = onClick, shape = MaterialTheme.shapes.medium, focusedScale = 1.05f) {
            com.xfin.core.designsystem.component.ChannelLogo(
                channel.name,
                channel.logoUrl,
                Modifier.fillMaxWidth().aspectRatio(16f / 9f).padding(10.dp),
            )
        }
        Text(
            channel.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp, start = 2.dp),
        )
        Text(
            playing?.title ?: channel.group,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 2.dp),
        )
    }
}
