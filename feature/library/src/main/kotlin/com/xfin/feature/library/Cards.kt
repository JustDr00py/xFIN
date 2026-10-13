package com.xfin.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.xfin.core.designsystem.component.FocusSurface
import com.xfin.core.designsystem.component.ProgressTrack
import com.xfin.core.designsystem.component.shimmer
import com.xfin.core.model.LibraryView
import com.xfin.core.model.MediaItem
import com.xfin.core.model.MediaKind

@Composable
internal fun Artwork(url: String?, contentDescription: String?, modifier: Modifier = Modifier, fallback: String? = null) {
    SubcomposeAsyncImage(
        model = url,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier,
        loading = { Box(Modifier.fillMaxSize().shimmer()) },
        error = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(Color(0xFF232A38), Color(0xFF151A23)))),
                contentAlignment = Alignment.Center,
            ) {
                if (fallback != null) {
                    Text(
                        fallback,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 3,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        },
    )
}

/** 2:3 poster for movies and shows. */
@Composable
internal fun PosterCard(item: MediaItem, width: Dp, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(if (width == Dp.Unspecified) modifier.fillMaxWidth() else modifier.width(width)) {
        FocusSurface(onClick = onClick, shape = MaterialTheme.shapes.medium, focusedScale = 1.06f) {
            Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
                Artwork(item.posterUrl, item.name, Modifier.fillMaxSize(), fallback = item.name)
                WatchedBadge(item, Modifier.align(Alignment.TopEnd))
                if (item.progress > 0f && !item.played) {
                    ProgressTrack(
                        item.progress,
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.dp),
                        height = 4.dp,
                    )
                }
            }
        }
        Text(
            item.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp, start = 2.dp),
        )
        item.year?.let {
            Text(it.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 16:9 still for "continue watching", next up and episodes. */
@Composable
internal fun ThumbCard(item: MediaItem, width: Dp, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.width(width)) {
        FocusSurface(onClick = onClick, shape = MaterialTheme.shapes.medium, focusedScale = 1.05f) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                Artwork(item.thumbUrl ?: item.backdropUrl, item.name, Modifier.fillMaxSize(), fallback = item.seriesName ?: item.name)
                WatchedBadge(item, Modifier.align(Alignment.TopEnd))
                if (item.progress > 0f && !item.played) {
                    ProgressTrack(
                        item.progress,
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(10.dp),
                        height = 4.dp,
                    )
                }
            }
        }
        Text(
            item.seriesName ?: item.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp, start = 2.dp),
        )
        Text(
            subtitleFor(item),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 2.dp),
        )
    }
}

@Composable
internal fun LibraryTile(view: LibraryView, width: Dp, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, shape = MaterialTheme.shapes.medium, modifier = Modifier.width(width)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            Artwork(view.imageUrl, view.name, Modifier.fillMaxSize())
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000)))),
            )
            Text(
                view.name,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
            )
        }
    }
}

@Composable
private fun WatchedBadge(item: MediaItem, modifier: Modifier) {
    when {
        item.played -> Icon(
            Icons.Rounded.CheckCircle,
            contentDescription = "Watched",
            tint = MaterialTheme.colorScheme.primary,
            modifier = modifier.padding(6.dp).size(22.dp).clip(MaterialTheme.shapes.extraLarge).background(Color(0xAA000000)),
        )
        (item.unplayedCount ?: 0) > 0 && item.kind == MediaKind.SERIES -> Text(
            item.unplayedCount.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = modifier
                .padding(6.dp)
                .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

internal fun subtitleFor(item: MediaItem): String = when (item.kind) {
    MediaKind.EPISODE -> listOfNotNull(item.episodeLabel, item.name.takeIf { item.seriesName != null }).joinToString(" · ")
    else -> listOfNotNull(item.year?.toString(), remaining(item)).joinToString(" · ")
}

internal fun remaining(item: MediaItem): String? {
    val runtime = item.runtimeMs ?: return null
    val left = if (item.canResume) runtime - item.positionMs else runtime
    return formatDuration(left) + if (item.canResume) " left" else ""
}

internal fun formatDuration(ms: Long): String {
    val minutes = (ms / 60_000).coerceAtLeast(1)
    return if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min"
}
