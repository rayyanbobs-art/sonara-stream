package com.sonara.desktop.data

import com.sonara.desktop.model.LastFmStats
import com.sonara.desktop.model.LastFmUser
import com.sonara.desktop.model.ScrobbleItem
import com.sonara.desktop.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class LastFmClient(
    private val apiKey: String = "2e00eb783c677abeab81e99c99be74e1",
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun getUserInfo(username: String): LastFmUser? = withContext(Dispatchers.IO) {
        if (username.isBlank()) return@withContext null
        try {
            val url = "https://ws.audioscrobbler.com/2.0/".toHttpUrlOrNull()!!.newBuilder()
                .addQueryParameter("method", "user.getinfo")
                .addQueryParameter("user", username.trim())
                .addQueryParameter("api_key", apiKey)
                .addQueryParameter("format", "json")
                .build()

            val req = Request.Builder().url(url).header("User-Agent", "SonaraStream/4.0.0").get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string() ?: return@withContext null
                val root = json.parseToJsonElement(body).jsonObject
                val userObj = root["user"]?.jsonObject ?: return@withContext null
                val name = userObj["name"]?.jsonPrimitive?.contentOrNull ?: username
                val playcount = userObj["playcount"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L

                val images = userObj["image"]?.jsonArray.orEmpty()
                val avatarUrl = images.lastOrNull()?.jsonObject?.get("#text")?.jsonPrimitive?.contentOrNull

                LastFmUser(
                    name = name,
                    playcount = playcount,
                    avatarUrl = avatarUrl?.takeIf { it.isNotBlank() },
                    isConnected = true
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getUserStats(username: String): LastFmStats = withContext(Dispatchers.IO) {
        if (username.isBlank()) return@withContext LastFmStats()
        try {
            val user = getUserInfo(username)
            val playcount = user?.playcount ?: 13L

            // Fetch top artists count
            val artistsCount = fetchTopCount(username, "user.gettopartists", "topartists", "artist")
            val tracksCount = fetchTopCount(username, "user.gettoptracks", "toptracks", "track")
            val albumsCount = fetchTopCount(username, "user.gettopalbums", "topalbums", "album")

            val minutes = (playcount * 3.5).toLong()
            val hours = minutes / 60
            val remMins = minutes % 60
            val timeStr = if (hours > 0) "${hours}h ${remMins}m" else "${remMins}m 0s"

            LastFmStats(
                scrobbles = playcount,
                tracks = if (tracksCount > 0) tracksCount else playcount,
                artists = if (artistsCount > 0) artistsCount else 10L,
                albums = if (albumsCount > 0) albumsCount else 12L,
                listeningTime = timeStr
            )
        } catch (_: Exception) {
            LastFmStats()
        }
    }

    private fun fetchTopCount(username: String, method: String, rootKey: String, itemKey: String): Long {
        return try {
            val url = "https://ws.audioscrobbler.com/2.0/".toHttpUrlOrNull()!!.newBuilder()
                .addQueryParameter("method", method)
                .addQueryParameter("user", username)
                .addQueryParameter("api_key", apiKey)
                .addQueryParameter("limit", "1")
                .addQueryParameter("format", "json")
                .build()

            val req = Request.Builder().url(url).header("User-Agent", "SonaraStream/4.0.0").get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return 0L
                val body = resp.body?.string() ?: return 0L
                val root = json.parseToJsonElement(body).jsonObject
                val container = root[rootKey]?.jsonObject ?: return 0L
                val attr = container["@attr"]?.jsonObject
                attr?.get("total")?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
            }
        } catch (_: Exception) {
            0L
        }
    }

    suspend fun getRecentTracks(username: String, limit: Int = 30): List<ScrobbleItem> = withContext(Dispatchers.IO) {
        if (username.isBlank()) return@withContext emptyList()
        try {
            val url = "https://ws.audioscrobbler.com/2.0/".toHttpUrlOrNull()!!.newBuilder()
                .addQueryParameter("method", "user.getrecenttracks")
                .addQueryParameter("user", username.trim())
                .addQueryParameter("api_key", apiKey)
                .addQueryParameter("limit", limit.toString())
                .addQueryParameter("format", "json")
                .build()

            val req = Request.Builder().url(url).header("User-Agent", "SonaraStream/4.0.0").get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val body = resp.body?.string() ?: return@withContext emptyList()
                val root = json.parseToJsonElement(body).jsonObject
                val recentObj = root["recenttracks"]?.jsonObject ?: return@withContext emptyList()
                val trackElement = recentObj["track"]

                val list = mutableListOf<ScrobbleItem>()
                val array = when (trackElement) {
                    is JsonArray -> trackElement
                    is JsonObject -> JsonArray(listOf(trackElement))
                    else -> null
                }

                array?.forEach { item ->
                    val obj = item.jsonObject
                    val title = obj["name"]?.jsonPrimitive?.contentOrNull ?: ""
                    val artist = obj["artist"]?.jsonObject?.get("#text")?.jsonPrimitive?.contentOrNull ?: ""
                    val album = obj["album"]?.jsonObject?.get("#text")?.jsonPrimitive?.contentOrNull ?: ""
                    val images = obj["image"]?.jsonArray.orEmpty()
                    val artworkUrl = images.lastOrNull()?.jsonObject?.get("#text")?.jsonPrimitive?.contentOrNull
                    val dateObj = obj["date"]?.jsonObject
                    val timestamp = dateObj?.get("uts")?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
                    val isNowPlaying = obj["@attr"]?.jsonObject?.get("nowplaying")?.jsonPrimitive?.contentOrNull == "true"

                    if (title.isNotBlank()) {
                        list.add(
                            ScrobbleItem(
                                title = title,
                                artist = artist,
                                album = album,
                                artworkUrl = artworkUrl?.takeIf { it.isNotBlank() },
                                timestamp = timestamp,
                                isNowPlaying = isNowPlaying
                            )
                        )
                    }
                }
                list
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getDiscoverTracks(limit: Int = 30): List<Track> = withContext(Dispatchers.IO) {
        try {
            val url = "https://ws.audioscrobbler.com/2.0/".toHttpUrlOrNull()!!.newBuilder()
                .addQueryParameter("method", "chart.gettoptracks")
                .addQueryParameter("api_key", apiKey)
                .addQueryParameter("limit", limit.toString())
                .addQueryParameter("format", "json")
                .build()

            val req = Request.Builder().url(url).header("User-Agent", "SonaraStream/4.0.0").get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext getDefaultDiscoverTracks()
                val body = resp.body?.string() ?: return@withContext getDefaultDiscoverTracks()
                val root = json.parseToJsonElement(body).jsonObject
                val tracksArray = root["tracks"]?.jsonObject?.get("track")?.jsonArray ?: return@withContext getDefaultDiscoverTracks()

                val result = mutableListOf<Track>()
                for (item in tracksArray) {
                    val obj = item.jsonObject
                    val title = obj["name"]?.jsonPrimitive?.contentOrNull ?: continue
                    val artist = obj["artist"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull ?: "Unknown Artist"
                    val images = obj["image"]?.jsonArray.orEmpty()
                    val artworkUrl = images.lastOrNull()?.jsonObject?.get("#text")?.jsonPrimitive?.contentOrNull
                    val durationSeconds = obj["duration"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 210L

                    result.add(
                        Track(
                            id = "lfm_${title.hashCode()}_${artist.hashCode()}",
                            title = title,
                            artist = artist,
                            album = "Last.fm Charts",
                            durationMs = durationSeconds * 1000L,
                            artworkUrl = artworkUrl?.takeIf { it.isNotBlank() },
                            audioQuality = "Lossless FLAC"
                        )
                    )
                }
                if (result.isNotEmpty()) result else getDefaultDiscoverTracks()
            }
        } catch (_: Exception) {
            getDefaultDiscoverTracks()
        }
    }

    fun getDefaultDiscoverTracks(): List<Track> {
        return listOf(
            Track("1", "Eye of the Tiger", "Survivor", "Eye of the Tiger", 245000L, "https://i.ytimg.com/vi/btPJPFnesV4/hqdefault.jpg", audioQuality = "Lossless FLAC"),
            Track("2", "Thunderstruck", "AC/DC", "The Razors Edge", 292000L, "https://i.ytimg.com/vi/v2AC41dglnM/hqdefault.jpg", audioQuality = "Lossless FLAC"),
            Track("3", "Smoke On The Water (2024 Remaster)", "Deep Purple", "Machine Head", 340000L, "https://i.ytimg.com/vi/zUwEIt9ez7M/hqdefault.jpg", audioQuality = "Lossless FLAC"),
            Track("4", "I Was Made For Lovin' You", "Kiss", "Dynasty", 270000L, "https://i.ytimg.com/vi/ZhIsAZO56XY/hqdefault.jpg", audioQuality = "Lossless FLAC"),
            Track("5", "War Machine", "AC/DC", "Black Ice", 189000L, "https://i.ytimg.com/vi/k0u_V-i_z7M/hqdefault.jpg", audioQuality = "Lossless FLAC"),
            Track("6", "Don't Stop Believin'", "Journey", "Escape", 251000L, "https://i.ytimg.com/vi/1k8craCGpgs/hqdefault.jpg", audioQuality = "Lossless FLAC"),
            Track("7", "Back In Black", "AC/DC", "Back In Black", 255000L, "https://i.ytimg.com/vi/pAgnJDJN4VA/hqdefault.jpg", audioQuality = "Lossless FLAC"),
            Track("8", "Every Breath You Take", "The Police", "Synchronicity", 253000L, "https://i.ytimg.com/vi/OMOGaugKtzs/hqdefault.jpg", audioQuality = "Lossless FLAC"),
            Track("9", "Smooth Criminal (Radio Edit)", "Michael Jackson", "Bad", 257000L, "https://i.ytimg.com/vi/h_D3VFkatAQ/hqdefault.jpg", audioQuality = "Lossless FLAC"),
            Track("10", "Another Day in Paradise", "Phil Collins", "...But Seriously", 322000L, "https://i.ytimg.com/vi/Qt2mbGP6vFI/hqdefault.jpg", audioQuality = "Lossless FLAC")
        )
    }
}
