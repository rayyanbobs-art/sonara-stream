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
import javafx.scene.web.WebView
import javafx.stage.Modality
import javafx.stage.Stage
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
                // JavaFX toolkit already started
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
                stage.initModality(Modality.APPLICATION_MODAL)
                stage.width = 1000.0
                stage.height = 760.0

                val webView = WebView()
                val engine = webView.engine
                engine.userAgent = CHROME_USER_AGENT

                val statusLabel = Label("Sign in to your Google / YouTube Music account...")
                statusLabel.style = "-fx-text-fill: #E6E8E6; -fx-font-size: 13px; -fx-font-weight: bold;"

                val progressBar = ProgressBar()
                progressBar.prefWidth = 200.0
                progressBar.visibleProperty().bind(engine.loadWorker.runningProperty())

                fun extractCookies(): String {
                    val store = cookieManager.cookieStore
                    val map = mutableMapOf<String, String>()

                    // 1. Check CookieHandler for music.youtube.com
                    try {
                        val headerMap = cookieManager.get(URI("https://music.youtube.com"), emptyMap())
                        headerMap["Cookie"]?.forEach { header ->
                            header.split(";").forEach { pair ->
                                val idx = pair.indexOf('=')
                                if (idx > 0) {
                                    map[pair.substring(0, idx).trim()] = pair.substring(idx + 1).trim()
                                }
                            }
                        }
                    } catch (_: Exception) {}

                    // 2. Check CookieStore directly
                    try {
                        for (cookie in store.cookies) {
                            val domain = cookie.domain?.lowercase().orEmpty()
                            if (domain.contains("youtube.com") || domain.contains("google.com") || domain.isBlank()) {
                                map[cookie.name] = cookie.value
                            }
                        }
                    } catch (_: Exception) {}

                    return map.entries.joinToString("; ") { "${it.key}=${it.value}" }
                }

                var successDispatched = false
                fun checkAndFinish(manual: Boolean = false) {
                    if (successDispatched) return
                    val cookies = extractCookies()
                    val hasSession = cookies.contains("SAPISID=") || cookies.contains("__Secure-3PAPISID=")

                    if (hasSession || manual) {
                        if (cookies.isNotBlank()) {
                            successDispatched = true
                            stage.close()
                            onSuccess(cookies)
                        }
                    }
                }

                // Check whenever location updates
                engine.locationProperty().addListener { _, _, newUrl ->
                    val url = newUrl.orEmpty()
                    if (url.contains("music.youtube.com")) {
                        statusLabel.text = "Detected YouTube Music! Verifying credentials..."
                        checkAndFinish(manual = false)
                    }
                }

                // Check on page load success
                engine.loadWorker.stateProperty().addListener { _, _, newState ->
                    if (newState == Worker.State.SUCCEEDED) {
                        val url = engine.location.orEmpty()
                        if (url.contains("music.youtube.com")) {
                            checkAndFinish(manual = false)
                        }
                    }
                }

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

                val syncButton = Button("I'm Signed In")
                syncButton.style = "-fx-background-color: #4CAF50; -fx-text-fill: #000000; -fx-font-weight: bold; -fx-background-radius: 6px; -fx-padding: 8 16;"
                syncButton.setOnAction {
                    checkAndFinish(manual = true)
                }

                bottomBar.children.addAll(statusBox, cancelButton, syncButton)

                val root = BorderPane()
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
