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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.ui.draw.clip
import kotlin.math.roundToInt
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.border
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.Replay30
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

/**
 * UHF-style live overlay: round controls on top, channel and program info with stream facts at
 * the bottom left, a control pill at the bottom right and the program timeline underneath.
 */
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
    onTogglePlay: () -> Unit,
    onPip: () -> Unit,
    scale: VideoScale,
    onScale: () -> Unit,
    onMultiview: () -> Unit,
    catchupPosition: Long? = null,
    canStartOver: Boolean = false,
    onStartOver: () -> Unit = {},
    onSeek: (Long) -> Unit = {},
    onSkip: (Long) -> Unit = {},
    onGoLive: () -> Unit = {},
) {
    val inArchive = ui.catchup != null
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent, Color(0x33000000), Color(0xE6000000)))),
        ) {
            if (!isTv) {
                Row(
                    modifier = Modifier.fillMaxWidth().safeDrawingPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RoundButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
                    Spacer(Modifier.weight(1f))
                    if (playback.audioTracks.size > 1 || playback.audioLanguage != null) {
                        AudioChip(playback.audioLanguage, onOptions)
                    }
                    RoundButton(Icons.Rounded.AspectRatio, "Picture size: ${scale.label}", onScale)
                    RoundButton(Icons.Rounded.PictureInPictureAlt, "Picture in picture", onPip)
                    if (ui.lineupSize > 1 && ui.hasMiniGuide) RoundButton(Icons.AutoMirrored.Rounded.ViewList, "Channel guide", onChannels)
                    RoundButton(Icons.Rounded.MoreHoriz, "More", onOptions)
                }
            }

            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .safeDrawingPadding()
                    .padding(start = 24.dp, end = 24.dp, bottom = if (isTv) 28.dp else 12.dp),
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    AnimatedContent(
                        targetState = ui.channel,
                        contentKey = { it?.id },
                        transitionSpec = {
                            val dir = ui.direction
                            (slideInVertically { h -> h * dir / 3 } + fadeIn()) togetherWith (slideOutVertically { h -> -h * dir / 3 } + fadeOut())
                        },
                        modifier = Modifier.weight(1f),
                        label = "channelInfo",
                    ) { channel ->
                        if (channel != null) {
                            ChannelInfo(channel, ui, playback, nowNext.takeIf { channel.id == ui.channel?.id }, isTv)
                        }
                    }
                    if (!isTv) {
                        ControlPill(
                            playing = playback.playing,
                            favorite = ui.channel?.isFavorite == true,
                            inArchive = inArchive,
                            canStartOver = canStartOver,
                            onTogglePlay = onTogglePlay,
                            onFavorite = onFavorite,
                            onOptions = onOptions,
                            onMultiview = onMultiview,
                            onStartOver = onStartOver,
                            onSkip = onSkip,
                            modifier = Modifier.padding(start = 16.dp),
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Timeline(
                    nowNext = nowNext.takeIf { ui.channel != null },
                    now = now,
                    position = catchupPosition,
                    seekable = inArchive || canStartOver,
                    onSeek = onSeek,
                    onGoLive = onGoLive,
                )
                if (isTv) {
                    Text(
                        if (inArchive) "◀◀ ▶▶ 30 seconds   ·   ▲▼ Change channel (goes live)   ·   OK Options"
                        else "▲▼ Change channel   ·   ◀ Channel guide   ·   OK Options   ·   0–9 Jump to number",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.45f),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelInfo(channel: Channel, ui: PlayerUiState, playback: LivePlayerState, nowNext: NowNext?, isTv: Boolean) {
    val program = nowNext?.now
    Column(Modifier.widthIn(max = if (isTv) 760.dp else 560.dp)) {
        ChannelLogo(channel.name, channel.logoUrl, Modifier.size(width = if (isTv) 128.dp else 104.dp, height = if (isTv) 80.dp else 64.dp))
        Spacer(Modifier.height(12.dp))
        Text(
            // With guide data the channel goes small and the program big; without, show the group.
            listOfNotNull(channel.number?.toString(), if (program != null) channel.name else channel.group).joinToString("  "),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.9f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            program?.title ?: channel.name,
            style = if (isTv) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        program?.let { p ->
            val text = listOfNotNull(
                listOfNotNull(p.episode, p.subtitle).joinToString(" · ").ifEmpty { null },
                p.description,
            ).joinToString("  ")
            if (text.isNotEmpty()) {
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = if (isTv) 3 else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        val chips = listOfNotNull(
            ui.sourceName,
            qualityLabel(playback.videoHeight),
            playback.videoCodec,
            playback.frameRate.takeIf { it > 0 }?.let { "${it.roundToInt()} FPS" },
            when (playback.audioChannels) {
                0 -> null
                1 -> "MONO"
                2 -> "STEREO"
                6 -> "5.1"
                8 -> "7.1"
                else -> "${playback.audioChannels}CH"
            },
            channel.catchup?.takeIf { it.days > 0 }?.let { "CATCH-UP ${it.days}D" },
        )
        if (chips.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 10.dp)) {
                chips.forEach { Tag(it.uppercase()) }
            }
        }
    }
}

/**
 * Program progress with start time, LIVE (or CATCH-UP and the archive time), the current time and
 * what's next. On channels with catch-up the bar is tappable: tap earlier to rewind.
 */
@Composable
private fun Timeline(
    nowNext: NowNext?,
    now: Long,
    position: Long?,
    seekable: Boolean,
    onSeek: (Long) -> Unit,
    onGoLive: () -> Unit,
) {
    val program = nowNext?.now
    val at = position ?: now
    val seek = if (seekable && program != null) {
        Modifier.pointerInput(program.start, program.end) {
            detectTapGestures { offset ->
                val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                onSeek(program.start + ((program.end - program.start) * fraction).toLong())
            }
        }
    } else {
        Modifier
    }
    Box(seek.fillMaxWidth().padding(vertical = 8.dp)) {
        ProgressTrack(
            program?.progress(at) ?: 1f,
            Modifier.fillMaxWidth(),
            height = 5.dp,
            color = Color.White.copy(alpha = 0.85f),
            trackColor = Color.White.copy(alpha = 0.22f),
        )
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(program?.let { formatTime(it.start) }.orEmpty(), style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.8f))
        Spacer(Modifier.weight(1f))
        if (position != null) {
            Tag("CATCH-UP")
            Spacer(Modifier.width(10.dp))
            Text(formatTime(position), style = MaterialTheme.typography.labelLarge, color = Color.White)
            Spacer(Modifier.width(10.dp))
            Row(
                Modifier.clip(CircleShape).clickable(onClick = onGoLive).border(1.dp, XfinColors.Live, CircleShape)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).background(XfinColors.Live, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text("Go live", style = MaterialTheme.typography.labelLarge, color = Color.White)
            }
        } else {
            Box(Modifier.size(9.dp).background(XfinColors.Live, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text("LIVE", style = MaterialTheme.typography.labelLarge, color = XfinColors.Live)
            Spacer(Modifier.width(14.dp))
            Text(formatTime(now), style = MaterialTheme.typography.labelLarge, color = Color.White)
        }
        nowNext?.next?.let { next ->
            Spacer(Modifier.width(14.dp))
            Text(
                "${formatTime(next.start)} – ${next.title}",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 320.dp),
            )
        }
    }
}

@Composable
private fun ControlPill(
    playing: Boolean,
    favorite: Boolean,
    inArchive: Boolean,
    canStartOver: Boolean,
    onTogglePlay: () -> Unit,
    onFavorite: () -> Unit,
    onOptions: () -> Unit,
    onMultiview: () -> Unit,
    onStartOver: () -> Unit,
    onSkip: (Long) -> Unit,
    modifier: Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        RoundButton(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pause" else "Play", onTogglePlay)
        Row(
            Modifier.clip(CircleShape).background(Color(0x66303540)).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (inArchive) {
                IconButton(onClick = { onSkip(-30_000) }) { Icon(Icons.Rounded.Replay30, "Back 30 seconds", tint = Color.White) }
                IconButton(onClick = { onSkip(30_000) }) { Icon(Icons.Rounded.Forward30, "Forward 30 seconds", tint = Color.White) }
            } else if (canStartOver) {
                IconButton(onClick = onStartOver) { Icon(Icons.Rounded.RestartAlt, "Start over", tint = Color.White) }
            }
            IconButton(onClick = onFavorite) {
                Icon(
                    if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    if (favorite) "Remove from favorites" else "Add to favorites",
                    tint = if (favorite) XfinColors.Live else Color.White,
                )
            }
            IconButton(onClick = onMultiview) { Icon(Icons.Rounded.GridView, "Multiview", tint = Color.White) }
            IconButton(onClick = onOptions) { Icon(Icons.Rounded.ClosedCaption, "Audio and subtitles", tint = Color.White) }
        }
    }
}

@Composable
private fun RoundButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp).clip(CircleShape).background(Color(0x66303540))) {
        Icon(icon, description, tint = Color.White)
    }
}

@Composable
private fun AudioChip(language: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(Color(0x66303540))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            language?.take(3)?.uppercase() ?: "AUDIO",
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
        )
        Icon(Icons.Rounded.KeyboardArrowDown, "Audio track", tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun Tag(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = Color.White.copy(alpha = 0.9f),
        maxLines = 1,
        modifier = Modifier
            // Dark backing keeps chips legible over bright video.
            .background(Color(0xB3181B22), MaterialTheme.shapes.extraSmall)
            .padding(horizontal = 8.dp, vertical = 3.dp),
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
internal fun ErrorCard(
    message: String,
    onRetry: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    nextLabel: String = "Next",
    /** An extra action between [nextLabel] and Retry, e.g. trying another version. */
    altLabel: String? = null,
    onAlt: () -> Unit = {},
) {
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
        OutlinedButton(onClick = onNext) { Text(nextLabel) }
        if (altLabel != null) {
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onAlt) { Text(altLabel) }
        }
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
    scale: VideoScale,
    onScale: (VideoScale) -> Unit,
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
            // Keys by position: picking a track changes its group object, and a changed key would
            // recreate the row and drop the remote's focus.
            items(playback.audioTracks.size, key = { "a$it" }) { i ->
                val track = playback.audioTracks[i]
                PanelItem(track.label, selected = track.selected, onClick = { onSelectTrack(track) })
            }
        }
        if (playback.textTracks.isNotEmpty()) {
            item { PanelHeader("Subtitles") }
            item {
                PanelItem("Off", selected = playback.textTracks.none { it.selected }, onClick = onSubtitlesOff)
            }
            items(playback.textTracks.size, key = { "t$it" }) { i ->
                val track = playback.textTracks[i]
                PanelItem(track.label, selected = track.selected, onClick = { onSelectTrack(track) })
            }
        }
        videoScaleItems(scale, onScale)
        item {
            Spacer(Modifier.height(8.dp))
            PanelItem("Close", onClick = onClose)
        }
    }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
}

@Composable
internal fun PanelHeader(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = Color.White.copy(alpha = 0.5f),
        modifier = Modifier.padding(start = 12.dp, top = 20.dp, bottom = 6.dp),
    )
}

@Composable
internal fun PanelItem(
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

/** "Picture" choices for the options panels (the only way to change it with a remote). */
internal fun androidx.compose.foundation.lazy.LazyListScope.videoScaleItems(scale: VideoScale, onScale: (VideoScale) -> Unit) {
    item { PanelHeader("Picture") }
    items(VideoScale.entries, key = { "scale${it.name}" }) { option ->
        PanelItem(option.label, selected = option == scale, onClick = { onScale(option) })
    }
}
