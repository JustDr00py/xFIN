package com.xfin.core.designsystem.component

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Logo URL to its tint; [NO_TINT] for logos without a clear colour (white or grey ones). */
private val tints = LruCache<String, Int>(1_024)
private const val NO_TINT = 0

/**
 * A colour taken from a channel's logo, like UHF's per-channel guide rows. Null until it is
 * known, and for logos that are mostly white, grey or black.
 */
@Composable
fun rememberLogoTint(logoUrl: String?): Color? {
    val context = LocalContext.current
    val cached = logoUrl?.let { tints.get(it) }
    val tint by produceState(cached?.takeIf { it != NO_TINT }?.let(::Color), logoUrl) {
        if (logoUrl == null || cached != null) return@produceState
        val request = ImageRequest.Builder(context).data(logoUrl).size(24).allowHardware(false).build()
        val bitmap = (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap()
            ?: return@produceState
        val argb = withContext(Dispatchers.Default) { dominantHue(bitmap) }
        tints.put(logoUrl, argb ?: NO_TINT)
        value = argb?.let(::Color)
    }
    return tint
}

/** The averaged hue of a logo's saturated pixels, as a muted colour; null when there are too few. */
private fun dominantHue(bitmap: Bitmap): Int? {
    val hsv = FloatArray(3)
    var x = 0.0
    var y = 0.0
    var weight = 0.0
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    for (p in pixels) {
        if ((p ushr 24) < 128) continue
        android.graphics.Color.colorToHSV(p, hsv)
        val s = hsv[1]
        val v = hsv[2]
        if (s < 0.25f || v < 0.2f) continue
        val w = (s * v).toDouble()
        val rad = Math.toRadians(hsv[0].toDouble())
        x += cos(rad) * w
        y += sin(rad) * w
        weight += w
    }
    // Ignore logos where coloured pixels are a speck, or spread over clashing hues.
    if (weight < pixels.size * 0.04 || hypot(x, y) < weight * 0.3) return null
    val hue = ((Math.toDegrees(atan2(y, x)) + 360) % 360).toFloat()
    return android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.55f, 0.8f))
}
