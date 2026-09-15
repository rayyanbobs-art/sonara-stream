package com.sonara.desktop.audio

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class DesktopAudioProxy(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
) {
    private var server: HttpServer? = null
    private var port: Int = 0
    private val executor = Executors.newCachedThreadPool { r ->
        Thread(r, "sonara-audio-proxy").apply { isDaemon = true }
    }
    private val urlMap = ConcurrentHashMap<String, String>()

    fun start(): Int {
        if (server != null) return port
        try {
            val s = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            s.executor = executor
            s.createContext("/stream.m4a") { exchange -> handleRequest(exchange, "audio/x-m4a") }
            s.createContext("/stream.mp3") { exchange -> handleRequest(exchange, "audio/mpeg") }
            s.start()
            server = s
            port = s.address.port
            return port
        } catch (e: Exception) {
            return 0
        }
    }

    fun getPlaybackUrl(remoteUrl: String): String {
        if (remoteUrl.startsWith("file:") || remoteUrl.startsWith("http://127.0.0.1")) {
            return remoteUrl
        }
        if (port == 0) {
            start()
        }
        if (port == 0) {
            return remoteUrl
        }

        val id = Integer.toHexString(remoteUrl.hashCode())
        urlMap[id] = remoteUrl

        val isMp3 = remoteUrl.contains(".mp3", ignoreCase = true)
        val endpoint = if (isMp3) "stream.mp3" else "stream.m4a"
        return "http://127.0.0.1:$port/$endpoint?id=$id"
    }

    private fun handleRequest(exchange: HttpExchange, defaultMimeType: String) {
        val method = exchange.requestMethod
        val uri = exchange.requestURI
        val rangeHeader = exchange.requestHeaders.getFirst("Range")
        println("[Proxy] ---> $method $uri Range=$rangeHeader")

        try {
            val query = uri.query.orEmpty()
            val params = parseQuery(query)
            val id = params["id"].orEmpty()
            val targetUrl = urlMap[id] ?: params["url"]?.let { URLDecoder.decode(it, "UTF-8") }

            if (targetUrl.isNullOrBlank()) {
                println("[Proxy] Target URL not found for id=$id")
                exchange.sendResponseHeaders(404, -1)
                exchange.close()
                return
            }

            val okReqBuilder = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "*/*")
                .header("Referer", "https://music.youtube.com/")
                .header("Origin", "https://music.youtube.com")

            if (!rangeHeader.isNullOrBlank()) {
                okReqBuilder.header("Range", rangeHeader)
            }

            val okResp = client.newCall(okReqBuilder.build()).execute()
            val code = okResp.code
            val body = okResp.body
            val contentLength = body?.contentLength() ?: -1L
            val contentRange = okResp.header("Content-Range")

            println("[Proxy] <--- Remote response: code=$code length=$contentLength contentRange=$contentRange")

            val responseHeaders = exchange.responseHeaders
            responseHeaders.set("Content-Type", defaultMimeType)
            responseHeaders.set("Accept-Ranges", "bytes")
            if (!contentRange.isNullOrBlank()) {
                responseHeaders.set("Content-Range", contentRange)
            }

            if (method.equals("HEAD", ignoreCase = true)) {
                exchange.sendResponseHeaders(code, -1)
                exchange.close()
                println("[Proxy] HEAD response sent")
                return
            }

            exchange.sendResponseHeaders(code, if (contentLength > 0) contentLength else 0)
            val os = exchange.responseBody
            var totalWritten = 0L
            try {
                body?.byteStream()?.use { input ->
                    val buf = ByteArray(32768)
                    var read: Int
                    while (input.read(buf).also { read = it } != -1) {
                        os.write(buf, 0, read)
                        os.flush()
                        totalWritten += read
                        println("[Proxy] Written $read bytes, total=$totalWritten / $contentLength")
                    }
                }
                os.flush()
                println("[Proxy] Finished writing body successfully. Total bytes: $totalWritten")
            } catch (e: Exception) {
                println("[Proxy] Stream transfer ended after $totalWritten bytes: ${e.javaClass.simpleName} - ${e.message}")
            } finally {
                try { os.close() } catch (_: Exception) {}
                try { exchange.close() } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            println("[Proxy] Error handling request: ${e.message}")
            e.printStackTrace()
            try { exchange.close() } catch (_: Exception) {}
        }
    }

    private fun parseQuery(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        return query.split("&").mapNotNull {
            val parts = it.split("=", limit = 2)
            if (parts.size == 2) parts[0] to parts[1] else null
        }.toMap()
    }

    fun stop() {
        try {
            server?.stop(0)
            server = null
            port = 0
            executor.shutdownNow()
        } catch (_: Exception) {}
    }
}
