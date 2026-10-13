package com.xfin.feature.livetv

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.xfin.core.model.ChannelFilter
import kotlinx.serialization.Serializable

@Serializable
data object LiveTvRoute

fun NavGraphBuilder.liveTvScreen(
    onPlay: (channelId: Long, filter: ChannelFilter) -> Unit,
    onSearch: () -> Unit,
    shell: @Composable (content: @Composable () -> Unit) -> Unit,
) {
    composable<LiveTvRoute> {
        shell { LiveTvScreen(onPlay = onPlay, onSearch = onSearch) }
    }
}
