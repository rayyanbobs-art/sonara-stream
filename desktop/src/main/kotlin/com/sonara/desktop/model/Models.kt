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
    val tracks: List<Track> = emptyList(),
)

enum class NavItem(val label: String) {
    DISCOVER("Discover"),
    SEARCH("Search"),
    FAVORITES("Favorites"),
    PLAYLISTS("Playlists"),
    SETTINGS("Settings")
}
