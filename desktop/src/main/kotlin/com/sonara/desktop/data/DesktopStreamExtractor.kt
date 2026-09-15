package com.sonara.desktop.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.IOException

class DesktopStreamExtractor(
    private val http: OkHttpClient = OkHttpClient()
) {
    private val downloader = OkHttpNewPipeDownloader(http)

    @Volatile
    private var initialized = false

    private fun initialize() {
        if (!initialized) {
            synchronized(this) {
                if (!initialized) {
                    NewPipe.init(downloader)
                    initialized = true
                }
            }
        }
    }

    suspend fun resolveAudioStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        initialize()
        try {
            val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId")
            // Prefer m4a/aac for native JavaFX media playback compatibility
            val m4aStream = info.audioStreams
                .filter { it.format?.mimeType?.contains("mp4") == true || it.format?.mimeType?.contains("m4a") == true }
                .maxByOrNull { maxOf(it.averageBitrate, it.bitrate) }

            val chosen = m4aStream ?: info.audioStreams.maxByOrNull { maxOf(it.averageBitrate, it.bitrate) }
            chosen?.content
        } catch (e: Exception) {
            null
        }
    }
}

private class OkHttpNewPipeDownloader(
    private val http: OkHttpClient
) : Downloader() {
    override fun execute(request: Request): Response {
        val headers = request.headers()
        val requestBuilder = okhttp3.Request.Builder()
            .url(request.url())
            .method(request.httpMethod(), request.dataToSend()?.toRequestBody())

        headers.forEach { (name, values) ->
            values.forEach { value -> requestBuilder.addHeader(name, value) }
        }
        if (headers.keys.none { it.equals("User-Agent", ignoreCase = true) }) {
            requestBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        }
        if (headers.keys.none { it.equals("Accept-Language", ignoreCase = true) }) {
            requestBuilder.header("Accept-Language", "en-US,en;q=0.9")
        }
        val origin = if (request.url().contains("music.youtube.com", ignoreCase = true)) {
            "https://music.youtube.com"
        } else {
            "https://www.youtube.com"
        }
        if (headers.keys.none { it.equals("Origin", ignoreCase = true) }) {
            requestBuilder.header("Origin", origin)
        }
        if (headers.keys.none { it.equals("Referer", ignoreCase = true) }) {
            requestBuilder.header("Referer", "$origin/")
        }

        return http.newCall(requestBuilder.build()).execute().use { response ->
            Response(
                response.code,
                response.message,
                response.headers.names().associateWith(response.headers::values),
                response.body?.string().orEmpty(),
                response.request.url.toString()
            )
        }
    }
}
