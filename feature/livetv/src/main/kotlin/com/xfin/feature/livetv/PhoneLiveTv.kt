package com.xfin.feature.livetv

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.xfin.core.designsystem.component.windowWidthDp
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.compose.itemKey
import com.xfin.core.designsystem.component.ChannelLogo
import com.xfin.core.designsystem.component.EmptyState
import com.xfin.core.designsystem.component.NowPlayingLine
import com.xfin.core.designsystem.component.rememberNowPlaying
import com.xfin.core.designsystem.component.shimmer
import com.xfin.core.designsystem.theme.XfinColors
import com.xfin.core.model.Channel
import com.xfin.core.model.RefreshState

@Composable
internal fun PhoneLiveTv(state: LiveTvState, onSearch: () -> Unit, onOpenSettings: () -> Unit) {
    var searching by rememberSaveable { mutableStateOf(false) }
    val wide = windowWidthDp() >= 600

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Header(
            searching = searching,
            query = state.query,
            onQuery = state.onQuery,
            // Search opens the app-wide search, scoped to live TV.
            onToggleSearch = onSearch,
            onOpenSettings = onOpenSettings,
        )
        val refresh = state.refresh
        AnimatedVisibility(refresh is RefreshState.Running) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                if (refresh is RefreshState.Running && refresh.itemsImported > 0) {
                    Text(
                        "Updating channels… ${"%,d".format(refresh.itemsImported)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        AnimatedVisibility(!searching || state.query.isBlank()) {
            FilterRow(state)
        }
        ChannelGrid(state, columns = if (wide) GridCells.Adaptive(320.dp) else GridCells.Fixed(1))
    }
}

@Composable
private fun Header(
    searching: Boolean,
    query: String,
    onQuery: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(searching, transitionSpec = { fadeIn() togetherWith fadeOut() }, modifier = Modifier.weight(1f)) { active ->
            if (active) {
                TextField(
                    value = query,
                    onValueChange = onQuery,
                    placeholder = { Text("Search channels") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    shape = MaterialTheme.shapes.large,
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus),
                )
                LaunchedEffect(Unit) { focus.requestFocus() }
            } else {
                Text("Live TV", style = MaterialTheme.typography.headlineMedium)
            }
        }
        IconButton(onClick = onToggleSearch) {
            Icon(if (searching) Icons.Rounded.Close else Icons.Rounded.Search, if (searching) "Close search" else "Search")
        }
        IconButton(onClick = onOpenSettings) { Icon(Icons.Rounded.Settings, "Settings") }
    }
}

@Composable
private fun FilterRow(state: LiveTvState) {
    val listState = rememberLazyListState()
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.tabs, key = { it.label + it.filter.hashCode() }) { tab ->
            val selected = tab.filter == state.selected
            FilterChip(
                selected = selected,
                onClick = { state.onSelect(tab.filter) },
                label = { Text(tab.label, maxLines = 1) },
                leadingIcon = if (tab.filter == com.xfin.core.model.ChannelFilter.Favorites) {
                    { Icon(Icons.Rounded.Star, null, Modifier.size(16.dp), tint = XfinColors.Favorite) }
                } else {
                    null
                },
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

@Composable
private fun ChannelGrid(state: LiveTvState, columns: GridCells) {
    val channels = state.channels
    val gridState = rememberLazyGridState()
    LaunchedEffect(state.selected) { gridState.scrollToItem(0) }

    when {
        channels.itemCount == 0 && channels.loadState.refresh is androidx.paging.LoadState.Loading -> LoadingRows()
        channels.itemCount == 0 && state.query.isNotBlank() -> EmptyState(
            icon = Icons.Rounded.SearchOff,
            title = "No matches",
            message = "No channel matches “${state.query}”.",
            modifier = Modifier.fillMaxSize(),
        )
        channels.itemCount == 0 -> EmptyState(
            icon = Icons.Rounded.Tv,
            title = "Nothing here yet",
            message = "Long-press a channel to add it to Favorites.",
            modifier = Modifier.fillMaxSize(),
        )
        else -> LazyVerticalGrid(
            columns = columns,
            state = gridState,
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 24.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(channels.itemCount, key = channels.itemKey { it.id }) { index ->
                val channel = channels[index]
                if (channel == null) {
                    PlaceholderRow()
                } else {
                    ChannelRow(channel, state, state.onPlay, state.onToggleFavorite)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelRow(channel: Channel, state: LiveTvState, onPlay: (Channel) -> Unit, onToggleFavorite: (Channel) -> Unit) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(
                onClick = { onPlay(channel) },
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleFavorite(channel)
                },
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        channel.number?.let {
            Text(
                it.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(36.dp),
                maxLines = 1,
            )
        }
        ChannelLogo(channel.name, channel.logoUrl, Modifier.size(width = 72.dp, height = 48.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(channel.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val playing = rememberNowPlaying(channel, state.now, state.guideVersion, state.nowPlaying)
            if (playing != null) {
                NowPlayingLine(playing, state.now, Modifier.padding(top = 2.dp))
            } else {
                Text(
                    channel.group,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = { onToggleFavorite(channel) }) {
            Icon(
                if (channel.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                contentDescription = if (channel.isFavorite) "Remove from favorites" else "Add to favorites",
                tint = if (channel.isFavorite) XfinColors.Favorite else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun PlaceholderRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 72.dp, height = 48.dp).clip(MaterialTheme.shapes.small).shimmer())
        Spacer(Modifier.width(14.dp))
        Box(Modifier.height(16.dp).width(160.dp).clip(MaterialTheme.shapes.extraSmall).shimmer())
    }
}

@Composable
private fun LoadingRows() {
    Column { repeat(8) { PlaceholderRow() } }
}

