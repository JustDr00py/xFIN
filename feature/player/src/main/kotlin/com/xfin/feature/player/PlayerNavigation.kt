package com.xfin.feature.player

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** [filter] is an encoded ChannelFilter defining the zapping lineup. */
@Serializable
data class PlayerRoute(
    val channelId: Long,
    val filter: String,
    /** When set, start in catch-up: the archive of [catchupStart]..[catchupEnd] (epoch ms). */
    val catchupStart: Long = 0,
    val catchupEnd: Long = 0,
    val catchupTitle: String? = null,
)

/** Movie or episode playback from a library source, starting at [startMs]. */
@Serializable
data class VodPlayerRoute(val sourceId: Long, val itemId: String, val startMs: Long, val mediaSourceId: String? = null)

/** Playback of [itemId] failed with [failedVersionId]; offer its other versions, starting at [startMs]. */
data class VersionRetry(val sourceId: Long, val itemId: String, val failedVersionId: String, val startMs: Long)

/** Up to four live channels at once, starting with [channelId]. */
@Serializable
data class MultiviewRoute(val channelId: Long, val filter: String)

fun NavGraphBuilder.playerScreen(
    onBack: () -> Unit,
    onPlayNext: (VodPlayerRoute) -> Unit = {},
    onTryAnotherVersion: (VersionRetry) -> Unit = {},
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
        VodPlayerScreen(onBack = onBack, onPlayNext = onPlayNext, onTryAnotherVersion = onTryAnotherVersion)
    }
}
