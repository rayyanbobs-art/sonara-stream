package com.sonara.desktop.model

import kotlinx.serialization.Serializable

@Serializable
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationMs: Long = 0L,
    val artworkUrl: String? = null,
    val videoId: String? = null,
    val streamUrl: String? = null,
    val isLossless: Boolean = false,
    val audioQuality: String = "16-bit / 44.1 kHz",
    val isDownloaded: Boolean = false,
    val localFilePath: String? = null,
)

enum class PlaybackStatus {
    IDLE,
    BUFFERING,
    PLAYING,
    PAUSED,
    ERROR
}

enum class RepeatMode {
    OFF,
    ALL,
    ONE
}

data class PlayerState(
    val track: Track? = null,
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val volume: Float = 1.0f,
    val isMuted: Boolean = false,
    val isShuffled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val errorMessage: String? = null,
)

@Serializable
data class SyncedLine(
    val timeMs: Long,
    val text: String,
    val translation: String? = null,
)

@Serializable
data class Playlist(
    val id: String,
    val title: String,
    val description: String = "",
    val trackCount: Int = 0,
    val coverUrl: String? = null,
    val isPinned: Boolean = false,
    val isRemote: Boolean = false,
    val tracks: List<Track> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class LastFmUser(
    val name: String,
    val playcount: Long = 0L,
    val avatarUrl: String? = null,
    val isConnected: Boolean = true
)

@Serializable
data class LastFmStats(
    val scrobbles: Long = 13L,
    val tracks: Long = 13L,
    val artists: Long = 10L,
    val albums: Long = 12L,
    val listeningTime: String = "49m 0s"
)

@Serializable
data class ScrobbleItem(
    val title: String,
    val artist: String,
    val album: String = "",
    val artworkUrl: String? = null,
    val timestamp: Long = 0L,
    val isNowPlaying: Boolean = false
)

enum class NavItem(val label: String) {
    FEED("Feed"),
    STATS("Stats"),
    PLAYLISTS("Playlists"),
    DISCOVER("Discover"),
    SETTINGS("Settings")
}
