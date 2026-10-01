package com.sonara.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonara.desktop.model.Track
import com.sonara.desktop.storage.DesktopDatabase

@Composable
fun FavoritesScreen(
    onPlayTrack: (Track, List<Track>) -> Unit,
    currentTrack: Track?,
    isPlaying: Boolean,
    database: DesktopDatabase,
    onPlayNext: ((Track) -> Unit)? = null,
    onAddToQueue: ((Track) -> Unit)? = null,
    onToggleLike: ((Track) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var favorites by remember { mutableStateOf(database.getFavorites()) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredFavorites = remember(favorites, searchQuery) {
        if (searchQuery.isBlank()) favorites
        else {
            val q = searchQuery.trim().lowercase()
            favorites.filter {
                it.title.lowercase().contains(q) ||
                it.artist.lowercase().contains(q) ||
                it.album.lowercase().contains(q)
            }
        }
    }

    val totalDurationMs = remember(filteredFavorites) {
        filteredFavorites.sumOf { it.durationMs }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 20.dp)
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text(
                    text = "Favorites",
                    color = SonaraTokens.TextPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${filteredFavorites.size} saved tracks • ${formatDuration(totalDurationMs)} total",
                    color = SonaraTokens.TextSecondary,
                    fontSize = 13.sp
                )
            }

            if (filteredFavorites.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val shuffled = filteredFavorites.shuffled()
                            val first = shuffled.firstOrNull()
                            if (first != null) onPlayTrack(first, shuffled)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SonaraTokens.SurfaceChip,
                            contentColor = SonaraTokens.TextPrimary
                        ),
                        shape = RoundedCornerShape(SonaraTokens.RadiusPill)
                    ) {
                        Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Shuffle", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            val first = filteredFavorites.firstOrNull()
                            if (first != null) onPlayTrack(first, filteredFavorites)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SonaraTokens.Accent,
                            contentColor = SonaraTokens.TextOnAccent
                        ),
                        shape = RoundedCornerShape(SonaraTokens.RadiusPill)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Play All", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (favorites.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search favorites...", color = SonaraTokens.TextSecondary, fontSize = 13.sp) },
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Rounded.Search, contentDescription = null, tint = SonaraTokens.TextSecondary, modifier = Modifier.size(18.dp))
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear", tint = SonaraTokens.TextSecondary, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = SonaraTokens.TextPrimary,
                    unfocusedTextColor = SonaraTokens.TextPrimary,
                    focusedBorderColor = SonaraTokens.Accent,
                    unfocusedBorderColor = SonaraTokens.SurfaceChip,
                    focusedContainerColor = SonaraTokens.Surface,
                    unfocusedContainerColor = SonaraTokens.Surface
                ),
                shape = RoundedCornerShape(SonaraTokens.RadiusPill),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (favorites.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(SonaraTokens.SurfaceChip),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Favorite,
                            contentDescription = null,
                            tint = SonaraTokens.Accent,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No liked tracks yet",
                        color = SonaraTokens.TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Click the heart icon while listening to save tracks here",
                        color = SonaraTokens.TextSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        } else if (filteredFavorites.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No favorites match \"$searchQuery\"",
                    color = SonaraTokens.TextSecondary,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(filteredFavorites, key = { index, item -> "${item.id}_$index" }) { _, track ->
                    DensityTrackRow(
                        track = track,
                        isPlaying = isPlaying,
                        isCurrent = currentTrack?.id == track.id,
                        onPlay = { onPlayTrack(track, filteredFavorites) },
                        onPlayNext = onPlayNext,
                        onAddToQueue = onAddToQueue,
                        onToggleLike = { trk ->
                            onToggleLike?.invoke(trk)
                            favorites = database.getFavorites()
                        },
                        isLiked = true
                    )
                }
            }
        }
    }
}
