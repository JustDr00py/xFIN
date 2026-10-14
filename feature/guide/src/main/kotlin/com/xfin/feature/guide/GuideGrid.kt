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
import com.xfin.core.model.Catchup
import com.xfin.core.model.Channel
import com.xfin.core.model.Program
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.Job
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.CompositionLocalProvider
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

    /** Like [bucket] for half viewports: rows compose less at a time but update twice as often. */
    val halfBucket: Int by derivedStateOf { if (viewport < 2) 0 else (offset / (viewport / 2f)).toInt() }

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
internal data class Cell(val start: Long, val end: Long, val program: Program?)

internal fun buildCells(programs: List<Program>, window: GuideWindow): List<Cell> {
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

/** Rows above the focused one on TV; the grid scrolls in whole rows to keep it there. */
private const val TV_PIVOT_ROW = 1

/** D-pad state for the TV grid: which row has focus and the time that up/down keeps. */
@Stable
private class TvGuideFocus(initialTime: Long) {
    var focusTime by mutableLongStateOf(initialTime)
    var row = 0
    var inChannelColumn = false

    /** The focused program's span, for paging through programs longer than the screen. */
    var cellStart = 0L
    var cellEnd = 0L

    /** The focused row's archive, if any: Left may only walk into the past where it can be played. */
    var catchup: Catchup? = null

    /** Set while up/down moves focus, so the time and horizontal position stay put. */
    var verticalMove = false
    val programRequesters = HashMap<Int, FocusRequester>()
    val channelRequesters = HashMap<Int, FocusRequester>()
}

@OptIn(ExperimentalFoundationApi::class)
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
    cachedPrograms: (Channel) -> List<Program>? = { null },
    prefetch: (List<Channel>) -> Unit = {},
    /** Changing this (e.g. the selected group) scrolls back to the first channel. */
    resetKey: Any? = null,
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val tv = if (isTv) remember { TvGuideFocus(now) } else null
    var scrollJob by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(resetKey) {
        listState.scrollToItem(0)
        tv?.row = 0
    }

    // Load the programs of the rows around the visible ones in batches, before they scroll in.
    LaunchedEffect(channels, listState) {
        snapshotFlow { listState.firstVisibleItemIndex to channels.itemCount }
            .collect { (first, count) ->
                if (count == 0) return@collect
                val from = (first - PREFETCH_BEHIND).coerceAtLeast(0)
                val to = (first + PREFETCH_AHEAD).coerceAtMost(count - 1)
                prefetch((from..to).mapNotNull { channels.peek(it) })
            }
    }

    /** Up/down on TV highlights whole channel rows; the timeline stays where it is. */
    fun moveRow(delta: Int): Boolean {
        val focus = tv ?: return false
        val target = focus.row + delta
        if (target < 0) return false // let focus leave to the bar above
        if (target >= channels.itemCount) return true
        focus.row = target
        scrollJob?.cancel()
        scrollJob = scope.launch {
            val requesters = focus.channelRequesters
            // Right from a row enters the program on now, or the first visible one.
            val visibleStart = timeline.timeAt(timeline.offset)
            val visibleEnd = timeline.timeAt(timeline.offset + timeline.viewport)
            focus.focusTime = if (now in visibleStart until visibleEnd) now else visibleStart
            if (requesters[target] == null) {
                // Not composed (e.g. far below after a jump): put it in place first.
                listState.scrollToItem((target - TV_PIVOT_ROW).coerceAtLeast(0))
                withFrameNanos { }
            }
            requesters[target]?.let { requester -> runCatching { requester.requestFocus() } }
        }
        return true
    }

    // The timeline scrolls itself (reveal); it must not chase focus, or on TV the platform's
    // default pivot spec drags it towards whatever gets focus, e.g. back hours to the channel column.
    /** Left/right inside a program that runs off screen pages the timeline instead of jumping past it. */
    fun pageWithinProgram(forward: Boolean): Boolean {
        val focus = tv ?: return false
        if (focus.inChannelColumn) return false
        val half = timeline.viewport / 2f
        val viewStart = timeline.timeAt(timeline.offset)
        val viewEnd = timeline.timeAt(timeline.offset + timeline.viewport)
        val target = when {
            forward && focus.cellEnd > viewEnd -> minOf(timeline.offset + half, timeline.x(focus.cellEnd) - timeline.viewport + half)
            !forward && focus.cellStart < viewStart -> maxOf(timeline.offset - half, timeline.x(focus.cellStart))
            else -> return false
        }
        scope.launch { timeline.animateTo(target) }
        return true
    }

    /**
     * Left from the program on now (or the oldest playable one) goes back to the row; past programs
     * are only visited on channels whose catch-up archive can play them.
     */
    fun leftToRow(): Boolean {
        val focus = tv ?: return false
        if (focus.inChannelColumn || focus.cellStart > now) return false
        val previousPlayable = focus.catchup?.covers(focus.cellStart - 1, now) == true
        if (previousPlayable) return false
        focus.channelRequesters[focus.row]?.let { runCatching { it.requestFocus() } }
        return true
    }

    Column(modifier.scrollable(
            state = timeline.scrollable,
            orientation = Orientation.Horizontal,
            overscrollEffect = null,
            enabled = true,
            reverseDirection = false,
            flingBehavior = null,
            interactionSource = null,
            bringIntoViewSpec = NoBringIntoView,
        )) {
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
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .then(
                    if (tv == null) {
                        Modifier
                    } else {
                        Modifier.onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (event.key) {
                                Key.DirectionDown -> moveRow(+1)
                                Key.DirectionUp -> moveRow(-1)
                                Key.DirectionRight -> pageWithinProgram(forward = true)
                                Key.DirectionLeft -> pageWithinProgram(forward = false) || leftToRow()
                                else -> false
                            }
                        }
                    },
                ),
        ) {
            val rowPx = with(LocalDensity.current) { metrics.rowHeight.toPx() }
            val pivotSpec = remember(rowPx) { PivotRowSpec(rowPx) }
            CompositionLocalProvider(LocalBringIntoViewSpec provides if (tv != null) pivotSpec else LocalBringIntoViewSpec.current) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(channels.itemCount, key = channels.itemKey { it.id }) { index ->
                    val channel = channels[index]
                    var rowFocused by remember { mutableStateOf(false) }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(metrics.rowHeight)
                            .then(if (rowFocused) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)) else Modifier),
                    ) {
                        if (channel == null) {
                            Box(Modifier.fillMaxSize().padding(4.dp).clip(MaterialTheme.shapes.small).shimmer())
                        } else {
                            val programRequester = remember { FocusRequester() }
                            val channelRequester = remember { FocusRequester() }
                            if (tv != null) {
                                DisposableEffect(index) {
                                    tv.programRequesters[index] = programRequester
                                    tv.channelRequesters[index] = channelRequester
                                    onDispose {
                                        if (tv.programRequesters[index] === programRequester) tv.programRequesters.remove(index)
                                        if (tv.channelRequesters[index] === channelRequester) tv.channelRequesters.remove(index)
                                    }
                                }
                            }
                            ChannelCell(
                                channel = channel,
                                metrics = metrics,
                                onClick = onChannelClick,
                                focused = rowFocused,
                                modifier = if (tv == null) {
                                    Modifier
                                } else {
                                    Modifier
                                        .focusRequester(channelRequester)
                                        .focusProperties { right = programRequester }
                                        .onFocusChanged {
                                            rowFocused = it.isFocused
                                            if (it.isFocused) {
                                                tv.row = index
                                                tv.inChannelColumn = true
                                            }
                                        }
                                },
                            )
                            ProgramRow(
                                channel = channel,
                                timeline = timeline,
                                isTv = isTv,
                                now = now,
                                guideVersion = guideVersion,
                                loadPrograms = loadPrograms,
                                cachedPrograms = cachedPrograms,
                                anchorTime = tv?.focusTime,
                                anchorRequester = if (tv != null) programRequester else null,
                                onFocused = { cell, x, w ->
                                    if (tv != null) {
                                        tv.row = index
                                        tv.inChannelColumn = false
                                        tv.cellStart = cell.start
                                        tv.cellEnd = cell.end
                                        tv.catchup = channel.catchup
                                        if (!tv.verticalMove) {
                                            // Left/right picks a new time: the visible part of this program.
                                            tv.focusTime = timeline.timeAt(timeline.offset).coerceIn(cell.start, cell.end - 1)
                                            scope.launch { timeline.reveal(x, w) }
                                        }
                                    }
                                },
                                onClick = { onProgramClick(channel, it) },
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                    }
                }
            }
            }
            NowLine(timeline, now, metrics.channelColumnWidth)
        }
    }

    if (tv != null) {
        LaunchedEffect(channels.itemCount > 0) {
            if (channels.itemCount > 0) {
                kotlinx.coroutines.delay(250)
                runCatching { tv.channelRequesters[listState.firstVisibleItemIndex]?.requestFocus() }
            }
        }
    }
}

