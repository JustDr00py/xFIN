package com.xfin.feature.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.xfin.core.designsystem.component.FocusSurface
import com.xfin.core.model.MediaItem
import com.xfin.core.model.MediaVersion

/** A play request waiting for the user to pick which file to play. */
internal data class PendingPlay(val item: MediaItem, val startMs: Long)

/** Shown after pressing Play when a title has more than one version (e.g. 4K and 1080p). */
@Composable
internal fun VersionPicker(
    pending: PendingPlay,
    onPick: (MediaVersion) -> Unit,
    onDismiss: () -> Unit,
) {
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.widthIn(max = 520.dp).padding(24.dp),
        ) {
            Column(Modifier.padding(vertical = 20.dp)) {
                Text(
                    "Choose a version",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Text(
                    listOfNotNull(pending.item.seriesName, pending.item.episodeLabel, pending.item.name).distinct().joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.fillMaxWidth()) {
                    itemsIndexed(pending.item.versions, key = { _, v -> v.id }) { index, version ->
                        FocusSurface(
                            onClick = { onPick(version) },
                            shape = MaterialTheme.shapes.medium,
                            focusedScale = 1.02f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                .then(if (index == 0) Modifier.focusRequester(first) else Modifier),
                        ) {
                            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                                Text(version.name, style = MaterialTheme.typography.titleMedium)
                                if (version.details.isNotEmpty()) {
                                    Text(
                                        version.details,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End).padding(end = 12.dp, top = 4.dp)) {
                    Text("Cancel")
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}
