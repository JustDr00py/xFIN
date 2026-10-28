package com.xfin.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.height
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.xfin.core.designsystem.component.rememberNowPlaying
import com.xfin.core.model.ProgramSlot
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xfin.core.designsystem.component.ChannelLogo
import com.xfin.core.designsystem.component.ProgressTrack
import com.xfin.core.model.Channel
import com.xfin.core.model.ChannelFilter
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/** UHF-style channel strip over the video: browse what's on and switch without leaving playback. */
@Composable
internal fun MiniGuide(
    lineup: LazyPagingItems<Channel>,
    current: Int,
    now: Long,
    guideVersion: Int,
    nowPlaying: suspend (Channel, Long) -> ProgramSlot?,
    isTv: Boolean,
    onSelect: (Int) -> Unit,
    /** TV: Left in the list opens the categories column. */
    onLeft: () -> Unit = {},
    /** Focus came (back) into the list, e.g. Right out of the categories column. */
    onFocusEnter: () -> Unit = {},
    /** Set when focus should come back to the list (e.g. after picking a category). */
    focusKey: Any? = null,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (current - 2).coerceAtLeast(0))
    val focus = remember { FocusRequester() }
    // Another category has no current row; its first channel takes focus instead.
    val focusRow = if (current >= 0) current else 0
    LazyColumn(
        state = listState,
        modifier = Modifier
            .onPreviewKeyEvent { event ->
                if (isTv && event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) {
                    onLeft()
                    true
                } else {
                    false
                }
            }
            .onFocusChanged { if (it.hasFocus) onFocusEnter() }
            .fillMaxHeight()
            .width(if (isTv) 440.dp else 360.dp)
            .background(Brush.horizontalGradient(listOf(Color(0xF5090B10), Color(0xE6090B10))))
            .safeDrawingPadding(),
        contentPadding = PaddingValues(vertical = 20.dp, horizontal = 12.dp),
    ) {
        items(lineup.itemCount, key = lineup.itemKey { it.id }) { index ->
            val channel = lineup[index]
            if (channel == null) {
                Spacer(Modifier.fillMaxWidth().height(56.dp))
                return@items
            }
            var focused by remember { mutableStateOf(false) }
            val selected = index == current
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(
                        when {
                            focused -> MaterialTheme.colorScheme.primaryContainer
                            selected -> Color.White.copy(alpha = 0.10f)
                            else -> Color.Transparent
                        },
                    )
                    .then(if (focused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium) else Modifier)
                    .then(if (index == focusRow) Modifier.focusRequester(focus) else Modifier)
                    .onFocusChanged { focused = it.isFocused }
                    .clickable { onSelect(index) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    channel.number?.toString() ?: (index + 1).toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.55f),
                    modifier = Modifier.width(36.dp),
                    maxLines = 1,
                )
                ChannelLogo(channel.name, channel.logoUrl, Modifier.size(width = 60.dp, height = 40.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        channel.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val playing = rememberNowPlaying(channel, now, guideVersion, nowPlaying)
                    if (playing != null) {
                        Text(
                            playing.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.65f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        ProgressTrack(playing.progress(now), Modifier.padding(top = 5.dp).fillMaxWidth(0.7f))
                    }
                }
            }
        }
    }
    // After picking a category the list briefly still holds the previous one's channels; when the new
    // ones replace them the focused row goes away and focus with it, so focus again once they arrive.
    val firstKey = if (lineup.itemCount > 0) lineup.peek(0)?.id else null
    LaunchedEffect(focusKey, firstKey) {
        if (isTv && firstKey != null) {
            listState.scrollToItem((focusRow - 2).coerceAtLeast(0))
            withFrameNanos { }
            runCatching { focus.requestFocus() }
        }
    }
}

/** The categories beside the mini guide: pick one to browse its channels. */
@Composable
internal fun CategoryColumn(
    categories: List<PlayerCategory>,
    selected: ChannelFilter,
    onSelect: (ChannelFilter) -> Unit,
) {
    val focus = remember { FocusRequester() }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (categories.indexOfFirst { it.filter == selected } - 2).coerceAtLeast(0),
    )
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxHeight()
            .width(300.dp)
            .background(Color(0xFA06080C))
            .safeDrawingPadding(),
        contentPadding = PaddingValues(vertical = 20.dp, horizontal = 12.dp),
    ) {
        item { PanelHeader("Categories") }
        items(categories, key = { it.filter.toString() }) { category ->
            PanelItem(
                label = category.label,
                selected = category.filter == selected,
                onClick = { onSelect(category.filter) },
                modifier = if (category.filter == selected) Modifier.focusRequester(focus) else Modifier,
            )
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}
