package com.xfin.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlin.math.absoluteValue

/**
 * Channel logo on a soft tile. Falls back to a monogram on a color derived from the name,
 * so huge playlists without logos still look intentional.
 */
@Composable
fun ChannelLogo(name: String, logoUrl: String?, modifier: Modifier = Modifier) {
    var failed by remember(logoUrl) { mutableStateOf(logoUrl == null) }
    val shape = MaterialTheme.shapes.small
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (failed) monogramBrush(name) else logoBackdrop),
        contentAlignment = Alignment.Center,
    ) {
        if (failed) {
            Text(
                text = monogram(name),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.92f),
                maxLines = 1,
            )
        } else {
            AsyncImage(
                model = logoUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                onError = { failed = true },
                modifier = Modifier.padding(6.dp),
            )
        }
    }
}

private val logoBackdrop = Brush.verticalGradient(listOf(Color(0xFF232A38), Color(0xFF1A1F2A)))

private val monogramHues = listOf(
    0xFF5B6CFF, 0xFF00A39A, 0xFFE0627A, 0xFFDB8B2B, 0xFF8E62E0, 0xFF2D8FDB, 0xFF4DA35A, 0xFFC4544B,
)

private fun monogramBrush(name: String): Brush {
    val base = Color(monogramHues[name.hashCode().absoluteValue % monogramHues.size])
    return Brush.linearGradient(listOf(base, base.copy(alpha = 0.55f)))
}

internal fun monogram(name: String): String {
    val words = name
        .replace(Regex("""\b(HD|FHD|UHD|4K|SD|HEVC|H265)\b""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""^[A-Z]{2,3}\s*[|:\-]\s*"""), "")
        .split(' ', '-', '_', '.', '|')
        .filter { it.isNotBlank() && it.first().isLetterOrDigit() }
    return when {
        words.isEmpty() -> name.take(2).uppercase()
        words.size == 1 -> words[0].take(3).uppercase()
        else -> words.take(2).joinToString("") { it.first().uppercase() }
    }
}
