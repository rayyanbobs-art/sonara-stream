package com.sonara.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.zIndex
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
import com.sonara.desktop.update.DesktopUpdateManager

fun main() = application {
    val windowState = rememberWindowState(width = 1120.dp, height = 860.dp)

    val database = remember { DesktopDatabase() }
    val lastFmClient = remember { LastFmClient() }
    val losslessClient = remember { LosslessClient() }
    val searchService = remember { MusicSearchService(losslessClient) }
    val lrclibClient = remember { LrclibClient() }
    val audioPlayer = remember { DesktopAudioPlayer(searchService, database) }
    val updateManager = remember { DesktopUpdateManager() }

    val playerState by audioPlayer.state.collectAsState()
    val updateState by updateManager.state.collectAsState()
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
                                        updateManager = updateManager,
                                        onSignOut = {
                                            isAuthenticated = false
                                            currentNav = NavItem.FEED
                                        }
                                    )
                                }
                            }
                        }

                        // Top Floating Update Banner
                        AnimatedVisibility(
                            visible = updateState.isUpdateAvailable && !updateState.isDismissed && !updateState.isDownloading,
                            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 16.dp)
                                .zIndex(10f)
                        ) {
                            Surface(
                                color = Color(0xFF1B2E1B),
                                border = BorderStroke(1.dp, SonaraTokens.Accent.copy(alpha = 0.6f)),
                                shape = RoundedCornerShape(16.dp),
                                shadowElevation = 8.dp,
                                modifier = Modifier
                                    .widthIn(max = 680.dp)
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.SystemUpdate,
                                        contentDescription = null,
                                        tint = SonaraTokens.Accent,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Update Available · Sonara Stream v${updateState.latestVersion}",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "A new version with performance improvements and bugfixes is available.",
                                            color = Color.White.copy(alpha = 0.75f),
                                            fontSize = 11.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Button(
                                        onClick = { updateManager.downloadAndInstall() },
                                        colors = ButtonDefaults.buttonColors(containerColor = SonaraTokens.Accent),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text("Install", color = SonaraTokens.TextOnAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    IconButton(
                                        onClick = { updateManager.dismissUpdate() },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Dismiss",
                                            tint = Color.White.copy(alpha = 0.7f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Auto-Update Download Progress Modal
                        if (updateState.isDownloading) {
                            AlertDialog(
                                onDismissRequest = {},
                                containerColor = SonaraTokens.SurfaceRaised,
                                shape = RoundedCornerShape(16.dp),
                                title = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Rounded.SystemUpdate,
                                            contentDescription = null,
                                            tint = SonaraTokens.Accent,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "Updating Sonara Stream...",
                                            color = SonaraTokens.TextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp
                                        )
                                    }
                                },
                                text = {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            text = updateState.downloadStatus ?: "Downloading installer...",
                                            color = SonaraTokens.TextSecondary,
                                            fontSize = 13.sp
                                        )
                                        LinearProgressIndicator(
                                            progress = { updateState.downloadProgress },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(8.dp)
                                                .clip(RoundedCornerShape(4.dp)),
                                            color = SonaraTokens.Accent,
                                            trackColor = SonaraTokens.SurfaceChip
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            Text(
                                                text = "${(updateState.downloadProgress * 100).toInt()}%",
                                                color = SonaraTokens.Accent,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                },
                                confirmButton = {}
                            )
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
