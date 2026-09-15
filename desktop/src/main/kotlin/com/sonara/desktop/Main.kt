package com.sonara.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.sonara.desktop.audio.DesktopAudioPlayer
import com.sonara.desktop.data.LosslessClient
import com.sonara.desktop.data.LrclibClient
import com.sonara.desktop.data.MusicSearchService
import com.sonara.desktop.model.NavItem
import com.sonara.desktop.model.SyncedLine
import com.sonara.desktop.model.Track
import com.sonara.desktop.storage.DesktopDatabase
import com.sonara.desktop.ui.*
import kotlinx.coroutines.launch

fun main() = application {
    val windowState = rememberWindowState(width = 1200.dp, height = 760.dp)

    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "Sonara Stream - Lossless Music Player"
    ) {
        val database = remember { DesktopDatabase() }
        val losslessClient = remember { LosslessClient() }
        val searchService = remember { MusicSearchService(losslessClient) }
        val lrclibClient = remember { LrclibClient() }
        val audioPlayer = remember { DesktopAudioPlayer(searchService, database) }
        val scope = rememberCoroutineScope()

        val playerState by audioPlayer.state.collectAsState()
        var currentNav by remember { mutableStateOf(NavItem.DISCOVER) }
        var isLyricsOpen by remember { mutableStateOf(false) }
        var lyrics by remember { mutableStateOf<List<SyncedLine>>(emptyList()) }
        var favoriteIds by remember { mutableStateOf(database.getFavorites().map { it.id }.toSet()) }

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

        DisposableEffect(Unit) {
            onDispose {
                audioPlayer.release()
            }
        }

        SonaraStreamTheme {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SonaraTheme.Background)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Main Content Row (Sidebar + Screen + Lyrics Panel)
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        // Left Navigation Sidebar
                        Sidebar(
                            currentNav = currentNav,
                            onNavSelect = { currentNav = it }
                        )

                        // Central View
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            when (currentNav) {
                                NavItem.DISCOVER -> DiscoverScreen(
                                    onPlayTrack = ::handlePlayTrack,
                                    currentTrack = playerState.track,
                                    isPlaying = playerState.status == com.sonara.desktop.model.PlaybackStatus.PLAYING,
                                    onLikeTrack = ::handleLikeTrack,
                                    isLiked = { favoriteIds.contains(it) },
                                    searchService = searchService
                                )
                                NavItem.SEARCH -> SearchScreen(
                                    onPlayTrack = ::handlePlayTrack,
                                    currentTrack = playerState.track,
                                    isPlaying = playerState.status == com.sonara.desktop.model.PlaybackStatus.PLAYING,
                                    onLikeTrack = ::handleLikeTrack,
                                    isLiked = { favoriteIds.contains(it) },
                                    searchService = searchService
                                )
                                NavItem.FAVORITES -> FavoritesScreen(
                                    onPlayTrack = ::handlePlayTrack,
                                    currentTrack = playerState.track,
                                    isPlaying = playerState.status == com.sonara.desktop.model.PlaybackStatus.PLAYING,
                                    onLikeTrack = ::handleLikeTrack,
                                    isLiked = { favoriteIds.contains(it) },
                                    database = database
                                )
                                NavItem.PLAYLISTS -> PlaylistsScreen(
                                    onPlayTrack = ::handlePlayTrack,
                                    currentTrack = playerState.track,
                                    isPlaying = playerState.status == com.sonara.desktop.model.PlaybackStatus.PLAYING,
                                    onLikeTrack = ::handleLikeTrack,
                                    isLiked = { favoriteIds.contains(it) },
                                    database = database
                                )
                                NavItem.SETTINGS -> SettingsScreen(
                                    losslessClient = losslessClient,
                                    database = database
                                )
                            }
                        }

                        // Right Slide-Over Synced Lyrics Panel
                        AnimatedVisibility(
                            visible = isLyricsOpen,
                            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
                        ) {
                            LyricsSheet(
                                lyrics = lyrics,
                                positionMs = playerState.positionMs,
                                onClose = { isLyricsOpen = false },
                                onSeekTo = { audioPlayer.seekTo(it) }
                            )
                        }
                    }

                    // Floating Dock Bottom Player Bar
                    PlayerBottomBar(
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
                        isLyricsOpen = isLyricsOpen
                    )
                }
            }
        }
    }
}
