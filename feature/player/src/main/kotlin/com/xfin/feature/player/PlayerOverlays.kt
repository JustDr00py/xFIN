package com.xfin.feature.player

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.SignalWifiStatusbarConnectedNoInternet4
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.xfin.core.designsystem.component.ChannelLogo
import com.xfin.core.designsystem.component.FocusSurface
import com.xfin.core.designsystem.component.LiveBadge
import com.xfin.core.designsystem.component.LocalIsTv
import com.xfin.core.designsystem.theme.XfinColors
import com.xfin.core.model.Channel
import com.xfin.core.model.NowNext
import com.xfin.core.designsystem.component.ProgressTrack
import com.xfin.core.designsystem.component.formatTime
import com.xfin.core.designsystem.component.formatTimeRange
import com.xfin.core.player.LivePlayerState
import com.xfin.core.player.TrackOption

@Composable
internal fun ChannelOverlay(
    visible: Boolean,
    ui: PlayerUiState,
    playback: LivePlayerState,
    nowNext: NowNext,
    now: Long,
    isTv: Boolean,
    onBack: () -> Unit,
    onFavorite: () -> Unit,
    onOptions: () -> Unit,
    onChannels: () -> Unit,
    onZap: (Int) -> Unit,
    peek: (Int) -> Channel?,
) {
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            // Top bar (touch only; TV uses the remote's Back and OK).
            if (!isTv) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(XfinColors.Scrim, Color.Transparent)))
                        .safeDrawingPadding()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White) }
                    Spacer(Modifier.weight(1f))
                    if (ui.lineupSize > 1) {
                        IconButton(onClick = onChannels) {
                            Icon(Icons.AutoMirrored.Rounded.ViewList, "Channel guide", tint = Color.White)
                        }
                    }
                    IconButton(onClick = onFavorite) {
                        val fav = ui.channel?.isFavorite == true
                        Icon(
                            if (fav) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            if (fav) "Remove from favorites" else "Add to favorites",
                            tint = if (fav) XfinColors.Favorite else Color.White,
                        )
                    }
                    IconButton(onClick = onOptions) { Icon(Icons.Rounded.Tune, "Audio and subtitles", tint = Color.White) }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, XfinColors.Scrim, Color(0xF0000000))))
                    .safeDrawingPadding()
                    .padding(start = 32.dp, end = 32.dp, top = 64.dp, bottom = if (isTv) 40.dp else 20.dp),
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    AnimatedContent(
                        targetState = ui.channel,
                        contentKey = { it?.id },
                        transitionSpec = {
                            val dir = ui.direction
                            (slideInVertically { h -> h * dir / 2 } + fadeIn()) togetherWith
                                (slideOutVertically { h -> -h * dir / 2 } + fadeOut())
                        },
                        modifier = Modifier.weight(1f),
                        label = "channelInfo",
                    ) { channel ->
                        if (channel != null) ChannelInfo(channel, ui, playback, nowNext.takeIf { channel.id == ui.channel?.id }, now, isTv)
                    }
                    if (ui.lineupSize > 1) {
                        ZapHints(peek(+1), peek(-1), isTv, onZap)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelInfo(
    channel: Channel,
    ui: PlayerUiState,
    playback: LivePlayerState,
    nowNext: NowNext?,
    now: Long,
    isTv: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ChannelLogo(
            channel.name,
            channel.logoUrl,
            Modifier.size(width = if (isTv) 136.dp else 104.dp, height = if (isTv) 88.dp else 68.dp),
        )
        Spacer(Modifier.width(20.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                channel.number?.let {
                    Text(it.toString(), style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.7f))
                }
                LiveBadge()
                qualityLabel(playback.videoHeight)?.let { Tag(it) }
                if (channel.isFavorite) {
                    Icon(Icons.Rounded.Star, null, tint = XfinColors.Favorite, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                channel.name,
                style = if (isTv) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            ProgramInfo(nowNext, now, isTv)
            Text(
                buildString {
                    append(channel.group)
                    if (ui.lineupSize > 1) append("  ·  ${ui.position + 1} of ${"%,d".format(ui.lineupSize)}")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.65f),
                maxLines = 1,
            )
            if (isTv) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "▲▼ Change channel   ·   ◀ Channel guide   ·   OK Options   ·   0–9 Jump to number",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.45f),
                )
            }
        }
    }
}

@Composable
private fun ZapHints(next: Channel?, previous: Channel?, isTv: Boolean, onZap: (Int) -> Unit) {
    Column(
        horizontalAlignment = Alignment.End,
        modifier = Modifier.widthIn(max = 240.dp).padding(start = 16.dp),
    ) {
        next?.let { ZapHint(Icons.Rounded.KeyboardArrowUp, it.name, enabled = !isTv) { onZap(+1) } }
        previous?.let { ZapHint(Icons.Rounded.KeyboardArrowDown, it.name, enabled = !isTv) { onZap(-1) } }
    }
}

