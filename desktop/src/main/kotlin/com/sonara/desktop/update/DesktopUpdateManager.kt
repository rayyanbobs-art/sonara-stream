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
    val currentVersion: String = "4.0.1",
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
        const val CURRENT_VERSION = "4.0.1"
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

    fun downloadAndInstall(force: Boolean = false) {
        if (_state.value.isDownloading) return

        scope.launch {
            _state.update {
                it.copy(
                    isDownloading = true,
                    downloadProgress = 0f,
                    downloadStatus = "Connecting to update server..."
                )
            }

            try {
                // Ensure we have the latest download URL
                var dlUrl = _state.value.downloadUrl
                var targetVer = _state.value.latestVersion.ifBlank { CURRENT_VERSION }

                if (dlUrl.isNullOrBlank() || dlUrl.contains("/releases/tag/") || dlUrl.endsWith("/releases")) {
                    val req = Request.Builder()
                        .url("https://api.github.com/repos/$GITHUB_REPO/releases/latest")
                        .header("Accept", "application/vnd.github.v3+json")
                        .header("User-Agent", "SonaraStream-Desktop/$CURRENT_VERSION")
                        .build()

                    val resp = client.newCall(req).execute()
                    if (resp.isSuccessful) {
                        val body = resp.body?.string().orEmpty()
                        val root = json.parseToJsonElement(body).jsonObject
                        val tagName = root["tag_name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        targetVer = tagName.removePrefix("v").removePrefix("V").ifBlank { targetVer }
                        val assets = root["assets"]?.jsonArray.orEmpty()

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

                        val chosen = exeAsset ?: msiAsset ?: zipAsset
                        dlUrl = chosen?.jsonObject?.get("browser_download_url")?.jsonPrimitive?.contentOrNull
                    }
                }

                if (dlUrl.isNullOrBlank()) {
                    _state.update {
                        it.copy(
                            isDownloading = false,
                            downloadStatus = "Could not find release binary. Opening release page..."
                        )
                    }
                    delay(1200)
                    openReleasePage()
                    return@launch
                }

                _state.update {
                    it.copy(
                        downloadStatus = "Downloading Sonara Stream v$targetVer...",
                        downloadProgress = 0.05f
                    )
                }

                val downloadReq = Request.Builder()
                    .url(dlUrl)
                    .header("User-Agent", "SonaraStream-Desktop/$CURRENT_VERSION")
                    .build()

                val downloadResp = client.newCall(downloadReq).execute()
                if (!downloadResp.isSuccessful) {
                    _state.update {
                        it.copy(
                            isDownloading = false,
                            downloadStatus = "Download failed (HTTP ${downloadResp.code})"
                        )
                    }
                    return@launch
                }

                val body = downloadResp.body ?: run {
                    _state.update { it.copy(isDownloading = false, downloadStatus = "Empty download response") }
                    return@launch
                }

                val totalBytes = body.contentLength()
                val isMsi = dlUrl.endsWith(".msi", ignoreCase = true)
                val ext = if (isMsi) "msi" else "exe"
                val tempDir = File(System.getProperty("java.io.tmpdir"))
                val tempFile = File(tempDir, "SonaraStream-Setup-${System.currentTimeMillis()}.$ext")

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(65536)
                        var bytesRead: Long = 0
                        var read: Int
                        var lastProgressUpdate = System.currentTimeMillis()

                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            bytesRead += read

                            val now = System.currentTimeMillis()
                            if (now - lastProgressUpdate > 100 || bytesRead == totalBytes) {
                                lastProgressUpdate = now
                                if (totalBytes > 0) {
                                    val progress = (bytesRead.toFloat() / totalBytes).coerceIn(0f, 1f)
                                    val mbRead = bytesRead / (1024 * 1024)
                                    val mbTotal = totalBytes / (1024 * 1024)
                                    val percent = (progress * 100).toInt()
                                    _state.update {
                                        it.copy(
                                            downloadProgress = progress,
                                            downloadStatus = "Downloading v$targetVer: $mbRead MB / $mbTotal MB ($percent%)"
                                        )
                                    }
                                } else {
                                    val mbRead = bytesRead / (1024 * 1024)
                                    _state.update {
                                        it.copy(
                                            downloadProgress = 0.5f,
                                            downloadStatus = "Downloading v$targetVer: $mbRead MB downloaded..."
                                        )
                                    }
                                }
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

                delay(600)

                // Launch installer on Windows with proper elevation and exit current process
                withContext(Dispatchers.IO) {
                    launchInstallerAndExit(tempFile, isMsi)
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isDownloading = false,
                        downloadStatus = "Update failed: ${e.message ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    private fun launchInstallerAndExit(file: File, isMsi: Boolean) {
        val path = file.absolutePath
        var launched = false

        // Attempt 1: Desktop.getDesktop().open(file)
        // Uses Windows ShellExecuteEx with the "open" verb, which properly requests UAC elevation.
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(file)
                launched = true
            }
        } catch (_: Exception) {}

        // Attempt 2: PowerShell Start-Process (handles UAC elevation reliably on all Windows versions)
        if (!launched) {
            try {
                val psCommand = if (isMsi) {
                    "Start-Process -FilePath 'msiexec.exe' -ArgumentList '/i \"$path\"'"
                } else {
                    "Start-Process -FilePath '$path'"
                }
                ProcessBuilder("powershell.exe", "-WindowStyle", "Hidden", "-Command", psCommand).start()
                launched = true
            } catch (_: Exception) {}
        }

        // Attempt 3: cmd.exe start
        if (!launched) {
            try {
                if (isMsi) {
                    ProcessBuilder("cmd.exe", "/c", "start", "", "msiexec", "/i", path).start()
                } else {
                    ProcessBuilder("cmd.exe", "/c", "start", "", path).start()
                }
                launched = true
            } catch (_: Exception) {}
        }

        // Give Windows Shell 1 second to create the installer process, then terminate Sonara
        // so file locks on app files are completely released for the installer to overwrite.
        Thread.sleep(1000)
        System.exit(0)
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
