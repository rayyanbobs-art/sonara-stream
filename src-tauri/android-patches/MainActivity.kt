package com.sonara.stream

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : TauriActivity() {

    companion object {
        var webViewInstance: WebView? = null

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
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemNavigation()

        // Prompt for notification permission on Android 13+ (Tiramisu)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }

    private fun ensurePlaybackServiceStarted() {
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
                if (isPlaying) {
                    ensurePlaybackServiceStarted()
                }
                MediaPlaybackService.updateTrack(title, artist, album, coverUrl, durationSecs, isPlaying, positionSecs)
            }

            @android.webkit.JavascriptInterface
            fun updatePlaybackState(isPlaying: Boolean, positionSecs: Double) {
                if (isPlaying) {
                    ensurePlaybackServiceStarted()
                }
                MediaPlaybackService.updateState(isPlaying, positionSecs)
            }
        }, "AndroidMedia")

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

    override fun onPause() {
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
    }
}
