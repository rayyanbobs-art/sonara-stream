package com.sonara.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonara.desktop.data.MusicSearchService
import com.sonara.desktop.model.Track

@Composable
fun DiscoverScreen(
    onPlayTrack: (Track, List<Track>) -> Unit,
    currentTrack: Track?,
    isPlaying: Boolean,
    onLikeTrack: (Track) -> Unit,
    isLiked: (String) -> Boolean,
    searchService: MusicSearchService,
    modifier: Modifier = Modifier
) {
    val curatedTracks = remember { searchService.getCuratedDiscoverTracks() }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Hero Banner
        item {
            Surface(
                color = Color.Transparent,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color(0xFF3B1E78),
                                Color(0xFF1E103E),
                                Color(0xFF0F0B1E)
                            )
                        )
                    )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxSize().padding(24.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        HiResBadge(label = "MASTER QUALITY AUDIO", isLossless = true)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Pure Lossless Streaming",
                            color = Color.White,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Enjoy pristine 24-bit / 96 kHz studio-master FLAC audio and real-time synchronized lyrics.",
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 13.sp,
                            maxLines = 2
                        )
                    }

                    Button(
                        onClick = {
                            curatedTracks.firstOrNull()?.let { onPlayTrack(it, curatedTracks) }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SonaraTheme.Secondary,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.padding(start = 16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Play Featured", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Quick Picks section
        item {
            Text(
                text = "Quick Picks & Hits",
                color = SonaraTheme.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(curatedTracks.take(5)) { track ->
                    TrackCard(
                        track = track,
                        isPlaying = isPlaying,
                        isCurrent = currentTrack?.id == track.id,
                        onPlay = { onPlayTrack(track, curatedTracks) }
                    )
                }
            }
        }

        // Popular Tracks
        item {
            Text(
                text = "Popular Tracks",
                color = SonaraTheme.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        itemsIndexed(curatedTracks) { index, track ->
            TrackRow(
                index = index + 1,
                track = track,
                isPlaying = isPlaying,
                isCurrent = currentTrack?.id == track.id,
                onPlay = { onPlayTrack(track, curatedTracks) },
                onLike = { onLikeTrack(track) },
                isLiked = isLiked(track.id)
            )
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}
