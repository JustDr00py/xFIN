package com.xfin.feature.guide

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.xfin.core.model.ChannelFilter
import kotlinx.serialization.Serializable

/** The guide for one category; [filter] is an encoded ChannelFilter (null: the last one used). */
@Serializable
data class GuideRoute(val filter: String? = null)

fun NavGraphBuilder.guideScreen(
    onPlay: (channelId: Long, filter: ChannelFilter) -> Unit,
    onOpenSettings: () -> Unit,
    onPlayArchive: (channelId: Long, filter: ChannelFilter, start: Long, end: Long, title: String) -> Unit,
    onBack: () -> Unit,
    shell: @Composable (content: @Composable () -> Unit) -> Unit,
) {
    composable<GuideRoute> {
        shell { GuideScreen(onPlay = onPlay, onOpenSettings = onOpenSettings, onPlayArchive = onPlayArchive, onBack = onBack) }
    }
}
