package com.xfin.feature.player

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
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
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPresentationState
import com.xfin.core.designsystem.component.LocalIsTv
import com.xfin.core.designsystem.theme.XfinColors
import com.xfin.core.model.MediaItem
import com.xfin.core.model.MediaKind
import com.xfin.core.player.VodPlayer
import com.xfin.core.player.VodPlayerState
import kotlinx.coroutines.delay
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.focusRequester

@OptIn(UnstableApi::class)
@Composable
internal fun VodPlayerScreen(
    onBack: () -> Unit,
    onPlayNext: (VodPlayerRoute) -> Unit,
    viewModel: VodPlayerViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val vod = viewModel.player
    val isTv = LocalIsTv.current

    var overlay by remember { mutableStateOf(true) }
    var panel by remember { mutableStateOf(false) }
    var interaction by remember { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }
    fun poke() {
        overlay = true
        interaction++
    }

    // TV: once the controls are brought up, the arrows move between their buttons (until they hide).
    var buttonsActive by remember { mutableStateOf(false) }
    val playFocus = remember { FocusRequester() }
    fun showButtons() {
        poke()
        buttonsActive = true
    }
    LaunchedEffect(buttonsActive, overlay) {
        if (buttonsActive && overlay) {
            withFrameNanos { }
            runCatching { playFocus.requestFocus() }
        }
    }
    LaunchedEffect(overlay) {
        if (!overlay) {
            buttonsActive = false
            if (!panel) runCatching { focus.requestFocus() }
        }
    }

    FullscreenEffect(lockLandscape = !isTv)
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vod.player.pause() }
    LaunchedEffect(interaction, panel, playback.playing) {
        if (!panel && playback.playing) {
            delay(4_000)
            overlay = false
        }
    }
    var panelWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(panel) {
        if (panel) {
            panelWasOpen = true
            return@LaunchedEffect
        }
        val closedPanel = panelWasOpen
        panelWasOpen = false
        if (isTv && closedPanel) {
            // Back from the panel lands on the controls again, not on nothing.
            buttonsActive = true
            interaction++
            withFrameNanos { }
            runCatching { playFocus.requestFocus() }
        } else {
            runCatching { focus.requestFocus() }
        }
    }
    BackHandler(enabled = panel) { panel = false }

    val presentation = rememberPresentationState(vod.player)
    val inPip = rememberIsInPip()
    // Skip intro / recap / credits: on its own for the first seconds of the segment, then with the
    // controls. On TV it stands aside while the control buttons have focus.
    val segment = ui.segments.skippableAt(playback.positionMs)
    val skip = segment?.takeIf {
        (overlay || playback.positionMs < it.startMs + SKIP_PROMPT_MS) &&
            !(isTv && buttonsActive && overlay) && !panel && !inPip && !playback.ended &&
            ui.error == null && playback.error == null
    }
    val scale = rememberVideoScale()
    val enterPip = if (isTv) ({}) else PipEffect(presentation.videoSizeDp ?: androidx.compose.ui.geometry.Size.Zero, playback.playing) {
        vod.togglePlay()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                // TV: Back closes the panel, then hides the controls, before leaving. Handled here
                // rather than with BackHandler: with focus on a control, Compose spends Back on
                // clearing that focus, so the handler never runs.
                if (isTv && event.key.nativeKeyCode == KeyEvent.KEYCODE_BACK && (panel || (buttonsActive && overlay))) {
                    if (event.type == KeyEventType.KeyUp) {
                        if (panel) panel = false else overlay = false
                    }
                    return@onPreviewKeyEvent true
                }
                // With an error showing, OK and the arrows belong to its Retry / Close buttons.
                val failed = ui.error != null || playback.error != null
                if (panel || failed || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (isTv && buttonsActive && overlay) {
                    // The buttons have focus: let them take the arrows and OK; media keys still work.
                    return@onPreviewKeyEvent when (event.key.nativeKeyCode) {
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { vod.togglePlay(); poke(); true }
                        KeyEvent.KEYCODE_MEDIA_PLAY -> { vod.player.play(); poke(); true }
                        KeyEvent.KEYCODE_MEDIA_PAUSE -> { vod.player.pause(); poke(); true }
                        KeyEvent.KEYCODE_MEDIA_REWIND -> { vod.seekBy(-VodPlayer.SEEK_STEP_MS); poke(); true }
                        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { vod.seekBy(VodPlayer.SEEK_STEP_MS); poke(); true }
                        KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_INFO -> { panel = true; true }
                        else -> { interaction++; false }
                    }
                }
                // OK presses the skip button while it's up; Play/Pause still pauses.
                val ok = event.key.nativeKeyCode.let {
                    it == KeyEvent.KEYCODE_DPAD_CENTER || it == KeyEvent.KEYCODE_ENTER || it == KeyEvent.KEYCODE_NUMPAD_ENTER
                }
                if (skip != null && ok) {
                    vod.seekTo(skip.endMs)
                    return@onPreviewKeyEvent true
                }
                when (event.key.nativeKeyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                        vod.seekBy(-VodPlayer.SEEK_STEP_MS); poke(); true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                        vod.seekBy(VodPlayer.SEEK_STEP_MS); poke(); true
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_SPACE -> {
                        vod.togglePlay()
                        if (isTv) showButtons() else poke()
                        true
                    }
                    KeyEvent.KEYCODE_MEDIA_PLAY -> { vod.player.play(); poke(); true }
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> { vod.player.pause(); poke(); true }
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (isTv) showButtons() else poke()
                        true
                    }
                    KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_INFO -> { panel = true; true }
                    else -> false
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        when {
                            panel -> panel = false
                            overlay -> overlay = false
                            else -> poke()
                        }
                    },
                    // Double-tap the left or right half to skip, like most video apps.
                    onDoubleTap = { offset ->
                        vod.seekBy(if (offset.x < size.width / 2) -VodPlayer.SEEK_STEP_MS else VodPlayer.SEEK_STEP_MS)
                        poke()
                    },
                )
            }
            .pinchToScale(scale),
    ) {
        PlayerSurface(
            player = vod.player,
            surfaceType = SURFACE_TYPE_SURFACE_VIEW,
            modifier = Modifier
                .align(Alignment.Center)
                .resizeWithContentScale(if (inPip) ContentScale.Fit else scale.value.contentScale, presentation.videoSizeDp),
        )
        if (presentation.coverSurface) Box(Modifier.fillMaxSize().background(Color.Black))
        Captions(vod.player, Modifier.fillMaxSize())
        if (!inPip) VideoScaleLabel(scale, Modifier.align(Alignment.TopCenter).padding(top = 96.dp))

        if (playback.buffering && ui.error == null && playback.error == null) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.align(Alignment.Center).size(52.dp))
        }

        AnimatedVisibility(overlay && !panel && !inPip, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Controls(
                item = ui.item,
                playback = playback,
                isTv = isTv,
                onBack = onBack,
                onTogglePlay = { vod.togglePlay(); poke() },
                onSeekBy = { vod.seekBy(it); poke() },
                onSeekTo = { vod.seekTo(it); poke() },
                onTracks = { panel = true },
                playFocus = playFocus,
                onPip = enterPip,
                onScale = { scale.cycle(); poke() },
            )
        }

        if (skip != null) {
            SkipButton(
                label = skip.skipLabel,
                onClick = { vod.seekTo(skip.endMs) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .safeDrawingPadding()
                    // Above the seek bar and TV buttons while the controls are up.
                    .padding(end = 32.dp, bottom = if (!overlay) 48.dp else if (isTv) 150.dp else 110.dp),
            )
        }

        val error = ui.error ?: playback.error
        if (error != null && !inPip) {
            ErrorCard(error, onRetry = viewModel::retry, onNext = onBack, nextLabel = "Close", modifier = Modifier.align(Alignment.Center))
        }

        val next = ui.upNext
        if (playback.ended) {
            if (next != null) {
                UpNext(
                    next = next,
                    onPlay = { onPlayNext(VodPlayerRoute(next.sourceId, next.id, if (next.canResume) next.positionMs else 0)) },
                    onCancel = onBack,
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            } else {
                LaunchedEffect(Unit) { onBack() }
            }
        }

        AnimatedVisibility(
            visible = panel,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            TracksPanel(playback, onSelect = vod::selectTrack, onSubtitlesOff = vod::disableSubtitles, scale = scale.value, onScale = scale::set, onClose = { panel = false })
        }
    }
}

