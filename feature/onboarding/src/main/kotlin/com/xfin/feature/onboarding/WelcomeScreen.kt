package com.xfin.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.xfin.core.designsystem.component.FocusSurface
import com.xfin.core.designsystem.component.LocalIsTv

@Composable
internal fun WelcomeScreen(onAddSource: (SourceKind) -> Unit) {
    val isTv = LocalIsTv.current
    val firstFocus = remember { FocusRequester() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    listOf(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f), Color.Transparent),
                    radius = 1400f,
                ),
            )
            .safeDrawingPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = if (isTv) 960.dp else 520.dp)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("x") }
                    append("FIN")
                },
                style = MaterialTheme.typography.displaySmall,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "All your TV, in one beautiful place.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(40.dp))
            val options: @Composable (Modifier) -> Unit = { itemModifier ->
                SourceOption(
                    icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                    title = "M3U playlist",
                    subtitle = "Paste a playlist URL or pick a file",
                    onClick = { onAddSource(SourceKind.M3U) },
                    modifier = itemModifier.focusRequester(firstFocus),
                )
                SourceOption(
                    icon = Icons.Rounded.Dns,
                    title = "Xtream Codes",
                    subtitle = "Sign in with server, username and password",
                    onClick = { onAddSource(SourceKind.XTREAM) },
                    modifier = itemModifier,
                )
                SourceOption(
                    icon = Icons.Rounded.VideoLibrary,
                    title = "Jellyfin",
                    subtitle = "Your movies and shows, with resume",
                    onClick = { onAddSource(SourceKind.JELLYFIN) },
                    modifier = itemModifier,
                )
            }
            if (isTv) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) { options(Modifier.weight(1f)) }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { options(Modifier.fillMaxWidth()) }
            }
        }
    }
    LaunchedEffect(isTv) { if (isTv) runCatching { firstFocus.requestFocus() } }
}

@Composable
private fun SourceOption(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FocusSurface(onClick = { if (enabled) onClick() }, modifier = modifier, focusedScale = 1.04f) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 0.16f else 0.06f),
                        MaterialTheme.shapes.small,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.4f),
                )
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.5f),
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
