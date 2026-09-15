package com.sonara.desktop.data

import com.sonara.desktop.model.SyncedLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

@Serializable
data class LrclibResponse(
    val id: Long? = null,
    val trackName: String? = null,
    val artistName: String? = null,
    val albumName: String? = null,
    val duration: Double? = null,
    val instrumental: Boolean = false,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null,
)

class LrclibClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val lrcPattern = Pattern.compile("""^\[(\d{2}):(\d{2})(?:\.(\d{2,3}))?\](.*)$""")

    suspend fun fetchLyrics(
        trackName: String,
        artistName: String,
        albumName: String? = null,
        durationSeconds: Int? = null,
    ): List<SyncedLine> = withContext(Dispatchers.IO) {
        if (trackName.isBlank()) return@withContext emptyList()

        // 1. Try exact match via /api/get
        val exactUrl = "https://lrclib.net/api/get".toHttpUrlOrNull()?.newBuilder()?.apply {
            addQueryParameter("track_name", trackName.trim())
            addQueryParameter("artist_name", artistName.trim())
            if (!albumName.isNullOrBlank()) addQueryParameter("album_name", albumName.trim())
            if (durationSeconds != null && durationSeconds > 0) {
                addQueryParameter("duration", durationSeconds.toString())
            }
        }?.build()

        if (exactUrl != null) {
            val exactLyrics = requestLyrics(exactUrl.toString())
            if (exactLyrics.isNotEmpty()) return@withContext exactLyrics
        }

        // 2. Fallback to search query /api/search?q=...
        val searchUrl = "https://lrclib.net/api/search".toHttpUrlOrNull()?.newBuilder()?.apply {
            addQueryParameter("q", "${trackName.trim()} ${artistName.trim()}")
        }?.build()

        if (searchUrl != null) {
            val searchLyrics = requestSearchLyrics(searchUrl.toString())
            if (searchLyrics.isNotEmpty()) return@withContext searchLyrics
        }

        emptyList()
    }

    private fun requestLyrics(url: String): List<SyncedLine> {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "SonaraStream/4.0.0 (https://github.com/rayyanbobs-art/sonara-stream)")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                val parsed = json.decodeFromString<LrclibResponse>(body)
                return parseLrc(parsed.syncedLyrics, parsed.plainLyrics)
            }
        } catch (e: Exception) {
            return emptyList()
        }
    }

    private fun requestSearchLyrics(url: String): List<SyncedLine> {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "SonaraStream/4.0.0 (https://github.com/rayyanbobs-art/sonara-stream)")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                val list = json.decodeFromString<List<LrclibResponse>>(body)
                val match = list.firstOrNull { !it.syncedLyrics.isNullOrBlank() } ?: list.firstOrNull()
                return if (match != null) parseLrc(match.syncedLyrics, match.plainLyrics) else emptyList()
            }
        } catch (e: Exception) {
            return emptyList()
        }
    }

    fun parseLrc(synced: String?, plain: String?): List<SyncedLine> {
        if (!synced.isNullOrBlank()) {
            val lines = mutableListOf<SyncedLine>()
            synced.lineSequence().forEach { rawLine ->
                val trimmed = rawLine.trim()
                val matcher = lrcPattern.matcher(trimmed)
                if (matcher.matches()) {
                    val minutes = matcher.group(1)?.toLongOrNull() ?: 0L
                    val seconds = matcher.group(2)?.toLongOrNull() ?: 0L
                    val fracStr = matcher.group(3) ?: "0"
                    val millis = when (fracStr.length) {
                        2 -> (fracStr.toLongOrNull() ?: 0L) * 10
                        3 -> fracStr.toLongOrNull() ?: 0L
                        else -> 0L
                    }
                    val totalMs = (minutes * 60 + seconds) * 1000 + millis
                    val text = matcher.group(4)?.trim() ?: ""
                    lines.add(SyncedLine(timeMs = totalMs, text = text))
                }
            }
            if (lines.isNotEmpty()) return lines.sortedBy { it.timeMs }
        }

        // Fallback to plain lyrics: space them roughly or show as list
        if (!plain.isNullOrBlank()) {
            return plain.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .mapIndexed { idx, line -> SyncedLine(timeMs = idx * 4000L, text = line) }
                .toList()
        }

        return emptyList()
    }
}
