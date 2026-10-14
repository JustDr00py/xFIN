package com.xfin.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import com.xfin.core.designsystem.component.ChannelLogo
import com.xfin.core.designsystem.component.FocusSurface
import com.xfin.core.designsystem.component.LocalIsTv
import com.xfin.core.designsystem.component.formatTime
import com.xfin.core.designsystem.component.rememberLogoTint
import com.xfin.core.model.Channel
import com.xfin.core.model.ChannelFilter
import com.xfin.core.model.NowNext
import com.xfin.data.sources.ChannelRepository
import com.xfin.data.sources.EpgRepository
import com.xfin.data.sources.minuteTicks
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class RecentChannelsViewModel @Inject constructor(
    channels: ChannelRepository,
    private val epg: EpgRepository,
) : ViewModel() {
    val recent = channels.pagedChannels(ChannelFilter.Recent).cachedIn(viewModelScope)

    val now: StateFlow<Long> = minuteTicks().stateIn(viewModelScope, SharingStarted.Eagerly, System.currentTimeMillis())

    suspend fun nowNext(channel: Channel, now: Long): NowNext = epg.nowNext(channel, now)
}

private const val ROWS = 4

/**
 * Home's "TV Guide": the last few channels watched, each with what's on now and next, like UHF.
 * Hidden until a channel has been watched.
 */
@Composable
internal fun RecentChannelsSection(
    gutter: Dp,
    onPlayChannel: (Long) -> Unit,
    onViewAll: () -> Unit,
    /** TV: where Up from the first row goes (the banner's More info). */
    upTarget: FocusRequester? = null,
    viewModel: RecentChannelsViewModel = hiltViewModel(),
) {
    val viewAll = remember { FocusRequester() }
    val channels = viewModel.recent.collectAsLazyPagingItems()
    val now by viewModel.now.collectAsStateWithLifecycle()
    if (channels.itemCount == 0) return
    val isTv = LocalIsTv.current
    val rowHeight = if (isTv) 84.dp else 68.dp

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = gutter, end = gutter - 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("TV Guide", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = onViewAll, modifier = Modifier.focusRequester(viewAll)) { Text("View all") }
        }
        for (i in 0 until minOf(ROWS, channels.itemCount)) {
            val channel = channels[i] ?: continue
            val nowNext by produceState(NowNext(null, null), channel.id, now) { value = viewModel.nowNext(channel, now) }
            ChannelNowRow(
                channel = channel,
                nowNext = nowNext,
                now = now,
                height = rowHeight,
                onClick = { onPlayChannel(channel.id) },
                modifier = Modifier
                    .padding(horizontal = gutter)
                    // TV: Up from the first row returns to the banner; Right reaches View all.
                    .focusProperties {
                        if (i == 0 && upTarget != null) up = upTarget
                        right = viewAll
                    },
            )
        }
    }
}

@Composable
private fun ChannelNowRow(
    channel: Channel,
    nowNext: NowNext,
    now: Long,
    height: Dp,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val tint = rememberLogoTint(channel.logoUrl)
    val colors = MaterialTheme.colorScheme
    FocusSurface(onClick = onClick, focusedScale = 1.02f, modifier = modifier.fillMaxWidth().height(height)) {
        Row(Modifier.fillMaxWidth().fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ChannelLogo(channel.name, channel.logoUrl, Modifier.width(height * 1.3f).fillMaxHeight())
            val current = nowNext.now
            ProgramBox(
                label = listOfNotNull(channel.name, current?.let { formatTime(it.start) }).joinToString(" · "),
                title = current?.title ?: "No information",
                background = (tint?.copy(alpha = 0.34f) ?: Color.Transparent).compositeOver(colors.surfaceContainerHighest),
                progress = current?.progress(now) ?: 0f,
                elapsed = (tint ?: colors.primary).copy(alpha = 0.22f),
                bold = true,
                modifier = Modifier.weight(1.7f),
            )
            nowNext.next?.let { next ->
                ProgramBox(
                    label = formatTime(next.start),
                    title = next.title,
                    background = (tint?.copy(alpha = 0.14f) ?: Color.Transparent).compositeOver(colors.surfaceContainerLow),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ProgramBox(
    label: String,
    title: String,
    background: Color,
    modifier: Modifier,
    progress: Float = 0f,
    elapsed: Color = Color.Transparent,
    bold: Boolean = false,
) {
    Box(
        modifier
            .fillMaxHeight()
            .clip(MaterialTheme.shapes.small)
            .drawBehind {
                drawRect(background)
                if (progress > 0f) drawRect(elapsed, size = Size(size.width * progress, size.height))
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
