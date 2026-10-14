package com.xfin.feature.player

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** How the picture fills the screen when its shape doesn't match. */
internal enum class VideoScale(val label: String, val contentScale: ContentScale) {
    /** The whole picture, with black bars. */
    Fit("Fit", ContentScale.Fit),

    /** Fills the screen; the edges are cropped. */
    Zoom("Zoom", ContentScale.Crop),

    /** Fills the screen by stretching the picture. */
    Stretch("Stretch", ContentScale.FillBounds),
    ;

    fun next(): VideoScale = entries[(ordinal + 1) % entries.size]
}

/** The scale mode shared by every player, remembered across launches. */
@Stable
internal class VideoScaleState(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var value by mutableStateOf(
        prefs.getString(KEY, null)?.let { saved -> VideoScale.entries.firstOrNull { it.name == saved } } ?: VideoScale.Fit,
    )
        private set

    /** Bumped on every change, so the label shows again even when the same mode is picked twice. */
    var changes by mutableIntStateOf(0)
        private set

    fun set(scale: VideoScale) {
        value = scale
        changes++
        prefs.edit().putString(KEY, scale.name).apply()
    }

    fun cycle() = set(value.next())

    private companion object {
        const val PREFS = "player"
        const val KEY = "video_scale"
    }
}

@Composable
internal fun rememberVideoScale(): VideoScaleState {
    val context = LocalContext.current.applicationContext
    return remember(context) { VideoScaleState(context) }
}

/**
 * Pinch out to fill the screen, pinch in to fit the whole picture. Only two-finger gestures are
 * consumed, so taps and swipes underneath keep working. Put it after other pointer handlers.
 */
internal fun Modifier.pinchToScale(state: VideoScaleState): Modifier = pointerInput(state) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var zoom = 1f
        var done = false
        do {
            val event = awaitPointerEvent()
            if (event.changes.count { it.pressed } >= 2) {
                zoom *= event.calculateZoom()
                if (!done && zoom > PINCH_OUT) {
                    if (state.value == VideoScale.Fit) state.set(VideoScale.Zoom)
                    done = true
                } else if (!done && zoom < PINCH_IN) {
                    if (state.value != VideoScale.Fit) state.set(VideoScale.Fit)
                    done = true
                }
                event.changes.forEach { it.consume() }
            }
        } while (event.changes.any { it.pressed })
    }
}

private const val PINCH_OUT = 1.2f
private const val PINCH_IN = 0.83f

/** Briefly names the mode after it changes. */
@Composable
internal fun VideoScaleLabel(state: VideoScaleState, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(state.changes) {
        if (state.changes == 0) return@LaunchedEffect
        visible = true
        delay(1_200)
        visible = false
    }
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Text(
            state.value.label,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            modifier = Modifier
                .background(Color(0x99000000), RoundedCornerShape(50))
                .padding(horizontal = 20.dp, vertical = 10.dp),
        )
    }
}
