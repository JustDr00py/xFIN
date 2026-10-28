package com.xfin.feature.player

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPresentationState
import androidx.paging.compose.collectAsLazyPagingItems
import com.xfin.core.designsystem.component.LocalIsTv

/**
 * Up to four live channels at once. Tap a tile to hear it, double-tap to watch it full screen,
 * ✕ removes it and + adds another channel. Each tile is one stream (one provider connection).
 */
@OptIn(UnstableApi::class)
@Composable
internal fun MultiviewScreen(
    onBack: () -> Unit,
    onOpenChannel: (PlayerRoute) -> Unit,
    viewModel: MultiviewViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val picker = viewModel.picker.collectAsLazyPagingItems()
    val isTv = LocalIsTv.current
    var picking by remember { mutableStateOf(false) }

    FullscreenEffect(lockLandscape = !isTv)
    // Stop rather than pause: a paused player keeps every tile's connection open in the background.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { state.tiles.forEach { it.player.stop() } }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { state.tiles.forEach { it.player.retry() } }
    BackHandler(enabled = picking) { picking = false }

    val canAdd = state.tiles.size < MultiviewViewModel.MAX_TILES
    // Slots: every tile plus an "add" slot while there is room; 3+ slots use a 2x2 grid.
    val slots = state.tiles.size + if (canAdd) 1 else 0

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!isTv) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White) }
                Text("Multiview", style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.padding(start = 8.dp))
                Text(
                    "  ·  tap to hear · double-tap for full screen",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.5f),
                )
            }
            val rows: List<List<Int>> = when {
                slots <= 2 -> listOf((0 until slots).toList())
                else -> listOf(listOf(0, 1), listOf(2, 3))
            }
            Column(Modifier.weight(1f).padding(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                rows.forEach { row ->
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { slot ->
                            when {
                                slot < state.tiles.size -> TileView(
                                    tile = state.tiles[slot],
                                    listening = slot == state.audio,
                                    removable = state.tiles.size > 1,
                                    onListen = { viewModel.listenTo(slot) },
                                    onFull = {
                                        onOpenChannel(PlayerRoute(state.tiles[slot].channel.id, viewModel.filterKey))
                                    },
                                    onRemove = { viewModel.remove(slot) },
                                )
                                slot == state.tiles.size && canAdd -> AddTile { picking = true }
                                else -> Box(Modifier.weight(1f).fillMaxSize())
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = picking,
            enter = slideInHorizontally { -it } + fadeIn(),
            exit = slideOutHorizontally { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            MiniGuide(
                lineup = picker,
                current = 0,
                now = System.currentTimeMillis(),
                guideVersion = 0,
                nowPlaying = viewModel::nowPlaying,
                isTv = isTv,
                onSelect = { index ->
                    picker.peek(index)?.let { viewModel.add(it.id) }
                    picking = false
                },
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun RowScope.TileView(
    tile: Tile,
    listening: Boolean,
    removable: Boolean,
    onListen: () -> Unit,
    onFull: () -> Unit,
    onRemove: () -> Unit,
) {
    val playback by tile.player.state.collectAsStateWithLifecycle()
    val presentation = rememberPresentationState(tile.player.player)
    Box(
        Modifier
            .weight(1f)
            .fillMaxSize()
            .clip(MaterialTheme.shapes.small)
            .background(Color(0xFF0B0D12))
            .border(if (listening) 3.dp else 1.dp, if (listening) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.12f), MaterialTheme.shapes.small)
            .pointerInput(tile) { detectTapGestures(onTap = { onListen() }, onDoubleTap = { onFull() }) }
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyUp) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionCenter, Key.Enter -> { onListen(); true }
                    Key.MediaPlayPause, Key.Menu -> { onFull(); true }
                    else -> false
                }
            }
            .focusable(),
    ) {
        // TextureView so several videos can be laid out and clipped like normal views.
        PlayerSurface(
            player = tile.player.player,
            surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
            modifier = Modifier.align(Alignment.Center).resizeWithContentScale(ContentScale.Fit, presentation.videoSizeDp),
        )
        if (playback.buffering && playback.error == null) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.align(Alignment.Center).size(32.dp))
        }
        playback.error?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                modifier = Modifier.align(Alignment.Center).padding(16.dp),
                maxLines = 4,
            )
        }
        Row(
            Modifier.align(Alignment.BottomStart).padding(8.dp).background(Color(0xB3000000), MaterialTheme.shapes.extraSmall).padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (listening) {
                Icon(Icons.Rounded.VolumeUp, "Playing audio", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            }
            Text(
                tile.channel.name,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = if (listening) 6.dp else 0.dp),
            )
        }
        if (removable) {
            IconButton(
                onClick = onRemove,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(36.dp).clip(CircleShape).background(Color(0x99000000)),
            ) { Icon(Icons.Rounded.Close, "Remove ${tile.channel.name}", tint = Color.White, modifier = Modifier.size(18.dp)) }
        }
    }
}

@Composable
private fun RowScope.AddTile(onClick: () -> Unit) {
    Box(
        Modifier
            .weight(1f)
            .fillMaxSize()
            .clip(MaterialTheme.shapes.small)
            .background(Color(0xFF151A23))
            .border(1.dp, Color.White.copy(alpha = 0.12f), MaterialTheme.shapes.small)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.Add, null, tint = Color.White, modifier = Modifier.size(36.dp))
            Text("Add channel", style = MaterialTheme.typography.labelLarge, color = Color.White)
            Text(
                "Each channel uses one connection",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f),
            )
        }
    }
}
