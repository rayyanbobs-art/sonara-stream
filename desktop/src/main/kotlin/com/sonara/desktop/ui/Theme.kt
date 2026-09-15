package com.sonara.desktop.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object SonaraTheme {
    val Background = Color(0xFF0D0D12)
    val Surface = Color(0xFF14141D)
    val CardSurface = Color(0xFF1B1B26)
    val CardBorder = Color(0xFF2A2A3A)
    val ElevatedSurface = Color(0xFF232330)

    val Primary = Color(0xFF7C5CFF)       // Sonara Neon Violet
    val Secondary = Color(0xFF00E5FF)     // Electric Cyan
    val Tertiary = Color(0xFFFF3366)      // Vibrant Coral
    val Success = Color(0xFF00E676)       // Vibrant Green

    val TextPrimary = Color(0xFFF6F6F9)
    val TextSecondary = Color(0xFFA0A0B2)
    val TextMuted = Color(0xFF6E6E82)

    val DarkColorScheme = darkColorScheme(
        primary = Primary,
        secondary = Secondary,
        tertiary = Tertiary,
        background = Background,
        surface = Surface,
        surfaceVariant = CardSurface,
        onBackground = TextPrimary,
        onSurface = TextPrimary,
        onSurfaceVariant = TextSecondary,
    )
}

@Composable
fun SonaraStreamTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SonaraTheme.DarkColorScheme,
        content = content
    )
}
