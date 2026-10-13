package com.xfin.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.xfin.core.designsystem.component.EmptyState
import com.xfin.core.designsystem.component.FocusSurface
import com.xfin.core.designsystem.component.LocalIsTv
import com.xfin.core.designsystem.component.ProgressTrack
import com.xfin.core.designsystem.theme.XfinColors
import com.xfin.core.model.MediaItem
import com.xfin.core.model.MediaKind

@Composable
internal fun ItemDetailScreen(
    onOpenItem: (sourceId: Long, itemId: String) -> Unit,
    onPlay: (sourceId: Long, itemId: String, startMs: Long) -> Unit,
    onBack: () -> Unit,
    viewModel: ItemDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isTv = LocalIsTv.current
    var returned by remember { mutableStateOf(false) }
    // Refresh progress after coming back from the player.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if (returned) viewModel.load() else returned = true }

    val item = state.item
    if (item == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (state.error != null) {
                EmptyState(Icons.Rounded.PlayArrow, "Couldn't load this title", state.error!!)
            } else {
                CircularProgressIndicator()
            }
        }
        return
    }
    val gutter = if (isTv) 56.dp else 20.dp
    val playFocus = remember { FocusRequester() }

    Box(Modifier.fillMaxSize()) {
        // Backdrop that fades into the page.
        Box(Modifier.fillMaxWidth().aspectRatio(if (isTv) 16f / 7f else 4f / 3f)) {
            AsyncImage(
                model = item.backdropUrl ?: item.thumbUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.25f),
                        0.55f to MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                        1f to MaterialTheme.colorScheme.background,
                    ),
                ),
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 40.dp),
        ) {
            item {
                Column(Modifier.padding(horizontal = gutter).widthIn(max = 900.dp)) {
                    Spacer(Modifier.statusBarsPadding().height(if (isTv) 140.dp else 170.dp))
                    if (item.logoUrl != null) {
                        AsyncImage(
                            model = item.logoUrl,
                            contentDescription = item.name,
                            contentScale = ContentScale.Fit,
                            alignment = Alignment.CenterStart,
                            modifier = Modifier.heightIn(max = if (isTv) 120.dp else 90.dp).widthIn(max = 420.dp),
                        )
                    } else {
                        Text(item.seriesName ?: item.name, style = MaterialTheme.typography.displaySmall)
                    }
                    if (item.kind == MediaKind.EPISODE) {
                        Text(
                            listOfNotNull(item.episodeLabel, item.name).joinToString(" · "),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    MetaLine(item, Modifier.padding(top = 10.dp))
                    if (item.genres.isNotEmpty()) {
                        Text(
                            item.genres.take(4).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                    Actions(item, state.upNext, playFocus, onPlay, viewModel::togglePlayed, viewModel.sourceId)
                    item.overview?.let { Overview(it, Modifier.padding(top = 20.dp)) }
                }
            }
            if (item.kind == MediaKind.SERIES && state.seasons.isNotEmpty()) {
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = gutter, vertical = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.seasons, key = { it.id }) { season ->
                            FilterChip(
                                selected = season.id == state.selectedSeasonId,
                                onClick = { viewModel.selectSeason(season.id) },
                                label = { Text(season.name) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                                border = null,
                                shape = MaterialTheme.shapes.large,
                            )
                        }
                    }
                }
                if (state.episodesLoading && state.episodes.isEmpty()) {
                    item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                }
                items(state.episodes, key = { it.id }) { episode ->
                    EpisodeRow(
                        episode = episode,
                        gutter = gutter,
                        onPlay = { onPlay(viewModel.sourceId, episode.id, if (episode.canResume) episode.positionMs else 0) },
                    )
                }
            }
        }
        if (!isTv) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.statusBarsPadding().padding(8.dp).clip(MaterialTheme.shapes.extraLarge).background(Color(0x66000000)),
            ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White) }
        }
    }
    LaunchedEffect(item.id) { if (isTv) runCatching { playFocus.requestFocus() } }
}

@Composable
private fun MetaLine(item: MediaItem, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val parts = listOfNotNull(
            item.year?.toString(),
            item.officialRating,
            item.runtimeMs?.takeIf { item.kind != MediaKind.SERIES }?.let(::formatDuration),
        )
        parts.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item.communityRating?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Star, null, tint = XfinColors.Favorite, modifier = Modifier.size(16.dp))
                Text(" %.1f".format(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Actions(
    item: MediaItem,
    upNext: MediaItem?,
    playFocus: FocusRequester,
    onPlay: (Long, String, Long) -> Unit,
    onTogglePlayed: () -> Unit,
    sourceId: Long,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        // Series play their next episode; everything else plays itself.
        val target = if (item.kind == MediaKind.SERIES) upNext else item
        if (target != null) {
            Button(
                onClick = { onPlay(sourceId, target.id, if (target.canResume) target.positionMs else 0) },
                modifier = Modifier.focusRequester(playFocus),
            ) {
                Icon(Icons.Rounded.PlayArrow, null)
                Spacer(Modifier.width(6.dp))
                Text(
                    when {
                        item.kind == MediaKind.SERIES -> listOfNotNull(if (target.canResume) "Resume" else "Play", target.episodeLabel).joinToString(" ")
                        target.canResume -> "Resume"
                        else -> "Play"
                    },
                )
            }
            if (target.canResume) {
                OutlinedButton(onClick = { onPlay(sourceId, target.id, 0) }) {
                    Icon(Icons.Rounded.Replay, null)
                    Spacer(Modifier.width(6.dp))
                    Text("From start")
                }
            }
        }
        FilledTonalButton(onClick = onTogglePlayed) {
            Icon(if (item.played) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null)
            Spacer(Modifier.width(6.dp))
            Text(if (item.played) "Watched" else "Mark watched")
        }
    }
    if (item.canResume && item.kind != MediaKind.SERIES) {
        ProgressTrack(item.progress, Modifier.padding(top = 12.dp).width(280.dp), height = 4.dp)
        remaining(item)?.let {
            Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun Overview(text: String, modifier: Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = if (expanded) Int.MAX_VALUE else 4,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.clickable { expanded = !expanded },
    )
}

@Composable
private fun EpisodeRow(episode: MediaItem, gutter: androidx.compose.ui.unit.Dp, onPlay: () -> Unit) {
    FocusSurface(
        onClick = onPlay,
        shape = MaterialTheme.shapes.medium,
        focusedScale = 1.02f,
        modifier = Modifier.padding(horizontal = gutter - 8.dp, vertical = 4.dp).fillMaxWidth(),
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(160.dp).aspectRatio(16f / 9f).clip(MaterialTheme.shapes.small)) {
                Artwork(episode.thumbUrl, episode.name, Modifier.fillMaxSize(), fallback = episode.episodeLabel)
                if (episode.progress > 0f && !episode.played) {
                    ProgressTrack(episode.progress, Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(6.dp), height = 3.dp)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        listOfNotNull(episode.episodeNumber?.let { "$it." }, episode.name).joinToString(" "),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (episode.played) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Rounded.CheckCircle, "Watched", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    }
                }
                remaining(episode)?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                episode.overview?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}
