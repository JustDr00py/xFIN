package com.xfin.feature.player

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.xfin.core.player.StreamStats
import com.xfin.core.player.StreamStatsTracker
import kotlinx.coroutines.delay
import java.util.Locale

/** "Under the Hood": live technical readings of the stream, refreshed every second. */
@OptIn(UnstableApi::class)
@Composable
internal fun UnderTheHood(tracker: StreamStatsTracker, player: ExoPlayer, modifier: Modifier = Modifier) {
    var stats by remember { mutableStateOf(tracker.snapshot(player)) }
    LaunchedEffect(tracker, player) {
        while (true) {
            stats = tracker.snapshot(player)
            delay(1_000)
        }
    }
    Column(
        modifier
            .widthIn(max = 420.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xCC000000))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text("Under the Hood", style = MaterialTheme.typography.titleSmall, color = Color.White)
        rows(stats).forEach { (label, value) ->
            Row(Modifier.padding(top = 4.dp)) {
                Text(label, style = mono(), color = Color.White.copy(alpha = 0.6f), modifier = Modifier.width(96.dp))
                Text(value, style = mono(), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun mono() = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)

private fun rows(s: StreamStats): List<Pair<String, String>> = buildList {
    add("State" to s.state)
    add("Video" to listOfNotNull(s.resolution, s.frameRate.takeIf { it > 0 }?.let { "%.2f fps".format(Locale.US, it) }).joinToString(" @ ").ifEmpty { "—" })
    add("V codec" to listOfNotNull(s.videoCodec, s.videoBitrate.takeIf { it > 0 }?.let(::bitrate)).joinToString(" · ").ifEmpty { "—" })
    add("V decoder" to (s.videoDecoder ?: "—"))
    add(
        "Audio" to listOfNotNull(
            s.audioCodec,
            s.audioChannels.takeIf { it > 0 }?.let(::channels),
            s.sampleRate.takeIf { it > 0 }?.let { "${it / 1000.0} kHz" },
            s.audioLanguage,
        ).joinToString(" · ").ifEmpty { "—" },
    )
    add("A decoder" to (s.audioDecoder ?: "—"))
    add("Buffer" to "%.1f s".format(Locale.US, s.bufferMs / 1000.0))
    add("Dropped" to "${s.droppedFrames} frames")
    add("Network" to if (s.bandwidthBps > 0) bitrate(s.bandwidthBps) else "—")
    add("Received" to megabytes(s.downloadedBytes))
    s.host?.let { add("Server" to it) }
}

private fun bitrate(bps: Number): String {
    val v = bps.toDouble()
    return if (v >= 1_000_000) "%.1f Mbps".format(Locale.US, v / 1_000_000) else "%.0f kbps".format(Locale.US, v / 1_000)
}

private fun channels(n: Int) = when (n) {
    1 -> "mono"
    2 -> "stereo"
    6 -> "5.1"
    8 -> "7.1"
    else -> "$n ch"
}

private fun megabytes(bytes: Long) = "%.1f MB".format(Locale.US, bytes / 1_048_576.0)
