package com.sonara.desktop.data

import com.sonara.desktop.model.Track
import com.sonara.desktop.storage.DesktopDatabase
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class DesktopArtworkResolver(
    private val database: DesktopDatabase,
    private val apiKey: String = "2e00eb783c677abeab81e99c99be74e1",
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val memoryCache = ConcurrentHashMap<String, String>()

    companion object {
        const val LASTFM_NO_ART_HASH = "2a96cbd8b46e442fc41c2b86b821562f"

        fun isRealArtwork(url: String?): Boolean {
            return !url.isNullOrBlank() && !url.contains(LASTFM_NO_ART_HASH)
        }

        fun cacheKey(title: String, artist: String): String {
            return "lfm:${title.trim().lowercase()}:${artist.trim().lowercase()}"
        }
    }

    suspend fun resolveArtwork(title: String, artist: String, albumHint: String = ""): String? = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext null
        val key = cacheKey(title, artist)

        // 1. In-memory cache
        memoryCache[key]?.let { cachedUrl ->
            if (isRealArtwork(cachedUrl)) return@withContext cachedUrl
        }

        // 2. Persistent SQLite cache
        try {
            val dbCached = database.getCachedArtwork(key)
            if (isRealArtwork(dbCached)) {
                memoryCache[key] = dbCached!!
                return@withContext dbCached
            }
        } catch (_: Exception) {}

        // 3. Query Last.fm track.getInfo
        var resolvedUrl: String? = null
        var resolvedAlbum = albumHint.ifBlank { "" }

        try {
            val urlBuilder = "https://ws.audioscrobbler.com/2.0/".toHttpUrlOrNull()!!.newBuilder()
                .addQueryParameter("method", "track.getInfo")
                .addQueryParameter("track", title.trim())
                .addQueryParameter("artist", artist.trim())
                .addQueryParameter("autocorrect", "1")
                .addQueryParameter("api_key", apiKey)
                .addQueryParameter("format", "json")

            val req = Request.Builder().url(urlBuilder.build()).header("User-Agent", "SonaraStream/4.0.0").get().build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    if (body.isNotBlank()) {
                        val root = json.parseToJsonElement(body).jsonObject
                        val trackObj = root["track"]?.jsonObject
                        val albumObj = trackObj?.get("album")?.jsonObject

                        if (resolvedAlbum.isBlank()) {
                            resolvedAlbum = albumObj?.get("title")?.jsonPrimitive?.contentOrNull.orEmpty()
                        }

                        // Try album images first
                        val albumImages = albumObj?.get("image")?.jsonArray.orEmpty()
                        resolvedUrl = extractBestImage(albumImages)

                        // Fallback to track images
                        if (!isRealArtwork(resolvedUrl)) {
                            val trackImages = trackObj?.get("image")?.jsonArray.orEmpty()
                            resolvedUrl = extractBestImage(trackImages)
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // 4. If still no image, try Last.fm album.getInfo
        if (!isRealArtwork(resolvedUrl) && resolvedAlbum.isNotBlank()) {
            try {
                val albumUrl = "https://ws.audioscrobbler.com/2.0/".toHttpUrlOrNull()!!.newBuilder()
                    .addQueryParameter("method", "album.getInfo")
                    .addQueryParameter("album", resolvedAlbum.trim())
                    .addQueryParameter("artist", artist.trim())
                    .addQueryParameter("autocorrect", "1")
                    .addQueryParameter("api_key", apiKey)
                    .addQueryParameter("format", "json")
                    .build()

                val albumReq = Request.Builder().url(albumUrl).header("User-Agent", "SonaraStream/4.0.0").get().build()
                client.newCall(albumReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string().orEmpty()
                        if (body.isNotBlank()) {
                            val root = json.parseToJsonElement(body).jsonObject
                            val albumObj = root["album"]?.jsonObject
                            val images = albumObj?.get("image")?.jsonArray.orEmpty()
                            resolvedUrl = extractBestImage(images)
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // Cache result if valid
        if (isRealArtwork(resolvedUrl)) {
            val finalUrl = resolvedUrl!!
            memoryCache[key] = finalUrl
            try {
                database.saveCachedArtwork(key, finalUrl)
            } catch (_: Exception) {}
            return@withContext finalUrl
        }

        null
    }

    suspend fun resolveTracks(tracks: List<Track>): List<Track> = coroutineScope {
        tracks.map { track ->
            async(Dispatchers.IO) {
                if (isRealArtwork(track.artworkUrl)) {
                    track
                } else {
                    val realArt = resolveArtwork(track.title, track.artist, track.album)
                    if (realArt != null) {
                        track.copy(artworkUrl = realArt)
                    } else {
                        track.copy(artworkUrl = null) // clean placeholder star
                    }
                }
            }
        }.awaitAll()
    }

    private fun extractBestImage(images: List<JsonElement>): String? {
        val bySize = { size: String ->
            images.firstOrNull {
                it.jsonObject["size"]?.jsonPrimitive?.contentOrNull == size &&
                        isRealArtwork(it.jsonObject["#text"]?.jsonPrimitive?.contentOrNull)
            }?.jsonObject?.get("#text")?.jsonPrimitive?.contentOrNull
        }

        return bySize("extralarge")
            ?: bySize("large")
            ?: bySize("medium")
            ?: images.mapNotNull { it.jsonObject["#text"]?.jsonPrimitive?.contentOrNull }
                .firstOrNull { isRealArtwork(it) }
    }
}
