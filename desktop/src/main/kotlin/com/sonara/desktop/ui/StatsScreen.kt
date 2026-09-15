package com.sonara.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonara.desktop.data.LastFmClient
import com.sonara.desktop.model.LastFmStats
import com.sonara.desktop.model.ScrobbleItem
import com.sonara.desktop.model.Track
import kotlinx.coroutines.launch

@Composable
fun StatsScreen(
    lastFmUser: String,
    lastFmClient: LastFmClient,
    onPlayTrack: (Track) -> Unit,
    onNavigateToDiscover: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var stats by remember { mutableStateOf(LastFmStats()) }
    var recentTracks by remember { mutableStateOf<List<ScrobbleItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(lastFmUser) {
        isLoading = true
        scope.launch {
            stats = lastFmClient.getUserStats(lastFmUser)
            recentTracks = lastFmClient.getRecentTracks(lastFmUser)
            isLoading = false
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header
        item {
            TopAppHeader(
                title = "Stats",
                onDiscoverClick = onNavigateToDiscover,
                onSearchClick = onNavigateToSearch,
                onSettingsClick = onNavigateToSettings
            )
        }

        // User Pill Row (Username + Listening Time)
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                // Username pill
                Surface(
                    color = SonaraTokens.Surface,
                    shape = RoundedCornerShape(SonaraTokens.RadiusPill),
                    modifier = Modifier.height(38.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = lastFmUser,
                            color = SonaraTokens.TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Listening Time pill
                Surface(
                    color = SonaraTokens.Surface,
                    shape = RoundedCornerShape(SonaraTokens.RadiusPill),
                    modifier = Modifier.height(38.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Headphones,
                            contentDescription = null,
                            tint = SonaraTokens.TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stats.listeningTime,
                            color = SonaraTokens.TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Hero Scrobble Count Card
        item {
            Surface(
                color = SonaraTokens.SurfaceRaised,
                shape = RoundedCornerShape(SonaraTokens.RadiusLg),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Inner green hero container
                    Surface(
                        color = SonaraTokens.AccentStrong,
                        shape = RoundedCornerShape(SonaraTokens.RadiusMd),
                        modifier = Modifier.fillMaxWidth().height(140.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp)
                        ) {
                            Column {
                                Text(
                                    text = "${stats.scrobbles}",
                                    color = Color.White,
                                    fontSize = 54.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-1).sp
                                )
                                Text(
                                    text = "Scrobbles",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            // Circular Arrow Button
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(SonaraTokens.Accent)
                                    .clickable { onNavigateToDiscover() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                    contentDescription = "View more",
                                    tint = SonaraTokens.TextOnAccent,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 3-up stat tiles (Tracks, Artists, Albums)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Tracks
                        Surface(
                            color = SonaraTokens.Surface,
                            shape = RoundedCornerShape(SonaraTokens.RadiusMd),
                            modifier = Modifier.weight(1f).height(80.dp)
                        ) {
                            Column(
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Text(
                                    text = "${stats.tracks}",
                                    color = SonaraTokens.TextPrimary,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Tracks",
                                    color = SonaraTokens.TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        // Artists
                        Surface(
                            color = SonaraTokens.Surface,
                            shape = RoundedCornerShape(SonaraTokens.RadiusMd),
                            modifier = Modifier.weight(1f).height(80.dp)
                        ) {
                            Column(
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Text(
                                    text = "${stats.artists}",
                                    color = SonaraTokens.TextPrimary,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Artists",
                                    color = SonaraTokens.TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        // Albums
                        Surface(
                            color = SonaraTokens.Surface,
                            shape = RoundedCornerShape(SonaraTokens.RadiusMd),
                            modifier = Modifier.weight(1f).height(80.dp)
                        ) {
                            Column(
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Text(
                                    text = "${stats.albums}",
                                    color = SonaraTokens.TextPrimary,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Albums",
                                    color = SonaraTokens.TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // List Section
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 4.dp)
            ) {
                Text(
                    text = "List",
                    color = SonaraTokens.TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )

                Surface(
                    color = SonaraTokens.Surface,
                    shape = RoundedCornerShape(SonaraTokens.RadiusPill),
                    modifier = Modifier.height(36.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Schedule,
                            contentDescription = null,
                            tint = SonaraTokens.TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Recent ▾",
                            color = SonaraTokens.TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        if (recentTracks.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No tracks yet",
                        color = SonaraTokens.TextSecondary,
                        fontSize = 15.sp
                    )
                }
            }
        } else {
            items(recentTracks) { scrobble ->
                val track = Track(
                    id = "scrobble_${scrobble.title.hashCode()}",
                    title = scrobble.title,
                    artist = scrobble.artist,
                    album = scrobble.album,
                    artworkUrl = scrobble.artworkUrl,
                    audioQuality = "Lossless FLAC"
                )
                DensityTrackRow(
                    track = track,
                    isPlaying = false,
                    isCurrent = false,
                    onPlay = { onPlayTrack(track) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(90.dp))
        }
    }
}
