package com.sonara.desktop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    onLikeTrack: (Track) -> Unit,
    isLiked: (String) -> Boolean,
    database: DesktopDatabase,
    modifier: Modifier = Modifier
) {
    var favorites by remember { mutableStateOf(database.getFavorites()) }

    fun refresh() {
        favorites = database.getFavorites()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Column {
                Text(
                    text = "Liked Songs",
                    color = SonaraTheme.TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${favorites.size} tracks saved in local library",
                    color = SonaraTheme.TextSecondary,
                    fontSize = 13.sp
                )
            }

            if (favorites.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            favorites.firstOrNull()?.let { onPlayTrack(it, favorites) }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SonaraTheme.Primary,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Play All", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            val shuffled = favorites.shuffled()
                            shuffled.firstOrNull()?.let { onPlayTrack(it, shuffled) }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SonaraTheme.CardSurface,
                            contentColor = SonaraTheme.Secondary
                        ),
                        shape = RoundedCornerShape(20.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SonaraTheme.CardBorder)
                    ) {
                        Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Shuffle", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (favorites.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Rounded.FavoriteBorder,
                        contentDescription = null,
                        tint = SonaraTheme.TextMuted,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No liked songs yet",
                        color = SonaraTheme.TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Click the heart icon on any song to save it here.",
                        color = SonaraTheme.TextMuted,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(favorites) { index, track ->
                    TrackRow(
                        index = index + 1,
                        track = track,
                        isPlaying = isPlaying,
                        isCurrent = currentTrack?.id == track.id,
                        onPlay = { onPlayTrack(track, favorites) },
                        onLike = {
                            onLikeTrack(track)
                            refresh()
                        },
                        isLiked = isLiked(track.id)
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }
}
