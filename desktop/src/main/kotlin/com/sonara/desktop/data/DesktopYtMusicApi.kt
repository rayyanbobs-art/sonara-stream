package com.sonara.desktop.data

import com.sonara.desktop.model.Playlist
import com.sonara.desktop.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class YtAccountInfo(
    val accountName: String,
    val channelHandle: String? = null,
    val photoUrl: String? = null
)

class DesktopYtMusicApi(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
) {
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val MUSIC_API = "https://music.youtube.com/youtubei/v1"
        private const val ORIGIN = "https://music.youtube.com"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        private const val API_KEY = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"
        private const val CLIENT_VERSION = "1.20260707.12.00"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun extractSapisid(cookies: String): String? {
            val map = parseCookieString(cookies)
            return map["__Secure-3PAPISID"] ?: map["SAPISID"] ?: map["APISID"]
        }

        fun generateAuthorizationHeader(sapisid: String, origin: String = ORIGIN): String {
            val timestamp = System.currentTimeMillis() / 1000
            val payload = "$timestamp $sapisid $origin"
            val digest = MessageDigest.getInstance("SHA-1").digest(payload.toByteArray(Charsets.UTF_8))
            val hex = digest.joinToString("") { "%02x".format(it) }
            return "SAPISIDHASH ${timestamp}_$hex"
        }

        fun parseCookieString(raw: String): Map<String, String> {
            return raw.split(';')
                .mapNotNull { pair ->
                    val idx = pair.indexOf('=')
                    if (idx <= 0) null else {
                        val name = pair.substring(0, idx).trim()
                        val value = pair.substring(idx + 1).trim()
                        if (name.isBlank() || value.isBlank()) null else name to value
                    }
                }
                .toMap()
        }
    }

    private fun buildContext(): JsonObject = buildJsonObject {
        put("client", buildJsonObject {
            put("clientName", "WEB_REMIX")
            put("clientVersion", CLIENT_VERSION)
            put("hl", "en")
            put("gl", "US")
        })
    }

    suspend fun fetchAccountInfo(cookies: String): YtAccountInfo? = withContext(Dispatchers.IO) {
        val sapisid = extractSapisid(cookies) ?: return@withContext null
        val authHeader = generateAuthorizationHeader(sapisid)

        val bodyJson = buildJsonObject {
            put("context", buildContext())
        }

        val request = Request.Builder()
            .url("$MUSIC_API/account/account_menu?key=$API_KEY&prettyPrint=false")
            .header("Content-Type", "application/json")
            .header("User-Agent", USER_AGENT)
            .header("Origin", ORIGIN)
            .header("Referer", "$ORIGIN/")
            .header("X-YouTube-Client-Name", "67")
            .header("X-YouTube-Client-Version", CLIENT_VERSION)
            .header("Cookie", cookies)
            .header("Authorization", authHeader)
            .post(bodyJson.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val text = response.body?.string().orEmpty()
                val root = json.parseToJsonElement(text).jsonObject

                val headers = mutableListOf<JsonObject>()
                collectObjects(root, "activeAccountHeaderRenderer", headers)
                val header = headers.firstOrNull() ?: return@withContext null

                val accountName = header["accountName"]?.jsonObject?.get("runs")?.jsonArray?.firstOrNull()
                    ?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull?.trim().orEmpty()

                if (accountName.isBlank()) return@withContext null

                val channelHandle = header["channelHandle"]?.jsonObject?.get("runs")?.jsonArray?.firstOrNull()
                    ?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull?.trim()

                val photoUrl = header["accountPhoto"]?.jsonObject?.get("thumbnails")?.jsonObject?.get("thumbnails")
                    ?.jsonArray?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull

                YtAccountInfo(
                    accountName = accountName,
                    channelHandle = channelHandle,
                    photoUrl = photoUrl
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun fetchLibraryPlaylists(cookies: String): List<Playlist> = withContext(Dispatchers.IO) {
        val sapisid = extractSapisid(cookies) ?: return@withContext emptyList()
        val authHeader = generateAuthorizationHeader(sapisid)

        val bodyJson = buildJsonObject {
            put("context", buildContext())
            put("browseId", "FEmusic_liked_playlists")
        }

        val request = Request.Builder()
            .url("$MUSIC_API/browse?key=$API_KEY&prettyPrint=false")
            .header("Content-Type", "application/json")
            .header("User-Agent", USER_AGENT)
            .header("Origin", ORIGIN)
            .header("Referer", "$ORIGIN/")
            .header("X-YouTube-Client-Name", "67")
            .header("X-YouTube-Client-Version", CLIENT_VERSION)
            .header("Cookie", cookies)
            .header("Authorization", authHeader)
            .post(bodyJson.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val text = response.body?.string().orEmpty()
                val root = json.parseToJsonElement(text).jsonObject

                val renderers = mutableListOf<JsonObject>()
                collectObjects(root, "musicResponsiveListItemRenderer", renderers)
                collectObjects(root, "musicTwoRowItemRenderer", renderers)
                collectObjects(root, "gridPlaylistRenderer", renderers)
                collectObjects(root, "musicGridItemRenderer", renderers)

                val result = mutableListOf<Playlist>()
                val seenIds = mutableSetOf<String>()

                for (r in renderers) {
                    val nav = r["navigationEndpoint"]?.jsonObject?.get("browseEndpoint")?.jsonObject
                        ?: r["title"]?.jsonObject?.get("runs")?.jsonArray?.firstOrNull()?.jsonObject
                            ?.get("navigationEndpoint")?.jsonObject?.get("browseEndpoint")?.jsonObject

                    val rawBrowseId = nav?.get("browseId")?.jsonPrimitive?.contentOrNull
                        ?: nav?.get("playlistId")?.jsonPrimitive?.contentOrNull
                        ?: continue

                    val playlistId = rawBrowseId.removePrefix("VL")
                    if (!seenIds.add(playlistId)) continue

                    val titleRuns = r["flexColumns"]?.jsonArray?.firstOrNull()?.jsonObject
                        ?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
                        ?.get("text")?.jsonObject?.get("runs")?.jsonArray
                        ?: r["title"]?.jsonObject?.get("runs")?.jsonArray

                    val title = titleRuns?.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull.orEmpty() }
                        ?.trim()
                        ?: r["title"]?.jsonObject?.get("simpleText")?.jsonPrimitive?.contentOrNull
                        ?: "YouTube Playlist"

                    val subtitleRuns = r["flexColumns"]?.jsonArray?.getOrNull(1)?.jsonObject
                        ?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
                        ?.get("text")?.jsonObject?.get("runs")?.jsonArray
                        ?: r["subtitle"]?.jsonObject?.get("runs")?.jsonArray

                    val desc = subtitleRuns?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
                        ?.joinToString(" · ") ?: "YouTube Music"

                    val thumbnails = r["thumbnailRenderer"]?.jsonObject?.get("musicThumbnailRenderer")?.jsonObject
                        ?.get("thumbnail")?.jsonObject?.get("thumbnails")?.jsonArray
                        ?: r["thumbnail"]?.jsonObject?.get("musicThumbnailRenderer")?.jsonObject
                            ?.get("thumbnail")?.jsonObject?.get("thumbnails")?.jsonArray
                        ?: r["thumbnail"]?.jsonObject?.get("thumbnails")?.jsonArray

                    val artwork = thumbnails?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull
                        ?.let { if (it.startsWith("//")) "https:$it" else it }

                    result.add(
                        Playlist(
                            id = playlistId,
                            title = title,
                            description = desc,
                            trackCount = 0,
                            coverUrl = artwork,
                            isRemote = true
                        )
                    )
                }
                result
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun fetchPlaylistTracks(playlistId: String, cookies: String? = null): List<Track> = withContext(Dispatchers.IO) {
        val cleanId = playlistId.removePrefix("VL")
        val browseId = "VL$cleanId"

        val bodyJson = buildJsonObject {
            put("context", buildContext())
            put("browseId", browseId)
        }

        val requestBuilder = Request.Builder()
            .url("$MUSIC_API/browse?key=$API_KEY&prettyPrint=false")
            .header("Content-Type", "application/json")
            .header("User-Agent", USER_AGENT)
            .header("Origin", ORIGIN)
            .header("Referer", "$ORIGIN/")
            .header("X-YouTube-Client-Name", "67")
            .header("X-YouTube-Client-Version", CLIENT_VERSION)

        if (!cookies.isNullOrBlank()) {
            val sapisid = extractSapisid(cookies)
            if (sapisid != null) {
                requestBuilder.header("Cookie", cookies)
                requestBuilder.header("Authorization", generateAuthorizationHeader(sapisid))
            }
        }

        val request = requestBuilder
            .post(bodyJson.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val text = response.body?.string().orEmpty()
                val root = json.parseToJsonElement(text).jsonObject

                val renderers = mutableListOf<JsonObject>()
                collectObjects(root, "musicResponsiveListItemRenderer", renderers)
                collectObjects(root, "playlistVideoRenderer", renderers)

                val tracks = mutableListOf<Track>()
                val seenVideoIds = mutableSetOf<String>()

                for (r in renderers) {
                    val videoId = r["playlistItemData"]?.jsonObject?.get("videoId")?.jsonPrimitive?.contentOrNull
                        ?: r["navigationEndpoint"]?.jsonObject?.get("watchEndpoint")?.jsonObject?.get("videoId")?.jsonPrimitive?.contentOrNull
                        ?: r["videoId"]?.jsonPrimitive?.contentOrNull
                        ?: continue

                    if (!seenVideoIds.add(videoId)) continue

                    val flexCols = r["flexColumns"]?.jsonArray
                    val titleRuns = flexCols?.firstOrNull()?.jsonObject
                        ?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
                        ?.get("text")?.jsonObject?.get("runs")?.jsonArray
                        ?: r["title"]?.jsonObject?.get("runs")?.jsonArray

                    val title = titleRuns?.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull.orEmpty() }
                        ?.trim()
                        ?: r["title"]?.jsonObject?.get("simpleText")?.jsonPrimitive?.contentOrNull
                        ?: "Unknown Track"

                    val detailRuns = flexCols?.getOrNull(1)?.jsonObject
                        ?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
                        ?.get("text")?.jsonObject?.get("runs")?.jsonArray
                        ?: r["shortBylineText"]?.jsonObject?.get("runs")?.jsonArray

                    val artist = detailRuns?.firstOrNull { run ->
                        run.jsonObject["navigationEndpoint"]?.jsonObject?.get("browseEndpoint")
                            ?.jsonObject?.get("browseId")?.jsonPrimitive?.contentOrNull?.startsWith("UC") == true
                    }?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                        ?: detailRuns?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                        ?: "YouTube Music"

                    val album = detailRuns?.firstOrNull { run ->
                        run.jsonObject["navigationEndpoint"]?.jsonObject?.get("browseEndpoint")
                            ?.jsonObject?.get("browseId")?.jsonPrimitive?.contentOrNull?.startsWith("MPRE") == true
                    }?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull.orEmpty()

                    val thumbnails = r["thumbnail"]?.jsonObject?.get("musicThumbnailRenderer")?.jsonObject
                        ?.get("thumbnail")?.jsonObject?.get("thumbnails")?.jsonArray
                        ?: r["thumbnail"]?.jsonObject?.get("thumbnails")?.jsonArray

                    val artworkUrl = thumbnails?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull
                        ?.let { if (it.startsWith("//")) "https:$it" else it }
                        ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

                    val durationStr = detailRuns?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
                        ?.lastOrNull { it.contains(":") }
                        ?: r["lengthSeconds"]?.jsonPrimitive?.contentOrNull

                    val durationMs = parseDurationMs(durationStr)

                    tracks.add(
                        Track(
                            id = "yt_$videoId",
                            title = title,
                            artist = artist,
                            album = album,
                            durationMs = durationMs,
                            artworkUrl = artworkUrl,
                            videoId = videoId,
                            streamUrl = null,
                            isLossless = false,
                            audioQuality = "Standard Stream"
                        )
                    )
                }
                tracks
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseDurationMs(raw: String?): Long {
        if (raw.isNullOrBlank()) return 0L
        if (raw.all { it.isDigit() }) {
            return (raw.toLongOrNull() ?: 0L) * 1000L
        }
        val parts = raw.split(":").mapNotNull { it.toLongOrNull() }
        return when (parts.size) {
            2 -> (parts[0] * 60 + parts[1]) * 1000L
            3 -> (parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000L
            else -> 0L
        }
    }

    private fun collectObjects(element: JsonElement, targetKey: String, sink: MutableList<JsonObject>) {
        when (element) {
            is JsonObject -> {
                element[targetKey]?.let { match ->
                    if (match is JsonObject) sink.add(match)
                }
                for ((_, child) in element) {
                    collectObjects(child, targetKey, sink)
                }
            }
            is JsonArray -> {
                for (child in element) {
                    collectObjects(child, targetKey, sink)
                }
            }
            else -> {}
        }
    }
}
