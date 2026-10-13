package com.xfin.feature.player

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** [filter] is an encoded ChannelFilter defining the zapping lineup. */
@Serializable
data class PlayerRoute(val channelId: Long, val filter: String)

fun NavGraphBuilder.playerScreen(onBack: () -> Unit) {
    composable<PlayerRoute> {
        PlayerScreen(onBack = onBack)
    }
}
