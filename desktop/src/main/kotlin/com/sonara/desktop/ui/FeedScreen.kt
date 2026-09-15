package com.sonara.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonara.desktop.data.MusicSearchService
import com.sonara.desktop.model.Track
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun FeedScreen(
    onPlayTrack: (Track, List<Track>) -> Unit,
    currentTrack: Track?,
    isPlaying: Boolean,
    onNavigateToDiscover: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToSettings: () -> Unit,
    searchService: MusicSearchService,
    modifier: Modifier = Modifier
) {
    val quickPicks = remember { searchService.getCuratedDiscoverTracks() }

    val greeting = remember {
        val hour = LocalTime.now().hour
        when (hour) {
            in 5..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            in 18..22 -> "Good evening"
            else -> "Good night"
        }
    }

    val todayDate = remember {
        LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d"))
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Top Header
        item {
            TopAppHeader(
                title = "Home",
                onDiscoverClick = onNavigateToDiscover,
                onSearchClick = onNavigateToSearch,
                onSettingsClick = onNavigateToSettings
            )
        }

        // Greeting
        item {
            Column(modifier = Modifier.padding(horizontal = 4.dp)) {
                Text(
                    text = greeting,
                    color = SonaraTokens.TextPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = todayDate,
                    color = SonaraTokens.TextSecondary,
                    fontSize = 14.sp
                )
            }
        }

        // Infinite Radio Hero Card
        item {
            Surface(
                color = SonaraTokens.SurfaceRaised,
                shape = RoundedCornerShape(SonaraTokens.RadiusLg),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    // Tag chip
                    Surface(
                        color = SonaraTokens.SurfaceChip,
                        shape = RoundedCornerShape(SonaraTokens.RadiusPill),
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = SonaraTokens.Accent,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "MADE FOR YOU",
                                color = SonaraTokens.Accent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    Text(
                        text = "Infinite Radio",
                        color = SonaraTokens.TextPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "An endless station shaped by your listening",
                        color = SonaraTokens.TextSecondary,
                        fontSize = 14.sp
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            quickPicks.firstOrNull()?.let { onPlayTrack(it, quickPicks) }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SonaraTokens.Accent,
                            contentColor = SonaraTokens.TextOnAccent
                        ),
                        shape = RoundedCornerShape(SonaraTokens.RadiusPill),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = SonaraTokens.TextOnAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Play",
                            color = SonaraTokens.TextOnAccent,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Quick Access Tiles (Liked Songs, Mix, New releases)
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Tile 1: Liked Songs
                Surface(
                    color = SonaraTokens.AccentTint,
                    shape = RoundedCornerShape(SonaraTokens.RadiusMd),
                    modifier = Modifier
                        .weight(1f)
                        .height(84.dp)
                        .clickable { quickPicks.firstOrNull()?.let { onPlayTrack(it, quickPicks) } }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(SonaraTokens.RadiusSm))
                                .background(SonaraTokens.SurfaceChip),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Favorite,
                                contentDescription = null,
                                tint = SonaraTokens.Accent,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Liked Songs",
                                color = SonaraTokens.TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Your collection",
                                color = SonaraTokens.TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // Tile 2: Mix
                Surface(
                    color = SonaraTokens.Surface,
                    shape = RoundedCornerShape(SonaraTokens.RadiusMd),
                    modifier = Modifier
                        .weight(1f)
                        .height(84.dp)
                        .clickable { quickPicks.shuffled().firstOrNull()?.let { onPlayTrack(it, quickPicks.shuffled()) } }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(SonaraTokens.RadiusSm))
                                .background(SonaraTokens.SurfaceChip),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MusicNote,
                                contentDescription = null,
                                tint = SonaraTokens.TextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Mix",
                                color = SonaraTokens.TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Made for you",
                                color = SonaraTokens.TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // Tile 3: New releases
                Surface(
                    color = SonaraTokens.Surface,
                    shape = RoundedCornerShape(SonaraTokens.RadiusMd),
                    modifier = Modifier
                        .weight(1f)
                        .height(84.dp)
                        .clickable { onNavigateToDiscover() }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(SonaraTokens.RadiusSm))
                                .background(SonaraTokens.SurfaceChip),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.NewReleases,
                                contentDescription = null,
                                tint = SonaraTokens.Accent,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "New releases",
                                color = SonaraTokens.TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Fresh drops",
                                color = SonaraTokens.TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        // Quick Picks Section Header
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 4.dp)
            ) {
                Column {
                    Text(
                        text = "Quick picks",
                        color = SonaraTokens.TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Matched to your taste profile",
                        color = SonaraTokens.TextSecondary,
                        fontSize = 13.sp
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    IconButton(
                        onClick = {
                            val shuffled = quickPicks.shuffled()
                            shuffled.firstOrNull()?.let { onPlayTrack(it, shuffled) }
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(SonaraTokens.Surface)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shuffle,
                            contentDescription = "Shuffle",
                            tint = SonaraTokens.TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Button(
                        onClick = {
                            quickPicks.firstOrNull()?.let { onPlayTrack(it, quickPicks) }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SonaraTokens.AccentStrong,
                            contentColor = SonaraTokens.TextPrimary
                        ),
                        shape = RoundedCornerShape(SonaraTokens.RadiusPill),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = SonaraTokens.TextPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Play all",
                            color = SonaraTokens.TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Quick picks track list
        items(quickPicks) { track ->
            DensityTrackRow(
                track = track,
                isPlaying = isPlaying,
                isCurrent = currentTrack?.id == track.id,
                onPlay = { onPlayTrack(track, quickPicks) }
            )
        }

        item {
            Spacer(modifier = Modifier.height(90.dp))
        }
    }
}
