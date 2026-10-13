package com.xfin.core.designsystem.component

import androidx.compose.runtime.staticCompositionLocalOf

/** True when running on a leanback (TV) device: D-pad navigation, 10-foot UI. */
val LocalIsTv = staticCompositionLocalOf { false }

/** Current window width in dp (multi-window and foldable aware). */
@androidx.compose.runtime.Composable
fun windowWidthDp(): Int {
    val px = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.width
    return with(androidx.compose.ui.platform.LocalDensity.current) { px.toDp().value.toInt() }
}
