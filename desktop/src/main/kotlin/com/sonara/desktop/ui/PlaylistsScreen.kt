package com.sonara.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonara.desktop.data.DesktopYtMusicApi
import com.sonara.desktop.model.Playlist
import com.sonara.desktop.model.Track
import com.sonara.desktop.storage.DesktopDatabase
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun PlaylistsScreen(
    onPlayTrack: (Track, List<Track>) -> Unit,
    currentTrack: Track?,
    isPlaying: Boolean,
    database: DesktopDatabase,
    onPlayNext: ((Track) -> Unit)? = null,
    onAddToQueue: ((Track) -> Unit)? = null,
    onToggleLike: ((Track) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var playlists by remember { mutableStateOf(database.getPlaylists()) }
    var favorites by remember { mutableStateOf(database.getFavorites()) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistTitle by remember { mutableStateOf("") }
    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }

    val isYtConnected = database.isYtConnected()
    val ytCookies = database.getYtCookies().orEmpty()
    val ytMusicApi = remember { DesktopYtMusicApi() }
    var remotePlaylists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var isLoadingRemote by remember { mutableStateOf(false) }
    var isLoadingTracks by remember { mutableStateOf(false) }

    fun syncYtPlaylists() {
        if (!isYtConnected || ytCookies.isBlank()) return
        scope.launch {
            isLoadingRemote = true
            try {
                val fetched = ytMusicApi.fetchLibraryPlaylists(ytCookies)
                if (fetched.isNotEmpty()) {
                    remotePlaylists = fetched
                }
            } catch (_: Exception) {}
            isLoadingRemote = false
        }
    }

    fun selectPlaylist(pl: Playlist) {
        selectedPlaylist = pl
        if (pl.isRemote && pl.tracks.isEmpty()) {
            scope.launch {
                isLoadingTracks = true
                try {
                    val tracks = ytMusicApi.fetchPlaylistTracks(pl.id, ytCookies)
                    selectedPlaylist = pl.copy(tracks = tracks, trackCount = tracks.size)
                } catch (_: Exception) {}
                isLoadingTracks = false
            }
        }
    }

    LaunchedEffect(isYtConnected) {
        if (isYtConnected) {
            syncYtPlaylists()
        }
    }

    fun refresh() {
        playlists = database.getPlaylists()
        favorites = database.getFavorites()
        selectedPlaylist = selectedPlaylist?.let { cur ->
            playlists.find { it.id == cur.id } ?: remotePlaylists.find { it.id == cur.id }
        }
    }

    val totalTracks = favorites.size + playlists.sumOf { it.tracks.size } + remotePlaylists.sumOf { it.tracks.size }
    val totalCount = playlists.size + remotePlaylists.size + 1 // +1 for Liked Songs

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create New Playlist", color = SonaraTokens.TextPrimary) },
            text = {
                OutlinedTextField(
                    value = newPlaylistTitle,
                    onValueChange = { newPlaylistTitle = it },
                    label = { Text("Playlist Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = SonaraTokens.TextPrimary,
                        unfocusedTextColor = SonaraTokens.TextPrimary,
                        focusedBorderColor = SonaraTokens.Accent,
                        unfocusedBorderColor = SonaraTokens.SurfaceChip
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPlaylistTitle.isNotBlank()) {
                            database.createPlaylist(newPlaylistTitle.trim())
                            newPlaylistTitle = ""
                            showCreateDialog = false
                            refresh()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SonaraTokens.Accent, contentColor = SonaraTokens.TextOnAccent)
                ) {
                    Text("Create", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel", color = SonaraTokens.TextSecondary)
                }
            },
            containerColor = SonaraTokens.Surface
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 14.dp)
                ) {
                    Column {
                        Text(
                            text = if (selectedPlaylist != null) selectedPlaylist!!.title else "Playlist",
                            color = SonaraTokens.TextPrimary,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        )
                        Text(
                            text = if (selectedPlaylist != null) "${selectedPlaylist!!.tracks.size} Tracks" else "$totalCount Playlists · $totalTracks Tracks",
                            color = SonaraTokens.TextSecondary,
                            fontSize = 13.sp
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (selectedPlaylist != null) {
                            IconButton(
                                onClick = { selectedPlaylist = null },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(SonaraTokens.SurfaceRaised)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ArrowBack,
                                    contentDescription = "Back",
                                    tint = SonaraTokens.TextPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        } else {
                            if (isYtConnected) {
                                IconButton(
                                    onClick = { syncYtPlaylists() },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(SonaraTokens.SurfaceRaised)
                                ) {
                                    if (isLoadingRemote) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = SonaraTokens.Accent
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Rounded.Sync,
                                            contentDescription = "Sync YouTube Music Playlists",
                                            tint = SonaraTokens.Accent,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            IconButton(
                                onClick = { showCreateDialog = true },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(SonaraTokens.SurfaceRaised)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = "Add Playlist",
                                    tint = SonaraTokens.TextPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Surface(
                                color = SonaraTokens.Surface,
                                shape = RoundedCornerShape(SonaraTokens.RadiusPill),
                                modifier = Modifier.height(40.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(horizontal = 14.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.Sort,
                                        contentDescription = null,
                                        tint = SonaraTokens.TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Sort",
                                        color = SonaraTokens.TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (selectedPlaylist != null) {
                if (isLoadingTracks) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(color = SonaraTokens.Accent)
                                Text("Loading tracks from YouTube Music...", color = SonaraTokens.TextSecondary, fontSize = 13.sp)
                            }
                        }
                    }
                } else if (selectedPlaylist!!.tracks.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No tracks found in this playlist", color = SonaraTokens.TextSecondary, fontSize = 14.sp)
                        }
                    }
                } else {
                    // Playlist tracks detail
                    items(selectedPlaylist!!.tracks) { track ->
                        DensityTrackRow(
                            track = track,
                            isPlaying = isPlaying,
                            isCurrent = currentTrack?.id == track.id,
                            onPlay = { onPlayTrack(track, selectedPlaylist!!.tracks) },
                            onPlayNext = onPlayNext,
                            onAddToQueue = onAddToQueue,
                            onToggleLike = onToggleLike,
                            isLiked = favorites.any { it.id == track.id }
                        )
                    }
                }
            } else {
                // Pinned Liked Songs row
                item {
                    val dateFormatted = remember {
                        LocalDateFormatter(System.currentTimeMillis())
                    }
                    Surface(
                        color = SonaraTokens.Surface,
                        shape = RoundedCornerShape(SonaraTokens.RadiusMd),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (favorites.isNotEmpty()) {
                                    onPlayTrack(favorites.first(), favorites)
                                }
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(RoundedCornerShape(SonaraTokens.RadiusSm))
                                    .background(SonaraTokens.SurfaceChip),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MusicNote,
                                    contentDescription = null,
                                    tint = SonaraTokens.TextSecondary,
                                    modifier = Modifier.size(26.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Liked Songs",
                                        color = SonaraTokens.TextPrimary,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text("📌", fontSize = 13.sp)
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${favorites.size} tracks · $dateFormatted",
                                    color = SonaraTokens.TextSecondary,
                                    fontSize = 13.sp
                                )
                            }

                            IconButton(
                                onClick = { },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreVert,
                                    contentDescription = "Options",
                                    tint = SonaraTokens.TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // Custom Playlists
                items(playlists) { pl ->
                    val dateFormatted = remember(pl.createdAt) {
                        LocalDateFormatter(pl.createdAt)
                    }

                    Surface(
                        color = SonaraTokens.Surface,
                        shape = RoundedCornerShape(SonaraTokens.RadiusMd),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedPlaylist = pl }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(14.dp)
                        ) {
                            AsyncArtwork(
                                url = pl.coverUrl,
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(RoundedCornerShape(SonaraTokens.RadiusSm))
                            )

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = pl.title,
                                    color = SonaraTokens.TextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${pl.tracks.size} tracks · $dateFormatted",
                                    color = SonaraTokens.TextSecondary,
                                    fontSize = 13.sp
                                )
                            }

                            // Circular Play Button
                            IconButton(
                                onClick = {
                                    pl.tracks.firstOrNull()?.let { onPlayTrack(it, pl.tracks) }
                                },
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(SonaraTokens.AccentStrong)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = "Play playlist",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            IconButton(
                                onClick = {
                                    database.deletePlaylist(pl.id)
                                    refresh()
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreVert,
                                    contentDescription = "Options",
                                    tint = SonaraTokens.TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                if (remotePlaylists.isNotEmpty()) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                        ) {
                            Text(
                                text = "YouTube Music Library",
                                color = SonaraTokens.TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                color = Color(0xFF421C1C),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "${remotePlaylists.size}",
                                    color = Color(0xFFFF6B6B),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    items(remotePlaylists) { pl ->
                        Surface(
                            color = SonaraTokens.Surface,
                            shape = RoundedCornerShape(SonaraTokens.RadiusMd),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectPlaylist(pl) }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(14.dp)
                            ) {
                                AsyncArtwork(
                                    url = pl.coverUrl,
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(RoundedCornerShape(SonaraTokens.RadiusSm))
                                )

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = pl.title,
                                            color = SonaraTokens.TextPrimary,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        Surface(
                                            color = Color(0xFF381E1E),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "YTM",
                                                color = Color(0xFFFF8080),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = pl.description.ifBlank { "YouTube Music Playlist" },
                                        color = SonaraTokens.TextSecondary,
                                        fontSize = 13.sp
                                    )
                                }

                                IconButton(
                                    onClick = { selectPlaylist(pl) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ChevronRight,
                                        contentDescription = "Open",
                                        tint = SonaraTokens.TextSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(90.dp))
            }
        }

        // Floating Sparkles Button (Generate a Mix)
        Surface(
            onClick = {
                val mix = database.getFavorites().shuffled().take(15)
                if (mix.isNotEmpty()) onPlayTrack(mix.first(), mix)
            },
            shape = CircleShape,
            color = SonaraTokens.AccentStrong,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 100.dp, end = 32.dp)
                .size(56.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = "Generate a mix",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

private fun LocalDateFormatter(epochMs: Long): String {
    return try {
        Instant.ofEpochMilli(epochMs)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
    } catch (_: Exception) {
        "Sep 15, 2026"
    }
}
