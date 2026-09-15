package com.sonara.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonara.desktop.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AsyncArtwork(
    url: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    val bitmapState = produceState<ImageBitmap?>(null, url) {
        if (url.isNullOrBlank()) {
            value = null
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            try {
                val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                conn.inputStream.use { input ->
                    loadImageBitmap(input)
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    val bitmap = bitmapState.value
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(SonaraTheme.CardSurface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = SonaraTheme.Primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun HiResBadge(
    label: String,
    isLossless: Boolean = true,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (isLossless) SonaraTheme.Primary.copy(alpha = 0.2f) else SonaraTheme.CardSurface,
        shape = RoundedCornerShape(4.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isLossless) SonaraTheme.Primary else SonaraTheme.CardBorder
        ),
        modifier = modifier
    ) {
        Text(
            text = label,
            color = if (isLossless) SonaraTheme.Secondary else SonaraTheme.TextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun Sidebar(
    currentNav: NavItem,
    onNavSelect: (NavItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(220.dp)
            .background(SonaraTheme.Surface)
            .border(width = 1.dp, color = SonaraTheme.CardBorder, shape = RoundedCornerShape(0.dp))
            .padding(16.dp)
    ) {
        // App branding
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Brush.linearGradient(listOf(SonaraTheme.Primary, SonaraTheme.Secondary))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column {
                Text(
                    text = "Sonara",
                    color = SonaraTheme.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "STREAM • HI-RES",
                    color = SonaraTheme.Secondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Navigation Menu
        val navItems = listOf(
            Triple(NavItem.DISCOVER, "Discover", Icons.Rounded.Explore),
            Triple(NavItem.SEARCH, "Search", Icons.Rounded.Search),
            Triple(NavItem.FAVORITES, "Favorites", Icons.Rounded.Favorite),
            Triple(NavItem.PLAYLISTS, "Playlists", Icons.Rounded.QueueMusic),
            Triple(NavItem.SETTINGS, "Settings", Icons.Rounded.Settings),
        )

        navItems.forEach { (item, label, icon) ->
            val isSelected = currentNav == item
            Surface(
                onClick = { onNavSelect(item) },
                color = if (isSelected) SonaraTheme.Primary.copy(alpha = 0.15f) else Color.Transparent,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = if (isSelected) SonaraTheme.Secondary else SonaraTheme.TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = label,
                        color = if (isSelected) SonaraTheme.TextPrimary else SonaraTheme.TextSecondary,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Bottom badge
        Surface(
            color = SonaraTheme.CardSurface,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.HighQuality,
                    contentDescription = null,
                    tint = SonaraTheme.Secondary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Lossless FLAC Audio Engine",
                    color = SonaraTheme.TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun TrackRow(
    index: Int,
    track: Track,
    isPlaying: Boolean,
    isCurrent: Boolean,
    onPlay: () -> Unit,
    onLike: () -> Unit,
    isLiked: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onPlay,
        color = if (isCurrent) SonaraTheme.Primary.copy(alpha = 0.12f) else Color.Transparent,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // Index or Playing indicator
            Box(
                modifier = Modifier.width(32.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isCurrent && isPlaying) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = "Playing",
                        tint = SonaraTheme.Secondary,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Text(
                        text = "$index",
                        color = SonaraTheme.TextMuted,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Artwork
            AsyncArtwork(
                url = track.artworkUrl,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(6.dp))
            )

            Spacer(modifier = Modifier.width(14.dp))

            // Title & Artist
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = track.title,
                        color = if (isCurrent) SonaraTheme.Secondary else SonaraTheme.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (track.isLossless) {
                        HiResBadge(label = "FLAC", isLossless = true)
                    }
                }
                Text(
                    text = track.artist,
                    color = SonaraTheme.TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Album
            if (track.album.isNotBlank()) {
                Text(
                    text = track.album,
                    color = SonaraTheme.TextMuted,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(0.7f).padding(horizontal = 12.dp)
                )
            }

            // Quality
            Text(
                text = track.audioQuality,
                color = SonaraTheme.TextMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            // Duration
            Text(
                text = formatDuration(track.durationMs),
                color = SonaraTheme.TextMuted,
                fontSize = 12.sp,
                modifier = Modifier.width(45.dp)
            )

            // Favorite button
            IconButton(
                onClick = onLike,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = "Like",
                    tint = if (isLiked) SonaraTheme.Tertiary else SonaraTheme.TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun TrackCard(
    track: Track,
    isPlaying: Boolean,
    isCurrent: Boolean,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onPlay,
        color = SonaraTheme.CardSurface,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isCurrent) SonaraTheme.Primary else SonaraTheme.CardBorder
        ),
        modifier = modifier
            .width(180.dp)
            .padding(6.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                AsyncArtwork(
                    url = track.artworkUrl,
                    modifier = Modifier.fillMaxSize()
                )

                if (isCurrent && isPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = null,
                            tint = SonaraTheme.Secondary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // Audio Quality Badge
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                ) {
                    HiResBadge(label = "Hi-Res", isLossless = true)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = track.title,
                color = if (isCurrent) SonaraTheme.Secondary else SonaraTheme.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = track.artist,
                color = SonaraTheme.TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun PlayerBottomBar(
    playerState: PlayerState,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onToggleMute: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleLyrics: () -> Unit,
    isLyricsOpen: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        color = SonaraTheme.Surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, SonaraTheme.CardBorder),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        shadowElevation = 8.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(84.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            // Left: Current Track Info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.width(260.dp)
            ) {
                AsyncArtwork(
                    url = playerState.track?.artworkUrl,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(8.dp))
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playerState.track?.title ?: "No track playing",
                        color = SonaraTheme.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = playerState.track?.artist ?: "Select a song to start",
                            color = SonaraTheme.TextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (playerState.track != null) {
                            HiResBadge(
                                label = if (playerState.track.isLossless) "FLAC" else "HQ",
                                isLossless = playerState.track.isLossless
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Center: Playback Controls & Seekbar
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f)
            ) {
                // Media Buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    IconButton(
                        onClick = onToggleShuffle,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (playerState.isShuffled) SonaraTheme.Secondary else SonaraTheme.TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onPrevious,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipPrevious,
                            contentDescription = "Previous",
                            tint = SonaraTheme.TextPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Play/Pause Button with pulse circle
                    Surface(
                        onClick = onPlayPause,
                        shape = CircleShape,
                        color = SonaraTheme.Primary,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (playerState.status == PlaybackStatus.BUFFERING) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = if (playerState.status == PlaybackStatus.PLAYING) {
                                        Icons.Rounded.Pause
                                    } else {
                                        Icons.Rounded.PlayArrow
                                    },
                                    contentDescription = "Play/Pause",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onNext,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipNext,
                            contentDescription = "Next",
                            tint = SonaraTheme.TextPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    IconButton(
                        onClick = onCycleRepeat,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = when (playerState.repeatMode) {
                                RepeatMode.ONE -> Icons.Rounded.RepeatOne
                                else -> Icons.Rounded.Repeat
                            },
                            contentDescription = "Repeat",
                            tint = if (playerState.repeatMode != RepeatMode.OFF) SonaraTheme.Secondary else SonaraTheme.TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Seekbar Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().height(22.dp)
                ) {
                    Text(
                        text = formatDuration(playerState.positionMs),
                        color = SonaraTheme.TextMuted,
                        fontSize = 11.sp
                    )

                    val currentRatio = if (playerState.durationMs > 0) {
                        (playerState.positionMs.toFloat() / playerState.durationMs.toFloat()).coerceIn(0f, 1f)
                    } else 0f

                    Slider(
                        value = currentRatio,
                        onValueChange = { ratio ->
                            val targetMs = (ratio * playerState.durationMs).toLong()
                            onSeek(targetMs)
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = SonaraTheme.Secondary,
                            activeTrackColor = SonaraTheme.Primary,
                            inactiveTrackColor = SonaraTheme.CardBorder
                        ),
                        modifier = Modifier.weight(1f).height(18.dp)
                    )

                    Text(
                        text = formatDuration(playerState.durationMs),
                        color = SonaraTheme.TextMuted,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Right: Lyrics & Volume Controls
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.width(220.dp)
            ) {
                // Lyrics button
                IconButton(
                    onClick = onToggleLyrics,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lyrics,
                        contentDescription = "Synced Lyrics",
                        tint = if (isLyricsOpen) SonaraTheme.Secondary else SonaraTheme.TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Volume icon (clickable for mute)
                IconButton(
                    onClick = onToggleMute,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (playerState.isMuted || playerState.volume == 0f) {
                            Icons.Rounded.VolumeOff
                        } else if (playerState.volume < 0.5f) {
                            Icons.Rounded.VolumeDown
                        } else {
                            Icons.Rounded.VolumeUp
                        },
                        contentDescription = "Volume",
                        tint = SonaraTheme.TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Volume slider
                Slider(
                    value = if (playerState.isMuted) 0f else playerState.volume,
                    onValueChange = onVolumeChange,
                    colors = SliderDefaults.colors(
                        thumbColor = SonaraTheme.Secondary,
                        activeTrackColor = SonaraTheme.Primary,
                        inactiveTrackColor = SonaraTheme.CardBorder
                    ),
                    modifier = Modifier.weight(1f).height(18.dp)
                )
            }
        }
    }
}

@Composable
fun LyricsSheet(
    lyrics: List<SyncedLine>,
    positionMs: Long,
    onClose: () -> Unit,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Find active synced lyric index
    val activeIndex = remember(lyrics, positionMs) {
        var found = -1
        for (i in lyrics.indices) {
            if (lyrics[i].timeMs <= positionMs) {
                found = i
            } else {
                break
            }
        }
        found
    }

    // Auto-scroll to active lyric
    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0 && lyrics.isNotEmpty()) {
            val target = (activeIndex - 2).coerceAtLeast(0)
            listState.animateScrollToItem(target)
        }
    }

    Surface(
        color = SonaraTheme.ElevatedSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, SonaraTheme.CardBorder),
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 12.dp,
        modifier = modifier
            .fillMaxHeight()
            .width(360.dp)
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lyrics,
                        contentDescription = null,
                        tint = SonaraTheme.Secondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Synchronized Lyrics",
                        color = SonaraTheme.TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close",
                        tint = SonaraTheme.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (lyrics.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Rounded.MusicOff,
                            contentDescription = null,
                            tint = SonaraTheme.TextMuted,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No synchronized lyrics found",
                            color = SonaraTheme.TextMuted,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(lyrics) { idx, line ->
                        val isActive = idx == activeIndex
                        Text(
                            text = line.text,
                            color = if (isActive) SonaraTheme.Secondary else SonaraTheme.TextMuted,
                            fontSize = if (isActive) 18.sp else 15.sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                            lineHeight = 24.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onSeekTo(line.timeMs) }
                                .padding(vertical = 4.dp, horizontal = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
