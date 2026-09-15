package com.sonara.desktop.data

import com.sonara.desktop.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class MusicSearchService(
    private val losslessClient: LosslessClient = LosslessClient(),
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val streamCache = ConcurrentHashMap<String, String>()

    suspend fun getSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()

        try {
            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            val url = "https://suggestqueries.google.com/complete/search?client=firefox&ds=yt&q=$encoded"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .get()
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val body = resp.body?.string() ?: return@withContext emptyList()
                val root = json.parseToJsonElement(body).jsonArray
                if (root.size < 2) return@withContext emptyList()
                val suggestions = root[1].jsonArray
                suggestions.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun searchSongs(query: String, limit: Int = 25): List<Track> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()

        try {
            val payload = buildJsonObject {
                put("context", buildJsonObject {
                    put("client", buildJsonObject {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", "1.20240101.01.00")
                        put("hl", "en")
                        put("gl", "US")
                    })
                })
                put("query", trimmed)
                put("params", "EgWKAQIIAWoKEAkQBRAKEAMQBA==") // Filter to Songs
            }

            val reqBody = payload.toString().toRequestBody("application/json".toMediaType())
            val req = Request.Builder()
                .url("https://music.youtube.com/youtubei/v1/search?prettyPrint=false")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Origin", "https://music.youtube.com")
                .header("Referer", "https://music.youtube.com/")
                .post(reqBody)
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val body = resp.body?.string() ?: return@withContext emptyList()
                parseInnerTubeTracks(body).take(limit)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private val streamExtractor = DesktopStreamExtractor(client)

    suspend fun resolveAudioStream(track: Track): Track = withContext(Dispatchers.IO) {
        // Check cache first
        streamCache[track.id]?.let { cachedUrl ->
            return@withContext track.copy(streamUrl = cachedUrl)
        }

        // 1. Try Lossless backend first for pure CD / Hi-Res FLAC
        try {
            val losslessResult = losslessClient.resolveLosslessStream(track.title, track.artist)
            if (losslessResult != null && losslessResult.url.isNotBlank()) {
                val qualityLabel = if (losslessResult.formatId == LosslessClient.QUALITY_MAX_HI_RES || losslessResult.formatId == LosslessClient.QUALITY_HI_RES_96) {
                    "Hi-Res FLAC ${losslessResult.bitDepth}-bit / ${losslessResult.samplingRate} kHz"
                } else if (losslessResult.formatId == LosslessClient.QUALITY_MP3_320) {
                    "MP3 320 kbps"
                } else {
                    "Lossless FLAC 16-bit / 44.1 kHz"
                }

                streamCache[track.id] = losslessResult.url
                return@withContext track.copy(
                    streamUrl = losslessResult.url,
                    isLossless = true,
                    audioQuality = qualityLabel
                )
            }
        } catch (_: Exception) { }

        // 2. Find real YouTube videoId if not already an 11-char ID
        val targetVideoId = if (track.videoId != null && track.videoId.length == 11 && !track.videoId.contains(" ")) {
            track.videoId
        } else if (track.id.length == 11 && !track.id.contains(" ") && !track.id.startsWith("lfm_")) {
            track.id
        } else {
            // Search YouTube Music for "$title $artist"
            val searchResults = searchSongs("${track.title} ${track.artist}", limit = 5)
            val matched = searchResults.firstOrNull { it.videoId != null && it.videoId.isNotBlank() }
            matched?.videoId ?: searchResults.firstOrNull()?.id
        }

        if (!targetVideoId.isNullOrBlank()) {
            // 3. Extract stream via NewPipe Extractor (M4A/AAC for native JavaFX compatibility)
            try {
                val streamUrl = streamExtractor.resolveAudioStreamUrl(targetVideoId)
                if (!streamUrl.isNullOrBlank()) {
                    streamCache[track.id] = streamUrl
                    return@withContext track.copy(
                        streamUrl = streamUrl,
                        videoId = targetVideoId,
                        isLossless = false,
                        audioQuality = "High Quality AAC"
                    )
                }
            } catch (_: Exception) {}

            // 4. Fallback to direct InnerTube or Piped
            val ytStreamUrl = fetchInnerTubeStreamUrl(targetVideoId)
            if (ytStreamUrl != null) {
                streamCache[track.id] = ytStreamUrl
                return@withContext track.copy(
                    streamUrl = ytStreamUrl,
                    videoId = targetVideoId,
                    isLossless = false,
                    audioQuality = "High Quality 256 kbps"
                )
            }

            val pipedUrl = fetchPipedStreamUrl(targetVideoId)
            if (pipedUrl != null) {
                streamCache[track.id] = pipedUrl
                return@withContext track.copy(
                    streamUrl = pipedUrl,
                    videoId = targetVideoId,
                    isLossless = false,
                    audioQuality = "HQ Web Audio"
                )
            }
        }

        track
    }

    private fun fetchInnerTubeStreamUrl(videoId: String): String? {
        try {
            // Use Android Music client context for direct un-throttled audio streams
            val payload = buildJsonObject {
                put("context", buildJsonObject {
                    put("client", buildJsonObject {
                        put("clientName", "ANDROID_MUSIC")
                        put("clientVersion", "6.42.52")
                        put("androidSdkVersion", 33)
                        put("hl", "en")
                        put("gl", "US")
                    })
                })
                put("videoId", videoId)
            }

            val reqBody = payload.toString().toRequestBody("application/json".toMediaType())
            val req = Request.Builder()
                .url("https://music.youtube.com/youtubei/v1/player?prettyPrint=false")
                .header("User-Agent", "com.google.android.apps.youtube.music/6.42.52 (Linux; U; Android 13)")
                .post(reqBody)
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val root = json.parseToJsonElement(body).jsonObject
                val streamingData = root["streamingData"]?.jsonObject ?: return null
                val adaptiveFormats = streamingData["adaptiveFormats"]?.jsonArray.orEmpty()

                // Pick best audio stream
                for (fmt in adaptiveFormats) {
                    val obj = fmt.jsonObject
                    val mime = obj["mimeType"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    if (mime.startsWith("audio/")) {
                        val directUrl = obj["url"]?.jsonPrimitive?.contentOrNull
                        if (!directUrl.isNullOrBlank()) {
                            return directUrl
                        }
                    }
                }
            }
        } catch (_: Exception) { }
        return null
    }

    private fun fetchPipedStreamUrl(videoId: String): String? {
        val instances = listOf(
            "https://pipedapi.kavin.rocks",
            "https://api.piped.privacydev.net",
            "https://piped-api.lunar.icu"
        )
        for (instance in instances) {
            try {
                val req = Request.Builder()
                    .url("$instance/streams/$videoId")
                    .header("User-Agent", "Mozilla/5.0")
                    .get()
                    .build()

                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: return@use
                        val root = json.parseToJsonElement(body).jsonObject
                        val audioStreams = root["audioStreams"]?.jsonArray.orEmpty()
                        val best = audioStreams.maxByOrNull {
                            it.jsonObject["bitrate"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
                        }
                        val url = best?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull
                        if (!url.isNullOrBlank()) return url
                    }
                }
            } catch (_: Exception) { }
        }
        return null
    }

    private fun parseInnerTubeTracks(body: String): List<Track> {
        val tracks = mutableListOf<Track>()
        try {
            val root = json.parseToJsonElement(body).jsonObject
            val contents = root["contents"]?.jsonObject ?: return emptyList()
            val tabRenderer = contents["tabbedSearchResultsRenderer"]?.jsonObject
                ?: contents["sectionListRenderer"]?.jsonObject
                ?: return emptyList()

            // Traverse JSON looking for musicResponsiveListItemRenderer
            fun findSongItems(element: JsonElement) {
                when (element) {
                    is JsonObject -> {
                        if (element.containsKey("musicResponsiveListItemRenderer")) {
                            parseMusicResponsiveItem(element["musicResponsiveListItemRenderer"]!!.jsonObject)?.let {
                                tracks.add(it)
                            }
                        } else {
                            element.values.forEach { findSongItems(it) }
                        }
                    }
                    is JsonArray -> {
                        element.forEach { findSongItems(it) }
                    }
                    else -> {}
                }
            }

            findSongItems(tabRenderer)
        } catch (_: Exception) { }
        return tracks
    }

    private fun parseMusicResponsiveItem(item: JsonObject): Track? {
        return try {
            val flexColumns = item["flexColumns"]?.jsonArray ?: return null
            if (flexColumns.isEmpty()) return null

            // Title is in first flex column
            val col0 = flexColumns[0].jsonObject["musicResponsiveListItemFlexColumnRenderer"]?.jsonObject
            val titleRuns = col0?.get("text")?.jsonObject?.get("runs")?.jsonArray
            val title = titleRuns?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull ?: return null

            // Artist and duration are in second flex column
            var artist = "Unknown Artist"
            var album = ""
            var durationMs = 0L

            if (flexColumns.size > 1) {
                val col1 = flexColumns[1].jsonObject["musicResponsiveListItemFlexColumnRenderer"]?.jsonObject
                val infoRuns = col1?.get("text")?.jsonObject?.get("runs")?.jsonArray.orEmpty()
                if (infoRuns.isNotEmpty()) {
                    artist = infoRuns[0].jsonObject["text"]?.jsonPrimitive?.contentOrNull ?: "Unknown Artist"
                }
                if (infoRuns.size >= 3) {
                    val possibleAlbum = infoRuns[2].jsonObject["text"]?.jsonPrimitive?.contentOrNull
                    if (possibleAlbum != null && !possibleAlbum.contains(":")) {
                        album = possibleAlbum
                    }
                }
                val durationText = infoRuns.lastOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                if (durationText != null && durationText.contains(":")) {
                    durationMs = parseDurationText(durationText)
                }
            }

            // Thumbnail
            val thumbnails = item["thumbnail"]?.jsonObject
                ?.get("musicThumbnailRenderer")?.jsonObject
                ?.get("thumbnail")?.jsonObject
                ?.get("thumbnails")?.jsonArray
            val artworkUrl = thumbnails?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull

            // VideoId from navigationEndpoint
            val navEndpoint = item["overlay"]?.jsonObject
                ?.get("musicItemThumbnailOverlayRenderer")?.jsonObject
                ?.get("content")?.jsonObject
                ?.get("musicPlayButtonRenderer")?.jsonObject
                ?.get("playNavigationEndpoint")?.jsonObject
                ?: item["navigationEndpoint"]?.jsonObject

            val videoId = navEndpoint?.get("watchEndpoint")?.jsonObject?.get("videoId")?.jsonPrimitive?.contentOrNull
                ?: item["playlistItemData"]?.jsonObject?.get("videoId")?.jsonPrimitive?.contentOrNull
                ?: return null

            Track(
                id = videoId,
                title = title,
                artist = artist,
                album = album,
                durationMs = durationMs,
                artworkUrl = artworkUrl,
                videoId = videoId,
                streamUrl = null,
                isLossless = false,
                audioQuality = "Lossless Ready"
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseDurationText(text: String): Long {
        return try {
            val parts = text.trim().split(":")
            if (parts.size == 2) {
                val min = parts[0].toLong()
                val sec = parts[1].toLong()
                (min * 60 + sec) * 1000L
            } else if (parts.size == 3) {
                val hr = parts[0].toLong()
                val min = parts[1].toLong()
                val sec = parts[2].toLong()
                (hr * 3600 + min * 60 + sec) * 1000L
            } else 0L
        } catch (_: Exception) {
            0L
        }
    }

    fun getCuratedDiscoverTracks(): List<Track> {
        return listOf(
            Track(
                id = "dQw4w9WgXcQ",
                title = "Never Gonna Give You Up",
                artist = "Rick Astley",
                album = "Whenever You Need Somebody",
                durationMs = 213000L,
                artworkUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
                videoId = "dQw4w9WgXcQ",
                audioQuality = "24-bit / 96 kHz FLAC"
            ),
            Track(
                id = "kJQP7kiw5Fk",
                title = "Despacito",
                artist = "Luis Fonsi ft. Daddy Yankee",
                album = "VIDA",
                durationMs = 282000L,
                artworkUrl = "https://i.ytimg.com/vi/kJQP7kiw5Fk/hqdefault.jpg",
                videoId = "kJQP7kiw5Fk",
                audioQuality = "16-bit / 44.1 kHz FLAC"
            ),
            Track(
                id = "JGwWNGJdvx8",
                title = "Shape of You",
                artist = "Ed Sheeran",
                album = "÷ (Divide)",
                durationMs = 233000L,
                artworkUrl = "https://i.ytimg.com/vi/JGwWNGJdvx8/hqdefault.jpg",
                videoId = "JGwWNGJdvx8",
                audioQuality = "24-bit / 96 kHz FLAC"
            ),
            Track(
                id = "fJ9rUzIMcZQ",
                title = "Bohemian Rhapsody",
                artist = "Queen",
                album = "A Night at the Opera",
                durationMs = 359000L,
                artworkUrl = "https://i.ytimg.com/vi/fJ9rUzIMcZQ/hqdefault.jpg",
                videoId = "fJ9rUzIMcZQ",
                audioQuality = "Master Hi-Res FLAC"
            ),
            Track(
                id = "4NRXx6U8ABQ",
                title = "Blinding Lights",
                artist = "The Weeknd",
                album = "After Hours",
                durationMs = 200000L,
                artworkUrl = "https://i.ytimg.com/vi/4NRXx6U8ABQ/hqdefault.jpg",
                videoId = "4NRXx6U8ABQ",
                audioQuality = "24-bit / 96 kHz FLAC"
            ),
            Track(
                id = "kXYiU_JCYtU",
                title = "Numb",
                artist = "Linkin Park",
                album = "Meteora",
                durationMs = 187000L,
                artworkUrl = "https://i.ytimg.com/vi/kXYiU_JCYtU/hqdefault.jpg",
                videoId = "kXYiU_JCYtU",
                audioQuality = "16-bit / 44.1 kHz FLAC"
            ),
            Track(
                id = "09R8_2nJtjg",
                title = "Sugar",
                artist = "Maroon 5",
                album = "V",
                durationMs = 235000L,
                artworkUrl = "https://i.ytimg.com/vi/09R8_2nJtjg/hqdefault.jpg",
                videoId = "09R8_2nJtjg",
                audioQuality = "Lossless FLAC"
            ),
            Track(
                id = "hT_nvWreIhg",
                title = "Counting Stars",
                artist = "OneRepublic",
                album = "Native",
                durationMs = 257000L,
                artworkUrl = "https://i.ytimg.com/vi/hT_nvWreIhg/hqdefault.jpg",
                videoId = "hT_nvWreIhg",
                audioQuality = "Lossless FLAC"
            )
        )
    }
}
