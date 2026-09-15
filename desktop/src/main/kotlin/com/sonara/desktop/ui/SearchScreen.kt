package com.sonara.desktop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    onLikeTrack: (Track) -> Unit,
    isLiked: (String) -> Boolean,
    searchService: MusicSearchService,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Track>>(emptyList()) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        // Search bar
        Surface(
            color = SonaraTheme.CardSurface,
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, SonaraTheme.CardBorder),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = "Search",
                    tint = SonaraTheme.Secondary,
                    modifier = Modifier.size(22.dp)
                )

                Spacer(modifier = Modifier.width(10.dp))

                TextField(
                    value = query,
                    onValueChange = { newQ ->
                        query = newQ
                        if (newQ.length >= 2) {
                            scope.launch {
                                suggestions = searchService.getSuggestions(newQ)
                            }
                        } else {
                            suggestions = emptyList()
                        }
                    },
                    placeholder = {
                        Text(
                            "Search songs, artists, albums...",
                            color = SonaraTheme.TextMuted,
                            fontSize = 14.sp
                        )
                    },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = SonaraTheme.TextPrimary,
                        unfocusedTextColor = SonaraTheme.TextPrimary,
                    ),
                    modifier = Modifier.weight(1f)
                )

                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            query = ""
                            suggestions = emptyList()
                            results = emptyList()
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Clear",
                            tint = SonaraTheme.TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Button(
                    onClick = {
                        if (query.isNotBlank()) {
                            isSearching = true
                            suggestions = emptyList()
                            scope.launch {
                                results = searchService.searchSongs(query)
                                isSearching = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SonaraTheme.Primary,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("Search", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Suggestions chips
        if (suggestions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(suggestions) { sugg ->
                    Surface(
                        onClick = {
                            query = sugg
                            suggestions = emptyList()
                            isSearching = true
                            scope.launch {
                                results = searchService.searchSongs(sugg)
                                isSearching = false
                            }
                        },
                        color = SonaraTheme.Surface,
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SonaraTheme.CardBorder)
                    ) {
                        Text(
                            text = sugg,
                            color = SonaraTheme.TextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Search Content
        if (isSearching) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = SonaraTheme.Secondary)
            }
        } else if (results.isEmpty() && query.isNotBlank()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No results found for '$query'",
                    color = SonaraTheme.TextMuted,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(results) { index, track ->
                    TrackRow(
                        index = index + 1,
                        track = track,
                        isPlaying = isPlaying,
                        isCurrent = currentTrack?.id == track.id,
                        onPlay = { onPlayTrack(track, results) },
                        onLike = { onLikeTrack(track) },
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
