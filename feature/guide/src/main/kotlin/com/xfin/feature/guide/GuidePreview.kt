package com.xfin.feature.guide

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import com.xfin.core.player.LivePlayer
import java.text.DateFormat
import java.util.Date

/** What's airing on the previewed channel, shown beside the video. */
@Composable
internal fun PreviewInfo(preview: GuidePreview?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            preview?.channel?.name.orEmpty(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val program = preview?.program
        Text(
            program?.title ?: "No program information",
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (program != null) {
            val time = DateFormat.getTimeInstance(DateFormat.SHORT)
            Text(
                "${time.format(Date(program.start))} – ${time.format(Date(program.end))}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            program.description?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/**
 * The live stream still playing from fullscreen; OK returns to it. Keeps the screen awake and
 * follows the app's lifecycle (the guide's own stops whenever fullscreen covers it).
 */
@OptIn(UnstableApi::class)
@Composable
internal fun PreviewVideo(
    livePlayer: LivePlayer,
    onOpen: () -> Unit,
    onSuspend: () -> Unit,
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playback by livePlayer.state.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    DisposableEffect(activity) {
        // A window flag, so it can't clash with the player screen's view.keepScreenOn on the way out.
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    val app = (activity as? LifecycleOwner) ?: LocalLifecycleOwner.current
    var stopped by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP, app) {
        stopped = true
        onSuspend()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START, app) {
        if (stopped) {
            stopped = false
            onResume()
        }
    }

    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        modifier
            .fillMaxHeight()
            .aspectRatio(16f / 9f)
            .clip(MaterialTheme.shapes.medium)
            .background(Color.Black)
            .border(
                width = if (focused) 3.dp else 0.dp,
                color = if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = MaterialTheme.shapes.medium,
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onOpen),
        contentAlignment = Alignment.Center,
    ) {
        PlayerSurface(player = livePlayer.player, surfaceType = SURFACE_TYPE_SURFACE_VIEW)
        when {
            playback.error != null -> Text(
                playback.error.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                maxLines = 3,
                modifier = Modifier.padding(12.dp),
            )
            playback.buffering -> CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
        }
    }
}
