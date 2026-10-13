package com.xfin.feature.livetv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.compose.itemKey
import com.xfin.core.designsystem.component.ChannelLogo
import com.xfin.core.designsystem.component.EmptyState
import com.xfin.core.designsystem.component.FocusSurface
import com.xfin.core.designsystem.component.NowPlayingLine
import com.xfin.core.designsystem.component.rememberNowPlaying
import com.xfin.core.designsystem.theme.XfinColors
import com.xfin.core.model.Channel
import com.xfin.core.model.ChannelFilter
import com.xfin.core.model.RefreshState
import kotlinx.coroutines.delay

@Composable
internal fun TvLiveTv(state: LiveTvState) {
    Row(Modifier.fillMaxSize()) {
        CategoryRail(state, Modifier.width(300.dp).fillMaxHeight())
        ChannelWall(state, Modifier.weight(1f).fillMaxHeight())
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun CategoryRail(state: LiveTvState, modifier: Modifier) {
    // Focus browsing like UHF: resting on a category for a moment opens it.
    var focused by remember { mutableStateOf<ChannelFilter?>(null) }
    val onSelect by rememberUpdatedState(state.onSelect)
    LaunchedEffect(focused) {
        val target = focused ?: return@LaunchedEffect
        delay(300)
        onSelect(target)
    }

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.6f))
            .padding(start = 24.dp, top = 28.dp, end = 12.dp),
    ) {
        Text("Live TV", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 8.dp))
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = state.query,
            onValueChange = state.onQuery,
            placeholder = { Text("Search") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        )
        val refresh = state.refresh
        if (refresh is RefreshState.Running) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(
            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.focusRestorer(),
        ) {
            items(state.tabs, key = { it.label + it.filter.hashCode() }) { tab ->
                val selected = tab.filter == state.selected && state.query.isBlank()
                FocusSurface(
                    onClick = { state.onSelect(tab.filter) },
                    selected = selected,
                    focusedScale = 1.03f,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { if (it.isFocused) focused = tab.filter },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (tab.filter == ChannelFilter.Favorites) {
                            Icon(Icons.Rounded.Star, null, tint = XfinColors.Favorite, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            tab.label,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        tab.count?.let {
                            Text(
                                "%,d".format(it),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ChannelWall(state: LiveTvState, modifier: Modifier) {
    val channels = state.channels
    Box(modifier) {
        if (channels.itemCount == 0 && channels.loadState.refresh !is androidx.paging.LoadState.Loading) {
            EmptyState(
                icon = Icons.Rounded.Tv,
                title = if (state.query.isNotBlank()) "No matches" else "Nothing here yet",
                message = if (state.query.isNotBlank()) {
                    "No channel matches “${state.query}”."
                } else {
                    "Hold OK on any channel to add it to Favorites."
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(200.dp),
                contentPadding = PaddingValues(start = 28.dp, end = 48.dp, top = 32.dp, bottom = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.fillMaxSize().focusRestorer(),
            ) {
                items(channels.itemCount, key = channels.itemKey { it.id }) { index ->
                    val channel = channels[index]
                    if (channel != null) {
                        ChannelCard(channel, state, state.onPlay, state.onToggleFavorite)
                    } else {
                        Box(Modifier.aspectRatio(16f / 11f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelCard(channel: Channel, state: LiveTvState, onPlay: (Channel) -> Unit, onToggleFavorite: (Channel) -> Unit) {
    FocusSurface(
        onClick = { onPlay(channel) },
        onLongClick = { onToggleFavorite(channel) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                ChannelLogo(channel.name, channel.logoUrl, Modifier.fillMaxSize().padding(12.dp))
                if (channel.isFavorite) {
                    Icon(
                        Icons.Rounded.Star,
                        contentDescription = "Favorite",
                        tint = XfinColors.Favorite,
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(18.dp),
                    )
                }
                channel.number?.let {
                    Text(
                        it.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .background(Brush.verticalGradient(listOf(XfinColors.ScrimLight, XfinColors.ScrimLight)), MaterialTheme.shapes.extraSmall)
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }
            Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                Text(
                    channel.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                rememberNowPlaying(channel, state.now, state.guideVersion, state.nowPlaying)
                    ?.let { NowPlayingLine(it, state.now, Modifier.padding(top = 2.dp)) }
            }
        }
    }
}
