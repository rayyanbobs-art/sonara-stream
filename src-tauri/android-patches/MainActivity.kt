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
import app.tauri.TauriActivity

class MainActivity : TauriActivity() {
    private var webViewInstance: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemNavigation()

        // Start Foreground Service to guarantee continuous background audio & screen lock playback
        try {
            val serviceIntent = Intent(this, MediaPlaybackService::class.java)
            ContextCompat.startForegroundService(this, serviceIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Prompt for notification permission on Android 13+ (Tiramisu) if not granted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }

    override fun onWebViewCreate(webView: WebView) {
        super.onWebViewCreate(webView)
        webViewInstance = webView
        webView.settings.mediaPlaybackRequiresUserGesture = false
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
        // Modern WindowInsetsController (Android 11+)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.navigationBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        // Legacy flags for Android 9 Pie (e.g. LG G Pad 5 10.1 FHD) and Android 10
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
    }

    override fun onPause() {
        super.onPause()
        // Prevent WebView from freezing audio/network when app is minimized
        resumeWebViewBackground()
    }

    override fun onStop() {
        super.onStop()
        // Prevent WebView from stopping playback when screen is locked
        resumeWebViewBackground()
    }

    private fun resumeWebViewBackground() {
        try {
            webViewInstance?.onResume()
            webViewInstance?.resumeTimers()

            // Notify WebView engine that window is visible to prevent media suspension
            val method = View::class.java.getDeclaredMethod("onWindowVisibilityChanged", Int::class.javaPrimitiveType)
            method.isAccessible = true
            method.invoke(webViewInstance, View.VISIBLE)
        } catch (e: Exception) {
            // Ignore reflection errors if restricted by newer Android hidden API policies
        }
    }
}
