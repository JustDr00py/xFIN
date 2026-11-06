package com.xfin.feature.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.xfin.core.model.MediaItem
import com.xfin.core.model.MediaKind

/** What a long press (phone) or a held OK (TV) on a home-row card can do with it. */
internal sealed interface ItemAction {
    data object Resume : ItemAction
    data object PlayFromStart : ItemAction
    data object Details : ItemAction
    data object RemoveFromContinue : ItemAction
    data class SetPlayed(val played: Boolean) : ItemAction
    data class SetFavorite(val favorite: Boolean) : ItemAction
}

@Composable
internal fun ItemActionsDialog(item: MediaItem, onAction: (ItemAction) -> Unit, onDismiss: () -> Unit) {
    val playable = item.kind == MediaKind.MOVIE || item.kind == MediaKind.EPISODE
    val actions = buildList {
        if (item.canResume) add(ItemAction.Resume to "Resume from ${formatDuration(item.positionMs)}")
        if (playable) add(ItemAction.PlayFromStart to if (item.canResume) "Play from the beginning" else "Play")
        add(ItemAction.Details to if (item.kind == MediaKind.EPISODE && item.seriesId != null) "Go to series" else "Details")
        if (item.canResume) add(ItemAction.RemoveFromContinue to "Remove from Continue watching")
        if (playable) add(ItemAction.SetPlayed(!item.played) to if (item.played) "Mark as unwatched" else "Mark as watched")
        // Episodes aren't favorited on their own; their series is.
        if (item.kind != MediaKind.EPISODE) {
            add(ItemAction.SetFavorite(!item.isFavorite) to if (item.isFavorite) "Remove from favorites" else "Add to favorites")
        }
    }
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.widthIn(min = 280.dp).padding(vertical = 16.dp)) {
                Text(
                    item.seriesName ?: item.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                actions.forEachIndexed { i, (action, label) ->
                    TextButton(
                        onClick = { onAction(action) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .then(if (i == 0) Modifier.focusRequester(first) else Modifier),
                    ) {
                        Text(label, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
        // TV: start on the first option so the remote works right away.
        LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    }
}