@Composable
private fun Controls(
    item: MediaItem?,
    playback: VodPlayerState,
    isTv: Boolean,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    onTracks: () -> Unit,
    onPip: () -> Unit,
    onScale: () -> Unit,
    playFocus: FocusRequester = remember { FocusRequester() },
) {
    Box(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(XfinColors.Scrim, Color.Transparent)))
                .safeDrawingPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!isTv) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White) }
            Column(Modifier.weight(1f).padding(start = if (isTv) 32.dp else 4.dp, top = if (isTv) 16.dp else 0.dp)) {
                Text(
                    item?.let { if (it.kind == MediaKind.EPISODE) it.seriesName ?: it.name else it.name }.orEmpty(),
                    style = if (isTv) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item?.kind == MediaKind.EPISODE) {
                    Text(
                        listOfNotNull(item.episodeLabel, item.name).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                    )
                }
            }
            if (!isTv) IconButton(onClick = onScale) { Icon(Icons.Rounded.AspectRatio, "Picture size", tint = Color.White) }
            if (!isTv) IconButton(onClick = onPip) { Icon(Icons.Rounded.PictureInPictureAlt, "Picture in picture", tint = Color.White) }
            // TV has the Audio & subtitles button with the playback controls.
            if (!isTv) IconButton(onClick = onTracks) { Icon(Icons.Rounded.Tune, "Audio and subtitles", tint = Color.White) }
        }

        if (!isTv) {
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(40.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { onSeekBy(-VodPlayer.SEEK_STEP_MS) }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.Replay10, "Back 10 seconds", tint = Color.White, modifier = Modifier.size(36.dp))
                }
                IconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier.size(76.dp).background(Color(0x66000000), MaterialTheme.shapes.extraLarge),
                ) {
                    Icon(
                        if (playback.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        if (playback.playing) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp),
                    )
                }
                IconButton(onClick = { onSeekBy(VodPlayer.SEEK_STEP_MS) }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.Forward10, "Forward 10 seconds", tint = Color.White, modifier = Modifier.size(36.dp))
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, XfinColors.Scrim)))
                .safeDrawingPadding()
                .padding(start = 24.dp, end = 24.dp, top = 40.dp, bottom = if (isTv) 32.dp else 12.dp),
        ) {
            SeekBar(playback, onSeekTo)
            if (isTv) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TvControl(Icons.Rounded.Replay10, "Back 10 seconds") { onSeekBy(-VodPlayer.SEEK_STEP_MS) }
                    TvControl(
                        if (playback.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        if (playback.playing) "Pause" else "Play",
                        modifier = Modifier.focusRequester(playFocus),
                        onClick = onTogglePlay,
                    )
                    TvControl(Icons.Rounded.Forward10, "Forward 10 seconds") { onSeekBy(VodPlayer.SEEK_STEP_MS) }
                    androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    TvControl(Icons.Rounded.Tune, "Audio & subtitles", label = "Audio & subtitles", onClick = onTracks)
                }
            }
        }
    }
}

