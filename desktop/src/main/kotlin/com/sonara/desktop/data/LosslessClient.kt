package com.sonara.desktop.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

@Serializable
data class LosslessStreamResult(
    val url: String,
    val mimeType: String = "audio/flac",
    val bitDepth: Int = 16,
    val samplingRate: Double = 44.1,
    val formatId: Int = 6,
    val bitrateKbps: Int? = null,
)

@Serializable
private data class TrackUrlResponse(
    val success: Boolean = false,
    val data: TrackUrlData? = null,
    val error: String? = null,
)

@Serializable
private data class TrackUrlData(
    val url: String? = null,
    @SerialName("format_id") val formatId: Int = 6,
    @SerialName("mime_type") val mimeType: String = "audio/flac",
    @SerialName("sampling_rate") val samplingRate: Double = 44.1,
    @SerialName("bit_depth") val bitDepth: Int = 16,
)

@Serializable
private data class CatalogSearchResponse(
    val success: Boolean = false,
    val results: CatalogSearchResults? = null,
)

@Serializable
private data class CatalogSearchResults(
    val tracks: CatalogTrackList? = null,
)

@Serializable
private data class CatalogTrackList(
    val items: List<CatalogTrackItem> = emptyList(),
)

@Serializable
private data class CatalogTrackItem(
    val id: Long,
    val title: String,
    val duration: Int = 0,
    val performer: CatalogPerformer? = null,
    val album: CatalogAlbumInfo? = null,
    val hires: Boolean = false,
    @SerialName("maximum_bit_depth") val maxBitDepth: Int? = null,
    @SerialName("maximum_sampling_rate") val maxSamplingRate: Double? = null,
)

@Serializable
private data class CatalogPerformer(val name: String = "")
@Serializable
private data class CatalogAlbumInfo(val title: String? = null)

class LosslessClient(
    private var baseUrl: String = System.getenv("LOSSLESS_BACKEND_URL") ?: "",
    private var apiKey: String = System.getenv("LOSSLESS_API_KEY") ?: "",
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    companion object {
        const val QUALITY_MAX_HI_RES = 27
        const val QUALITY_HI_RES_96 = 7
        const val QUALITY_CD_LOSSLESS = 6
        const val QUALITY_MP3_320 = 5
        const val QUALITY_STANDARD = -1
    }

    fun configure(url: String, key: String) {
        baseUrl = url.trimEnd('/')
        apiKey = key.trim()
    }

    fun isConfigured(): Boolean = baseUrl.isNotBlank()

    suspend fun resolveLosslessStream(
        title: String,
        artist: String,
        preferredQuality: Int = QUALITY_CD_LOSSLESS
    ): LosslessStreamResult? = withContext(Dispatchers.IO) {
        if (!isConfigured() || title.isBlank() || artist.isBlank()) return@withContext null

        try {
            // 1. Search backend for exact track ID
            val searchUrl = "$baseUrl/api/search".toHttpUrlOrNull()?.newBuilder()?.apply {
                addQueryParameter("q", "$title $artist")
                addQueryParameter("type", "track")
            }?.build() ?: return@withContext null

            val searchReq = Request.Builder().url(searchUrl).apply {
                if (apiKey.isNotBlank()) addHeader("X-API-Key", apiKey)
            }.get().build()

            val catalogId: Long? = client.newCall(searchReq).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string() ?: return@use null
                val parsed = json.decodeFromString<CatalogSearchResponse>(body)
                val items = parsed.results?.tracks?.items.orEmpty()
                items.firstOrNull()?.id
            }

            if (catalogId == null) return@withContext null

            // 2. Fetch stream URL for the track
            val urlEndpoint = "$baseUrl/api/track/$catalogId/url".toHttpUrlOrNull()?.newBuilder()?.apply {
                addQueryParameter("quality", preferredQuality.toString())
                addQueryParameter("fallback", "true")
                addQueryParameter("title", title)
                addQueryParameter("artist", artist)
            }?.build() ?: return@withContext null

            val urlReq = Request.Builder().url(urlEndpoint).apply {
                if (apiKey.isNotBlank()) addHeader("X-API-Key", apiKey)
            }.get().build()

            client.newCall(urlReq).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string() ?: return@use null
                val parsed = json.decodeFromString<TrackUrlResponse>(body)
                val data = parsed.data ?: return@use null
                val streamUrl = data.url?.takeIf { it.isNotBlank() } ?: return@use null

                LosslessStreamResult(
                    url = streamUrl,
                    mimeType = data.mimeType,
                    bitDepth = data.bitDepth,
                    samplingRate = data.samplingRate,
                    formatId = data.formatId,
                    bitrateKbps = if (data.formatId == QUALITY_MP3_320) 320 else null
                )
            }
        } catch (e: Exception) {
            null
        }
    }
}
