package com.xfin.feature.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.foundation.layout.Row
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.expandHorizontally
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPresentationState
import com.xfin.core.designsystem.component.LocalIsTv
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
internal fun PlayerScreen(
    onBack: () -> Unit,
    onOpenMultiview: (MultiviewRoute) -> Unit = {},
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val nowNext by viewModel.nowNext.collectAsStateWithLifecycle()
    val now by viewModel.now.collectAsStateWithLifecycle()
    val guideVersion by viewModel.guideVersion.collectAsStateWithLifecycle()
    val catchupPosition by viewModel.catchupPosition.collectAsStateWithLifecycle()
    val miniGuide = viewModel.miniGuide.collectAsLazyPagingItems()
    val player = viewModel.livePlayer.player
    val isTv = LocalIsTv.current

    var overlayVisible by remember { mutableStateOf(true) }
    var panelOpen by remember { mutableStateOf(false) }
    var guideOpen by remember { mutableStateOf(false) }
    var categoriesOpen by remember { mutableStateOf(false) }
    var guideFocus by remember { mutableStateOf(0) }
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val browsing by viewModel.browseFilter.collectAsStateWithLifecycle()
    // Closing the mini guide drops whatever category was being browsed.
    LaunchedEffect(guideOpen) {
        if (!guideOpen) {
            categoriesOpen = false
            viewModel.resetBrowse()
        }
    }
    var interaction by remember { mutableStateOf(0) }
    val focus = remember { FocusRequester() }

    fun poke() {
        overlayVisible = true
        interaction++
    }

    FullscreenEffect(lockLandscape = !isTv)
    var stopped by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        stopped = true
        player.pause()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        if (stopped) {
            stopped = false
            viewModel.resume()
        }
    }
    LaunchedEffect(ui.channel?.id) { if (ui.channel != null) poke() }
    LaunchedEffect(interaction, panelOpen, guideOpen, playback.error) {
        if (!panelOpen && !guideOpen && playback.error == null) {
            delay(OVERLAY_TIMEOUT_MS)
            overlayVisible = false
        }
    }
    LaunchedEffect(panelOpen, guideOpen) { if (!panelOpen && !guideOpen) focus.requestFocus() }
    BackHandler(enabled = panelOpen) { panelOpen = false }
    BackHandler(enabled = guideOpen) { guideOpen = false }
    BackHandler(enabled = categoriesOpen) {
        categoriesOpen = false
        guideFocus++
    }

    val swipeThreshold = with(LocalDensity.current) { 64.dp.toPx() }
    val presentation = rememberPresentationState(player)
    val inPip = rememberIsInPip()
    val scale = rememberVideoScale()
    val enterPip = if (isTv) ({}) else PipEffect(presentation.videoSizeDp ?: androidx.compose.ui.geometry.Size.Zero, playback.playing) {
        viewModel.livePlayer.togglePlay()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                // TV: Back closes the innermost open panel. Handled here rather than with BackHandler:
                // with focus on a panel item, Compose spends Back on clearing that focus.
                if (isTv && event.key.nativeKeyCode == KeyEvent.KEYCODE_BACK && (panelOpen || guideOpen)) {
                    if (event.type == KeyEventType.KeyUp) {
                        when {
                            categoriesOpen -> {
                                categoriesOpen = false
                                guideFocus++
                            }
                            panelOpen -> panelOpen = false
                            else -> guideOpen = false
                        }
                    }
                    return@onPreviewKeyEvent true
                }
                if (panelOpen || guideOpen || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (val code = event.key.nativeKeyCode) {
                    // The remote's CH+/CH− always switch straight away.
                    KeyEvent.KEYCODE_CHANNEL_UP -> {
                        viewModel.zap(+1); true
                    }
                    KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                        viewModel.zap(-1); true
                    }
                    // Up/down first bring up what's on; once it shows, they switch channels.
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        if (overlayVisible) viewModel.zap(+1) else poke()
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (overlayVisible) viewModel.zap(-1) else poke()
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                        when {
                            ui.numberEntry.isNotEmpty() -> viewModel.commitNumber()
                            overlayVisible -> panelOpen = true
                            else -> poke()
                        }
                        true
                    }
                    KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_INFO -> {
                        panelOpen = true; true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_GUIDE -> {
                        guideOpen = true; true
                    }
                    KeyEvent.KEYCODE_MEDIA_REWIND -> {
                        if (ui.catchup != null) viewModel.skip(-30_000) else if (viewModel.canStartOver()) viewModel.startOver()
                        poke(); true
                    }
                    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                        if (ui.catchup != null) viewModel.skip(30_000)
                        poke(); true
                    }
                    in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
                        viewModel.digit(code - KeyEvent.KEYCODE_0); poke(); true
                    }
                    else -> false
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    if (panelOpen) panelOpen = false
                    else if (guideOpen) guideOpen = false
                    else if (overlayVisible) overlayVisible = false
                    else poke()
                })
            }
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onVerticalDrag = { _, dy -> total += dy },
                    onDragEnd = {
                        // Swipe up reveals the next channel, like a feed.
                        if (total < -swipeThreshold) viewModel.zap(+1)
                        if (total > swipeThreshold) viewModel.zap(-1)
                    },
                )
            }
            .pinchToScale(scale),
    ) {
        PlayerSurface(
            player = player,
            surfaceType = SURFACE_TYPE_SURFACE_VIEW,
            modifier = Modifier
                .align(Alignment.Center)
                .resizeWithContentScale(if (inPip) ContentScale.Fit else scale.value.contentScale, presentation.videoSizeDp),
        )
        if (presentation.coverSurface) {
            Box(Modifier.fillMaxSize().background(Color.Black))
        }
        Captions(player, Modifier.fillMaxSize())
        if (!inPip) VideoScaleLabel(scale, Modifier.align(Alignment.TopCenter).padding(top = 96.dp))

        if (playback.buffering && playback.error == null && ui.resolveError == null) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 3.dp,
                modifier = Modifier.align(Alignment.Center).size(48.dp),
            )
        }

        ChannelOverlay(
            visible = overlayVisible && !panelOpen && !guideOpen && !inPip,
            ui = ui,
            playback = playback,
            nowNext = nowNext,
            now = now,
            isTv = isTv,
            onBack = onBack,
            onFavorite = { viewModel.toggleFavorite(); poke() },
            onOptions = { panelOpen = true },
            onChannels = { guideOpen = true },
            onZap = { viewModel.zap(it); poke() },
            onTogglePlay = { viewModel.livePlayer.togglePlay(); poke() },
            onPip = enterPip,
            scale = scale.value,
            onScale = { scale.cycle(); poke() },
            onMultiview = { ui.channel?.let { onOpenMultiview(MultiviewRoute(it.id, viewModel.filterKey)) } },
            catchupPosition = catchupPosition,
            canStartOver = remember(nowNext, ui.channel, ui.catchup) { viewModel.canStartOver() },
            onStartOver = { viewModel.startOver(); poke() },
            onSeek = { viewModel.seekTo(it); poke() },
            onSkip = { viewModel.skip(it); poke() },
            onGoLive = { viewModel.goLive(); poke() },
        )

        val error = ui.resolveError ?: playback.error
        if (error != null && !panelOpen && !guideOpen && !inPip) {
            ErrorCard(
                message = error,
                onRetry = viewModel::retry,
                onNext = { viewModel.zap(+1) },
                modifier = Modifier.align(Alignment.Center),
            )
        }

        if (ui.numberEntry.isNotEmpty()) {
            Text(
                ui.numberEntry,
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(32.dp)
                    .background(Color(0xAA000000), MaterialTheme.shapes.medium)
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }

        AnimatedVisibility(
            visible = guideOpen,
            enter = slideInHorizontally { -it } + fadeIn(),
            exit = slideOutHorizontally { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            Row {
            AnimatedVisibility(visible = categoriesOpen, enter = expandHorizontally() + fadeIn(), exit = shrinkHorizontally() + fadeOut()) {
                CategoryColumn(
                    categories = categories,
                    selected = browsing,
                    onSelect = {
                        viewModel.browse(it)
                        categoriesOpen = false
                        guideFocus++
                    },
                )
            }
            key(browsing) {
            MiniGuide(
                lineup = miniGuide,
                current = if (browsing == viewModel.browseFilter.value && viewModel.browsingCurrent) ui.position else -1,
                now = now,
                guideVersion = guideVersion,
                nowPlaying = viewModel::nowPlaying,
                isTv = isTv,
                onSelect = {
                    viewModel.tuneFromGuide(it)
                    guideOpen = false
                },
                onLeft = { categoriesOpen = true },
                focusKey = guideFocus,
            )
            }
            }
        }

        AnimatedVisibility(
            visible = panelOpen,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            OptionsPanel(
                ui = ui,
                playback = playback,
                onFavorite = viewModel::toggleFavorite,
                onSelectTrack = { viewModel.livePlayer.selectTrack(it) },
                onSubtitlesOff = viewModel.livePlayer::disableSubtitles,
                scale = scale.value,
                onScale = scale::set,
                onClose = { panelOpen = false },
            )
        }
    }
}

/** Landscape, immersive and screen-on while the player is visible. */
@Composable
internal fun FullscreenEffect(lockLandscape: Boolean) {
    val context = LocalContext.current
    val view = LocalView.current
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        // Player screens can replace each other (player <-> multiview); the next one starts before the
        // previous is disposed, so only the last one out restores orientation and system bars.
        if (FullscreenLock.holders++ == 0) FullscreenLock.previousOrientation = activity?.requestedOrientation
        if (lockLandscape) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        view.keepScreenOn = true
        onDispose {
            if (--FullscreenLock.holders == 0) {
                view.keepScreenOn = false
                controller?.show(WindowInsetsCompat.Type.systemBars())
                FullscreenLock.previousOrientation?.let { if (lockLandscape) activity?.requestedOrientation = it }
            }
        }
    }
}

private object FullscreenLock {
    var holders = 0
    var previousOrientation: Int? = null
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private const val OVERLAY_TIMEOUT_MS = 4_500L
