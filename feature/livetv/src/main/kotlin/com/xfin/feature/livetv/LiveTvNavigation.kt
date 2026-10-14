package com.xfin.feature.livetv

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.xfin.core.model.ChannelFilter
import kotlinx.serialization.Serializable

@Serializable
data object LiveTvRoute

fun NavGraphBuilder.liveTvScreen(
    onOpenCategory: (ChannelFilter) -> Unit,
    onSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    shell: @Composable (content: @Composable () -> Unit) -> Unit,
) {
    composable<LiveTvRoute> {
        shell { LiveTvScreen(onOpenCategory = onOpenCategory, onSearch = onSearch, onOpenSettings = onOpenSettings) }
    }
}
