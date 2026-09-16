package com.sonara.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonara.desktop.data.MusicSearchService
import com.sonara.desktop.model.Track
import kotlinx.coroutines.launch

@Composable
fun SearchScreen(
    onPlayTrack: (Track, List<Track>) -> Unit,
    currentTrack: Track?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    searchService: MusicSearchService,
    onPlayNext: ((Track) -> Unit)? = null,
    onAddToQueue: ((Track) -> Unit)? = null,
    onToggleLike: ((Track) -> Unit)? = null,
    favoriteIds: Set<String> = emptySet(),
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Track>>(emptyList()) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val trendingTags = listOf("Lossless Rock", "Electronic", "Synthwave", "Jazz Classics", "Ambient", "Metal Hits", "Eminem", "Pink Floyd")

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        // Search Input Bar
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

            Spacer(modifier = Modifier.width(14.dp))

            Surface(
                color = SonaraTokens.SurfaceRaised,
                shape = RoundedCornerShape(SonaraTokens.RadiusPill),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Search",
                        tint = SonaraTokens.Accent,
                        modifier = Modifier.size(22.dp)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    OutlinedTextField(
                        value = query,
                        onValueChange = { newQuery ->
                            query = newQuery
                            if (newQuery.length >= 2) {
                                scope.launch {
                                    suggestions = searchService.getSuggestions(newQuery)
                                    isSearching = true
                                    results = searchService.searchSongs(newQuery)
                                    isSearching = false
                                }
                            } else {
                                suggestions = emptyList()
                                results = emptyList()
                            }
                        },
                        placeholder = {
                            Text(
                                "Search tracks, artists, albums...",
                                color = SonaraTokens.TextSecondary.copy(alpha = 0.6f),
                                fontSize = 14.sp
                            )
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = SonaraTokens.TextPrimary,
                            unfocusedTextColor = SonaraTokens.TextPrimary,
                            cursorColor = SonaraTokens.Accent
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                query = ""
                                results = emptyList()
                                suggestions = emptyList()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Clear",
                                tint = SonaraTokens.TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // Trending tags pill row
        Text(
            text = "Explore & Genres",
            color = SonaraTokens.TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            items(trendingTags) { tag ->
                Surface(
                    color = SonaraTokens.SurfaceChip,
                    shape = RoundedCornerShape(SonaraTokens.RadiusPill),
                    modifier = Modifier.clickable {
                        query = tag
                        scope.launch {
                            isSearching = true
                            results = searchService.searchSongs(tag)
                            isSearching = false
                        }
                    }
                ) {
                    Text(
                        text = tag,
                        color = SonaraTokens.TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }

        if (isSearching) {
            Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = SonaraTokens.Accent, modifier = Modifier.size(32.dp))
            }
        } else if (results.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(results, key = { index, item -> "${item.id}_$index" }) { _, track ->
                    DensityTrackRow(
                        track = track,
                        isPlaying = isPlaying,
                        isCurrent = currentTrack?.id == track.id,
                        onPlay = { onPlayTrack(track, results) },
                        onPlayNext = onPlayNext,
                        onAddToQueue = onAddToQueue,
                        onToggleLike = onToggleLike,
                        isLiked = favoriteIds.contains(track.id)
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(84.dp))
                }
            }
        } else if (query.isNotBlank()) {
            Box(modifier = Modifier.fillMaxSize().padding(top = 40.dp), contentAlignment = Alignment.TopCenter) {
                Text("No matching tracks found for \"$query\"", color = SonaraTokens.TextSecondary, fontSize = 14.sp)
            }
        } else {
            Box(modifier = Modifier.fillMaxSize().padding(top = 40.dp), contentAlignment = Alignment.TopCenter) {
                Text("Type to find studio-master lossless music", color = SonaraTokens.TextSecondary.copy(alpha = 0.5f), fontSize = 14.sp)
            }
        }
    }
}
