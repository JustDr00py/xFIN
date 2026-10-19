package com.xfin.core.designsystem.theme

import androidx.compose.ui.graphics.Color

internal object XfinPalette {
    val Ink = Color(0xFF07090D)
    val Night = Color(0xFF0D1017)
    val Slate = Color(0xFF151A23)
    val SlateHigh = Color(0xFF1D2330)
    val SlateHighest = Color(0xFF262D3C)
    val Outline = Color(0xFF343C4E)
    val Mist = Color(0xFF9AA3B5)
    val Snow = Color(0xFFF2F4F8)

    val Accent = Color(0xFF7B93FF)
    val AccentDeep = Color(0xFF2B3A80)
    val Live = Color(0xFFFF4D5E)
    val Gold = Color(0xFFFFC857)
}

/** Semantic colors outside the Material scheme. */
object XfinColors {
    val Live = XfinPalette.Live
    val Favorite = XfinPalette.Gold
    val Watched = Color(0xFF3DD68C)
    val Scrim = Color(0xCC000000)
    val ScrimLight = Color(0x66000000)
}
