package com.xfin.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer

/**
 * An icon button for screen headers (search, sort, settings). On TV the stock focus tint is too
 * faint to see from the couch, so the focused button turns into a solid light circle with a dark
 * icon; elsewhere it is a plain [IconButton].
 */
@Composable
fun HeaderIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (!LocalIsTv.current) {
        IconButton(onClick = onClick, modifier = modifier, content = content)
        return
    }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.12f else 1f, label = "headerIconFocus")
    val colors = MaterialTheme.colorScheme
    IconButton(
        onClick = onClick,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (focused) colors.onSurface else Color.Transparent,
            contentColor = if (focused) colors.surface else LocalContentColor.current,
        ),
        interactionSource = interaction,
        content = content,
    )
}