@Composable
private fun SeekBar(playback: VodPlayerState, onSeekTo: (Long) -> Unit) {
    val duration = playback.durationMs.coerceAtLeast(1)
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val shown = if (dragging) (dragValue * duration).toLong() else playback.positionMs
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(formatClock(shown), style = MaterialTheme.typography.labelLarge, color = Color.White)
        Slider(
            value = if (dragging) dragValue else (playback.positionMs.toFloat() / duration).coerceIn(0f, 1f),
            onValueChange = {
                dragging = true
                dragValue = it
            },
            onValueChangeFinished = {
                onSeekTo((dragValue * duration).toLong())
                dragging = false
            },
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = Color.White.copy(alpha = 0.25f),
            ),
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
        )
        Text("-" + formatClock((duration - shown).coerceAtLeast(0)), style = MaterialTheme.typography.labelLarge, color = Color.White)
    }
}

@Composable
private fun UpNext(next: MediaItem, onPlay: () -> Unit, onCancel: () -> Unit, modifier: Modifier) {
    var seconds by remember { mutableIntStateOf(10) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { focus.requestFocus() }
        while (seconds > 0) {
            delay(1_000)
            seconds--
        }
        onPlay()
    }
    Column(
        modifier = modifier
            .safeDrawingPadding()
            .padding(32.dp)
            .widthIn(max = 380.dp)
            .background(Color(0xF0151A23), MaterialTheme.shapes.large)
            .padding(20.dp),
    ) {
        Text("Up next in $seconds", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(6.dp))
        Text(
            listOfNotNull(next.episodeLabel, next.name).joinToString(" · "),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            maxLines = 2,
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onCancel) { Text("Close") }
            Button(onClick = onPlay, modifier = Modifier.focusRequester(focus)) { Text("Play now") }
        }
    }
}

