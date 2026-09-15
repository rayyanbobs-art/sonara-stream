package com.sonara.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonara.desktop.data.LastFmClient
import com.sonara.desktop.data.MusicSearchService
import com.sonara.desktop.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DiscoverScreen(
    onPlayTrack: (Track, List<Track>) -> Unit,
    currentTrack: Track?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    lastFmClient: LastFmClient,
    searchService: MusicSearchService,
    modifier: Modifier = Modifier
) {
    var tracks by remember { mutableStateOf<List<Track>>(lastFmClient.getDefaultDiscoverTracks()) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isLoading = true
        val fetched = withContext(Dispatchers.IO) {
            try {
                lastFmClient.getDiscoverTracks(50)
            } catch (_: Exception) {
                lastFmClient.getDefaultDiscoverTracks()
            }
        }
        if (fetched.isNotEmpty()) {
            tracks = fetched
        }
        isLoading = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        // Top Header matching Screenshot 4
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(SonaraTokens.SurfaceChip)
            ) {
                Icon(
                    imageVector = Icons.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = SonaraTokens.TextPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Discover",
                    color = SonaraTokens.TextPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Fresh tracks, powered by Last.fm",
                    color = SonaraTokens.TextSecondary,
                    fontSize = 13.sp
                )
            }

            // Bookmark button
            IconButton(
                onClick = { /* Saved bookmark action */ },
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(SonaraTokens.SurfaceChip)
            ) {
                Icon(
                    imageVector = Icons.Rounded.BookmarkAdd,
                    contentDescription = "Bookmark",
                    tint = SonaraTokens.TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Shuffle button
            IconButton(
                onClick = {
                    if (tracks.isNotEmpty()) {
                        val shuffled = tracks.shuffled()
                        onPlayTrack(shuffled.first(), shuffled)
                    }
                },
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(SonaraTokens.SurfaceChip)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shuffle,
                    contentDescription = "Shuffle and Play",
                    tint = SonaraTokens.TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Dividerless dense list of rounded track cards
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(tracks, key = { index, item -> "${item.id}_$index" }) { _, track ->
                val isCurrent = currentTrack?.id == track.id
                Surface(
                    color = if (isCurrent) SonaraTokens.SurfaceRaised else SonaraTokens.Surface,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPlayTrack(track, tracks) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Artwork
                        AsyncArtwork(
                            url = track.artworkUrl,
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )

                        Spacer(modifier = Modifier.width(14.dp))

                        // Title and Artist
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                color = if (isCurrent) SonaraTokens.Accent else SonaraTokens.TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = track.artist,
                                color = SonaraTokens.TextSecondary,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Trailing circular options button
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (isCurrent) SonaraTokens.AccentTint else Color.Transparent)
                                .clickable { /* Track options */ },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "More",
                                tint = if (isCurrent) SonaraTokens.Accent else SonaraTokens.TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(84.dp))
            }
        }
    }
}
