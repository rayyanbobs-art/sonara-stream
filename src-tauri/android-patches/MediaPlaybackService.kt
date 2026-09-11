package com.sonara.stream

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import java.io.File
import java.net.URL
import java.net.URLDecoder

class MediaPlaybackService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var mediaSession: MediaSessionCompat? = null
    private var exoPlayer: ExoPlayer? = null

    private var isForegroundService: Boolean = false

    private var currentTitle: String = "Sonara Stream"
    private var currentArtist: String = "Streaming Audio"
    private var currentAlbum: String = "Sonara"
    private var currentCoverUrl: String? = null
    private var currentArtBitmap: Bitmap? = null
    private var currentDurationSecs: Double = 0.0
    private var currentPositionSecs: Double = 0.0
    private var currentSongId: Long = 0L
    private var isCurrentlyPlaying: Boolean = false

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            isCurrentlyPlaying = isPlaying
            setLocksHeld(isPlaying)
            if (isPlaying) {
                startPositionUpdates()
            } else {
                stopPositionUpdates()
            }
            syncMediaState()
            dispatchStateToJs()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_ENDED -> {
                    isCurrentlyPlaying = false
                    setLocksHeld(false)
                    stopPositionUpdates()
                    syncMediaState()
                    MainActivity.dispatchPlaybackEnded()
                }
                Player.STATE_READY -> {
                    val durMs = exoPlayer?.duration ?: 0L
                    if (durMs > 0) {
                        currentDurationSecs = durMs / 1000.0
                    }
                    syncMediaState()
                    dispatchStateToJs()
                }
                Player.STATE_BUFFERING -> {}
                Player.STATE_IDLE -> {}
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e("MediaPlaybackService", "ExoPlayer playback error: ${error.errorCodeName}", error)
            MainActivity.dispatchPlaybackError(error.localizedMessage ?: error.errorCodeName, currentSongId)
        }
    }

    private val positionUpdateRunnable = object : Runnable {
        override fun run() {
            if (isCurrentlyPlaying) {
                val posMs = exoPlayer?.currentPosition ?: 0L
                val durMs = exoPlayer?.duration ?: 0L
                currentPositionSecs = posMs / 1000.0
                if (durMs > 0) {
                    currentDurationSecs = durMs / 1000.0
                }
                dispatchStateToJs()
                mainHandler.postDelayed(this, 1000)
            }
        }
    }

    private fun startPositionUpdates() {
        mainHandler.removeCallbacks(positionUpdateRunnable)
        mainHandler.post(positionUpdateRunnable)
    }

    private fun stopPositionUpdates() {
        mainHandler.removeCallbacks(positionUpdateRunnable)
    }

    private fun dispatchStateToJs() {
        val pos = (exoPlayer?.currentPosition ?: 0L) / 1000.0
        val dur = if ((exoPlayer?.duration ?: 0L) > 0) (exoPlayer?.duration ?: 0L) / 1000.0 else currentDurationSecs
        MainActivity.dispatchPlaybackState(isCurrentlyPlaying, pos, dur)
    }

    private val actionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_PLAY -> exoPlayer?.play()
                ACTION_PAUSE -> exoPlayer?.pause()
                ACTION_TOGGLE -> {
                    if (exoPlayer?.isPlaying == true) {
                        exoPlayer?.pause()
                    } else {
                        exoPlayer?.play()
                    }
                }
                ACTION_NEXT -> MainActivity.dispatchMediaEvent("sonara-media-next")
                ACTION_PREV -> MainActivity.dispatchMediaEvent("sonara-media-prev")
                ACTION_STOP_SERVICE -> {
                    stopPlaybackInternal()
                }
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "sonara_playback_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_PLAY = "com.sonara.stream.ACTION_PLAY"
        const val ACTION_PAUSE = "com.sonara.stream.ACTION_PAUSE"
        const val ACTION_TOGGLE = "com.sonara.stream.ACTION_TOGGLE"
        const val ACTION_NEXT = "com.sonara.stream.ACTION_NEXT"
        const val ACTION_PREV = "com.sonara.stream.ACTION_PREV"
        const val ACTION_STOP_SERVICE = "com.sonara.stream.ACTION_STOP"

        var instance: MediaPlaybackService? = null

        data class PendingTrack(
            val source: String,
            val isLocal: Boolean,
            val songId: Long,
            val title: String,
            val artist: String,
            val albumArtUri: String?,
            val durationSecs: Long
        )

        var pendingTrack: PendingTrack? = null

        fun loadTrack(
            source: String,
            isLocal: Boolean,
            songId: Long,
            title: String,
            artist: String,
            albumArtUri: String?,
            durationSecs: Long
        ) {
            val service = instance
            if (service != null) {
                pendingTrack = null
                service.loadTrackInternal(source, isLocal, songId, title, artist, albumArtUri, durationSecs)
            } else {
                pendingTrack = PendingTrack(source, isLocal, songId, title, artist, albumArtUri, durationSecs)
            }
        }

        fun play() {
            instance?.playInternal()
        }

        fun pause() {
            instance?.pauseInternal()
        }

        fun seekTo(positionSecs: Double) {
            instance?.seekToInternal(positionSecs)
        }

        fun setVolume(volume: Float) {
            instance?.setVolumeInternal(volume)
        }

        fun updateTrack(
            title: String,
            artist: String,
            album: String?,
            coverUrl: String?,
            durationSecs: Double,
            isPlaying: Boolean,
            positionSecs: Double
        ) {
            instance?.updateTrackInternal(title, artist, album, coverUrl, durationSecs, isPlaying, positionSecs)
        }

        fun updateState(isPlaying: Boolean, positionSecs: Double) {
            instance?.updateStateInternal(isPlaying, positionSecs)
        }

        fun stopPlayback() {
            instance?.stopPlaybackInternal()
        }

        fun isPlaybackActive(): Boolean {
            return instance?.isCurrentlyPlaying == true
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        initExoPlayer()
        initMediaSession()

        // Populate metadata from MainActivity cache if available
        currentTitle = MainActivity.lastTitle
        currentArtist = MainActivity.lastArtist
        currentAlbum = MainActivity.lastAlbum ?: "Sonara Stream"
        currentDurationSecs = MainActivity.lastDurationSecs
        currentPositionSecs = MainActivity.lastPositionSecs
        val cachedCover = MainActivity.lastCoverUrl
        if (!cachedCover.isNullOrBlank() && cachedCover != currentCoverUrl) {
            currentCoverUrl = cachedCover
            Thread {
                currentArtBitmap = loadArtworkBitmap(cachedCover)
                mainHandler.post {
                    syncMediaState()
                }
            }.start()
        }

        val filter = IntentFilter().apply {
            addAction(ACTION_PLAY)
            addAction(ACTION_PAUSE)
            addAction(ACTION_TOGGLE)
            addAction(ACTION_NEXT)
            addAction(ACTION_PREV)
            addAction(ACTION_STOP_SERVICE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(actionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(actionReceiver, filter)
        }

        // Process any track queued during cold start before service was created
        val pending = pendingTrack
        if (pending != null) {
            pendingTrack = null
            loadTrackInternal(
                pending.source,
                pending.isLocal,
                pending.songId,
                pending.title,
                pending.artist,
                pending.albumArtUri,
                pending.durationSecs
            )
        }
    }

    private fun initExoPlayer() {
        try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build()

            exoPlayer = ExoPlayer.Builder(this)
                .setAudioAttributes(audioAttributes, true)
                .setHandleAudioBecomingNoisy(true)
                .build().apply {
                    addListener(playerListener)
                }
        } catch (e: Exception) {
            Log.e("MediaPlaybackService", "Error initializing ExoPlayer", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            stopPlaybackInternal()
            return START_NOT_STICKY
        }

        startForegroundWithNotification()
        return START_NOT_STICKY
    }

    private fun initMediaSession() {
        mediaSession = MediaSessionCompat(this, "SonaraStreamMediaSession").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    exoPlayer?.play()
                }

                override fun onPause() {
                    exoPlayer?.pause()
                }

                override fun onSkipToNext() {
                    MainActivity.dispatchMediaEvent("sonara-media-next")
                }

                override fun onSkipToPrevious() {
                    MainActivity.dispatchMediaEvent("sonara-media-prev")
                }

                override fun onSeekTo(pos: Long) {
                    currentPositionSecs = pos / 1000.0
                    exoPlayer?.seekTo(pos)
                    syncMediaState()
                    dispatchStateToJs()
                }

                override fun onStop() {
                    stopPlaybackInternal()
                }
            })
            isActive = true
        }
    }

    fun loadTrackInternal(
        source: String,
        isLocal: Boolean,
        songId: Long,
        title: String,
        artist: String,
        albumArtUri: String?,
        durationSecs: Long
    ) {
        mainHandler.post {
            try {
                currentTitle = title
                currentArtist = artist
                currentAlbum = "Sonara Stream"
                currentDurationSecs = durationSecs.toDouble()
                currentPositionSecs = 0.0
                currentSongId = songId

                val mediaItem = if (isLocal) {
                    if (source.startsWith("content://")) {
                        MediaItem.fromUri(android.net.Uri.parse(source))
                    } else {
                        val cleanPath = normalizeLocalPath(source)
                        val file = File(cleanPath)
                        MediaItem.fromUri(android.net.Uri.fromFile(file))
                    }
                } else {
                    MediaItem.fromUri(android.net.Uri.parse(source))
                }

                exoPlayer?.let { player ->
                    player.setMediaItem(mediaItem)
                    player.prepare()
                    player.playWhenReady = true
                }

                if (!albumArtUri.isNullOrBlank() && albumArtUri != currentCoverUrl) {
                    currentCoverUrl = albumArtUri
                    Thread {
                        currentArtBitmap = loadArtworkBitmap(albumArtUri)
                        mainHandler.post {
                            syncMediaState()
                        }
                    }.start()
                } else {
                    syncMediaState()
                }
            } catch (e: Exception) {
                Log.e("MediaPlaybackService", "Error loading track: $source", e)
                MainActivity.dispatchPlaybackError("Failed to load track: ${e.message}", songId)
            }
        }
    }

    fun playInternal() {
        mainHandler.post {
            exoPlayer?.play()
        }
    }

    fun pauseInternal() {
        mainHandler.post {
            exoPlayer?.pause()
        }
    }

    fun seekToInternal(positionSecs: Double) {
        mainHandler.post {
            currentPositionSecs = positionSecs
            exoPlayer?.seekTo((positionSecs * 1000).toLong())
            syncMediaState()
            dispatchStateToJs()
        }
    }

    fun setVolumeInternal(volume: Float) {
        mainHandler.post {
            exoPlayer?.volume = volume.coerceIn(0f, 1f)
        }
    }

    private fun normalizeLocalPath(source: String): String {
        var path = source.trim()
        if (path.startsWith("file://")) {
            path = path.substring(7)
        }
        if (path.contains("%")) {
            try {
                path = URLDecoder.decode(path, "UTF-8")
            } catch (_: Exception) {}
        }
        return path
    }

    private fun loadArtworkBitmap(coverUrl: String?): Bitmap? {
        if (coverUrl.isNullOrBlank()) return null
        try {
            val rawBitmap = when {
                coverUrl.startsWith("http://asset.localhost") || coverUrl.startsWith("https://asset.localhost") -> {
                    val uri = android.net.Uri.parse(coverUrl)
                    val rawPath = uri.path?.let { URLDecoder.decode(it, "UTF-8") }
                    if (rawPath != null && File(rawPath).exists()) {
                        BitmapFactory.decodeFile(rawPath)
                    } else null
                }
                coverUrl.startsWith("http://127.0.0.1") || coverUrl.startsWith("http://localhost") -> {
                    val pathIdx = coverUrl.indexOf("path=")
                    if (pathIdx != -1) {
                        val encoded = coverUrl.substring(pathIdx + 5).substringBefore('&')
                        val rawPath = URLDecoder.decode(encoded, "UTF-8")
                        if (File(rawPath).exists()) BitmapFactory.decodeFile(rawPath) else null
                    } else null
                }
                coverUrl.startsWith("file://") -> {
                    val raw = coverUrl.removePrefix("file://")
                    val rawPath = URLDecoder.decode(raw, "UTF-8")
                    if (File(rawPath).exists()) BitmapFactory.decodeFile(rawPath) else null
                }
                coverUrl.startsWith("/") -> {
                    val rawPath = URLDecoder.decode(coverUrl, "UTF-8")
                    if (File(rawPath).exists()) BitmapFactory.decodeFile(rawPath) else null
                }
                coverUrl.startsWith("http://") || coverUrl.startsWith("https://") -> {
                    val url = URL(coverUrl)
                    val conn = url.openConnection()
                    conn.connectTimeout = 5000
                    conn.readTimeout = 5000
                    conn.getInputStream().use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                }
                else -> null
            } ?: return null

            return scaleBitmapSafely(rawBitmap, 192)
        } catch (e: Exception) {
            Log.w("MediaPlaybackService", "Error loading cover artwork: $coverUrl", e)
            return null
        }
    }

    private fun scaleBitmapSafely(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) {
            return bitmap
        }
        val ratio = width.toFloat() / height.toFloat()
        val targetWidth: Int
        val targetHeight: Int
        if (ratio > 1f) {
            targetWidth = maxDimension
            targetHeight = (maxDimension / ratio).toInt().coerceAtLeast(1)
        } else {
            targetHeight = maxDimension
            targetWidth = (maxDimension * ratio).toInt().coerceAtLeast(1)
        }
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    fun updateTrackInternal(
        title: String,
        artist: String,
        album: String?,
        coverUrl: String?,
        durationSecs: Double,
        isPlaying: Boolean,
        positionSecs: Double
    ) {
        currentTitle = title
        currentArtist = artist
        currentAlbum = album ?: "Sonara Stream"
        currentDurationSecs = durationSecs
        currentPositionSecs = positionSecs
        isCurrentlyPlaying = isPlaying

        // Fetch artwork bitmap in background if URL changed
        if (coverUrl != null && coverUrl != currentCoverUrl) {
            currentCoverUrl = coverUrl
            Thread {
                currentArtBitmap = loadArtworkBitmap(coverUrl)
                mainHandler.post {
                    syncMediaState()
                }
            }.start()
        } else {
            mainHandler.post {
                syncMediaState()
            }
        }
    }

    fun updateStateInternal(isPlaying: Boolean, positionSecs: Double) {
        isCurrentlyPlaying = isPlaying
        currentPositionSecs = positionSecs
        mainHandler.post {
            syncMediaState()
        }
    }

    fun stopPlayback() {
        stopPlaybackInternal()
    }

    fun stopPlaybackInternal() {
        mainHandler.post {
            try {
                isCurrentlyPlaying = false
                currentArtBitmap = null
                stopPositionUpdates()
                exoPlayer?.stop()
                mediaSession?.isActive = false
                releaseLocks()
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                notificationManager?.cancel(NOTIFICATION_ID)
                if (isForegroundService) {
                    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    isForegroundService = false
                }
                stopSelf()
            } catch (e: Exception) {
                Log.e("MediaPlaybackService", "Error stopping playback service", e)
            }
        }
    }

    private fun syncMediaState() {
        try {
            val state = if (isCurrentlyPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
            val actions = PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_SEEK_TO or
                    PlaybackStateCompat.ACTION_STOP

            val playbackState = PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(state, (currentPositionSecs * 1000).toLong(), 1.0f)
                .build()
            mediaSession?.setPlaybackState(playbackState)

            val metaBuilder = MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentTitle)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentArtist)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, currentAlbum)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, (currentDurationSecs * 1000).toLong())

            currentArtBitmap?.let {
                metaBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it)
            }
            mediaSession?.setMetadata(metaBuilder.build())

            val notification = buildNotification()
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

            if (!isForegroundService) {
                startForegroundWithNotification()
            } else {
                notificationManager?.notify(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e("MediaPlaybackService", "Error syncing media state", e)
        }
    }

    private fun startForegroundWithNotification() {
        try {
            val notification = buildNotification()
            if (!isForegroundService) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceCompat.startForeground(
                        this,
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                isForegroundService = true
            } else {
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                notificationManager?.notify(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e("MediaPlaybackService", "Failed to startForegroundWithNotification", e)
            isForegroundService = true
            try {
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                notificationManager?.notify(NOTIFICATION_ID, buildNotification())
            } catch (_: Exception) {}
        }
    }

    private fun buildNotification(): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        fun createActionPendingIntent(action: String, requestCode: Int): PendingIntent {
            val intent = Intent(action).setPackage(packageName)
            return PendingIntent.getBroadcast(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )
        }

        val prevIntent = createActionPendingIntent(ACTION_PREV, 1)
        val toggleIntent = createActionPendingIntent(if (isCurrentlyPlaying) ACTION_PAUSE else ACTION_PLAY, 2)
        val nextIntent = createActionPendingIntent(ACTION_NEXT, 3)
        val stopIntent = createActionPendingIntent(ACTION_STOP_SERVICE, 4)

        val playPauseIcon = if (isCurrentlyPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseTitle = if (isCurrentlyPlaying) "Pause" else "Play"

        val style = androidx.media.app.NotificationCompat.MediaStyle()
            .setMediaSession(mediaSession?.sessionToken)
            .setShowActionsInCompactView(0, 1, 2)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setStyle(style)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(currentTitle)
            .setContentText(currentArtist)
            .setSubText(currentAlbum)
            .setContentIntent(contentIntent)
            .setDeleteIntent(stopIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(isCurrentlyPlaying)
            .addAction(android.R.drawable.ic_media_previous, "Previous", prevIntent)
            .addAction(playPauseIcon, playPauseTitle, toggleIntent)
            .addAction(android.R.drawable.ic_media_next, "Next", nextIntent)

        currentArtBitmap?.let {
            try {
                builder.setLargeIcon(it)
            } catch (e: Exception) {
                Log.w("MediaPlaybackService", "Failed to attach large icon", e)
            }
        }

        return try {
            builder.build()
        } catch (e: Exception) {
            Log.e("MediaPlaybackService", "Notification build failed, falling back to minimal notification", e)
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(currentTitle)
                .setContentText(currentArtist)
                .setContentIntent(contentIntent)
                .build()
        }
    }

    private fun setLocksHeld(held: Boolean) {
        if (held) {
            acquireLocks()
        } else {
            releaseLocks()
        }
    }

    private fun acquireLocks() {
        try {
            if (wakeLock == null) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "SonaraStream::MediaPlaybackWakeLock"
                ).apply {
                    setReferenceCounted(false)
                }
            }
            if (wakeLock?.isHeld != true) {
                wakeLock?.acquire()
            }

            if (wifiLock == null) {
                val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                @Suppress("DEPRECATION")
                wifiLock = wifiManager?.createWifiLock(
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                    "SonaraStream::MediaWifiLock"
                )?.apply {
                    setReferenceCounted(false)
                }
            }
            if (wifiLock?.isHeld != true) {
                wifiLock?.acquire()
            }
        } catch (e: Exception) {
            Log.w("MediaPlaybackService", "Error acquiring locks", e)
        }
    }

    private fun releaseLocks() {
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
            wifiLock?.let { if (it.isHeld) it.release() }
        } catch (e: Exception) {
            Log.w("MediaPlaybackService", "Error releasing locks", e)
        } finally {
            wakeLock = null
            wifiLock = null
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Sonara Stream Player"
            val descriptionText = "Interactive media playback controls and background streaming"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (!isCurrentlyPlaying) {
            stopPlaybackInternal()
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(actionReceiver)
        } catch (_: Exception) {}
        stopPositionUpdates()
        exoPlayer?.removeListener(playerListener)
        exoPlayer?.release()
        exoPlayer = null
        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null
        releaseLocks()
        isForegroundService = false
        instance = null
        super.onDestroy()
    }
}