/** On TV, OK presses it while the player keeps focus, so it's always drawn as the focused choice. */
@Composable
private fun SkipButton(label: String, onClick: () -> Unit, modifier: Modifier) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
        modifier = modifier,
    ) {
        Icon(Icons.Rounded.SkipNext, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun TracksPanel(
    playback: VodPlayerState,
    onSelect: (com.xfin.core.player.TrackOption) -> Unit,
    onSubtitlesOff: () -> Unit,
    scale: VideoScale,
    onScale: (VideoScale) -> Unit,
    onClose: () -> Unit,
) {
    // TV: the button that opened the panel is hidden, so focus has to be moved into the panel.
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { firstFocus.requestFocus() }
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxHeight()
            .width(360.dp)
            .background(Color(0xF2101318))
            .safeDrawingPadding()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 24.dp),
    ) {
        if (playback.audioTracks.isNotEmpty()) {
            item { PanelHeader("Audio") }
            // Keys by position: a track's group object changes when a track is picked, and a changed
            // key recreates the row and drops the remote's focus.
            itemsIndexed(playback.audioTracks, key = { i, _ -> "a$i" }) { i, track ->
                PanelItem(
                    track.label,
                    selected = track.selected,
                    onClick = { onSelect(track) },
                    modifier = if (i == 0) Modifier.focusRequester(firstFocus) else Modifier,
                )
            }
        }
        item { PanelHeader("Subtitles") }
        item {
            PanelItem(
                "Off",
                selected = playback.textTracks.none { it.selected },
                onClick = onSubtitlesOff,
                modifier = if (playback.audioTracks.isEmpty()) Modifier.focusRequester(firstFocus) else Modifier,
            )
        }
        items(playback.textTracks.size, key = { "t$it" }) { i ->
            val track = playback.textTracks[i]
            PanelItem(track.label, selected = track.selected, onClick = { onSelect(track) })
        }
        videoScaleItems(scale, onScale)
        item {
            Spacer(Modifier.height(8.dp))
            PanelItem("Close", onClick = onClose)
        }
    }
}

private fun formatClock(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** A round control on TV (focus ring and scale come from FocusSurface); [label] makes it a pill. */
@Composable
private fun TvControl(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    onClick: () -> Unit,
) {
    com.xfin.core.designsystem.component.FocusSurface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        focusedScale = 1.1f,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (label != null) 20.dp else 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, description, modifier = Modifier.size(32.dp))
            if (label != null) {
                androidx.compose.foundation.layout.Spacer(Modifier.width(10.dp))
                Text(label, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
