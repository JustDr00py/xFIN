package com.xfin.feature.guide

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.xfin.core.designsystem.component.EmptyState
import com.xfin.core.designsystem.component.LocalIsTv
import com.xfin.core.model.ChannelFilter
import com.xfin.core.model.EpgRefreshState
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@Composable
internal fun GuideScreen(
    onPlay: (channelId: Long, filter: ChannelFilter) -> Unit,
    onOpenSettings: () -> Unit,
    onPlayArchive: (channelId: Long, filter: ChannelFilter, start: Long, end: Long, title: String) -> Unit,
    onBack: () -> Unit,
    viewModel: GuideViewModel = hiltViewModel(),
) {
    val isTv = LocalIsTv.current
    val now by viewModel.now.collectAsStateWithLifecycle()
    val tabs by viewModel.tabs.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val hasGuide by viewModel.hasGuide.collectAsStateWithLifecycle()
    val refresh by viewModel.refresh.collectAsStateWithLifecycle()
    val version by viewModel.guideVersion.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val channels = viewModel.channelPages.collectAsLazyPagingItems()
    val scope = rememberCoroutineScope()
    var picking by remember { mutableStateOf(false) }

    val metrics = if (isTv) {
        GuideMetrics(minuteWidth = 7.dp, rowHeight = 76.dp, channelColumnWidth = 240.dp, showChannelNames = true)
    } else {
        GuideMetrics(minuteWidth = 4.5.dp, rowHeight = 68.dp, channelColumnWidth = 84.dp, showChannelNames = false)
    }
    val timeline = rememberTimelineState(viewModel.window, metrics.minuteWidth, now)
    val playFilter = filter ?: ChannelFilter.All

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 12.dp, top = if (isTv) 24.dp else 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Opened from a Live TV category; the remote's Back does the same on TV.
            if (!isTv) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            }
            // On phones the category name is the title (as in UHF); the TV has room for both.
            if (isTv) Text("Guide", style = MaterialTheme.typography.headlineMedium, maxLines = 1, softWrap = false)
            if (refresh is EpgRefreshState.Running) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            }
            // The picker takes whatever room is left and ellipsizes long group names, so the
            // fixed-size buttons beside it never get squeezed.
            Box(Modifier.weight(1f), contentAlignment = if (isTv) Alignment.CenterEnd else Alignment.CenterStart) {
                TextButton(onClick = { picking = true }) {
                    Text(
                        tabs.firstOrNull { it.filter == filter }?.label ?: "All channels",
                        style = if (isTv) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Icon(Icons.Rounded.ArrowDropDown, null)
                }
            }
            // Phones have a Settings tab; the TV keeps the shortcut.
            if (isTv) IconButton(onClick = onOpenSettings) { Icon(Icons.Rounded.Settings, "Settings") }
            FilledTonalButton(onClick = {
                scope.launch { timeline.animateTo(timeline.x(now - TimeUnit.MINUTES.toMillis(30))) }
            }) {
                Icon(Icons.Rounded.Schedule, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Now", maxLines = 1, softWrap = false)
            }
        }

        when {
            hasGuide == false && refresh is EpgRefreshState.Running -> EmptyState(
                icon = Icons.Rounded.DateRange,
                title = "Loading the guide…",
                message = "${"%,d".format((refresh as EpgRefreshState.Running).programs)} programs so far. This only takes long the first time.",
                modifier = Modifier.fillMaxSize(),
            )
            hasGuide == false -> EmptyState(
                icon = Icons.Rounded.DateRange,
                title = "No guide data yet",
                message = "Add an XMLTV guide URL to your playlist in Settings, or check that your provider offers one.",
                modifier = Modifier.fillMaxSize(),
                action = { Button(onClick = onOpenSettings) { Text("Open Settings") } },
            )
            else -> GuideGrid(
                channels = channels,
                timeline = timeline,
                metrics = metrics,
                now = now,
                guideVersion = version,
                isTv = isTv,
                loadPrograms = viewModel::programs,
                cachedPrograms = viewModel::cachedPrograms,
                prefetch = viewModel::prefetch,
                onToggleFavorite = viewModel::toggleFavorite,
                onChannelClick = { onPlay(it.id, playFilter) },
                onProgramClick = { channel, program ->
                    // Tapping what's on now tunes straight in; anything else shows details first.
                    if (program == null || program.isAiring(now)) onPlay(channel.id, playFilter) else viewModel.open(channel, program)
                },
                modifier = Modifier.fillMaxSize(),
                resetKey = filter,
            )
        }
    }

    if (picking) {
        GroupPicker(
            tabs = tabs,
            selected = filter,
            onSelect = {
                viewModel.select(it)
                picking = false
            },
            onDismiss = { picking = false },
        )
    }

    selection?.let { sel ->
        ProgramDetailsDialog(
            selection = sel,
            now = now,
            onWatch = {
                viewModel.dismiss()
                onPlay(sel.channel.id, playFilter)
            },
            onWatchFromStart = {
                viewModel.dismiss()
                sel.program?.let { onPlayArchive(sel.channel.id, playFilter, it.start, it.end, it.title) }
            },
            onDismiss = viewModel::dismiss,
        )
    }
}

@Composable
private fun GroupPicker(
    tabs: List<GuideTab>,
    selected: ChannelFilter?,
    onSelect: (ChannelFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(vertical = 16.dp)) {
                Text(
                    "Show channels",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                LazyColumn(Modifier.fillMaxWidth().height(420.dp)) {
                    items(tabs, key = { it.label + it.filter.hashCode() }) { tab ->
                        TextButton(
                            onClick = { onSelect(tab.filter) },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        ) {
                            Text(tab.label, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (tab.filter == selected) Icon(Icons.Rounded.Check, "Selected")
                        }
                    }
                }
            }
        }
    }
}
