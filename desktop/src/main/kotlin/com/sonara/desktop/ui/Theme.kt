package com.sonara.desktop.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object SonaraTokens {
    // Surfaces — warm near-black, olive cast. Not pure #000/#111.
    val Bg = Color(0xFF14170F)
    val Surface = Color(0xFF1E2318)
    val SurfaceRaised = Color(0xFF262B1F)
    val SurfaceChip = Color(0xFF333829)

    // Accent — moss/sage green. This IS the brand, not a placeholder.
    val Accent = Color(0xFF9FBE8E)         // light sage: play button fill, active nav pill, liked heart
    val AccentStrong = Color(0xFF7FA06E)   // deeper moss: stats hero card, solid downloads callout
    val AccentTint = Color(0xFF202A1B)     // faint green-tinted tile background

    // Status
    val Danger = Color(0xFFC0524A)
    val Warning = Color(0xFFD9814A)

    // Text
    val TextPrimary = Color(0xFFF4F3EE)    // off-white
    val TextSecondary = Color(0xFFA9AE9F)  // muted sage-gray
    val TextOnAccent = Color(0xFF14170F)   // dark text on light sage buttons

    // Shapes
    val RadiusSm = 12.dp
    val RadiusMd = 20.dp
    val RadiusLg = 28.dp
    val RadiusPill = 999.dp

    val DarkColorScheme = darkColorScheme(
        primary = Accent,
        onPrimary = TextOnAccent,
        primaryContainer = AccentStrong,
        onPrimaryContainer = TextPrimary,
        secondary = Accent,
        onSecondary = TextOnAccent,
        background = Bg,
        surface = Surface,
        surfaceVariant = SurfaceRaised,
        onBackground = TextPrimary,
        onSurface = TextPrimary,
        onSurfaceVariant = TextSecondary,
        outline = SurfaceChip,
    )
}

@Composable
fun SonaraStreamTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SonaraTokens.DarkColorScheme,
        content = content
    )
}
