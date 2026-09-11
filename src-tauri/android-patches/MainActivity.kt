package com.sonara.stream

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : TauriActivity() {

    companion object {
        var webViewInstance: WebView? = null
        var lastTitle: String = "Sonara Stream"
        var lastArtist: String = "Streaming Audio"
        var lastAlbum: String? = "Sonara"
        var lastCoverUrl: String? = null
        var lastDurationSecs: Double = 0.0
        var lastPositionSecs: Double = 0.0

        fun dispatchMediaEvent(eventName: String, payload: Double? = null) {
            val js = if (payload != null) {
                "window.dispatchEvent(new CustomEvent('$eventName', { detail: $payload }));"
            } else {
                "window.dispatchEvent(new CustomEvent('$eventName'));"
            }
            webViewInstance?.post {
                webViewInstance?.evaluateJavascript(js, null)
            }
        }

        fun dispatchPlaybackState(isPlaying: Boolean, positionSecs: Double, durationSecs: Double) {
            val js = "window.dispatchEvent(new CustomEvent('sonara-playback-state', { detail: { isPlaying: $isPlaying, position: $positionSecs, duration: $durationSecs } }));"
            webViewInstance?.post {
                webViewInstance?.evaluateJavascript(js, null)
            }
        }

        fun dispatchPlaybackEnded() {
            val js = "window.dispatchEvent(new CustomEvent('sonara-playback-ended'));"
            webViewInstance?.post {
                webViewInstance?.evaluateJavascript(js, null)
            }
        }

        fun dispatchPlaybackError(message: String, songId: Long) {
            val escaped = message.replace("'", "\\'").replace("\n", " ")
            val js = "window.dispatchEvent(new CustomEvent('sonara-playback-error', { detail: { message: '$escaped', songId: $songId } }));"
            webViewInstance?.post {
                webViewInstance?.evaluateJavascript(js, null)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemNavigation()

        // Always move task to back when Back is pressed on root screen, keeping background playback and preventing activity teardown crashes
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                moveTaskToBack(true)
            }
        })

        // Prompt for notification permission on Android 13+ (Tiramisu)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isActivityInForeground: Boolean = false
    private var rendererTerminated: Boolean = false

    override fun onStart() {
        super.onStart()
        isActivityInForeground = true
    }

    private fun ensurePlaybackServiceStarted() {
        if (MediaPlaybackService.instance != null) {
            return
        }
        if (!isActivityInForeground) {
            android.util.Log.d("MainActivity", "Skipping service start: Activity is not in foreground")
            return
        }
        try {
            val serviceIntent = Intent(this, MediaPlaybackService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(this, serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "Failed to start MediaPlaybackService", e)
        }
    }

    override fun onWebViewCreate(webView: WebView) {
        super.onWebViewCreate(webView)
        webViewInstance = webView
        webView.setBackgroundColor(android.graphics.Color.parseColor("#09090b"))
        webView.settings.mediaPlaybackRequiresUserGesture = false
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = true
        webView.settings.domStorageEnabled = true
        webView.settings.databaseEnabled = true
        webView.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

        // Decorate WebViewClient to survive render process death under memory pressure
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val existingClient = webView.webViewClient
            webView.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    return existingClient?.shouldInterceptRequest(view, request)
                        ?: super.shouldInterceptRequest(view, request)
                }

                @Suppress("DEPRECATION")
                override fun shouldInterceptRequest(
                    view: WebView?,
                    url: String?
                ): WebResourceResponse? {
                    return existingClient?.shouldInterceptRequest(view, url)
                        ?: super.shouldInterceptRequest(view, url)
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    return existingClient?.shouldOverrideUrlLoading(view, request)
                        ?: super.shouldOverrideUrlLoading(view, request)
                }

                @Suppress("DEPRECATION")
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    url: String?
                ): Boolean {
                    return existingClient?.shouldOverrideUrlLoading(view, url)
                        ?: super.shouldOverrideUrlLoading(view, url)
                }

                override fun onPageStarted(
                    view: WebView?,
                    url: String?,
                    favicon: android.graphics.Bitmap?
                ) {
                    existingClient?.onPageStarted(view, url, favicon) ?: super.onPageStarted(view, url, favicon)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    existingClient?.onPageFinished(view, url) ?: super.onPageFinished(view, url)
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    existingClient?.onReceivedError(view, request, error)
                        ?: super.onReceivedError(view, request, error)
                }

                override fun onRenderProcessGone(
                    view: WebView?,
                    detail: RenderProcessGoneDetail?
                ): Boolean {
                    val didCrash = detail?.didCrash() ?: false
                    android.util.Log.e("MainActivity", "WebView render process gone! (didCrash=$didCrash)")
                    try {
                        view?.let {
                            (it.parent as? android.view.ViewGroup)?.removeView(it)
                            it.destroy()
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("MainActivity", "Error destroying terminated webView", e)
                    }
                    webViewInstance = null
                    rendererTerminated = true
                    if (isActivityInForeground) {
                        rendererTerminated = false
                        mainHandler.post { recreate() }
                    }
                    return true
                }
            }
        }

        // Bridge to allow frontend to open external URLs / download APKs directly
        webView.addJavascriptInterface(object {
            @android.webkit.JavascriptInterface
            fun openUrl(url: String) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }, "AndroidNative")

        // Native Media Session bridge for rich background notification & Control Center controls
        webView.addJavascriptInterface(object {
            @android.webkit.JavascriptInterface
            fun updateMetadata(
                title: String,
                artist: String,
                album: String?,
                coverUrl: String?,
                durationSecs: Double,
                isPlaying: Boolean,
                positionSecs: Double
            ) {
                lastTitle = title
                lastArtist = artist
                lastAlbum = album
                lastCoverUrl = coverUrl
                lastDurationSecs = durationSecs
                lastPositionSecs = positionSecs

                mainHandler.post {
                    if (isPlaying) {
                        ensurePlaybackServiceStarted()
                    }
                    MediaPlaybackService.updateTrack(title, artist, album, coverUrl, durationSecs, isPlaying, positionSecs)
                }
            }

            @android.webkit.JavascriptInterface
            fun updatePlaybackState(isPlaying: Boolean, positionSecs: Double) {
                mainHandler.post {
                    if (isPlaying) {
                        ensurePlaybackServiceStarted()
                    }
                    MediaPlaybackService.updateState(isPlaying, positionSecs)
                }
            }

            @android.webkit.JavascriptInterface
            fun stopPlayback() {
                mainHandler.post {
                    try {
                        MediaPlaybackService.stopPlayback()
                    } catch (e: Exception) {
                        android.util.Log.w("MainActivity", "Failed to stop playback service", e)
                    }
                }
            }
        }, "AndroidMedia")

        // Native ExoPlayer Playback Bridge with explicit Main Looper dispatch
        webView.addJavascriptInterface(object {
            @android.webkit.JavascriptInterface
            fun loadTrack(
                source: String,
                isLocal: Boolean,
                songId: Long,
                title: String,
                artist: String,
                albumArtUri: String?,
                durationSecs: Long
            ) {
                lastTitle = title
                lastArtist = artist
                lastCoverUrl = albumArtUri
                lastDurationSecs = durationSecs.toDouble()
                mainHandler.post {
                    ensurePlaybackServiceStarted()
                    MediaPlaybackService.loadTrack(source, isLocal, songId, title, artist, albumArtUri, durationSecs)
                }
            }

            @android.webkit.JavascriptInterface
            fun play() {
                mainHandler.post {
                    ensurePlaybackServiceStarted()
                    MediaPlaybackService.play()
                }
            }

            @android.webkit.JavascriptInterface
            fun pause() {
                mainHandler.post {
                    MediaPlaybackService.pause()
                }
            }

            @android.webkit.JavascriptInterface
            fun seekTo(positionSecs: Double) {
                mainHandler.post {
                    MediaPlaybackService.seekTo(positionSecs)
                }
            }

            @android.webkit.JavascriptInterface
            fun setVolume(volume: Float) {
                mainHandler.post {
                    MediaPlaybackService.setVolume(volume)
                }
            }

            @android.webkit.JavascriptInterface
            fun stop() {
                mainHandler.post {
                    try {
                        MediaPlaybackService.stopPlayback()
                    } catch (e: Exception) {
                        android.util.Log.w("MainActivity", "Failed to stop playback service", e)
                    }
                }
            }
        }, "AndroidPlayback")

        // Handle direct file download navigations
        webView.setDownloadListener { url, _, _, _, _ ->
            try {
                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemNavigation()
        }
    }

    override fun onResume() {
        super.onResume()
        isActivityInForeground = true
        if (rendererTerminated) {
            rendererTerminated = false
            recreate()
            return
        }
        hideSystemNavigation()
        webViewInstance?.onResume()
        webViewInstance?.resumeTimers()
    }

    @Suppress("DEPRECATION")
    private fun hideSystemNavigation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        // Modern WindowInsetsController (Android 11+)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        // Legacy flags for Android 9 / 10
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        moveTaskToBack(true)
    }

    override fun onPause() {
        super.onPause()
        // If media is playing or service is active, keep WebView and JS timers alive in background
        if (MediaPlaybackService.isPlaybackActive() || MediaPlaybackService.instance != null) {
            webViewInstance?.onResume()
            webViewInstance?.resumeTimers()
        }
    }

    override fun onStop() {
        super.onStop()
        isActivityInForeground = false
        // Ensure WebView audio and timers remain active when activity is stopped in background
        if (MediaPlaybackService.isPlaybackActive() || MediaPlaybackService.instance != null) {
            webViewInstance?.onResume()
            webViewInstance?.resumeTimers()
        }
    }
}
