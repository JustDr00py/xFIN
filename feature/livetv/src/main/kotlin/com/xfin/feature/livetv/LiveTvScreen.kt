package com.xfin.feature.livetv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.xfin.core.designsystem.component.LocalIsTv
import com.xfin.core.model.ChannelFilter

@Composable
internal fun LiveTvScreen(
    onPlay: (channelId: Long, filter: ChannelFilter) -> Unit,
    viewModel: LiveTvViewModel = hiltViewModel(),
) {
    val tabs by viewModel.tabs.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val refresh by viewModel.refreshState.collectAsStateWithLifecycle()
    val channels = viewModel.channelPages.collectAsLazyPagingItems()
    val now by viewModel.now.collectAsStateWithLifecycle()

    val state = LiveTvState(
        tabs = tabs,
        selected = filter,
        query = query,
        refresh = refresh,
        channels = channels,
        now = now,
        onSelect = viewModel::select,
        onQuery = viewModel::search,
        onPlay = { onPlay(it.id, viewModel.playbackFilter()) },
        onToggleFavorite = viewModel::toggleFavorite,
    )
    if (LocalIsTv.current) TvLiveTv(state) else PhoneLiveTv(state)
}

internal class LiveTvState(
    val tabs: List<FilterTab>,
    val selected: ChannelFilter?,
    val query: String,
    val refresh: com.xfin.core.model.RefreshState,
    val channels: androidx.paging.compose.LazyPagingItems<com.xfin.core.model.Channel>,
    val now: Long,
    val onSelect: (ChannelFilter) -> Unit,
    val onQuery: (String) -> Unit,
    val onPlay: (com.xfin.core.model.Channel) -> Unit,
    val onToggleFavorite: (com.xfin.core.model.Channel) -> Unit,
)
