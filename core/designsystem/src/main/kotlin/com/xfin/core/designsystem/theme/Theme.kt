package com.xfin.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val colors = darkColorScheme(
    primary = XfinPalette.Accent,
    onPrimary = XfinPalette.Ink,
    primaryContainer = XfinPalette.AccentDeep,
    onPrimaryContainer = XfinPalette.Snow,
    secondary = XfinPalette.Mist,
    onSecondary = XfinPalette.Ink,
    secondaryContainer = XfinPalette.SlateHighest,
    onSecondaryContainer = XfinPalette.Snow,
    tertiary = XfinPalette.Gold,
    background = XfinPalette.Night,
    onBackground = XfinPalette.Snow,
    surface = XfinPalette.Night,
    onSurface = XfinPalette.Snow,
    surfaceVariant = XfinPalette.SlateHigh,
    onSurfaceVariant = XfinPalette.Mist,
    surfaceContainerLowest = XfinPalette.Ink,
    surfaceContainerLow = XfinPalette.Slate,
    surfaceContainer = XfinPalette.Slate,
    surfaceContainerHigh = XfinPalette.SlateHigh,
    surfaceContainerHighest = XfinPalette.SlateHighest,
    outline = XfinPalette.Outline,
    outlineVariant = XfinPalette.SlateHighest,
    error = XfinPalette.Live,
)

private val base = Typography()

private val typography = Typography(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val tvColors = androidx.tv.material3.darkColorScheme(
    primary = XfinPalette.Accent,
    onPrimary = XfinPalette.Ink,
    primaryContainer = XfinPalette.AccentDeep,
    onPrimaryContainer = XfinPalette.Snow,
    background = XfinPalette.Night,
    onBackground = XfinPalette.Snow,
    surface = XfinPalette.Slate,
    onSurface = XfinPalette.Snow,
    surfaceVariant = XfinPalette.SlateHigh,
    onSurfaceVariant = XfinPalette.Mist,
    border = XfinPalette.Outline,
)

@Composable
fun XfinTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, shapes = shapes) {
        androidx.tv.material3.MaterialTheme(colorScheme = tvColors, content = content)
    }
}
