package com.xfin.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.xfin.core.designsystem.component.EmptyState
import com.xfin.core.designsystem.component.LocalIsTv
import com.xfin.core.designsystem.component.formatTime
import com.xfin.core.model.Channel
import com.xfin.core.model.MediaItem
import com.xfin.core.model.MediaKind
import com.xfin.core.model.Program
import com.xfin.core.model.ProgramSlot
import com.xfin.data.library.LibraryRepository
import com.xfin.data.sources.ChannelRepository
import com.xfin.data.sources.EpgRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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

/** A program found in the guide, with a channel showing it. */
data class ProgramHit(val channel: Channel, val program: Program)

data class SearchResults(
    val onNow: List<ProgramHit> = emptyList(),
    val later: List<ProgramHit> = emptyList(),
    val channels: List<Channel> = emptyList(),
    val movies: List<MediaItem> = emptyList(),
    val shows: List<MediaItem> = emptyList(),
    val episodes: List<MediaItem> = emptyList(),
    /** Source names by id; empty with a single source, where a badge would say nothing. */
    val sources: Map<Long, String> = emptyMap(),
) {
    val isEmpty get() = onNow.isEmpty() && later.isEmpty() && channels.isEmpty() && movies.isEmpty() && shows.isEmpty() && episodes.isEmpty()
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
    private val jellyfin: LibraryRepository,
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
                val now = System.currentTimeMillis()
                val (found, live, programs) = coroutineScope {
                    val live = async {
                        if (scope == SearchScope.LIBRARY) emptyList() else runCatching { channels.search(term) }.getOrDefault(emptyList())
                    }
                    val programs = async {
                        if (scope == SearchScope.LIBRARY) emptyList() else runCatching { programHits(term, now) }.getOrDefault(emptyList())
                    }
                    val media = servers.map { server -> async { runCatching { jellyfin.search(server.id, term) }.getOrDefault(emptyList()) } }
                    Triple(media.awaitAll().flatten(), live.await(), programs.await())
                }
                _state.value = _state.value.copy(
                    searching = false,
                    results = SearchResults(
                        onNow = programs.filter { it.program.isAiring(now) },
                        later = programs.filter { it.program.start > now },
                        channels = live,
                        movies = found.filter { it.kind == MediaKind.MOVIE },
                        shows = found.filter { it.kind == MediaKind.SERIES },
                        episodes = found.filter { it.kind == MediaKind.EPISODE },
                        sources = if (servers.size > 1) servers.associate { it.id to it.name } else emptyMap(),
                    ),
                )
            }
        }
    }

    suspend fun nowPlaying(channel: Channel, now: Long): ProgramSlot? = epg.nowPlaying(channel, now)

    /** Guide programs matching [term] from now on, with their channels. */
    private suspend fun programHits(term: String, now: Long): List<ProgramHit> {
        val found = epg.searchPrograms(term, now)
        val byId = channels.channels(found.map { it.first }.distinct()).associateBy { it.id }
        return found.mapNotNull { (channelId, program) -> byId[channelId]?.let { ProgramHit(it, program) } }
    }

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
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val gutter = if (isTv) 40.dp else 16.dp
    var details by remember { androidx.compose.runtime.mutableStateOf<ProgramHit?>(null) }

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
                            SearchScope.LIVE -> "Channels and programs"
                            SearchScope.ALL -> "Channels, programs, movies, shows"
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
                // Results follow the typing already; Search just puts the keyboard away. On TV,
                // stepping focus down to the results is what closes it and leaves the remote there.
                keyboardActions = KeyboardActions(
                    onSearch = {
                        keyboard?.hide()
                        if (!isTv || !focusManager.moveFocus(FocusDirection.Down)) focusManager.clearFocus()
                    },
                ),
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
                    SearchScope.LIVE -> "Find channels, and programs on now or coming up in the guide."
                    SearchScope.ALL -> "Find live channels, programs, movies, shows and episodes in one place."
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
                if (results.onNow.isNotEmpty()) {
                    item(key = "On now") {
                        ProgramSection("On now", results.onNow, gutter, isTv, now = remember { System.currentTimeMillis() }) { onPlayChannel(it.channel.id) }
                    }
                }
                if (results.later.isNotEmpty()) {
                    item(key = "Coming up") {
                        ProgramSection("Coming up", results.later, gutter, isTv, now = remember { System.currentTimeMillis() }) { details = it }
                    }
                }
                if (results.channels.isNotEmpty()) {
                    item(key = "Live TV") {
                        Column {
                            Text("Channels", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = gutter, bottom = 10.dp))
                            LazyRow(contentPadding = PaddingValues(horizontal = gutter), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(results.channels, key = { "ch-${it.id}" }) { channel ->
                                    ChannelResult(channel, if (isTv) 220.dp else 168.dp, viewModel::nowPlaying) { onPlayChannel(channel.id) }
                                }
                            }
                        }
                    }
                }
                section("Movies", results.movies, gutter) { item -> PosterCard(item, if (isTv) 160.dp else 116.dp, onClick = { onOpenItem(item.sourceId, item.id) }, badge = results.sources[item.sourceId]) }
                section("Shows", results.shows, gutter) { item -> PosterCard(item, if (isTv) 160.dp else 116.dp, onClick = { onOpenItem(item.sourceId, item.id) }, badge = results.sources[item.sourceId]) }
                section("Episodes", results.episodes, gutter) { item -> ThumbCard(item, if (isTv) 280.dp else 220.dp, onClick = { onOpenItem(item.sourceId, item.id) }, badge = results.sources[item.sourceId]) }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    details?.let { hit ->
        AlertDialog(
            onDismissRequest = { details = null },
            title = { Text(hit.program.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${hit.channel.name} · ${airingLabel(hit.program.start)} – ${formatTime(hit.program.end)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    listOfNotNull(hit.program.subtitle, hit.program.description).forEach {
                        Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 8, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    details = null
                    onPlayChannel(hit.channel.id)
                }) { Text("Watch ${hit.channel.name}") }
            },
            dismissButton = { TextButton(onClick = { details = null }) { Text("Close") } },
        )
    }
}

