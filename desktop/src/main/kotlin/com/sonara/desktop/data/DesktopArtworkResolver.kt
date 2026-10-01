package com.sonara.desktop.data

import com.sonara.desktop.model.Track
import com.sonara.desktop.storage.DesktopDatabase
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import androidx.compose.ui.graphics.ImageBitmap
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object DesktopArtworkCache {
    private val cache = ConcurrentHashMap<String, ImageBitmap>()

    fun get(url: String): ImageBitmap? = cache[url]

    fun put(url: String, bitmap: ImageBitmap) {
        if (cache.size > 250) {
            val toRemove = cache.keys().toList().take(50)
            toRemove.forEach { cache.remove(it) }
        }
        cache[url] = bitmap
    }

    fun clear() {
        cache.clear()
    }
}

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

        fun upgradeQuality(url: String?): String? {
            if (url.isNullOrBlank() || !isRealArtwork(url)) return null
            var upgraded = url.trim()

            // 1. Google / YouTube user content (lh3.googleusercontent.com, yt3.ggpht.com, etc.)
            // Upgrade small thumbnail parameters (=w120-h120, =s60, etc.) to 1200x1200 studio resolution
            if (upgraded.contains("googleusercontent.com") || upgraded.contains("ggpht.com")) {
                upgraded = upgraded.replace(Regex("=w\\d+-h\\d+.*$"), "=w1200-h1200-l90-rj")
                upgraded = upgraded.replace(Regex("=s\\d+.*$"), "=s1200-c-k-c0x00ffffff-no-rj")
                upgraded = upgraded.replace(Regex("/w\\d+-h\\d+/"), "/w1200-h1200/")
            }

            // 2. YouTube static thumbnails (i.ytimg.com / img.youtube.com)
            // Upgrade hqdefault/mqdefault/default/sddefault to maxresdefault (1280x720)
            if (upgraded.contains("ytimg.com") || upgraded.contains("youtube.com")) {
                upgraded = upgraded.replace(Regex("/(hqdefault|mqdefault|default|sddefault)\\.jpg(\\?.*)?$"), "/maxresdefault.jpg$2")
            }

            // 3. Apple Music / iTunes Store
            // Upgrade 100x100bb or 60x60bb to 1200x1200bb studio master
            if (upgraded.contains("mzstatic.com") || upgraded.contains("apple.com")) {
                upgraded = upgraded.replace(Regex("/\\d+x\\d+bb\\.(jpg|png|webp)$", RegexOption.IGNORE_CASE), "/1200x1200bb.$1")
            }

            // 4. Last.fm CDN (fastly / akamaized)
            // Replace /300x300/, /174s/, /64s/, /avatar170s/ with /_/ for uncompressed original master image
            if (upgraded.contains("lastfm") || upgraded.contains("fastly.net")) {
                upgraded = upgraded.replace(Regex("/i/u/(300x300|174s|64s|avatar170s)/"), "/i/u/_/")
            }

            // 5. Spotify CDN
            // Upgrade 300x300 or 64x64 hash to 640x640 high-res master
            if (upgraded.contains("scdn.co") || upgraded.contains("spotifycdn.com")) {
                upgraded = upgraded.replace("ab67616d00001e02", "ab67616d0000b273")
                    .replace("ab67616d00004851", "ab67616d0000b273")
            }

            // 6. Deezer CDN
            // Upgrade to 1000x1000
            if (upgraded.contains("dzcdn.net") || upgraded.contains("deezer.com")) {
                upgraded = upgraded.replace(Regex("/\\d+x\\d+(-000000-80-0-0\\.jpg)"), "/1000x1000$1")
            }

            return upgraded
        }
    }

    suspend fun resolveArtwork(title: String, artist: String, albumHint: String = ""): String? = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext null
        val key = cacheKey(title, artist)

        // 1. In-memory cache
        memoryCache[key]?.let { cachedUrl ->
            if (isRealArtwork(cachedUrl)) return@withContext upgradeQuality(cachedUrl)
        }

        // 2. Persistent SQLite cache
        try {
            val dbCached = database.getCachedArtwork(key)
            if (isRealArtwork(dbCached)) {
                val upgraded = upgradeQuality(dbCached) ?: dbCached!!
                memoryCache[key] = upgraded
                return@withContext upgraded
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

        // 5. iTunes search fallback for studio master 1200x1200 artwork
        if (!isRealArtwork(resolvedUrl)) {
            val itunesUrl = queryITunesArtwork(title, artist)
            if (isRealArtwork(itunesUrl)) {
                resolvedUrl = itunesUrl
            }
        }

        // Cache result if valid
        if (isRealArtwork(resolvedUrl)) {
            val finalUrl = upgradeQuality(resolvedUrl) ?: resolvedUrl!!
            memoryCache[key] = finalUrl
            try {
                database.saveCachedArtwork(key, finalUrl)
            } catch (_: Exception) {}
            return@withContext finalUrl
        }

        null
    }

    private fun cleanTitle(title: String): String = title
        .replace(Regex("(?i)\\s*[(|\\[](feat|ft|with|featuring)\\.?\\s+.*?[)|\\]]"), "")
        .replace(Regex("(?i)\\s*[(|\\[].*?(remaster|live|version|edit|mono|stereo|deluxe|bonus).*?[)|\\]]"), "")
        .trim()

    private fun cleanArtist(artist: String): String = artist
        .replace(Regex("(?i)\\s*(feat|ft|with|&|,|/|x)\\s+.*"), "")
        .trim()

    private suspend fun queryITunesArtwork(title: String, artist: String): String? = withContext(Dispatchers.IO) {
        val search = suspend { query: String ->
            try {
                val url = "https://itunes.apple.com/search?term=${URLEncoder.encode(query, "UTF-8")}&media=music&entity=song&limit=1"
                val req = Request.Builder().url(url).header("User-Agent", "SonaraStream/4.0.0").get().build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string().orEmpty()
                        if (body.isNotBlank()) {
                            val root = json.parseToJsonElement(body).jsonObject
                            val results = root["results"]?.jsonArray
                            val first = results?.firstOrNull()?.jsonObject
                            val rawUrl = first?.get("artworkUrl100")?.jsonPrimitive?.contentOrNull
                                ?: first?.get("artworkUrl60")?.jsonPrimitive?.contentOrNull
                            upgradeQuality(rawUrl)
                        } else null
                    } else null
                }
            } catch (_: Exception) {
                null
            }
        }

        val directTerm = if (artist.isNotBlank()) "${title.trim()} ${artist.trim()}" else title.trim()
        var result = search(directTerm)
        if (result == null) {
            val cTitle = cleanTitle(title)
            val cArtist = cleanArtist(artist)
            val cleanedTerm = if (cArtist.isNotBlank()) "$cTitle $cArtist" else cTitle
            if (cleanedTerm != directTerm && cleanedTerm.isNotBlank()) {
                result = search(cleanedTerm)
            }
        }
        result
    }

    suspend fun resolveTracks(tracks: List<Track>): List<Track> = coroutineScope {
        tracks.map { track ->
            async(Dispatchers.IO) {
                if (isRealArtwork(track.artworkUrl)) {
                    val upgraded = upgradeQuality(track.artworkUrl)
                    if (upgraded != null && upgraded != track.artworkUrl) {
                        track.copy(artworkUrl = upgraded)
                    } else {
                        track
                    }
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

        val raw = bySize("extralarge")
            ?: bySize("large")
            ?: bySize("medium")
            ?: images.mapNotNull { it.jsonObject["#text"]?.jsonPrimitive?.contentOrNull }
                .firstOrNull { isRealArtwork(it) }

        return upgradeQuality(raw)
    }
}
