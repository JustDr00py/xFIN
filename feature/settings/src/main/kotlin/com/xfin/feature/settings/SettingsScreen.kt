package com.xfin.feature.settings

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xfin.core.designsystem.component.LocalIsTv
import com.xfin.core.model.EpgRefreshState
import com.xfin.core.model.RefreshState
import com.xfin.core.model.Source
import com.xfin.core.model.SourceType

@Composable
internal fun SettingsScreen(
    onAddSource: () -> Unit,
    onEditSource: (Source) -> Unit,
    onOpenGroups: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()) {
    val sources by viewModel.sourceList.collectAsStateWithLifecycle()
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val refresh by viewModel.refreshState.collectAsStateWithLifecycle()
    val epgState by viewModel.epgState.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<Source?>(null) }
    var editingGuide by remember { mutableStateOf<Source?>(null) }
    val isTv = LocalIsTv.current

    Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = if (isTv) 32.dp else 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                Text(
                    "Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 16.dp),
                )
            }
            item { SectionTitle("Sources") }
            items(sources, key = { it.id }) { source ->
                SourceItem(
                    source = source,
                    refreshing = (refresh as? RefreshState.Running)?.sourceId == source.id,
                    guideLoading = (epgState as? EpgRefreshState.Running)?.takeIf { it.sourceId == source.id },
                    onRefresh = { viewModel.refresh(source) },
                    onDelete = { pendingDelete = source },
                    onEdit = { onEditSource(source) },
                    onEditGuide = { editingGuide = source },
                    onRefreshGuide = { viewModel.refreshGuide(source) },
                )
            }
            item {
                FilledTonalButton(onClick = onAddSource, modifier = Modifier.padding(top = 8.dp)) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add source")
                }
            }

            if (groups.isNotEmpty()) {
                item {
                    Spacer(Modifier.size(16.dp))
                    ListItem(
                        headlineContent = { Text("Channel groups") },
                        supportingContent = {
                            val hidden = groups.count { it.hidden }
                            Text(
                                if (hidden == 0) "All %,d groups shown".format(groups.size)
                                else "%,d of %,d shown".format(groups.size - hidden, groups.size),
                            )
                        },
                        trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.clip(MaterialTheme.shapes.medium).clickable(onClick = onOpenGroups),
                    )
                }
            }

            item {
                Spacer(Modifier.size(24.dp))
                SectionTitle("About")
                Text(
                    "xFIN · Live TV and media for Android",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }

    editingGuide?.let { source ->
        GuideUrlDialog(
            source = source,
            onSave = {
                viewModel.setEpgUrl(source, it)
                editingGuide = null
            },
            onDismiss = { editingGuide = null },
        )
    }

    pendingDelete?.let { source ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Remove ${source.name}?") },
            text = { Text("Its channels, favorites and history will be removed from xFIN.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(source)
                    pendingDelete = null
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
    )
}

@Composable
private fun SourceItem(
    source: Source,
    refreshing: Boolean,
    guideLoading: EpgRefreshState.Running?,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onEditGuide: () -> Unit,
    onRefreshGuide: () -> Unit,
) {
    ListItem(
        leadingContent = {
            Icon(
                when (source.type) {
                    SourceType.M3U -> Icons.AutoMirrored.Rounded.PlaylistPlay
                    SourceType.XTREAM -> Icons.Rounded.Dns
                    SourceType.JELLYFIN -> Icons.Rounded.VideoLibrary
                },
                null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        // Actions sit beside the name so the details below get the full width.
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(source.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (refreshing) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp).padding(2.dp))
                } else if (source.type != SourceType.JELLYFIN) {
                    IconButton(onClick = onRefresh) { Icon(Icons.Rounded.Refresh, "Refresh ${source.name}") }
                }
                IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, "Edit ${source.name}") }
                IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, "Remove ${source.name}") }
            }
        },
        supportingContent = {
            Column {
                val updated = source.lastRefreshedAt?.let {
                    DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
                }
                if (source.type == SourceType.JELLYFIN) {
                    Text(listOfNotNull("Jellyfin", source.username?.let { "signed in as $it" }).joinToString(" · "))
                    Text(source.url.substringAfter("://"), maxLines = 1)
                } else {
                    Text(
                        listOfNotNull(
                            if (source.type == SourceType.XTREAM) "Xtream Codes" else "M3U",
                            "${"%,d".format(source.channelCount)} channels",
                            source.vodMovieCount.takeIf { it > 0 }?.let { "${"%,d".format(it)} movies" },
                            source.vodSeriesCount.takeIf { it > 0 }?.let { "${"%,d".format(it)} series" },
                            updated?.let { "updated $it" },
                        ).joinToString(" · "),
                    )
                    source.lastError?.let { Text(it, color = MaterialTheme.colorScheme.error, maxLines = 2) }
                    GuideStatus(source, guideLoading, onEditGuide, onRefreshGuide)
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.clip(MaterialTheme.shapes.medium),
    )
}

@Composable
private fun GuideStatus(
    source: Source,
    loading: EpgRefreshState.Running?,
    onEdit: () -> Unit,
    onRefresh: () -> Unit,
) {
    val hasGuideUrl = source.epgUrl != null || source.type == SourceType.XTREAM
    val status = when {
        loading != null -> "Guide: loading… ${"%,d".format(loading.programs)} programs"
        source.epg.error != null -> "Guide: ${source.epg.error}"
        source.epg.updatedAt != null -> "Guide: ${"%,d".format(source.epg.programCount)} programs · updated " +
            DateUtils.getRelativeTimeSpanString(source.epg.updatedAt!!, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
        hasGuideUrl -> "Guide: not loaded yet"
        else -> "No guide URL"
    }
    Text(
        status,
        color = if (source.epg.error != null && loading == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if (source.type == SourceType.M3U || source.type == SourceType.XTREAM) {
            TextButton(onClick = onEdit, contentPadding = PaddingValues(horizontal = 0.dp)) {
                Text(
                    when {
                        source.epgUrl != null -> "Edit guide URL"
                        source.type == SourceType.XTREAM -> "Use another guide"
                        else -> "Add guide URL"
                    },
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
        if (hasGuideUrl && loading == null) {
            TextButton(onClick = onRefresh, contentPadding = PaddingValues(horizontal = 0.dp)) { Text("Update guide", maxLines = 1, softWrap = false) }
        }
    }
}

@Composable
private fun GuideUrlDialog(source: Source, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var url by remember { mutableStateOf(source.epgUrl.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Guide (XMLTV) URL") },
        text = {
            Column {
                Text(
                    "xFIN matches guide channels to ${source.name} by tvg-id, then by name. Plain .xml and .xml.gz both work." +
                        if (source.type == SourceType.XTREAM) " Leave it empty to use the provider's own guide." else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(12.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    singleLine = true,
                    placeholder = { Text("https://…/guide.xml.gz") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(url) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
