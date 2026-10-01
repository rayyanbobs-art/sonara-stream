package com.sonara.desktop.audio

import com.sonara.desktop.data.LastFmClient
import com.sonara.desktop.data.MusicSearchService
import com.sonara.desktop.model.PlaybackStatus
import com.sonara.desktop.model.PlayerState
import com.sonara.desktop.model.RepeatMode
import com.sonara.desktop.model.Track
import com.sonara.desktop.storage.DesktopDatabase
import javafx.application.Platform
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.util.Duration
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class DesktopAudioPlayer(
    private val searchService: MusicSearchService,
    private val database: DesktopDatabase
) {
    var lastFmClient: LastFmClient? = null

    private val _scrobbleEvents = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 10)
    val scrobbleEvents: SharedFlow<Unit> = _scrobbleEvents.asSharedFlow()

    val liveListenSeconds = MutableStateFlow(0L)

    private var hasScrobbledCurrent = false
    private var hasUpdatedNowPlaying = false
    private var lastRecordedSecond = -1L
    companion object {
        private val javafxStarted = AtomicBoolean(false)
        fun initJavaFx() {
            if (javafxStarted.compareAndSet(false, true)) {
                try {
                    Platform.startup {
                        Platform.setImplicitExit(false)
                    }
                } catch (_: IllegalStateException) {
                    try {
                        Platform.setImplicitExit(false)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val proxy = DesktopAudioProxy()
    private val audioCache = DesktopAudioCache()
    private var mediaPlayer: MediaPlayer? = null
    private var tickerJob: Job? = null

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val playlistQueue = mutableListOf<Track>()
    private var queueIndex = -1

    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queue: StateFlow<List<Track>> = _queue.asStateFlow()

    private val _queueIndex = MutableStateFlow(-1)
    val currentQueueIndex: StateFlow<Int> = _queueIndex.asStateFlow()

    private fun syncQueueState() {
        _queue.value = playlistQueue.toList()
        _queueIndex.value = queueIndex
    }

    init {
        initJavaFx()
        proxy.start()
    }

    fun play(track: Track, newQueue: List<Track> = emptyList()) {
        hasScrobbledCurrent = false
        hasUpdatedNowPlaying = false
        lastRecordedSecond = -1L

        scope.launch {
            try {
                if (newQueue.isNotEmpty()) {
                    playlistQueue.clear()
                    playlistQueue.addAll(newQueue)
                    queueIndex = playlistQueue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
                } else if (!playlistQueue.any { it.id == track.id }) {
                    playlistQueue.add(track)
                    queueIndex = playlistQueue.lastIndex
                } else {
                    queueIndex = playlistQueue.indexOfFirst { it.id == track.id }
                }
                syncQueueState()

                _state.value = _state.value.copy(
                    track = track,
                    status = PlaybackStatus.BUFFERING,
                    errorMessage = null,
                    positionMs = 0L,
                    durationMs = track.durationMs
                )

                // Check if this is a local audio file
                val localFile = track.localFilePath?.let { File(it) }
                val isLocalFile = localFile != null && localFile.exists() && localFile.length() > 0

                val resolved = if (isLocalFile) {
                    track
                } else if (track.streamUrl.isNullOrBlank()) {
                    // Resolve audio stream URL with preferred user quality (lossless FLAC or HQ YouTube stream)
                    val quality = database.getStreamingQuality()
                    searchService.resolveAudioStream(track, quality)
                } else {
                    track
                }

                _state.value = _state.value.copy(
                    track = resolved,
                    durationMs = if (resolved.durationMs > 0) resolved.durationMs else track.durationMs
                )

                database.addToHistory(resolved)

                val mediaSource = if (isLocalFile) {
                    localFile!!.toURI().toString()
                } else {
                    val finalUrl = resolved.streamUrl
                    if (finalUrl.isNullOrBlank()) {
                        _state.value = _state.value.copy(
                            status = PlaybackStatus.ERROR,
                            errorMessage = "Could not resolve audio stream for ${track.title}"
                        )
                        return@launch
                    }

                    // High-speed chunked cache: download in 1MB Range chunks (~500ms) to ensure continuous playback
                    val targetId = resolved.videoId ?: resolved.id
                    val cachedFile = if (targetId.isNotBlank()) audioCache.getCachedFile(targetId) else null

                    if (cachedFile != null && cachedFile.exists() && cachedFile.length() > 0) {
                        cachedFile.toURI().toString()
                    } else if (!finalUrl.isNullOrBlank()) {
                        val downloaded = audioCache.ensureTrackCached(targetId, finalUrl)
                        if (downloaded.exists() && downloaded.length() > 0) {
                            downloaded.toURI().toString()
                        } else {
                            proxy.getPlaybackUrl(finalUrl)
                        }
                    } else {
                        null
                    }
                }

                if (mediaSource.isNullOrBlank()) {
                    _state.value = _state.value.copy(
                        status = PlaybackStatus.ERROR,
                        errorMessage = "Could not load audio for ${track.title}"
                    )
                    return@launch
                }

                // Pre-warm next track in queue in background
                prewarmNextTrack()

                // Start playback on JavaFX thread
                Platform.runLater {
                    try {
                        stopCurrentPlayer()

                        val media = Media(mediaSource)
                        media.onError = Runnable {
                            val err = media.error?.message ?: "Media load error"
                            _state.value = _state.value.copy(
                                status = PlaybackStatus.ERROR,
                                errorMessage = err
                            )
                        }
                        val player = MediaPlayer(media)
                        mediaPlayer = player

                        player.volume = if (database.getBitPerfect()) {
                            1.0
                        } else {
                            if (_state.value.isMuted) 0.0 else _state.value.volume.toDouble()
                        }

                        player.currentTimeProperty().addListener { _, _, newTime ->
                            if (newTime != null) {
                                val ms = newTime.toMillis().toLong()
                                if (ms >= 0) {
                                    _state.value = _state.value.copy(positionMs = ms)
                                    val sec = ms / 1000
                                    if (sec != lastRecordedSecond && _state.value.status == PlaybackStatus.PLAYING) {
                                        lastRecordedSecond = sec
                                        liveListenSeconds.value += 1L
                                    }
                                    val totalMs = _state.value.durationMs
                                    if (!hasScrobbledCurrent && totalMs > 0L) {
                                        val scrobbleThreshold = minOf(totalMs / 2, 240_000L)
                                        if (ms >= 30_000L && ms >= scrobbleThreshold) {
                                            hasScrobbledCurrent = true
                                            val sk = database.getLastFmSessionKey()
                                            scope.launch(Dispatchers.IO) {
                                                if (sk.isNotBlank()) {
                                                    lastFmClient?.scrobble(resolved.artist, resolved.title, resolved.album, System.currentTimeMillis() / 1000, sk)
                                                }
                                                _scrobbleEvents.emit(Unit)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        player.totalDurationProperty().addListener { _, _, dur ->
                            if (dur != null && !dur.isUnknown) {
                                val ms = dur.toMillis().toLong()
                                if (ms > 0) {
                                    _state.value = _state.value.copy(durationMs = ms)
                                }
                            }
                        }

                        player.setOnReady {
                            val durationMs = media.duration?.toMillis()?.toLong() ?: resolved.durationMs
                            _state.value = _state.value.copy(
                                durationMs = if (durationMs > 0) durationMs else resolved.durationMs,
                                status = PlaybackStatus.PLAYING
                            )
                        }

                        player.setOnPlaying {
                            _state.value = _state.value.copy(status = PlaybackStatus.PLAYING)
                            if (!hasUpdatedNowPlaying) {
                                hasUpdatedNowPlaying = true
                                val sk = database.getLastFmSessionKey()
                                if (sk.isNotBlank()) {
                                    scope.launch(Dispatchers.IO) {
                                        lastFmClient?.updateNowPlaying(resolved.artist, resolved.title, resolved.album, sk)
                                    }
                                }
                            }
                        }

                        player.setOnPaused {
                            _state.value = _state.value.copy(status = PlaybackStatus.PAUSED)
                        }

                        player.setOnEndOfMedia {
                            handleEndOfTrack()
                        }

                        player.setOnError {
                            val msg = player.error?.message ?: "Playback error"
                            _state.value = _state.value.copy(
                                status = PlaybackStatus.ERROR,
                                errorMessage = msg
                            )
                        }

                        player.play()
                    } catch (e: Exception) {
                        _state.value = _state.value.copy(
                            status = PlaybackStatus.ERROR,
                            errorMessage = e.message ?: "Failed to initialize media player"
                        )
                    }
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    status = PlaybackStatus.ERROR,
                    errorMessage = e.message ?: "Error preparing playback"
                )
            }
        }
    }

    private fun prewarmNextTrack() {
        if (playlistQueue.isNotEmpty()) {
            val nextIdx = (queueIndex + 1) % playlistQueue.size
            val nextTrack = playlistQueue.getOrNull(nextIdx)
            if (nextTrack != null && nextTrack.id != _state.value.track?.id) {
                scope.launch(Dispatchers.IO) {
                    try {
                        val nextResolved = searchService.resolveAudioStream(nextTrack)
                        val nextId = nextResolved.videoId ?: nextResolved.id
                        val nextUrl = nextResolved.streamUrl
                        if (!nextUrl.isNullOrBlank() && nextId.isNotBlank()) {
                            audioCache.ensureTrackCached(nextId, nextUrl)
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }

    fun playNext(track: Track) {
        if (playlistQueue.isEmpty() || queueIndex < 0) {
            play(track, listOf(track))
        } else {
            val insertIdx = (queueIndex + 1).coerceAtMost(playlistQueue.size)
            playlistQueue.add(insertIdx, track)
            syncQueueState()
            prewarmNextTrack()
        }
    }

    fun addToQueue(track: Track) {
        if (playlistQueue.isEmpty()) {
            play(track, listOf(track))
        } else {
            playlistQueue.add(track)
            syncQueueState()
            if (playlistQueue.size == queueIndex + 2) {
                prewarmNextTrack()
            }
        }
    }

    fun removeFromQueue(index: Int) {
        if (index in playlistQueue.indices) {
            playlistQueue.removeAt(index)
            if (queueIndex > index) {
                queueIndex--
            } else if (queueIndex == index) {
                if (playlistQueue.isNotEmpty()) {
                    queueIndex = queueIndex.coerceAtMost(playlistQueue.lastIndex)
                    play(playlistQueue[queueIndex])
                } else {
                    stopCurrentPlayer()
                    _state.value = PlayerState()
                }
            }
            syncQueueState()
        }
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        if (fromIndex in playlistQueue.indices && toIndex in playlistQueue.indices && fromIndex != toIndex) {
            val currentTrack = playlistQueue.getOrNull(queueIndex)
            val item = playlistQueue.removeAt(fromIndex)
            playlistQueue.add(toIndex, item)
            if (currentTrack != null) {
                queueIndex = playlistQueue.indexOfFirst { it.id == currentTrack.id }.coerceAtLeast(0)
            }
            syncQueueState()
        }
    }

    fun clearQueue() {
        val current = _state.value.track
        playlistQueue.clear()
        if (current != null) {
            playlistQueue.add(current)
            queueIndex = 0
        } else {
            queueIndex = -1
        }
        syncQueueState()
    }

    fun jumpToQueueIndex(index: Int) {
        if (index in playlistQueue.indices) {
            queueIndex = index
            syncQueueState()
            play(playlistQueue[index])
        }
    }

    fun togglePlayPause() {
        when (_state.value.status) {
            PlaybackStatus.PLAYING -> pause()
            PlaybackStatus.PAUSED -> resume()
            PlaybackStatus.IDLE, PlaybackStatus.ERROR -> {
                _state.value.track?.let { play(it) }
            }
            else -> {}
        }
    }

    fun resume() {
        Platform.runLater {
            mediaPlayer?.play()
        }
    }

    fun pause() {
        Platform.runLater {
            mediaPlayer?.pause()
        }
    }

    fun seekTo(positionMs: Long) {
        val target = positionMs.coerceIn(0L, _state.value.durationMs.coerceAtLeast(0L))
        _state.value = _state.value.copy(positionMs = target)
        Platform.runLater {
            mediaPlayer?.seek(Duration.millis(target.toDouble()))
        }
    }

    fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        _state.value = _state.value.copy(volume = clamped, isMuted = false)
        Platform.runLater {
            if (!database.getBitPerfect()) {
                mediaPlayer?.volume = clamped.toDouble()
                mediaPlayer?.isMute = false
            } else {
                mediaPlayer?.volume = 1.0
                mediaPlayer?.isMute = false
            }
        }
    }

    fun toggleMute() {
        val newMuted = !_state.value.isMuted
        _state.value = _state.value.copy(isMuted = newMuted)
        Platform.runLater {
            mediaPlayer?.isMute = newMuted
        }
    }

    fun next() {
        if (playlistQueue.isEmpty()) return
        if (_state.value.isShuffled) {
            queueIndex = (playlistQueue.indices).random()
        } else {
            queueIndex = (queueIndex + 1) % playlistQueue.size
        }
        val nextTrack = playlistQueue.getOrNull(queueIndex) ?: return
        if (database.getCrossfade() && mediaPlayer != null && _state.value.status == PlaybackStatus.PLAYING) {
            val oldPlayer = mediaPlayer
            scope.launch {
                val currentVol = oldPlayer?.volume ?: 1.0
                for (step in 5 downTo 1) {
                    Platform.runLater { oldPlayer?.volume = currentVol * (step / 5.0) }
                    delay(200)
                }
                play(nextTrack)
            }
        } else {
            play(nextTrack)
        }
    }

    fun previous() {
        if (playlistQueue.isEmpty()) return
        if (_state.value.positionMs > 3000L) {
            seekTo(0L)
            return
        }
        if (queueIndex > 0) {
            queueIndex--
        } else {
            queueIndex = playlistQueue.lastIndex
        }
        val prevTrack = playlistQueue.getOrNull(queueIndex) ?: return
        play(prevTrack)
    }

    fun toggleShuffle() {
        _state.value = _state.value.copy(isShuffled = !_state.value.isShuffled)
    }

    fun cycleRepeatMode() {
        val nextMode = when (_state.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        _state.value = _state.value.copy(repeatMode = nextMode)
    }

    private fun handleEndOfTrack() {
        when (_state.value.repeatMode) {
            RepeatMode.ONE -> {
                seekTo(0L)
                resume()
            }
            RepeatMode.ALL -> {
                next()
            }
            RepeatMode.OFF -> {
                if (queueIndex < playlistQueue.lastIndex) {
                    next()
                } else {
                    _state.value = _state.value.copy(status = PlaybackStatus.IDLE, positionMs = 0L)
                }
            }
        }
    }

    private fun stopCurrentPlayer() {
        mediaPlayer?.let { p ->
            p.stop()
            p.dispose()
        }
        mediaPlayer = null
    }

    fun release() {
        proxy.stop()
        stopCurrentPlayer()
        scope.cancel()
    }
}
