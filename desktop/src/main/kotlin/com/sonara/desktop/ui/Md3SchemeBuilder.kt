package com.sonara.desktop.ui

import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt

/**
 * Generates harmonious dark Material You color palettes from any seed hex color,
 * matching the algorithm from the Sonara Android app / web engine.
 */
object Md3SchemeBuilder {

    /** Extracts hue (0-360) from a hex string (#RRGGBB). */
    fun hueOf(hex: String): Int {
        val clean = hex.removePrefix("#")
        if (clean.length < 6) return 0
        val rgb = clean.take(6).toIntOrNull(16) ?: return 0
        val r = ((rgb ushr 16) and 0xFF) / 255f
        val g = ((rgb ushr 8) and 0xFF) / 255f
        val b = (rgb and 0xFF) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        if (max == min) return 0
        val d = max - min
        val h = when (max) {
            r -> ((g - b) / d + (if (g < b) 6f else 0f))
            g -> ((b - r) / d + 2f)
            else -> ((r - g) / d + 4f)
        } / 6f
        return (h * 360f).roundToInt().let { ((it % 360) + 360) % 360 }
    }

    /**
     * Builds [SonaraColors] dynamically from a seed hex (Windows accent, wallpaper, or artwork).
     */
    fun buildSonaraColors(seedHex: String, amoled: Boolean): SonaraColors {
        val h = hueOf(seedHex)
        return if (amoled) {
            SonaraColors(
                bg = Color(0xFF000000),
                surface = Color(0xFF0A0A0A),
                surfaceRaised = Color(0xFF141414),
                surfaceChip = Color(0xFF222222),
                accent = hsl(h, 45, 74),
                accentStrong = hsl(h, 50, 52),
                accentTint = hsl(h, 30, 10),
                textPrimary = Color(0xFFF4F3EE),
                textSecondary = Color(0xFFA0A499),
                textOnAccent = Color(0xFF000000),
                danger = Color(0xFFC0524A),
                warning = Color(0xFFD9814A)
            )
        } else {
            SonaraColors(
                bg = hsl(h, 15, 8),
                surface = hsl(h, 14, 12),
                surfaceRaised = hsl(h, 14, 16),
                surfaceChip = hsl(h, 14, 20),
                accent = hsl(h, 45, 72),
                accentStrong = hsl(h, 50, 50),
                accentTint = hsl(h, 25, 14),
                textPrimary = Color(0xFFF4F3EE),
                textSecondary = hsl(h, 8, 68),
                textOnAccent = hsl(h, 25, 8),
                danger = Color(0xFFC0524A),
                warning = Color(0xFFD9814A)
            )
        }
    }

    /**
     * Signature Sonara Sage Green palette used when dynamic theming is disabled.
     */
    fun defaultSonaraColors(amoled: Boolean): SonaraColors {
        return if (amoled) {
            SonaraColors(
                bg = Color(0xFF000000),
                surface = Color(0xFF0A0A0A),
                surfaceRaised = Color(0xFF141414),
                surfaceChip = Color(0xFF222222),
                accent = Color(0xFF9FBE8E),
                accentStrong = Color(0xFF7FA06E),
                accentTint = Color(0xFF0F150D),
                textPrimary = Color(0xFFF4F3EE),
                textSecondary = Color(0xFFA0A499),
                textOnAccent = Color(0xFF000000),
                danger = Color(0xFFC0524A),
                warning = Color(0xFFD9814A)
            )
        } else {
            SonaraColors(
                bg = Color(0xFF14170F),
                surface = Color(0xFF1E2318),
                surfaceRaised = Color(0xFF262B1F),
                surfaceChip = Color(0xFF333829),
                accent = Color(0xFF9FBE8E),
                accentStrong = Color(0xFF7FA06E),
                accentTint = Color(0xFF202A1B),
                textPrimary = Color(0xFFF4F3EE),
                textSecondary = Color(0xFFA9AE9F),
                textOnAccent = Color(0xFF14170F),
                danger = Color(0xFFC0524A),
                warning = Color(0xFFD9814A)
            )
        }
    }

    /**
     * Converts HSL(0-360, 0-100, 0-100) to Compose [Color].
     */
    fun hsl(h: Int, s: Int, l: Int): Color {
        val hf = ((h % 360) + 360) % 360 / 360f
        val sf = (s.coerceIn(0, 100)) / 100f
        val lf = (l.coerceIn(0, 100)) / 100f

        if (sf == 0f) return Color(lf, lf, lf)

        val q = if (lf < 0.5f) lf * (1 + sf) else lf + sf - lf * sf
        val p = 2 * lf - q

        fun hueToRgb(t: Float): Float {
            var tt = t
            if (tt < 0f) tt += 1f
            if (tt > 1f) tt -= 1f
            return when {
                tt < 1f / 6f -> p + (q - p) * 6f * tt
                tt < 1f / 2f -> q
                tt < 2f / 3f -> p + (q - p) * (2f / 3f - tt) * 6f
                else -> p
            }
        }

        val r = hueToRgb(hf + 1f / 3f)
        val g = hueToRgb(hf)
        val b = hueToRgb(hf - 1f / 3f)
        return Color(r, g, b)
    }
}
