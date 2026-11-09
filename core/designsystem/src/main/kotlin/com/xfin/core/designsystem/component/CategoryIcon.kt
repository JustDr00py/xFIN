package com.xfin.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.xfin.core.model.CategoryName

/**
 * A fixed-size icon slot for a live category, so rows with and without icons line up.
 *
 * Shows the provider's [iconUrl] once it has loaded (through the app's Coil loader, which caches
 * in memory and on disk). Until then, and when there is no URL or it fails to load, it shows the
 * name's leading emoji, or else the first letter of the name in a circle.
 */
@Composable
fun CategoryIcon(name: CategoryName, iconUrl: String?, modifier: Modifier = Modifier, size: Dp = 28.dp) {
    var loaded by remember(iconUrl) { mutableStateOf(false) }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        if (!loaded) CategoryFallback(name, size)
        if (iconUrl != null) {
            AsyncImage(
                model = iconUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                onSuccess = { loaded = true },
                onError = { loaded = false },
                modifier = Modifier.fillMaxSize().alpha(if (loaded) 1f else 0f),
            )
        }
    }
}

@Composable
private fun CategoryFallback(name: CategoryName, size: Dp) {
    val density = LocalDensity.current
    val emoji = name.emoji
    if (emoji != null) {
        Text(emoji, style = TextStyle(fontSize = with(density) { (size * 0.62f).toSp() }), maxLines = 1)
        return
    }
    val color = LocalContentColor.current
    Box(
        Modifier.size(size).clip(CircleShape).background(color.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initial(name.label),
            style = TextStyle(fontSize = with(density) { (size * 0.45f).toSp() }, fontWeight = FontWeight.Bold),
            color = color.copy(alpha = 0.85f),
            maxLines = 1,
        )
    }
}

/** First letter or digit of the name ("#", if it has none). */
private fun initial(label: String): String {
    var i = 0
    while (i < label.length) {
        val cp = label.codePointAt(i)
        if (Character.isLetterOrDigit(cp)) return String(Character.toChars(Character.toUpperCase(cp)))
        i += Character.charCount(cp)
    }
    return "#"
}
