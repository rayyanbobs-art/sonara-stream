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
import java.net.ServerSocket
import java.util.concurrent.TimeUnit

class LastFmClient(
    val apiKey: String = "2e00eb783c677abeab81e99c99be74e1",
    val apiSecret: String = "b7e562de696f17fdfde7c448f02b599f",
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun getAuthUrl(callbackUrl: String = "http://localhost:8989/callback"): String {
        return "https://www.last.fm/api/auth/?api_key=$apiKey&cb=${java.net.URLEncoder.encode(callbackUrl, "UTF-8")}"
    }

    suspend fun completeWebAuth(token: String): Pair<String, String>? = withContext(Dispatchers.IO) {
        try {
            val signParams = mapOf(
                "method" to "auth.getSession",
                "token" to token.trim(),
                "api_key" to apiKey
            )
            val apiSig = LastFmSigner.sign(signParams, apiSecret)
            val url = "https://ws.audioscrobbler.com/2.0/".toHttpUrlOrNull()!!.newBuilder()
                .addQueryParameter("method", "auth.getSession")
                .addQueryParameter("token", token.trim())
                .addQueryParameter("api_key", apiKey)
                .addQueryParameter("api_sig", apiSig)
                .addQueryParameter("format", "json")
                .build()

            val req = Request.Builder().url(url).header("User-Agent", "SonaraStream/4.0.0").get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string() ?: return@withContext null
                val root = json.parseToJsonElement(body).jsonObject
                val session = root["session"]?.jsonObject ?: return@withContext null
                val name = session["name"]?.jsonPrimitive?.contentOrNull ?: return@withContext null
                val key = session["key"]?.jsonPrimitive?.contentOrNull ?: ""
                Pair(name, key)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun startLocalAuthServer(port: Int = 8989, onTokenReceived: (String) -> Unit): ServerSocket? {
        return try {
            val server = ServerSocket(port)
            Thread {
                try {
                    val socket = server.accept()
                    val reader = socket.getInputStream().bufferedReader()
                    val requestLine = reader.readLine() ?: ""
                    val tokenRegex = Regex("[?&]token=([^&\\s]+)")
                    val tokenMatch = tokenRegex.find(requestLine)
                    val token = tokenMatch?.groupValues?.get(1)

                    val html = """
                        <!DOCTYPE html>
                        <html>
                        <head><meta charset="utf-8"><title>Sonara Stream</title>
                        <style>
                          body { background: #131413; color: #E2E3DE; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; display: flex; align-items: center; justify-content: center; height: 100vh; margin: 0; }
                          .card { background: #1B1D1A; padding: 48px; border-radius: 28px; text-align: center; border: 1px solid #272B25; max-width: 420px; box-shadow: 0 10px 30px rgba(0,0,0,0.5); }
                          .icon { font-size: 48px; margin-bottom: 16px; color: #9AD48E; }
                          h1 { color: #E2E3DE; font-size: 24px; margin: 0 0 10px 0; }
                          p { color: #A6A8A1; font-size: 15px; margin: 0; line-height: 1.5; }
                        </style>
                        </head>
                        <body>
                          <div class="card">
                            <div class="icon">&#10003;</div>
                            <h1>Connected to Last.fm!</h1>
                            <p>Your account is approved. You can now close this tab and return to <strong>Sonara Stream</strong>.</p>
                          </div>
                        </body>
                        </html>
                    """.trimIndent()

                    val writer = socket.getOutputStream().bufferedWriter()
                    writer.write("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${html.toByteArray().size}\r\nConnection: close\r\n\r\n")
                    writer.write(html)
                    writer.flush()
                    socket.close()
                    server.close()

                    if (!token.isNullOrBlank()) {
                        onTokenReceived(token)
                    }
                } catch (_: Exception) {
                    try { server.close() } catch (_: Exception) {}
                }
            }.apply { isDaemon = true; start() }
            server
        } catch (_: Exception) {
            null
        }
    }

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

    suspend fun getUserTopTracks(username: String, limit: Int = 30): List<Track> = withContext(Dispatchers.IO) {
        if (username.isBlank()) return@withContext emptyList()
        try {
            val url = "https://ws.audioscrobbler.com/2.0/".toHttpUrlOrNull()!!.newBuilder()
                .addQueryParameter("method", "user.gettoptracks")
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
                val trackArray = root["toptracks"]?.jsonObject?.get("track")?.jsonArray ?: return@withContext emptyList()

                val list = mutableListOf<Track>()
                for (item in trackArray) {
                    val obj = item.jsonObject
                    val title = obj["name"]?.jsonPrimitive?.contentOrNull ?: continue
                    val artist = obj["artist"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull ?: "Unknown Artist"
                    val images = obj["image"]?.jsonArray.orEmpty()
                    val artworkUrl = images.lastOrNull()?.jsonObject?.get("#text")?.jsonPrimitive?.contentOrNull
                    val durationSeconds = obj["duration"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 210L
                    val playcount = obj["playcount"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 1L

                    list.add(
                        Track(
                            id = "lfm_top_${title.hashCode()}_${artist.hashCode()}",
                            title = title,
                            artist = artist,
                            album = "Top Track (${playcount} plays)",
                            durationMs = if (durationSeconds > 0) durationSeconds * 1000L else 210000L,
                            artworkUrl = artworkUrl?.takeIf { it.isNotBlank() },
                            audioQuality = "Lossless FLAC"
                        )
                    )
                }
                list
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getUserRecentTracksAsTracks(username: String, limit: Int = 30): List<Track> = withContext(Dispatchers.IO) {
        val scrobbles = getRecentTracks(username, limit)
        scrobbles.map { scrobble ->
            Track(
                id = "lfm_recent_${scrobble.title.hashCode()}_${scrobble.artist.hashCode()}",
                title = scrobble.title,
                artist = scrobble.artist,
                album = scrobble.album.ifBlank { "Recent Scrobble" },
                durationMs = 210000L,
                artworkUrl = scrobble.artworkUrl,
                audioQuality = "Lossless FLAC"
            )
        }
    }

    suspend fun getUserPersonalizedFeed(username: String): List<Track> = withContext(Dispatchers.IO) {
        val top = getUserTopTracks(username, 20)
        val recent = getUserRecentTracksAsTracks(username, 20)
        val seen = mutableSetOf<String>()
        val result = mutableListOf<Track>()
        for (i in 0 until maxOf(top.size, recent.size)) {
            if (i < recent.size) {
                val t = recent[i]
                val key = "${t.title.lowercase()}:${t.artist.lowercase()}"
                if (seen.add(key)) result.add(t)
            }
            if (i < top.size) {
                val t = top[i]
                val key = "${t.title.lowercase()}:${t.artist.lowercase()}"
                if (seen.add(key)) result.add(t)
            }
        }
        if (result.isEmpty()) getDiscoverTracks() else result
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
            Track("1", "The Final Countdown", "Europe", "The Final Countdown", 240000L, "https://i.ytimg.com/vi/9jK-NcRmVcw/hqdefault.jpg", audioQuality = "Lossless FLAC"),
            Track("2", "Back In Black", "AC/DC", "Back In Black", 255000L, "https://i.ytimg.com/vi/pAgnJDJN4VA/hqdefault.jpg", audioQuality = "Lossless FLAC"),
            Track("3", "Highway to Hell", "AC/DC", "Highway to Hell", 208000L, "https://i.ytimg.com/vi/gEPmA3USJdI/hqdefault.jpg", audioQuality = "Lossless FLAC"),
            Track("4", "Remember the Name (feat. Styles of Beyond)", "Fort Minor", "The Rising Tied", 230000L, "https://i.ytimg.com/vi/VDvr08sCPOc/hqdefault.jpg", audioQuality = "Lossless FLAC"),
            Track("5", "Thunderstruck", "AC/DC", "The Razors Edge", 292000L, "https://i.ytimg.com/vi/v2AC41dglnM/hqdefault.jpg", audioQuality = "Lossless FLAC"),
            Track("6", "Paranoid (2009 Remaster)", "Black Sabbath", "Paranoid", 167000L, "https://i.ytimg.com/vi/0qanF-91aJo/hqdefault.jpg", audioQuality = "Lossless FLAC"),
            Track("7", "Back in the Game", "Airbourne", "Black Dog Barking", 206000L, "https://i.ytimg.com/vi/W_a_Fj68F3I/hqdefault.jpg", audioQuality = "Lossless FLAC"),
            Track("8", "Fast Lane", "Bad Meets Evil", "Hell: The Sequel", 252000L, "https://i.ytimg.com/vi/rJOsjP33nF4/hqdefault.jpg", audioQuality = "Lossless FLAC"),
            Track("9", "Smoke On The Water (2024 Remaster)", "Deep Purple", "Machine Head", 340000L, "https://i.ytimg.com/vi/zUwEIt9ez7M/hqdefault.jpg", audioQuality = "Lossless FLAC"),
            Track("10", "Khasara", "Abdul Hannan", "Khasara", 160000L, "https://i.ytimg.com/vi/q_G_1KjVqfg/hqdefault.jpg", audioQuality = "Lossless FLAC")
        )
    }
}
