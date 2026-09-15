package com.sonara.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.sonara.desktop.audio.DesktopAudioPlayer
import com.sonara.desktop.data.LastFmClient
import com.sonara.desktop.data.LosslessClient
import com.sonara.desktop.data.LrclibClient
import com.sonara.desktop.data.MusicSearchService
import com.sonara.desktop.model.NavItem
import com.sonara.desktop.model.PlaybackStatus
import com.sonara.desktop.model.SyncedLine
import com.sonara.desktop.model.Track
import com.sonara.desktop.storage.DesktopDatabase
import com.sonara.desktop.ui.*

fun main() = application {
    val windowState = rememberWindowState(width = 1120.dp, height = 860.dp)

    val database = remember { DesktopDatabase() }
    val lastFmClient = remember { LastFmClient() }
    val losslessClient = remember { LosslessClient() }
    val searchService = remember { MusicSearchService(losslessClient) }
    val lrclibClient = remember { LrclibClient() }
    val audioPlayer = remember { DesktopAudioPlayer(searchService, database) }

    val playerState by audioPlayer.state.collectAsState()
    var isAuthenticated by remember { mutableStateOf(database.isAuthenticated()) }
    var currentNav by remember { mutableStateOf(NavItem.FEED) }
    var searchActive by remember { mutableStateOf(false) }
    var isLyricsOpen by remember { mutableStateOf(false) }
    var lyrics by remember { mutableStateOf<List<SyncedLine>>(emptyList()) }
    var favoriteIds by remember { mutableStateOf(database.getFavorites().map { it.id }.toSet()) }
    var lastFmUser by remember { mutableStateOf(database.getLastFmUser()) }

    // Fetch real-time synced lyrics when track changes
    LaunchedEffect(playerState.track?.id) {
        val track = playerState.track
        if (track != null) {
            lyrics = emptyList()
            lyrics = lrclibClient.fetchLyrics(
                trackName = track.title,
                artistName = track.artist,
                albumName = track.album,
                durationSeconds = if (track.durationMs > 0) (track.durationMs / 1000).toInt() else null
            )
        }
    }

    fun handlePlayTrack(track: Track, queue: List<Track>) {
        audioPlayer.play(track, queue)
    }

    fun handleLikeTrack(track: Track) {
        if (favoriteIds.contains(track.id)) {
            database.removeFavorite(track.id)
            favoriteIds = favoriteIds - track.id
        } else {
            database.addFavorite(track)
            favoriteIds = favoriteIds + track.id
        }
    }

    fun handleLikeCurrentTrack() {
        playerState.track?.let { handleLikeTrack(it) }
    }

    DisposableEffect(Unit) {
        onDispose {
            audioPlayer.release()
        }
    }

    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "Sonara Stream - Studio Master Audio",
        onKeyEvent = { keyEvent ->
            if (keyEvent.type == KeyEventType.KeyDown) {
                when {
                    keyEvent.key == Key.Spacebar && !keyEvent.isCtrlPressed -> {
                        audioPlayer.togglePlayPause()
                        true
                    }
                    keyEvent.isCtrlPressed && keyEvent.key == Key.DirectionLeft -> {
                        audioPlayer.previous()
                        true
                    }
                    keyEvent.isCtrlPressed && keyEvent.key == Key.DirectionRight -> {
                        audioPlayer.next()
                        true
                    }
                    keyEvent.key == Key.DirectionLeft -> {
                        audioPlayer.seekTo((playerState.positionMs - 5000L).coerceAtLeast(0L))
                        true
                    }
                    keyEvent.key == Key.DirectionRight -> {
                        val maxDur = playerState.track?.durationMs ?: 0L
                        audioPlayer.seekTo((playerState.positionMs + 5000L).coerceAtMost(maxDur))
                        true
                    }
                    keyEvent.isCtrlPressed && keyEvent.key == Key.L -> {
                        handleLikeCurrentTrack()
                        true
                    }
                    keyEvent.isCtrlPressed && keyEvent.key == Key.Comma -> {
                        currentNav = NavItem.SETTINGS
                        searchActive = false
                        true
                    }
                    keyEvent.isCtrlPressed && keyEvent.key == Key.F -> {
                        searchActive = true
                        true
                    }
                    keyEvent.key == Key.Escape -> {
                        when {
                            isLyricsOpen -> {
                                isLyricsOpen = false
                                true
                            }
                            searchActive -> {
                                searchActive = false
                                true
                            }
                            currentNav != NavItem.FEED -> {
                                currentNav = NavItem.FEED
                                true
                            }
                            else -> false
                        }
                    }
                    else -> false
                }
            } else {
                false
            }
        }
    ) {
        SonaraStreamTheme {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SonaraTokens.Bg)
            ) {
                if (!isAuthenticated) {
                    LoginScreen(
                        database = database,
                        lastFmClient = lastFmClient,
                        onLoginSuccess = { user ->
                            lastFmUser = user
                            isAuthenticated = true
                            currentNav = NavItem.FEED
                        }
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        // Centered Adaptive Content Area
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .widthIn(max = 760.dp)
                                .padding(bottom = if (playerState.track != null) 170.dp else 90.dp)
                        ) {
                            if (searchActive) {
                                SearchScreen(
                                    onPlayTrack = ::handlePlayTrack,
                                    currentTrack = playerState.track,
                                    isPlaying = playerState.status == PlaybackStatus.PLAYING,
                                    onBack = { searchActive = false },
                                    searchService = searchService
                                )
                            } else {
                                when (currentNav) {
                                    NavItem.FEED -> FeedScreen(
                                        onPlayTrack = ::handlePlayTrack,
                                        currentTrack = playerState.track,
                                        isPlaying = playerState.status == PlaybackStatus.PLAYING,
                                        onNavigateToDiscover = { currentNav = NavItem.DISCOVER },
                                        onNavigateToSearch = { searchActive = true },
                                        onNavigateToSettings = { currentNav = NavItem.SETTINGS },
                                        searchService = searchService,
                                        lastFmClient = lastFmClient,
                                        lastFmUser = lastFmUser
                                    )
                                    NavItem.STATS -> StatsScreen(
                                        lastFmUser = lastFmUser,
                                        lastFmClient = lastFmClient,
                                        onPlayTrack = { trk -> handlePlayTrack(trk, listOf(trk)) },
                                        onNavigateToDiscover = { currentNav = NavItem.DISCOVER },
                                        onNavigateToSearch = { searchActive = true },
                                        onNavigateToSettings = { currentNav = NavItem.SETTINGS }
                                    )
                                    NavItem.PLAYLISTS -> PlaylistsScreen(
                                        onPlayTrack = ::handlePlayTrack,
                                        currentTrack = playerState.track,
                                        isPlaying = playerState.status == PlaybackStatus.PLAYING,
                                        database = database
                                    )
                                    NavItem.DISCOVER -> DiscoverScreen(
                                        onPlayTrack = ::handlePlayTrack,
                                        currentTrack = playerState.track,
                                        isPlaying = playerState.status == PlaybackStatus.PLAYING,
                                        onBack = { currentNav = NavItem.FEED },
                                        lastFmClient = lastFmClient,
                                        searchService = searchService
                                    )
                                    NavItem.SETTINGS -> SettingsScreen(
                                        onBack = { currentNav = NavItem.FEED },
                                        losslessClient = losslessClient,
                                        database = database,
                                        lastFmClient = lastFmClient,
                                        onSignOut = {
                                            isAuthenticated = false
                                            currentNav = NavItem.FEED
                                        }
                                    )
                                }
                            }
                        }

                        // Bottom Floating Dock and Now Playing Bar
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .widthIn(max = 760.dp)
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (playerState.track != null) {
                                NowPlayingBottomBar(
                                    playerState = playerState,
                                    onPlayPause = { audioPlayer.togglePlayPause() },
                                    onNext = { audioPlayer.next() },
                                    onPrevious = { audioPlayer.previous() },
                                    onSeek = { audioPlayer.seekTo(it) },
                                    onVolumeChange = { audioPlayer.setVolume(it) },
                                    onToggleMute = { audioPlayer.toggleMute() },
                                    onToggleShuffle = { audioPlayer.toggleShuffle() },
                                    onCycleRepeat = { audioPlayer.cycleRepeatMode() },
                                    onToggleLyrics = { isLyricsOpen = !isLyricsOpen },
                                    isLyricsOpen = isLyricsOpen,
                                    isLiked = favoriteIds.contains(playerState.track!!.id),
                                    onLikeTrack = ::handleLikeCurrentTrack
                                )
                            }

                            // Android Floating Pill Dock
                            FloatingBottomNavDock(
                                currentNav = if (currentNav in listOf(NavItem.FEED, NavItem.STATS, NavItem.PLAYLISTS)) currentNav else NavItem.FEED,
                                onNavSelect = { nav ->
                                    currentNav = nav
                                    searchActive = false
                                }
                            )
                        }

                        // Right Slide-Over Synced Lyrics Panel
                        AnimatedVisibility(
                            visible = isLyricsOpen,
                            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
                            modifier = Modifier.align(Alignment.CenterEnd)
                        ) {
                            LyricsSheet(
                                lyrics = lyrics,
                                positionMs = playerState.positionMs,
                                onClose = { isLyricsOpen = false },
                                onSeekTo = { audioPlayer.seekTo(it) }
                            )
                        }
                    }
                }
            }
        }
    }
}
