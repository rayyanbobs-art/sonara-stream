package com.sonara.desktop.audio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class DesktopAudioCache(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
) {
    private val cacheDir = File(System.getProperty("user.home"), ".sonara/cache/audio").apply {
        mkdirs()
    }
    private val activeDownloads = ConcurrentHashMap<String, File>()

    fun getCachedFile(videoId: String): File? {
        val safeName = sanitizeFilename(videoId)
        val file = File(cacheDir, "$safeName.m4a")
        return if (file.exists() && file.length() > 0) file else null
    }

    suspend fun ensureTrackCached(videoId: String, streamUrl: String): File = withContext(Dispatchers.IO) {
        val safeName = sanitizeFilename(videoId)
        val finalFile = File(cacheDir, "$safeName.m4a")

        if (finalFile.exists() && finalFile.length() > 0) {
            return@withContext finalFile
        }

        val tmpFile = File(cacheDir, "$safeName.tmp")

        try {
            // 1. Discover total length with Range: bytes=0-0 to bypass YouTube throttling
            var totalLength = 0L
            val probeReq = Request.Builder()
                .url(streamUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Range", "bytes=0-0")
                .build()

            client.newCall(probeReq).execute().use { resp ->
                val cr = resp.header("Content-Range")
                totalLength = cr?.substringAfterLast("/")?.toLongOrNull() ?: 0L
            }

            if (totalLength <= 0L) {
                // Fallback to standard request if range discovery unavailable
                val fullReq = Request.Builder()
                    .url(streamUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build()
                client.newCall(fullReq).execute().use { resp ->
                    resp.body?.byteStream()?.use { input ->
                        FileOutputStream(tmpFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            } else {
                // Download in 1MB un-throttled chunks for maximum transfer speed (~500ms total)
                val chunkSize = 1024 * 1024L
                var offset = 0L

                FileOutputStream(tmpFile).use { os ->
                    while (offset < totalLength) {
                        val end = minOf(offset + chunkSize - 1, totalLength - 1)
                        val chunkReq = Request.Builder()
                            .url(streamUrl)
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                            .header("Range", "bytes=$offset-$end")
                            .build()

                        client.newCall(chunkReq).execute().use { resp ->
                            resp.body?.byteStream()?.copyTo(os)
                        }
                        offset = end + 1
                    }
                }
            }

            if (tmpFile.exists() && tmpFile.length() > 0) {
                if (finalFile.exists()) finalFile.delete()
                tmpFile.renameTo(finalFile)
                return@withContext finalFile
            }
        } catch (e: Exception) {
            tmpFile.delete()
        }

        // If download failed, return whatever exists or temp file
        finalFile
    }

    private fun sanitizeFilename(input: String): String {
        return input.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    }
}
