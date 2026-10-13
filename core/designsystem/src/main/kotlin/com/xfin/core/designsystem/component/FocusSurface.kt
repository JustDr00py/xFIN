package com.xfin.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface

/**
 * D-pad friendly clickable surface in the xFIN style: lifts, brightens and gains an accent
 * outline when focused. On touch devices it is a plain clickable tile (tv-material surfaces
 * only react to key events).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FocusSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    selected: Boolean = false,
    shape: Shape = MaterialTheme.shapes.medium,
    focusedScale: Float = 1.06f,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    if (!LocalIsTv.current) {
        Box(
            modifier = modifier
                .clip(shape)
                .background(if (selected) colors.primaryContainer else colors.surfaceContainerLow)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
            content = content,
        )
        return
    }
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = focusedScale),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) colors.primaryContainer else colors.surfaceContainerLow,
            contentColor = colors.onSurface,
            focusedContainerColor = colors.surfaceContainerHighest,
            focusedContentColor = colors.onSurface,
            pressedContainerColor = colors.surfaceContainerHigh,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, colors.primary), inset = 0.dp, shape = shape),
            border = Border(BorderStroke(0.dp, Color.Transparent), shape = shape),
        ),
        content = content,
    )
}
