package com.sonara.desktop.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.image.BufferedImage
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.ln

object DesktopColorExtractor {

    private val artworkCache = ConcurrentHashMap<String, String>()
    private var cachedSystemHex: String? = null
    private var lastSystemCheckTime = 0L

    /**
     * Resolves the dynamic color for Windows desktop:
     * 1. Checks wallpaper if it contains a vibrant, saturated color.
     * 2. Falls back to Windows System Accent Color (DWM / AccentPalette) if wallpaper is monochrome/dark.
     */
    fun getWindowsDynamicHex(): String? {
        val now = System.currentTimeMillis()
        if (cachedSystemHex != null && (now - lastSystemCheckTime) < 3000L) {
            return cachedSystemHex
        }

        // Try wallpaper first if vibrant
        val wallpaperHex = getWindowsWallpaperHex()
        val hex = if (!wallpaperHex.isNullOrBlank()) {
            wallpaperHex
        } else {
            // Fallback to Windows Accent Color
            getWindowsAccentColorHex()
        }

        cachedSystemHex = hex
        lastSystemCheckTime = now
        return hex
    }

    /**
     * Reads Windows System Accent Color from HKCU\Software\Microsoft\Windows\DWM (AccentColor)
     * or HKCU\Software\Microsoft\Windows\CurrentVersion\Explorer\Accent (AccentPalette).
     */
    fun getWindowsAccentColorHex(): String? {
        // 1. Try DWM AccentColor (DWORD: 0xAABBGGRR)
        val dwmOutput = queryRegistry("HKCU\\Software\\Microsoft\\Windows\\DWM", "AccentColor")
        if (!dwmOutput.isNullOrBlank()) {
            val hex = parseDwmAccentColor(dwmOutput)
            if (hex != null) return hex
        }

        // 2. Try Explorer AccentPalette (REG_BINARY: 8 4-byte RGBA shades)
        val paletteOutput = queryRegistry("HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Accent", "AccentPalette")
        if (!paletteOutput.isNullOrBlank()) {
            val hex = parseAccentPalette(paletteOutput)
            if (hex != null) return hex
        }

        return null
    }

