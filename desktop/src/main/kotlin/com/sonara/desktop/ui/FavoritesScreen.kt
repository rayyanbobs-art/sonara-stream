package com.sonara.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Column {
                Text(
                    text = "Liked Songs 📌",
                    color = SonaraTokens.TextPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${favorites.size} saved tracks · Offline & Streaming",
                    color = SonaraTokens.TextSecondary,
                    fontSize = 13.sp
                )
            }

            if (favorites.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val shuffled = favorites.shuffled()
                            onPlayTrack(shuffled.first(), shuffled)
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
                            onPlayTrack(favorites.first(), favorites)
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

        if (favorites.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp),
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
                        "Click the heart button while listening to save tracks here",
                        color = SonaraTokens.TextSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(favorites, key = { index, item -> "${item.id}_$index" }) { _, track ->
                    DensityTrackRow(
                        track = track,
                        isPlaying = isPlaying,
                        isCurrent = currentTrack?.id == track.id,
                        onPlay = { onPlayTrack(track, favorites) },
                        onPlayNext = onPlayNext,
                        onAddToQueue = onAddToQueue,
                        onToggleLike = { trk ->
                            onToggleLike?.invoke(trk)
                            favorites = database.getFavorites()
                        },
                        isLiked = true
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(84.dp))
                }
            }
        }
    }
}
