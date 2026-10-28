package com.xfin.feature.player

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.os.Build
import androidx.annotation.RequiresApi
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer

/**
 * Implemented by the activity: holds the PiP parameters of the player on screen so pressing Home
 * can enter picture-in-picture on Android versions without auto-enter (before 12).
 */
interface PipHost {
    var pipParams: PictureInPictureParams?
}

/** True while the activity is shown in a picture-in-picture window. */
@Composable
fun rememberIsInPip(): Boolean {
    val activity = LocalContext.current.findComponentActivity() ?: return false
    var inPip by remember { mutableStateOf(activity.isInPictureInPictureMode) }
    DisposableEffect(activity) {
        val listener = Consumer<PictureInPictureModeChangedInfo> { inPip = it.isInPictureInPictureMode }
        activity.addOnPictureInPictureModeChangedListener(listener)
        onDispose { activity.removeOnPictureInPictureModeChangedListener(listener) }
    }
    return inPip
}

/**
 * Makes the current player eligible for picture-in-picture: correct aspect ratio, auto-enter on
 * Home (Android 12+, or via [PipHost] before that) and a play/pause button in the PiP window.
 * Returns a function that enters PiP right away (for the overlay's PiP button).
 */
@Composable
fun PipEffect(videoSize: Size, playing: Boolean, onTogglePlay: () -> Unit): () -> Unit {
    // Picture-in-picture arrived in Android 8.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return {}
    val context = LocalContext.current
    val activity = context.findComponentActivity()
    val toggle by rememberUpdatedState(onTogglePlay)

    // Play/pause from the PiP window arrives as a broadcast.
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) = toggle()
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(ACTION_TOGGLE), ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }

    val params = remember(videoSize, playing) { activity?.let { buildParams(it, videoSize, playing, autoEnter = true) } }
    DisposableEffect(params) {
        if (activity != null && params != null) {
            activity.setPictureInPictureParams(params)
            (activity as? PipHost)?.pipParams = params
        }
        onDispose {
            if (activity != null) {
                // Leaving the player: Home must no longer shrink the app into PiP.
                activity.setPictureInPictureParams(buildParams(activity, videoSize, playing, autoEnter = false))
                (activity as? PipHost)?.pipParams = null
            }
        }
    }
    return { if (activity != null && params != null) runCatching { activity.enterPictureInPictureMode(params) } }
}

@RequiresApi(Build.VERSION_CODES.O)
private fun buildParams(activity: Activity, videoSize: Size, playing: Boolean, autoEnter: Boolean): PictureInPictureParams {
    val ratio = if (videoSize.width > 0 && videoSize.height > 0) {
        // Android only accepts ratios between 1:2.39 and 2.39:1.
        val r = (videoSize.width / videoSize.height).coerceIn(1 / 2.38f, 2.38f)
        Rational((r * 1000).toInt(), 1000)
    } else {
        Rational(16, 9)
    }
    val intent = PendingIntent.getBroadcast(
        activity,
        0,
        Intent(ACTION_TOGGLE).setPackage(activity.packageName),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val action = RemoteAction(
        Icon.createWithResource(activity, if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play),
        if (playing) "Pause" else "Play",
        if (playing) "Pause" else "Play",
        intent,
    )
    return PictureInPictureParams.Builder()
        .setAspectRatio(ratio)
        .setActions(listOf(action))
        .apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setAutoEnterEnabled(autoEnter)
                setSeamlessResizeEnabled(true)
            }
        }
        .build()
}

private const val ACTION_TOGGLE = "com.xfin.app.PIP_TOGGLE_PLAY"

private tailrec fun Context.findComponentActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findComponentActivity()
    else -> null
}
