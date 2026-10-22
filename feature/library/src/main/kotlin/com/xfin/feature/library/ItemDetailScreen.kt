package com.xfin.feature.library

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import com.xfin.core.designsystem.component.EmptyState
import com.xfin.core.designsystem.component.FocusSurface
import com.xfin.core.designsystem.component.LocalIsTv
import com.xfin.core.designsystem.component.ProgressTrack
import com.xfin.core.designsystem.theme.XfinColors
import com.xfin.core.model.MediaItem
import com.xfin.core.model.MediaKind

/** The version picker is open for [item]; [failedId] is set when it reopened after a failed playback. */
private data class VersionChoice(val item: MediaItem, val startMs: Long, val failedId: String? = null)

@Composable
internal fun ItemDetailScreen(
    onOpenItem: (sourceId: Long, itemId: String) -> Unit,
    onPlay: (sourceId: Long, itemId: String, startMs: Long, mediaSourceId: String?) -> Unit,
    onBack: () -> Unit,
    viewModel: ItemDetailViewModel = hiltViewModel(),
) {
    // Play asks which version to use when a title has more than one; the last one that played is
    // highlighted. After a failed playback the player can send the user back here to pick another.
    var picking by remember { mutableStateOf<VersionChoice?>(null) }
    val play: (MediaItem, Long) -> Unit = { target, startMs ->
        if (target.versions.size > 1) picking = VersionChoice(target, startMs)
        else onPlay(viewModel.sourceId, target.id, startMs, viewModel.versionFor(target)?.id)
    }
    val versionRetry by viewModel.versionRetry.collectAsStateWithLifecycle()
    LaunchedEffect(versionRetry) {
        versionRetry?.let {
            picking = VersionChoice(it.item, it.startMs, it.failedVersionId)
            viewModel.versionRetryShown()
        }
    }
    picking?.let { (target, startMs, failedId) ->
        VersionPicker(
            item = target,
            selectedId = viewModel.versionFor(target)?.id,
            failedId = failedId,
            onPick = { version ->
                picking = null
                onPlay(viewModel.sourceId, target.id, startMs, version.id)
            },
            onDismiss = { picking = null },
        )
    }
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
                    val title = item.seriesName ?: item.name
                    if (item.logoUrl != null) {
                        // The title text stands in while the logo loads (or if it fails).
                        SubcomposeAsyncImage(
                            model = item.logoUrl,
                            contentDescription = title,
                            contentScale = ContentScale.Fit,
                            alignment = Alignment.CenterStart,
                            loading = { Text(title, style = MaterialTheme.typography.displaySmall) },
                            error = { Text(title, style = MaterialTheme.typography.displaySmall) },
                            modifier = Modifier.heightIn(max = if (isTv) 120.dp else 90.dp).widthIn(max = 420.dp),
                        )
                    } else {
                        Text(title, style = MaterialTheme.typography.displaySmall)
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
                    state.source?.let {
                        Text(
                            "From $it",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                    Actions(item, state.upNext, playFocus, play, viewModel::togglePlayed, viewModel::toggleFavorite)
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
                            val selected = season.id == state.selectedSeasonId
                            val interaction = remember { MutableInteractionSource() }
                            // A chip's own focus tint is too faint to follow from the couch: the
                            // focused season turns solid light, the shown one keeps its color and check.
                            val focused by interaction.collectIsFocusedAsState()
                            val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "seasonFocus")
                            val colors = MaterialTheme.colorScheme
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.selectSeason(season.id) },
                                label = { Text(season.name, fontWeight = if (focused || selected) FontWeight.SemiBold else null) },
                                leadingIcon = if (selected) {
                                    { Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)) }
                                } else {
                                    null
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = if (focused) colors.onSurface else colors.surfaceContainerHigh,
                                    labelColor = if (focused) colors.surface else colors.onSurface,
                                    selectedContainerColor = if (focused) colors.onSurface else colors.primary,
                                    selectedLabelColor = if (focused) colors.surface else colors.onPrimary,
                                    selectedLeadingIconColor = if (focused) colors.primary else colors.onPrimary,
                                ),
                                border = null,
                                shape = MaterialTheme.shapes.large,
                                interactionSource = interaction,
                                modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale },
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
                        onPlay = { play(episode, if (episode.canResume) episode.positionMs else 0) },
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

/**
 * A round icon-only action beside Play. [tint] colors the icon for an "on" state (Color.Unspecified
 * keeps the normal content color). On TV the focused button turns solid light, like other focus.
 */
@Composable
private fun ActionIconButton(onClick: () -> Unit, icon: ImageVector, description: String, tint: Color) {
    val isTv = LocalIsTv.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val lit = isTv && focused
    val scale by animateFloatAsState(if (lit) 1.12f else 1f, label = "actionFocus")
    val colors = MaterialTheme.colorScheme
    FilledTonalIconButton(
        onClick = onClick,
        interactionSource = interaction,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = if (lit) colors.onSurface else colors.secondaryContainer,
            contentColor = if (lit) colors.surface else colors.onSecondaryContainer,
        ),
        modifier = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
    ) {
        Icon(icon, contentDescription = description, tint = if (tint == Color.Unspecified) LocalContentColor.current else tint)
    }
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
    onPlay: (MediaItem, Long) -> Unit,
    onTogglePlayed: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    // Wraps on phones, where Play, From start, Mark watched and Favorite don't fit on one line.
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        // Series play their next episode; everything else plays itself.
        val target = if (item.kind == MediaKind.SERIES) upNext else item
        if (target != null) {
            Button(
                onClick = { onPlay(target, if (target.canResume) target.positionMs else 0) },
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
                OutlinedButton(onClick = { onPlay(target, 0) }) {
                    Icon(Icons.Rounded.Replay, null)
                    Spacer(Modifier.width(6.dp))
                    Text("From start")
                }
            }
        }
        // Icons only, so the actions stay on one row on a phone.
        ActionIconButton(
            onClick = onTogglePlayed,
            icon = if (item.played) Icons.Rounded.CheckCircle else Icons.Outlined.CheckCircle,
            description = if (item.played) "Watched, mark unwatched" else "Mark watched",
            tint = if (item.played) XfinColors.Watched else Color.Unspecified,
        )
        // Movies and shows; an episode's favorite belongs to its show.
        if (item.kind == MediaKind.MOVIE || item.kind == MediaKind.SERIES) {
            ActionIconButton(
                onClick = onToggleFavorite,
                icon = if (item.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                description = if (item.isFavorite) "Remove from favorites" else "Add to favorites",
                tint = if (item.isFavorite) XfinColors.Favorite else Color.Unspecified,
            )
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