    /**
     * Reads the active desktop wallpaper path from HKCU\Control Panel\Desktop (Wallpaper)
     * and extracts a vibrant color if present and sufficiently saturated.
     */
    fun getWindowsWallpaperHex(): String? {
        val wallpaperPath = getWindowsWallpaperPath() ?: return null
        val file = File(wallpaperPath)
        if (!file.exists() || !file.canRead() || file.length() == 0L) return null

        return try {
            val img = ImageIO.read(file) ?: return null
            // For wallpapers, only accept colors with noticeable saturation (>= 0.18)
            // so monochrome/grayscale wallpapers gracefully fall back to the Windows accent color.
            extractDominantColor(img, minSaturation = 0.18f)
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Reads wallpaper file path from Windows registry.
     */
    fun getWindowsWallpaperPath(): String? {
        val output = queryRegistry("HKCU\\Control Panel\\Desktop", "Wallpaper") ?: return null
        for (line in output.lines()) {
            if (line.contains("Wallpaper") && line.contains("REG_SZ")) {
                val idx = line.indexOf("REG_SZ")
                val path = line.substring(idx + "REG_SZ".length).trim()
                if (path.isNotBlank() && !path.equals("none", ignoreCase = true)) {
                    return path
                }
            }
        }
        return null
    }

    /**
     * Extracts dominant vibrant accent from track artwork (remote URL or local file).
     */
    suspend fun extractFromArtwork(artworkUrl: String): String? = withContext(Dispatchers.IO) {
        if (artworkUrl.isBlank()) return@withContext null
        artworkCache[artworkUrl]?.let { return@withContext it }

        try {
            val image: BufferedImage? = if (artworkUrl.startsWith("http://", ignoreCase = true) || artworkUrl.startsWith("https://", ignoreCase = true)) {
                val conn = URL(artworkUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                conn.setRequestProperty("User-Agent", "SonaraStream/4.0.0")
                conn.inputStream.use { ImageIO.read(it) }
            } else {
                val f = File(artworkUrl)
                if (f.exists()) ImageIO.read(f) else null
            }

            if (image == null) return@withContext null

            val hex = extractDominantColor(image, minSaturation = 0.10f)
            if (hex != null) {
                artworkCache[artworkUrl] = hex
            }
            hex
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * High-speed, robust color quantizer and vibrant swatch selector.
     * Evaluates a stride-downsampled grid (~1500 pixels) in < 2ms.
     */
    fun extractDominantColor(image: BufferedImage, minSaturation: Float = 0.12f): String? {
        val width = image.width
        val height = image.height
        if (width <= 0 || height <= 0) return null

        val stepX = maxOf(1, width / 44)
        val stepY = maxOf(1, height / 44)

        class SwatchCandidate(val r: Int, val g: Int, val b: Int, val h: Float, val s: Float, val l: Float) {
            var count = 0
        }

        val candidates = mutableMapOf<Int, SwatchCandidate>()

        for (y in 0 until height step stepY) {
            for (x in 0 until width step stepX) {
                val rgb = image.getRGB(x, y)
                val a = (rgb ushr 24) and 0xFF
                if (a < 128) continue

                val r = (rgb ushr 16) and 0xFF
                val g = (rgb ushr 8) and 0xFF
                val b = rgb and 0xFF

                val rf = r / 255f
                val gf = g / 255f
                val bf = b / 255f
                val max = maxOf(rf, gf, bf)
                val min = minOf(rf, gf, bf)
                val l = (max + min) / 2f

                // Discard near-black and near-white extremes
                if (l < 0.08f || l > 0.92f) continue

                val d = max - min
                val s = if (d == 0f) 0f else if (l > 0.5f) d / (2f - max - min) else d / (max + min)
                if (s < minSaturation) continue

                val h = if (d == 0f) 0f else when (max) {
                    rf -> ((gf - bf) / d + (if (gf < bf) 6f else 0f))
                    gf -> ((bf - rf) / d + 2f)
                    else -> ((rf - gf) / d + 4f)
                } * 60f

                // Bucket: 16 hue slices, 3 saturation slices, 3 lightness slices
                val hBucket = ((h % 360) / 22.5f).toInt()
                val sBucket = (s * 3).toInt().coerceIn(0, 2)
                val lBucket = (l * 3).toInt().coerceIn(0, 2)
                val key = (hBucket shl 4) or (sBucket shl 2) or lBucket

                val cand = candidates.getOrPut(key) { SwatchCandidate(r, g, b, h, s, l) }
                cand.count++
            }
        }

        if (candidates.isEmpty()) return null

        var bestCandidate: SwatchCandidate? = null
        var bestScore = -1f

        for (cand in candidates.values) {
            val satScore = cand.s.coerceIn(0f, 1f)
            val lightScore = 1f - abs(cand.l - 0.5f) * 2f
            val popScore = ln(cand.count.toFloat() + 1f)

            // Favor vibrant, medium-lightness colors with sufficient presence
            val score = (satScore * 2.8f + lightScore * 1.2f) * popScore
            if (score > bestScore) {
                bestScore = score
                bestCandidate = cand
            }
        }

        val winner = bestCandidate ?: candidates.values.maxByOrNull { it.count } ?: return null
        return String.format("#%02X%02X%02X", winner.r, winner.g, winner.b)
    }

    private fun parseDwmAccentColor(output: String): String? {
        for (line in output.lines()) {
            if (line.contains("AccentColor") && line.contains("REG_DWORD")) {
                val hexStr = line.substringAfter("REG_DWORD").trim()
                val clean = hexStr.removePrefix("0x").removePrefix("0X")
                val dword = clean.toLongOrNull(16) ?: continue
                // Format in DWM is 0xAABBGGRR
                val b = ((dword ushr 16) and 0xFF).toInt()
                val g = ((dword ushr 8) and 0xFF).toInt()
                val r = (dword and 0xFF).toInt()
                return String.format("#%02X%02X%02X", r, g, b)
            }
        }
        return null
    }

    private fun parseAccentPalette(output: String): String? {
        for (line in output.lines()) {
            if (line.contains("AccentPalette") && line.contains("REG_BINARY")) {
                val rawHex = line.substringAfter("REG_BINARY").trim().replace(" ", "")
                // Each color is 4 bytes (8 hex chars) RGBA
                // Color 3 (Normal accent): index 12..15 (chars 24..31)
                // Color 2: index 8..11 (chars 16..23)
                if (rawHex.length >= 32) {
                    val colorHex = rawHex.substring(24, 30) // Take R, G, B of shade 3
                    val r = colorHex.substring(0, 2).toIntOrNull(16) ?: continue
                    val g = colorHex.substring(2, 4).toIntOrNull(16) ?: continue
                    val b = colorHex.substring(4, 6).toIntOrNull(16) ?: continue
                    return String.format("#%02X%02X%02X", r, g, b)
                }
            }
        }
        return null
    }

    private fun queryRegistry(key: String, value: String): String? {
        return try {
            val process = ProcessBuilder("reg", "query", key, "/v", value)
                .redirectErrorStream(true)
                .start()
            val text = process.inputStream.bufferedReader().use { it.readText() }
            val completed = process.waitFor(1200, TimeUnit.MILLISECONDS)
            if (!completed) {
                process.destroyForcibly()
                return null
            }
            text
        } catch (_: Throwable) {
            null
        }
    }
}
