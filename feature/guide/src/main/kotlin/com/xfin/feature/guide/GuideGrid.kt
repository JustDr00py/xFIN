package com.xfin.feature.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.xfin.core.designsystem.component.ChannelLogo
import com.xfin.core.designsystem.component.formatTime
import com.xfin.core.designsystem.component.shimmer
import com.xfin.core.designsystem.theme.XfinColors
import com.xfin.core.model.Channel
import com.xfin.core.model.Program
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.roundToInt

/** Sizing of the timeline grid; phone and TV use different densities. */
data class GuideMetrics(
    val minuteWidth: Dp,
    val rowHeight: Dp,
    val channelColumnWidth: Dp,
    val showChannelNames: Boolean,
)

/** Horizontal position shared by the ruler, every row and the now-line. */
@Stable
class TimelineState(
    val window: GuideWindow,
    private val pxPerMs: Float,
    initialOffset: Float,
) {
    var offset by mutableFloatStateOf(initialOffset)
        private set
    var viewport by mutableIntStateOf(0)
    val totalWidth: Float = (window.end - window.start) * pxPerMs

    val scrollable: ScrollableState = ScrollableState { delta ->
        val old = offset
        offset = (offset - delta).coerceIn(0f, max(0f, totalWidth - viewport))
        old - offset
    }

    /** Changes only when the view moves a full viewport width, to recompose rows sparingly. */
    val bucket: Int by derivedStateOf { if (viewport == 0) 0 else (offset / viewport).toInt() }

    fun x(time: Long): Float = (time - window.start) * pxPerMs

    fun timeAt(x: Float): Long = window.start + (x / pxPerMs).toLong()

    suspend fun animateTo(target: Float) {
        scrollable.animateScrollBy(offset - target.coerceIn(0f, max(0f, totalWidth - viewport)))
    }

    /** TV focus: scroll only as much as needed for a cell to be (partly) visible. */
    suspend fun reveal(x: Float, width: Float) {
        val end = offset + viewport
        val visibleWidth = minOf(width, viewport * 0.8f)
        when {
            x + width <= offset + 1 -> animateTo(x)
            x >= end - 1 -> animateTo(x + visibleWidth - viewport)
            x > offset && x + visibleWidth > end -> animateTo(x + visibleWidth - viewport)
        }
    }
}

@Composable
fun rememberTimelineState(window: GuideWindow, minuteWidth: Dp, now: Long): TimelineState {
    val pxPerMs = with(LocalDensity.current) { minuteWidth.toPx() } / 60_000f
    return remember(window, pxPerMs) {
        TimelineState(window, pxPerMs, initialOffset = ((now - TimeUnit.MINUTES.toMillis(30)) - window.start) * pxPerMs)
    }
}

/** A program or an empty stretch of the timeline. */
private data class Cell(val start: Long, val end: Long, val program: Program?)

private fun buildCells(programs: List<Program>, window: GuideWindow): List<Cell> {
    val cells = ArrayList<Cell>(programs.size + 4)
    var cursor = window.start
    for (p in programs) {
        val start = max(p.start, window.start)
        if (start > cursor) cells += Cell(cursor, start, null)
        if (p.end > start && p.end > cursor) {
            cells += Cell(max(start, cursor), minOf(p.end, window.end), p)
            cursor = minOf(p.end, window.end)
        }
    }
    if (cursor < window.end) cells += Cell(cursor, window.end, null)
    return cells
}

