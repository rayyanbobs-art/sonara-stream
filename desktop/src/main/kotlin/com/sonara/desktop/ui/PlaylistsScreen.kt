package com.sonara.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonara.desktop.model.Playlist
import com.sonara.desktop.model.Track
import com.sonara.desktop.storage.DesktopDatabase

@Composable
fun PlaylistsScreen(
    onPlayTrack: (Track, List<Track>) -> Unit,
    currentTrack: Track?,
    isPlaying: Boolean,
    onLikeTrack: (Track) -> Unit,
    isLiked: (String) -> Boolean,
    database: DesktopDatabase,
    modifier: Modifier = Modifier
) {
    var playlists by remember { mutableStateOf(database.getPlaylists()) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistTitle by remember { mutableStateOf("") }
    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }

    fun refresh() {
        playlists = database.getPlaylists()
        selectedPlaylist = selectedPlaylist?.let { cur ->
            playlists.find { it.id == cur.id }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create New Playlist", color = SonaraTheme.TextPrimary) },
            text = {
                OutlinedTextField(
                    value = newPlaylistTitle,
                    onValueChange = { newPlaylistTitle = it },
                    label = { Text("Playlist Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = SonaraTheme.TextPrimary,
                        unfocusedTextColor = SonaraTheme.TextPrimary,
                        focusedBorderColor = SonaraTheme.Secondary,
                        unfocusedBorderColor = SonaraTheme.CardBorder
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
                    colors = ButtonDefaults.buttonColors(containerColor = SonaraTheme.Primary)
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel", color = SonaraTheme.TextMuted)
                }
            },
            containerColor = SonaraTheme.ElevatedSurface
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        // Top Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Column {
                Text(
                    text = if (selectedPlaylist != null) selectedPlaylist!!.title else "Playlists",
                    color = SonaraTheme.TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (selectedPlaylist != null) "${selectedPlaylist!!.tracks.size} tracks" else "${playlists.size} playlists",
                    color = SonaraTheme.TextSecondary,
                    fontSize = 13.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (selectedPlaylist != null) {
                    Button(
                        onClick = { selectedPlaylist = null },
                        colors = ButtonDefaults.buttonColors(containerColor = SonaraTheme.CardSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SonaraTheme.CardBorder)
                    ) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Back to All", color = SonaraTheme.TextSecondary)
                    }
                } else {
                    Button(
                        onClick = { showCreateDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = SonaraTheme.Primary),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("New Playlist", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (selectedPlaylist != null) {
            // Detailed Playlist View
            val currentTracks = selectedPlaylist!!.tracks
            if (currentTracks.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("This playlist is empty. Add songs from Search or Discover!", color = SonaraTheme.TextMuted)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(currentTracks) { idx, track ->
                        TrackRow(
                            index = idx + 1,
                            track = track,
                            isPlaying = isPlaying,
                            isCurrent = currentTrack?.id == track.id,
                            onPlay = { onPlayTrack(track, currentTracks) },
                            onLike = { onLikeTrack(track) },
                            isLiked = isLiked(track.id)
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        } else {
            // Playlists Grid / List
            if (playlists.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.QueueMusic, contentDescription = null, tint = SonaraTheme.TextMuted, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No playlists yet", color = SonaraTheme.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Create one above to organize your music.", color = SonaraTheme.TextMuted, fontSize = 13.sp)
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(playlists) { pl ->
                        Surface(
                            onClick = { selectedPlaylist = pl },
                            color = SonaraTheme.CardSurface,
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SonaraTheme.CardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(14.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(SonaraTheme.ElevatedSurface),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Rounded.QueueMusic, contentDescription = null, tint = SonaraTheme.Primary, modifier = Modifier.size(28.dp))
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(pl.title, color = SonaraTheme.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    Text("${pl.tracks.size} tracks", color = SonaraTheme.TextSecondary, fontSize = 12.sp)
                                }

                                IconButton(
                                    onClick = {
                                        database.deletePlaylist(pl.id)
                                        refresh()
                                    }
                                ) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete", tint = SonaraTheme.TextMuted, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }
}
