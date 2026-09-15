package com.sonara.desktop.update

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.awt.Desktop
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.util.concurrent.TimeUnit

data class DesktopUpdateState(
    val isChecking: Boolean = false,
    val isUpdateAvailable: Boolean = false,
    val currentVersion: String = "4.0.0",
    val latestVersion: String = "",
    val releaseTitle: String = "",
    val releaseNotes: String = "",
    val releaseUrl: String = "",
    val downloadUrl: String? = null,
    val isDismissed: Boolean = false,
    val message: String? = null,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f, // 0.0 to 1.0
    val downloadStatus: String? = null
)

class DesktopUpdateManager(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        const val CURRENT_VERSION = "4.0.0"
        const val GITHUB_REPO = "rayyanbobs-art/sonara-stream"
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(DesktopUpdateState(currentVersion = CURRENT_VERSION))
    val state: StateFlow<DesktopUpdateState> = _state.asStateFlow()

    init {
        checkForUpdates(isSilent = true)
    }

    fun checkForUpdates(isSilent: Boolean = false) {
        scope.launch {
            _state.update {
                it.copy(
                    isChecking = true,
                    message = if (!isSilent) "Checking for updates..." else it.message
                )
            }

            try {
                val req = Request.Builder()
                    .url("https://api.github.com/repos/$GITHUB_REPO/releases/latest")
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("User-Agent", "SonaraStream-Desktop/$CURRENT_VERSION")
                    .build()

                val response = client.newCall(req).execute()
                if (!response.isSuccessful) {
                    val code = response.code
                    _state.update {
                        it.copy(
                            isChecking = false,
                            message = if (!isSilent) "Could not check updates (HTTP $code)" else null
                        )
                    }
                    return@launch
                }

                val body = response.body?.string().orEmpty()
                val root = json.parseToJsonElement(body).jsonObject

                val tagName = root["tag_name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val releaseTitle = root["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val releaseUrl = root["html_url"]?.jsonPrimitive?.contentOrNull
                    ?: "https://github.com/$GITHUB_REPO/releases"
                val releaseNotes = root["body"]?.jsonPrimitive?.contentOrNull.orEmpty()

                var downloadUrl: String? = null
                val assets = root["assets"]?.jsonArray.orEmpty()

                // Priority: .exe installer > .msi installer > .zip
                val exeAsset = assets.firstOrNull {
                    val name = it.jsonObject["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    name.endsWith(".exe", ignoreCase = true)
                }
                val msiAsset = assets.firstOrNull {
                    val name = it.jsonObject["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    name.endsWith(".msi", ignoreCase = true)
                }
                val zipAsset = assets.firstOrNull {
                    val name = it.jsonObject["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    name.endsWith(".zip", ignoreCase = true)
                }

                val chosenAsset = exeAsset ?: msiAsset ?: zipAsset
                downloadUrl = chosenAsset?.jsonObject?.get("browser_download_url")?.jsonPrimitive?.contentOrNull

                val cleanTag = tagName.removePrefix("v").removePrefix("V")
                val hasNewer = isNewerVersion(cleanTag, CURRENT_VERSION)

                _state.update {
                    it.copy(
                        isChecking = false,
                        isUpdateAvailable = hasNewer,
                        latestVersion = cleanTag,
                        releaseTitle = releaseTitle,
                        releaseNotes = releaseNotes,
                        releaseUrl = releaseUrl,
                        downloadUrl = downloadUrl ?: releaseUrl,
                        message = if (!isSilent) {
                            if (hasNewer) "Version v$cleanTag is available!" else "You're on the latest version ($CURRENT_VERSION)"
                        } else null
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isChecking = false,
                        message = if (!isSilent) "Update check failed: ${e.message ?: "Network error"}" else null
                    )
                }
            }
        }
    }

    fun dismissUpdate() {
        _state.update { it.copy(isDismissed = true) }
    }

    fun openReleasePage() {
        try {
            val url = _state.value.releaseUrl.ifBlank { "https://github.com/$GITHUB_REPO/releases" }
            Desktop.getDesktop().browse(URI(url))
        } catch (_: Exception) {}
    }

    fun downloadAndInstall() {
        val dlUrl = _state.value.downloadUrl ?: return
        if (_state.value.isDownloading) return

        scope.launch {
            _state.update {
                it.copy(
                    isDownloading = true,
                    downloadProgress = 0f,
                    downloadStatus = "Connecting to download server..."
                )
            }

            try {
                val req = Request.Builder()
                    .url(dlUrl)
                    .header("User-Agent", "SonaraStream-Desktop/$CURRENT_VERSION")
                    .build()

                val resp = client.newCall(req).execute()
                if (!resp.isSuccessful) {
                    _state.update {
                        it.copy(
                            isDownloading = false,
                            downloadStatus = "Download failed (HTTP ${resp.code})"
                        )
                    }
                    return@launch
                }

                val body = resp.body ?: run {
                    _state.update { it.copy(isDownloading = false, downloadStatus = "Empty download body") }
                    return@launch
                }

                val totalBytes = body.contentLength()
                val isMsi = dlUrl.endsWith(".msi", ignoreCase = true)
                val ext = if (isMsi) "msi" else "exe"
                val tempFile = File(System.getProperty("java.io.tmpdir"), "SonaraStream-Update-${_state.value.latestVersion}.$ext")

                _state.update { it.copy(downloadStatus = "Downloading Sonara Stream v${_state.value.latestVersion}...") }

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(32768)
                        var bytesRead: Long = 0
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            bytesRead += read
                            if (totalBytes > 0) {
                                val progress = (bytesRead.toFloat() / totalBytes).coerceIn(0f, 1f)
                                _state.update { it.copy(downloadProgress = progress) }
                            }
                        }
                    }
                }

                _state.update {
                    it.copy(
                        downloadProgress = 1f,
                        downloadStatus = "Download complete. Starting installer..."
                    )
                }

                delay(800)

                // Launch installer on Windows
                withContext(Dispatchers.IO) {
                    if (isMsi) {
                        ProcessBuilder("msiexec", "/i", tempFile.absolutePath).start()
                    } else {
                        ProcessBuilder(tempFile.absolutePath).start()
                    }
                }

                // Give the installer process half a second to launch then exit cleanly
                delay(500)
                System.exit(0)
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isDownloading = false,
                        downloadStatus = "Update failed: ${e.message}"
                    )
                }
            }
        }
    }

    private fun isNewerVersion(remote: String, local: String): Boolean {
        if (remote.isBlank() || local.isBlank()) return false
        val cleanRemote = remote.removePrefix("v").removePrefix("V").substringBefore("-")
        val cleanLocal = local.removePrefix("v").removePrefix("V").substringBefore("-")
        if (cleanRemote == cleanLocal) return false

        val rParts = cleanRemote.split(".").mapNotNull { it.toIntOrNull() }
        val cParts = cleanLocal.split(".").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(rParts.size, cParts.size)
        for (i in 0 until maxLen) {
            val r = rParts.getOrElse(i) { 0 }
            val c = cParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }
}
