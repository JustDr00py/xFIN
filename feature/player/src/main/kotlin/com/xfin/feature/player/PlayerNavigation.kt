package com.xfin.feature.player

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** [filter] is an encoded ChannelFilter defining the zapping lineup. */
@Serializable
data class PlayerRoute(val channelId: Long, val filter: String)

/** Movie or episode playback from a library source, starting at [startMs]. */
@Serializable
data class VodPlayerRoute(val sourceId: Long, val itemId: String, val startMs: Long, val mediaSourceId: String? = null)

fun NavGraphBuilder.playerScreen(onBack: () -> Unit, onPlayNext: (VodPlayerRoute) -> Unit = {}) {
    composable<PlayerRoute> {
        PlayerScreen(onBack = onBack)
    }
    composable<VodPlayerRoute> {
        VodPlayerScreen(onBack = onBack, onPlayNext = onPlayNext)
    }
}
