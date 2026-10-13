package com.xfin.feature.guide

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.xfin.core.model.ChannelFilter
import kotlinx.serialization.Serializable

@Serializable
data object GuideRoute

fun NavGraphBuilder.guideScreen(
    onPlay: (channelId: Long, filter: ChannelFilter) -> Unit,
    onOpenSettings: () -> Unit,
    shell: @Composable (content: @Composable () -> Unit) -> Unit,
) {
    composable<GuideRoute> {
        shell { GuideScreen(onPlay = onPlay, onOpenSettings = onOpenSettings) }
    }
}
