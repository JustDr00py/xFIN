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

/** Up to four live channels at once, starting with [channelId]. */
@Serializable
data class MultiviewRoute(val channelId: Long, val filter: String)

fun NavGraphBuilder.playerScreen(
    onBack: () -> Unit,
    onPlayNext: (VodPlayerRoute) -> Unit = {},
    onOpenMultiview: (MultiviewRoute) -> Unit = {},
    onOpenChannel: (PlayerRoute) -> Unit = {},
) {
    composable<PlayerRoute> {
        PlayerScreen(onBack = onBack, onOpenMultiview = onOpenMultiview)
    }
    composable<MultiviewRoute> {
        MultiviewScreen(onBack = onBack, onOpenChannel = onOpenChannel)
    }
    composable<VodPlayerRoute> {
        VodPlayerScreen(onBack = onBack, onPlayNext = onPlayNext)
    }
}
