package com.sonara.desktop.ui

import javafx.application.Platform
import javafx.concurrent.Worker
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.ProgressBar
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.scene.web.WebView
import javafx.stage.Stage
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.io.File
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.URI

object YouTubeLoginWindow {

    private const val LOGIN_URL =
        "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com%2F&hl=en"
    private const val CHROME_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private var jfxInitialized = false

    private fun ensureJavaFx(action: () -> Unit) {
        if (!jfxInitialized) {
            try {
                Platform.startup {
                    Platform.setImplicitExit(false)
                    jfxInitialized = true
                    action()
                }
            } catch (_: IllegalStateException) {
                try {
                    Platform.setImplicitExit(false)
                } catch (_: Exception) {}
                jfxInitialized = true
                Platform.runLater(action)
            }
        } else {
            Platform.runLater(action)
        }
    }

    private fun getClipboardText(): String? {
        return try {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            val data = clipboard.getData(DataFlavor.stringFlavor) as? String
            data?.trim()
        } catch (_: Exception) {
            null
        }
    }

    fun open(
        onSuccess: (cookies: String) -> Unit,
        onCancel: () -> Unit = {}
    ) {
        ensureJavaFx {
            try {
                // Set default CookieManager to capture all session cookies from WebView
                val existingManager = CookieHandler.getDefault() as? CookieManager
                val cookieManager = existingManager ?: CookieManager(null, CookiePolicy.ACCEPT_ALL).also {
                    CookieHandler.setDefault(it)
                }

                val stage = Stage()
                stage.title = "Sign in to YouTube Music — Sonara Stream"
                stage.width = 1020.0
                stage.height = 780.0

                val webView = WebView()
                val engine = webView.engine
                engine.userAgent = CHROME_USER_AGENT

                // Enable local storage and persistence
                val appData = System.getenv("APPDATA") ?: System.getProperty("user.home")
                val userDataDir = File(appData, "SonaraStream/webview-data").apply { mkdirs() }
                engine.userDataDirectory = userDataDir

                // Dark background so user is never blinded by blank white screen
                try {
                    webView.pageFill = Color.valueOf("#121413")
                } catch (_: Throwable) {}

                val statusLabel = Label("Loading Google / YouTube Music sign-in...")
                statusLabel.style = "-fx-text-fill: #E6E8E6; -fx-font-size: 13px; -fx-font-weight: bold;"

                val progressBar = ProgressBar()
                progressBar.prefWidth = 200.0
                progressBar.visibleProperty().bind(engine.loadWorker.runningProperty())

                fun extractCookies(): String {
                    val store = cookieManager.cookieStore
                    val map = mutableMapOf<String, String>()

                    val uris = listOf(
                        "https://music.youtube.com",
                        "https://accounts.google.com",
                        "https://www.youtube.com",
                        "https://youtube.com"
                    )
                    for (u in uris) {
                        try {
                            val headerMap = cookieManager.get(URI(u), emptyMap())
                            headerMap["Cookie"]?.forEach { header ->
                                header.split(";").forEach { pair ->
                                    val idx = pair.indexOf('=')
                                    if (idx > 0) {
                                        map[pair.substring(0, idx).trim()] = pair.substring(idx + 1).trim()
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    }

                    try {
                        for (cookie in store.cookies) {
                            val domain = cookie.domain?.lowercase().orEmpty()
                            if (domain.contains("youtube.com") || domain.contains("google.com") || domain.isBlank()) {
                                map[cookie.name] = cookie.value
                            }
                        }
                    } catch (_: Exception) {}

                    try {
                        val jsCookies = engine.executeScript("document.cookie") as? String
                        jsCookies?.split(";")?.forEach { pair ->
                            val idx = pair.indexOf('=')
                            if (idx > 0) {
                                map[pair.substring(0, idx).trim()] = pair.substring(idx + 1).trim()
                            }
                        }
                    } catch (_: Exception) {}

                    return map.entries.joinToString("; ") { "${it.key}=${it.value}" }
                }

                var successDispatched = false
                fun checkAndFinish(manual: Boolean = false) {
                    if (successDispatched) return
                    var cookies = extractCookies()
                    var hasSession = cookies.contains("SAPISID=") || cookies.contains("__Secure-3PAPISID=")

                    if (!hasSession && manual) {
                        val clip = getClipboardText()
                        if (!clip.isNullOrBlank() && (clip.contains("SAPISID") || clip.contains("__Secure-3PAPISID") || clip.contains("SID="))) {
                            cookies = clip
                            hasSession = true
                        }
                    }

                    if (hasSession) {
                        successDispatched = true
                        stage.close()
                        onSuccess(cookies)
                    } else if (manual) {
                        statusLabel.text = "No session cookies found yet. Sign in above or copy cookies to clipboard."
                    }
                }

                // Check location updates
                engine.locationProperty().addListener { _, _, newUrl ->
                    val url = newUrl.orEmpty()
                    val isGoogleAccounts = url.contains("accounts.google.com")
                    val isYouTubeMusic = (url.startsWith("https://music.youtube.com") || url.startsWith("http://music.youtube.com")) && !isGoogleAccounts

                    if (isYouTubeMusic) {
                        statusLabel.text = "Detected YouTube Music! Verifying credentials..."
                        checkAndFinish(manual = false)
                    } else if (isGoogleAccounts) {
                        statusLabel.text = "Please enter your Google credentials above..."
                    } else {
                        statusLabel.text = "Navigating: ${url.take(60)}..."
                    }
                }

                // Check load worker state
                engine.loadWorker.stateProperty().addListener { _, _, newState ->
                    when (newState) {
                        Worker.State.SUCCEEDED -> {
                            val url = engine.location.orEmpty()
                            val isGoogleAccounts = url.contains("accounts.google.com")
                            val isYouTubeMusic = (url.startsWith("https://music.youtube.com") || url.startsWith("http://music.youtube.com")) && !isGoogleAccounts

                            if (isYouTubeMusic) {
                                checkAndFinish(manual = false)
                            } else if (isGoogleAccounts) {
                                statusLabel.text = "Ready. Please enter your email and password above."
                            }
                        }
                        Worker.State.FAILED -> {
                            statusLabel.text = "Page failed to load. Try 'Reload' or 'Open in Browser' below."
                        }
                        else -> {}
                    }
                }

                // Top Helper / Navigation Bar
                val topBar = HBox(10.0)
                topBar.alignment = Pos.CENTER_LEFT
                topBar.padding = Insets(8.0, 16.0, 8.0, 16.0)
                topBar.style = "-fx-background-color: #161817; -fx-border-color: #242826; -fx-border-width: 0 0 1 0;"

                val hintLabel = Label("Sign in to your Google Account:")
                hintLabel.style = "-fx-text-fill: #A0A5A2; -fx-font-size: 12px;"

                val reloadBtn = Button("Reload")
                reloadBtn.style = "-fx-background-color: #242826; -fx-text-fill: #E6E8E6; -fx-background-radius: 4px; -fx-padding: 4 10;"
                reloadBtn.setOnAction {
                    engine.reload()
                }

                val directYtmBtn = Button("YouTube Music Home")
                directYtmBtn.style = "-fx-background-color: #242826; -fx-text-fill: #E6E8E6; -fx-background-radius: 4px; -fx-padding: 4 10;"
                directYtmBtn.setOnAction {
                    engine.load("https://music.youtube.com")
                }

                val openBrowserBtn = Button("Open in Browser")
                openBrowserBtn.style = "-fx-background-color: #242826; -fx-text-fill: #E6E8E6; -fx-background-radius: 4px; -fx-padding: 4 10;"
                openBrowserBtn.setOnAction {
                    try {
                        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                            Desktop.getDesktop().browse(URI("https://music.youtube.com"))
                            statusLabel.text = "Opened in browser. Once signed in, copy cookies or click 'I'm Signed In'."
                        }
                    } catch (e: Exception) {
                        statusLabel.text = "Could not open browser: ${e.message}"
                    }
                }

                topBar.children.addAll(hintLabel, reloadBtn, directYtmBtn, openBrowserBtn)

                // Bottom Action Bar
                val bottomBar = HBox(12.0)
                bottomBar.alignment = Pos.CENTER_RIGHT
                bottomBar.padding = Insets(12.0, 16.0, 12.0, 16.0)
                bottomBar.style = "-fx-background-color: #161817; -fx-border-color: #242826; -fx-border-width: 1 0 0 0;"

                val statusBox = VBox(4.0)
                statusBox.alignment = Pos.CENTER_LEFT
                statusBox.children.addAll(statusLabel, progressBar)
                HBox.setHgrow(statusBox, Priority.ALWAYS)

                val cancelButton = Button("Cancel")
                cancelButton.style = "-fx-background-color: #242826; -fx-text-fill: #A0A5A2; -fx-background-radius: 6px; -fx-padding: 8 16;"
                cancelButton.setOnAction {
                    stage.close()
                    onCancel()
                }

                val pasteBtn = Button("Paste from Clipboard")
                pasteBtn.style = "-fx-background-color: #2E3330; -fx-text-fill: #C7E2B5; -fx-background-radius: 6px; -fx-padding: 8 16;"
                pasteBtn.setOnAction {
                    val clip = getClipboardText()
                    if (!clip.isNullOrBlank() && (clip.contains("SAPISID") || clip.contains("__Secure-3PAPISID") || clip.contains("SID="))) {
                        successDispatched = true
                        stage.close()
                        onSuccess(clip)
                    } else {
                        statusLabel.text = "Clipboard does not contain YouTube session cookies."
                    }
                }

                val syncButton = Button("I'm Signed In")
                syncButton.style = "-fx-background-color: #4CAF50; -fx-text-fill: #000000; -fx-font-weight: bold; -fx-background-radius: 6px; -fx-padding: 8 16;"
                syncButton.setOnAction {
                    checkAndFinish(manual = true)
                }

                bottomBar.children.addAll(statusBox, cancelButton, pasteBtn, syncButton)

                val root = BorderPane()
                root.top = topBar
                root.center = webView
                root.bottom = bottomBar
                root.style = "-fx-background-color: #121413;"

                stage.scene = Scene(root)
                stage.setOnCloseRequest {
                    onCancel()
                }

                engine.load(LOGIN_URL)
                stage.show()
            } catch (e: Exception) {
                e.printStackTrace()
                onCancel()
            }
        }
    }
}