/** A row of programs from the guide: what's on, where, and how far in (or when it starts). */
@Composable
private fun ProgramSection(
    title: String,
    hits: List<ProgramHit>,
    gutter: androidx.compose.ui.unit.Dp,
    isTv: Boolean,
    now: Long,
    onClick: (ProgramHit) -> Unit,
) {
    Column {
        // The count hints that the row scrolls sideways (a phone shows two at a time).
        Text("$title · ${hits.size}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = gutter, bottom = 10.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = gutter), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(hits, key = { "p-${it.program.id}" }) { hit -> ProgramResult(hit, if (isTv) 300.dp else 240.dp, now) { onClick(hit) } }
        }
    }
}

@Composable
private fun ProgramResult(hit: ProgramHit, width: androidx.compose.ui.unit.Dp, now: Long, onClick: () -> Unit) {
    val airing = hit.program.isAiring(now)
    val tint = com.xfin.core.designsystem.component.rememberLogoTint(hit.channel.logoUrl)
    val colors = MaterialTheme.colorScheme
    com.xfin.core.designsystem.component.FocusSurface(onClick = onClick, focusedScale = 1.04f, modifier = Modifier.width(width)) {
        Column(
            Modifier
                .fillMaxWidth()
                .background((tint?.copy(alpha = if (airing) 0.3f else 0.15f) ?: Color.Transparent).compositeOver(colors.surfaceContainerHigh))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.xfin.core.designsystem.component.ChannelLogo(hit.channel.name, hit.channel.logoUrl, Modifier.width(52.dp).aspectRatio(16f / 10f))
                androidx.compose.foundation.layout.Spacer(Modifier.width(10.dp))
                Text(
                    hit.channel.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            Text(
                hit.program.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Text(
                if (airing) "Until ${formatTime(hit.program.end)}" else airingLabel(hit.program.start),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            if (airing) com.xfin.core.designsystem.component.ProgressTrack(hit.program.progress(now), Modifier.fillMaxWidth())
        }
    }
}

/** "9:30 PM" today, "Tomorrow 9:30 PM", or "Thu 9:30 PM". */
private fun airingLabel(time: Long): String {
    val day = java.util.Calendar.getInstance().apply { timeInMillis = time }
    val today = java.util.Calendar.getInstance()
    val diff = day.get(java.util.Calendar.DAY_OF_YEAR) - today.get(java.util.Calendar.DAY_OF_YEAR) +
        (day.get(java.util.Calendar.YEAR) - today.get(java.util.Calendar.YEAR)) * 365
    val prefix = when (diff) {
        0 -> ""
        1 -> "Tomorrow "
        else -> java.text.SimpleDateFormat("EEE ", java.util.Locale.getDefault()).format(day.time)
    }
    return prefix + formatTime(time)
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
