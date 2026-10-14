package com.xfin.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.Alignment
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xfin.core.designsystem.component.EmptyState
import com.xfin.core.designsystem.component.LocalIsTv
import com.xfin.core.designsystem.component.shimmer
import com.xfin.core.model.MediaKind

@Composable
internal fun LibraryHomeScreen(
    onOpenItem: (sourceId: Long, itemId: String) -> Unit,
    onOpenLibrary: (LibraryGridRoute) -> Unit,
    onPlay: (sourceId: Long, itemId: String, startMs: Long, mediaSourceId: String?) -> Unit,
    onConnect: () -> Unit,
    onSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onPlayChannel: (Long) -> Unit = {},
    onOpenRecentGuide: () -> Unit = {},
    viewModel: LibraryHomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isTv = LocalIsTv.current
    // Progress changes while watching; refresh rows when coming back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    val servers = state.servers ?: return
    if (servers.isEmpty()) {
        EmptyState(
            icon = Icons.Rounded.VideoLibrary,
            title = "Your movies and shows",
            message = "Connect a Jellyfin server to browse and play your library here, next to live TV.",
            modifier = Modifier.fillMaxSize(),
            action = { Button(onClick = onConnect) { Text("Connect Jellyfin") } },
        )
        return
    }

    val thumbWidth = if (isTv) 300.dp else 240.dp
    val posterWidth = if (isTv) 170.dp else 124.dp
    val gutter = if (isTv) 40.dp else 20.dp

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 32.dp, top = if (isTv) 24.dp else 8.dp),
        verticalArrangement = Arrangement.spacedBy(if (isTv) 28.dp else 22.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = gutter, end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Home", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, "Search") }
                IconButton(onClick = onOpenSettings) { Icon(Icons.Rounded.Settings, "Settings") }
            }
        }
        item(key = "tv-guide") { RecentChannelsSection(gutter, onPlayChannel = onPlayChannel, onViewAll = onOpenRecentGuide) }
        servers.forEach { home ->
            if (servers.size > 1) {
                item(key = "server-${home.server.id}") {
                    Text(
                        home.server.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = gutter),
                    )
                }
            }
            when {
                home.loading && home.rows.isEmpty() -> item(key = "loading-${home.server.id}") { LoadingRows(gutter, thumbWidth) }
                home.error != null && home.rows.isEmpty() -> item(key = "error-${home.server.id}") {
                    EmptyState(
                        icon = Icons.Rounded.CloudOff,
                        title = "Can't reach ${home.server.name}",
                        message = home.error,
                        action = { TextButton(onClick = viewModel::refresh) { Text("Try again") } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                else -> {
                    home.rows.forEach { row ->
                        item(key = "${home.server.id}-${row.key}") {
                            Section(row.title, gutter) {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = gutter),
                                    horizontalArrangement = Arrangement.spacedBy(if (isTv) 20.dp else 12.dp),
                                ) {
                                    items(row.items, key = { it.id }) { item ->
                                        val open = {
                                            // Resumable rows play right away; everything else opens details.
                                            if (row.style == RowStyle.THUMB && item.kind == MediaKind.EPISODE || item.canResume) {
                                                onPlay(item.sourceId, item.id, if (item.canResume) item.positionMs else 0, null)
                                            } else {
                                                onOpenItem(item.sourceId, item.id)
                                            }
                                        }
                                        when (row.style) {
                                            RowStyle.THUMB -> ThumbCard(item, thumbWidth, open)
                                            RowStyle.POSTER -> PosterCard(item, posterWidth, onClick = { onOpenItem(item.sourceId, item.id) })
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (home.libraries.isNotEmpty()) {
                        item(key = "libraries-${home.server.id}") {
                            Section("Libraries", gutter) {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = gutter),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    items(home.libraries, key = { it.id }) { view ->
                                        LibraryTile(view, thumbWidth) {
                                            onOpenLibrary(LibraryGridRoute(view.sourceId, view.id, view.name, view.collectionType))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, gutter: androidx.compose.ui.unit.Dp, content: @Composable () -> Unit) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = gutter, bottom = 10.dp),
        )
        content()
    }
}

@Composable
private fun LoadingRows(gutter: androidx.compose.ui.unit.Dp, width: androidx.compose.ui.unit.Dp) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        repeat(2) {
            Column(Modifier.padding(start = gutter)) {
                Box(Modifier.width(160.dp).height(18.dp).clip(MaterialTheme.shapes.extraSmall).shimmer())
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(3) { Box(Modifier.width(width).aspectRatio(16f / 9f).clip(MaterialTheme.shapes.medium).shimmer()) }
                }
            }
        }
    }
}