/**
 * TV: whatever gains focus is scrolled so its row sits at [TV_PIVOT_ROW], aligned to row edges
 * (the list moves under a fixed focus position, like a TV guide). Rows have equal padding
 * above and below their cells, which is how the row's top is found from the focused cell.
 */
@OptIn(ExperimentalFoundationApi::class)
private class PivotRowSpec(private val rowPx: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val rowTop = offset - (rowPx - size) / 2f
        return rowTop - rowPx * TV_PIVOT_ROW
    }
}

@OptIn(ExperimentalFoundationApi::class)
private object NoBringIntoView : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}

private const val PREFETCH_BEHIND = 5
private const val PREFETCH_AHEAD = 20

@Composable
private fun ChannelCell(
    channel: Channel,
    metrics: GuideMetrics,
    onClick: (Channel) -> Unit,
    modifier: Modifier = Modifier,
    focused: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .width(metrics.channelColumnWidth)
            .fillMaxHeight()
            .padding(start = 8.dp, end = 4.dp, top = 3.dp, bottom = 3.dp)
            .clip(MaterialTheme.shapes.small)
            .background(if (focused) colors.primaryContainer else colors.surfaceContainerLow)
            .then(if (focused) Modifier.border(2.dp, colors.primary, MaterialTheme.shapes.small) else Modifier)
            .clickable(interactionSource = null, indication = if (focused) null else LocalIndication.current) { onClick(channel) }
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
    cachedPrograms: (Channel) -> List<Program>?,
    /** TV: the cell airing at this time gets [anchorRequester], the target of up/down. */
    anchorTime: Long?,
    anchorRequester: FocusRequester?,
    onFocused: (cell: Cell, x: Float, width: Float) -> Unit,
    onClick: (Program?) -> Unit,
    modifier: Modifier,
) {
    val programs by produceState(cachedPrograms(channel), channel.id, guideVersion) {
        value = cachedPrograms(channel) ?: loadPrograms(channel)
    }
    val cells = remember(programs) { programs?.let { buildCells(it, timeline.window) } }
    Box(modifier.clipToBounds()) {
        if (cells == null) return@Box
        val half = max(timeline.viewport, 2) / 2f
        val bucket = timeline.halfBucket
        // The view spans half-viewport buckets [bucket, bucket + 2]. On TV compose one more half
        // either side so left/right always has a neighbour to move to.
        val from = (if (isTv) bucket - 1 else bucket) * half
        val to = (if (isTv) bucket + 4 else bucket + 3) * half
        val anchor = if (anchorTime != null) cells.indexOfFirst { it.end > anchorTime } else -1
        cells.forEachIndexed { i, cell ->
            val x = timeline.x(cell.start)
            val w = timeline.x(cell.end) - x
            if ((x + w < from || x > to) && i != anchor) return@forEachIndexed
            androidx.compose.runtime.key(cell.start) {
                ProgramCell(
                    cell = cell,
                    x = x,
                    width = w,
                    timeline = timeline,
                    now = now,
                    isTv = isTv,
                    focusRequester = if (i == anchor) anchorRequester else null,
                    onFocused = { onFocused(cell, x, w) },
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
    isTv: Boolean,
    focusRequester: FocusRequester?,
    onFocused: () -> Unit,
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
    val progress = if (airing) program!!.progress(now) else 0f
    val elapsed = colors.primary.copy(alpha = 0.13f)
    val border = colors.primary
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
            // One draw pass for the card, the elapsed part and the focus ring (cheaper than
            // clip + background + border + a child box for every cell).
            .drawWithCache {
                val radius = CornerRadius(CELL_RADIUS.toPx())
                val ring = 2.dp.toPx()
                val path = Path().apply { addRoundRect(RoundRect(size.toRect(), radius)) }
                onDrawBehind {
                    drawRoundRect(background, cornerRadius = radius)
                    if (progress > 0f) {
                        clipPath(path) { drawRect(elapsed, size = Size(size.width * progress, size.height)) }
                    }
                    if (focused) {
                        drawRoundRect(
                            border,
                            topLeft = Offset(ring / 2, ring / 2),
                            size = Size(size.width - ring, size.height - ring),
                            cornerRadius = CornerRadius(radius.x - ring / 2),
                            style = Stroke(ring),
                        )
                    }
                }
            }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            // TV shows focus itself; skip the ripple there.
            .then(if (isTv) Modifier.clickable(interactionSource = null, indication = null, onClick = onClick) else Modifier.clickable(onClick = onClick)),
    ) {
        // Keep the title readable when the program started left of the visible area.
        Column(
            Modifier
                .offset {
                    val shift = (timeline.offset - x).coerceIn(0f, max(0f, width - MIN_TITLE_PX))
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

private val CELL_RADIUS = 10.dp

/** Narrowest the sliding title gets before it stops following the left edge. */
private const val MIN_TITLE_PX = 120f

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
    val day by remember { derivedStateOf { dayLabel(timeline.timeAt(timeline.offset)) } }
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