@Composable
fun GuideGrid(
    channels: LazyPagingItems<Channel>,
    timeline: TimelineState,
    metrics: GuideMetrics,
    now: Long,
    guideVersion: Int,
    isTv: Boolean,
    loadPrograms: suspend (Channel) -> List<Program>,
    onChannelClick: (Channel) -> Unit,
    onProgramClick: (Channel, Program?) -> Unit,
    modifier: Modifier = Modifier,
    /** Changing this (e.g. the selected group) scrolls back to the first channel. */
    resetKey: Any? = null,
) {
    val scope = rememberCoroutineScope()
    val firstFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    LaunchedEffect(resetKey) { listState.scrollToItem(0) }

    Column(modifier.scrollable(timeline.scrollable, Orientation.Horizontal)) {
        Row(Modifier.fillMaxWidth().height(36.dp)) {
            Box(Modifier.width(metrics.channelColumnWidth).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                DayLabel(timeline, Modifier.padding(start = 12.dp))
            }
            TimeRuler(
                timeline,
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .onSizeChanged { timeline.viewport = it.width },
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(channels.itemCount, key = channels.itemKey { it.id }) { index ->
                    val channel = channels[index]
                    Row(Modifier.fillMaxWidth().height(metrics.rowHeight)) {
                        if (channel == null) {
                            Box(Modifier.fillMaxSize().padding(4.dp).clip(MaterialTheme.shapes.small).shimmer())
                        } else {
                            ChannelCell(channel, metrics, onChannelClick)
                            ProgramRow(
                                channel = channel,
                                timeline = timeline,
                                isTv = isTv,
                                now = now,
                                guideVersion = guideVersion,
                                loadPrograms = loadPrograms,
                                firstFocus = if (index == 0 && isTv) firstFocus else null,
                                onFocused = { x, w -> scope.launch { timeline.reveal(x, w) } },
                                onClick = { onProgramClick(channel, it) },
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                    }
                }
            }
            NowLine(timeline, now, metrics.channelColumnWidth)
        }
    }

    if (isTv) {
        LaunchedEffect(channels.itemCount > 0) {
            if (channels.itemCount > 0) {
                kotlinx.coroutines.delay(250)
                runCatching { firstFocus.requestFocus() }
            }
        }
    }
}

@Composable
private fun ChannelCell(channel: Channel, metrics: GuideMetrics, onClick: (Channel) -> Unit) {
    Row(
        modifier = Modifier
            .width(metrics.channelColumnWidth)
            .fillMaxHeight()
            .padding(start = 8.dp, end = 4.dp, top = 3.dp, bottom = 3.dp)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable { onClick(channel) }
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelLogo(
            channel.name,
            channel.logoUrl,
            Modifier.size(width = if (metrics.showChannelNames) 64.dp else 56.dp, height = metrics.rowHeight - 22.dp),
        )
        if (metrics.showChannelNames) {
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                channel.number?.let {
                    Text(it.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(channel.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ProgramRow(
    channel: Channel,
    timeline: TimelineState,
    isTv: Boolean,
    now: Long,
    guideVersion: Int,
    loadPrograms: suspend (Channel) -> List<Program>,
    firstFocus: FocusRequester?,
    onFocused: (x: Float, width: Float) -> Unit,
    onClick: (Program?) -> Unit,
    modifier: Modifier,
) {
    val programs by produceState<List<Program>?>(null, channel.id, guideVersion) { value = loadPrograms(channel) }
    val cells = remember(programs) { programs?.let { buildCells(it, timeline.window) } }
    Box(modifier.clipToBounds()) {
        if (cells == null) return@Box
        val bucket = timeline.bucket
        val vp = max(timeline.viewport, 1)
        // The view spans buckets [bucket, bucket + 2). On TV compose one extra viewport either side
        // so D-pad focus can always find a neighbour; touch needs only what can be visible.
        val from = (if (isTv) bucket - 1 else bucket) * vp.toFloat()
        val to = (if (isTv) bucket + 3 else bucket + 2) * vp.toFloat()
        var focusAssigned = false
        for (cell in cells) {
            val x = timeline.x(cell.start)
            val w = timeline.x(cell.end) - x
            if (x + w < from || x > to) continue
            val requester = if (firstFocus != null && !focusAssigned && (cell.end > now)) {
                focusAssigned = true
                firstFocus
            } else {
                null
            }
            androidx.compose.runtime.key(cell.start) {
                ProgramCell(
                    cell = cell,
                    x = x,
                    width = w,
                    timeline = timeline,
                    now = now,
                    focusRequester = requester,
                    onFocused = onFocused,
                    onClick = { onClick(cell.program) },
                )
            }
        }
    }
}

@Composable
private fun ProgramCell(
    cell: Cell,
    x: Float,
    width: Float,
    timeline: TimelineState,
    now: Long,
    focusRequester: FocusRequester?,
    onFocused: (Float, Float) -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val program = cell.program
    val airing = program?.isAiring(now) == true
    val past = cell.end <= now
    val colors = MaterialTheme.colorScheme
    val background = when {
        focused -> colors.primaryContainer
        airing -> colors.surfaceContainerHighest
        program == null -> colors.surfaceContainerLowest
        else -> colors.surfaceContainerLow
    }
    Box(
        modifier = Modifier
            // Measured at its true width (can be far wider than the row) and placed on the shared timeline;
            // only the placement reads the scroll offset, so scrolling never recomposes.
            .layout { measurable, constraints ->
                val placeable = measurable.measure(Constraints.fixed(width.roundToInt().coerceAtLeast(1), constraints.maxHeight))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.place((x - timeline.offset).roundToInt(), 0)
                }
            }
            .padding(horizontal = 1.5.dp, vertical = 3.dp)
            .clip(MaterialTheme.shapes.small)
            .background(background)
            .then(if (focused) Modifier.border(2.dp, colors.primary, MaterialTheme.shapes.small) else Modifier)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused(x, width)
            }
            .clickable(onClick = onClick),
    ) {
        if (airing) {
            // Elapsed part of the airing program.
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(program!!.progress(now))
                    .background(colors.primary.copy(alpha = 0.13f)),
            )
        }
        // Keep the title readable when the program started left of the visible area.
        Column(
            Modifier
                .offset {
                    val shift = (timeline.offset - x).coerceIn(0f, max(0f, width - 120f))
                    IntOffset(shift.roundToInt(), 0)
                }
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            val alpha = if (past && !focused) 0.5f else 1f
            Text(
                program?.title ?: "No information",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (airing) FontWeight.SemiBold else FontWeight.Normal,
                color = colors.onSurface.copy(alpha = if (program == null) 0.35f else alpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (program != null) {
                Text(
                    formatTime(program.start),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant.copy(alpha = alpha),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun TimeRuler(timeline: TimelineState, modifier: Modifier) {
    val halfHour = TimeUnit.MINUTES.toMillis(30)
    Box(modifier.clipToBounds()) {
        val vp = max(timeline.viewport, 1)
        val bucket = timeline.bucket
        val from = timeline.timeAt((bucket - 1) * vp.toFloat()) / halfHour * halfHour
        val to = timeline.timeAt((bucket + 3) * vp.toFloat())
        var t = max(from, timeline.window.start)
        while (t <= to && t <= timeline.window.end) {
            val x = timeline.x(t)
            val label = formatTime(t)
            androidx.compose.runtime.key(t) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset { IntOffset((x - timeline.offset).roundToInt() + 8, 0) },
                )
            }
            t += halfHour
        }
    }
}

@Composable
private fun DayLabel(timeline: TimelineState, modifier: Modifier) {
    val day by remember { derivedStateOf { dayLabel(timeline.timeAt(timeline.offset + timeline.viewport / 2f)) } }
    Text(
        day,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        modifier = modifier,
    )
}

internal fun dayLabel(time: Long): String {
    val target = Calendar.getInstance().apply { timeInMillis = time }
    val today = Calendar.getInstance()
    val diff = target.get(Calendar.DAY_OF_YEAR) - today.get(Calendar.DAY_OF_YEAR) +
        (target.get(Calendar.YEAR) - today.get(Calendar.YEAR)) * 365
    return when (diff) {
        0 -> "Today"
        1 -> "Tomorrow"
        -1 -> "Yesterday"
        else -> java.text.SimpleDateFormat("EEE d", java.util.Locale.getDefault()).format(target.time)
    }
}

@Composable
private fun NowLine(timeline: TimelineState, now: Long, channelColumn: Dp) {
    val columnPx = with(LocalDensity.current) { channelColumn.toPx() }
    val x = timeline.x(now)
    Box(
        Modifier
            .offset { IntOffset((columnPx + x - timeline.offset).roundToInt(), 0) }
            .width(2.dp)
            .fillMaxHeight()
            .then(
                if (x < timeline.offset || x > timeline.offset + timeline.viewport) {
                    Modifier
                } else {
                    Modifier.background(XfinColors.Live.copy(alpha = 0.85f))
                },
            ),
    )
}

