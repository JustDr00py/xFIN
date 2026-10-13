package com.xfin.feature.guide

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xfin.core.designsystem.component.ChannelLogo
import com.xfin.core.designsystem.component.LiveBadge
import com.xfin.core.designsystem.component.ProgressTrack
import com.xfin.core.designsystem.component.formatTimeRange
import java.util.concurrent.TimeUnit

@Composable
internal fun ProgramDetailsDialog(
    selection: ProgramSelection,
    now: Long,
    onWatch: () -> Unit,
    onWatchFromStart: () -> Unit,
    onDismiss: () -> Unit,
) {
    val program = selection.program
    val archived = program != null && selection.channel.catchup?.covers(program.start, now) == true
    val past = program != null && program.end <= now
    val focus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.widthIn(max = 560.dp).padding(24.dp),
        ) {
            Column(Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChannelLogo(selection.channel.name, selection.channel.logoUrl, Modifier.size(width = 64.dp, height = 42.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        selection.channel.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(16.dp))
                if (program != null) {
                    Text(program.title, style = MaterialTheme.typography.headlineSmall)
                    listOfNotNull(program.episode, program.subtitle).joinToString(" · ").takeIf { it.isNotEmpty() }?.let {
                        Text(it, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (program.isAiring(now)) LiveBadge()
                        Text(
                            buildString {
                                append(formatTimeRange(program.start, program.end))
                                append(" · ").append(dayLabel(program.start))
                                append(" · ").append(relative(program.start, program.end, now))
                                program.category?.let { append(" · ").append(it) }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (program.isAiring(now)) {
                        Spacer(Modifier.height(10.dp))
                        ProgressTrack(program.progress(now), Modifier.fillMaxWidth())
                    }
                    program.description?.let {
                        Spacer(Modifier.height(14.dp))
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
                        )
                    }
                } else {
                    Text("No program information", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(24.dp))
                if (past && !archived) {
                    Text(
                        if (selection.channel.catchup == null) "This channel has no catch-up, so past programs can't be replayed."
                        else "This is older than the channel's ${selection.channel.catchup?.days}-day archive.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = onDismiss) { Text("Close") }
                    if (archived) {
                        if (past) {
                            TextButton(onClick = onWatch) { Text("Watch live", maxLines = 1) }
                            Button(onClick = onWatchFromStart, modifier = Modifier.focusRequester(focus)) {
                                Icon(Icons.Rounded.Replay, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Watch from start", maxLines = 1)
                            }
                        } else {
                            OutlinedButton(onClick = onWatchFromStart) {
                                Icon(Icons.Rounded.RestartAlt, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Start over", maxLines = 1)
                            }
                            Button(onClick = onWatch, modifier = Modifier.focusRequester(focus)) { Text("Watch live", maxLines = 1) }
                        }
                    } else {
                        Button(onClick = onWatch, modifier = Modifier.focusRequester(focus)) {
                            Text("Watch ${selection.channel.name}", maxLines = 1)
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

private fun relative(start: Long, end: Long, now: Long): String {
    fun span(ms: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
        return if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min"
    }
    return when {
        now < start -> "starts in ${span(start - now)}"
        now < end -> "${span(end - now)} left"
        else -> "ended"
    }
}
