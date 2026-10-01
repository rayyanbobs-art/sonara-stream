package com.sonara.desktop.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SonaraColors(
    val bg: Color = Color(0xFF14170F),
    val surface: Color = Color(0xFF1E2318),
    val surfaceRaised: Color = Color(0xFF262B1F),
    val surfaceChip: Color = Color(0xFF333829),
    val accent: Color = Color(0xFF9FBE8E),
    val accentStrong: Color = Color(0xFF7FA06E),
    val accentTint: Color = Color(0xFF202A1B),
    val textPrimary: Color = Color(0xFFF4F3EE),
    val textSecondary: Color = Color(0xFFA9AE9F),
    val textOnAccent: Color = Color(0xFF14170F),
    val danger: Color = Color(0xFFC0524A),
    val warning: Color = Color(0xFFD9814A),
)

val LocalSonaraColors = staticCompositionLocalOf { SonaraColors() }

object SonaraTokens {
    // Surfaces
    val Bg: Color @Composable get() = LocalSonaraColors.current.bg
    val Surface: Color @Composable get() = LocalSonaraColors.current.surface
    val SurfaceRaised: Color @Composable get() = LocalSonaraColors.current.surfaceRaised
    val SurfaceChip: Color @Composable get() = LocalSonaraColors.current.surfaceChip

    // Accent
    val Accent: Color @Composable get() = LocalSonaraColors.current.accent
    val AccentStrong: Color @Composable get() = LocalSonaraColors.current.accentStrong
    val AccentTint: Color @Composable get() = LocalSonaraColors.current.accentTint

    // Status
    val Danger: Color @Composable get() = LocalSonaraColors.current.danger
    val Warning: Color @Composable get() = LocalSonaraColors.current.warning

    // Text
    val TextPrimary: Color @Composable get() = LocalSonaraColors.current.textPrimary
    val TextSecondary: Color @Composable get() = LocalSonaraColors.current.textSecondary
    val TextOnAccent: Color @Composable get() = LocalSonaraColors.current.textOnAccent

    // Shapes
    val RadiusSm = 12.dp
    val RadiusMd = 20.dp
    val RadiusLg = 28.dp
    val RadiusPill = 999.dp
}

@Composable
fun SonaraStreamTheme(
    amoledMode: Boolean = false,
    dynamicColor: Boolean = false,
    dynamicNowPlaying: Boolean = false,
    nowPlayingArtworkUrl: String? = null,
    content: @Composable () -> Unit
) {
    var activeSeedHex by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(dynamicColor, dynamicNowPlaying, nowPlayingArtworkUrl) {
        withContext(Dispatchers.IO) {
            var hex: String? = null
            if (dynamicNowPlaying && !nowPlayingArtworkUrl.isNullOrBlank()) {
                hex = DesktopColorExtractor.extractFromArtwork(nowPlayingArtworkUrl)
            }
            if (hex == null && dynamicColor) {
                hex = DesktopColorExtractor.getWindowsDynamicHex()
            }
            activeSeedHex = hex
        }
    }

    val colors = remember(amoledMode, activeSeedHex) {
        if (activeSeedHex != null) {
            Md3SchemeBuilder.buildSonaraColors(activeSeedHex!!, amoledMode)
        } else {
            Md3SchemeBuilder.defaultSonaraColors(amoledMode)
        }
    }

    val materialColors = darkColorScheme(
        primary = colors.accent,
        onPrimary = colors.textOnAccent,
        primaryContainer = colors.accentStrong,
        onPrimaryContainer = colors.textPrimary,
        secondary = colors.accent,
        onSecondary = colors.textOnAccent,
        background = colors.bg,
        surface = colors.surface,
        surfaceVariant = colors.surfaceRaised,
        onBackground = colors.textPrimary,
        onSurface = colors.textPrimary,
        onSurfaceVariant = colors.textSecondary,
        outline = colors.surfaceChip,
    )

    CompositionLocalProvider(LocalSonaraColors provides colors) {
        MaterialTheme(
            colorScheme = materialColors,
            content = content
        )
    }
}