@Composable
private fun ZapHint(icon: ImageVector, name: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 4.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            name,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Icon(icon, null, tint = Color.White.copy(alpha = 0.6f))
    }
}

@Composable
private fun Tag(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = Color.White,
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.16f), MaterialTheme.shapes.extraSmall)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

private fun qualityLabel(height: Int): String? = when {
    height >= 2000 -> "4K"
    height >= 1000 -> "FHD"
    height >= 700 -> "HD"
    height > 0 -> "SD"
    else -> null
}

@Composable
internal fun ErrorCard(message: String, onRetry: () -> Unit, onNext: () -> Unit, modifier: Modifier = Modifier) {
    val focus = remember { FocusRequester() }
    // Compact single row so the channel info underneath stays readable in landscape.
    Row(
        modifier = modifier
            .widthIn(max = 760.dp)
            .padding(24.dp)
            .background(Color(0xF0151A23), MaterialTheme.shapes.large)
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.SignalWifiStatusbarConnectedNoInternet4,
            null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(32.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(message, color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(16.dp))
        OutlinedButton(onClick = onNext) { Text("Next") }
        Spacer(Modifier.width(8.dp))
        Button(onClick = onRetry, modifier = Modifier.focusRequester(focus)) { Text("Retry") }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

@OptIn(UnstableApi::class)
@Composable
internal fun OptionsPanel(
    ui: PlayerUiState,
    playback: LivePlayerState,
    onFavorite: () -> Unit,
    onSelectTrack: (TrackOption) -> Unit,
    onSubtitlesOff: () -> Unit,
    onClose: () -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    LazyColumn(
        modifier = Modifier
            .fillMaxHeight()
            .width(360.dp)
            .background(Color(0xF2101318))
            .safeDrawingPadding()
            .padding(horizontal = 16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            val fav = ui.channel?.isFavorite == true
            PanelItem(
                label = if (fav) "Remove from Favorites" else "Add to Favorites",
                leading = if (fav) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                leadingTint = if (fav) XfinColors.Favorite else Color.White,
                onClick = onFavorite,
                modifier = Modifier.focusRequester(firstFocus),
            )
        }
        if (playback.audioTracks.size > 1) {
            item { PanelHeader("Audio") }
            items(playback.audioTracks, key = { "a${it.label}${it.index}${it.group.hashCode()}" }) { track ->
                PanelItem(track.label, selected = track.selected, onClick = { onSelectTrack(track) })
            }
        }
        if (playback.textTracks.isNotEmpty()) {
            item { PanelHeader("Subtitles") }
            item {
                PanelItem("Off", selected = playback.textTracks.none { it.selected }, onClick = onSubtitlesOff)
            }
            items(playback.textTracks, key = { "t${it.label}${it.index}${it.group.hashCode()}" }) { track ->
                PanelItem(track.label, selected = track.selected, onClick = { onSelectTrack(track) })
            }
        }
        item {
            Spacer(Modifier.height(8.dp))
            PanelItem("Close", onClick = onClose)
        }
    }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
}

@Composable
private fun PanelHeader(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = Color.White.copy(alpha = 0.5f),
        modifier = Modifier.padding(start = 12.dp, top = 20.dp, bottom = 6.dp),
    )
}

@Composable
private fun PanelItem(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    leading: ImageVector? = null,
    leadingTint: Color = Color.White,
) {
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                Icon(leading, null, tint = leadingTint, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
            }
            Text(label, color = Color.White, modifier = Modifier.weight(1f), maxLines = 2)
            if (selected) Icon(Icons.Rounded.Check, "Selected", tint = MaterialTheme.colorScheme.primary)
        }
    }
    if (LocalIsTv.current) {
        FocusSurface(onClick = onClick, modifier = modifier.fillMaxWidth(), focusedScale = 1.02f, shape = MaterialTheme.shapes.small) {
            content()
        }
    } else {
        Box(
            modifier
                .fillMaxWidth()
                .background(Color.Transparent, MaterialTheme.shapes.small)
                .clickable(onClick = onClick),
        ) { content() }
    }
}

@Composable
private fun ProgramInfo(nowNext: NowNext?, now: Long, isTv: Boolean) {
    val current = nowNext?.now ?: return
    Column(Modifier.padding(top = 6.dp, bottom = 4.dp).widthIn(max = if (isTv) 560.dp else 420.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                current.title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                formatTimeRange(current.start, current.end),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.6f),
            )
        }
        Spacer(Modifier.height(6.dp))
        ProgressTrack(current.progress(now), Modifier.fillMaxWidth(), height = 4.dp)
        nowNext.next?.let { next ->
            Spacer(Modifier.height(6.dp))
            Text(
                "Next  ${formatTime(next.start)}  ${next.title}",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
