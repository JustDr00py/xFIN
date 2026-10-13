package com.xfin.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xfin.core.model.ProgramSlot
import java.text.DateFormat
import java.util.Date

/** Thin rounded progress track for "how far into the programme are we". */
@Composable
fun ProgressTrack(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 3.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = Color.White.copy(alpha = 0.14f),
) {
    Box(
        modifier
            .height(height)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(trackColor),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .background(color),
        )
    }
}

/** Programme title with a progress track underneath, as shown in channel lists. */
@Composable
fun NowPlayingLine(
    slot: ProgramSlot,
    now: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodySmall,
) {
    Column(modifier) {
        Text(
            slot.title,
            style = style,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(5.dp))
        ProgressTrack(slot.progress(now), Modifier.fillMaxWidth(0.6f))
    }
}

/** Locale-aware short time, e.g. "21:30" or "9:30 PM". */
fun formatTime(millis: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(millis))

fun formatTimeRange(start: Long, end: Long): String = "${formatTime(start)} – ${formatTime(end)}"

/**
 * Loads "on now" for one visible row. Reloads when the minute ticks or [guideVersion] changes;
 * [load] is expected to be cached, so idle rows cost nothing.
 */
@androidx.compose.runtime.Composable
fun rememberNowPlaying(
    channel: com.xfin.core.model.Channel,
    now: Long,
    guideVersion: Int,
    load: suspend (com.xfin.core.model.Channel, Long) -> ProgramSlot?,
): ProgramSlot? {
    val slot = androidx.compose.runtime.produceState(channel.nowPlaying, channel.id, channel.epgId, now / 60_000, guideVersion) {
        value = load(channel, now)
    }
    return slot.value
}
