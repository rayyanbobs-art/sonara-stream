package com.sonara.desktop.ui

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.sonara.desktop.data.DesktopArtworkCache
import com.sonara.desktop.data.DesktopArtworkResolver
import com.sonara.desktop.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI
import java.net.URLEncoder

import com.sonara.desktop.storage.DesktopDatabase
import com.sonara.desktop.i18n.LocalStrings

val LocalArtworkResolver = staticCompositionLocalOf<DesktopArtworkResolver?> { null }
val LocalDesktopDatabase = staticCompositionLocalOf<DesktopDatabase?> { null }

@Composable
fun AddToPlaylistDialog(
    track: Track,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current
    val database = LocalDesktopDatabase.current
    if (database == null) {
        onDismiss()
        return
    }
    var playlists by remember { mutableStateOf(database.getPlaylists()) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf("") }
    var addedToTitle by remember { mutableStateOf<String?>(null) }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            containerColor = SonaraTokens.SurfaceRaised,
            title = { Text(strings.newPlaylist, color = SonaraTokens.TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    placeholder = { Text(strings.playlistName, color = SonaraTokens.TextSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = SonaraTokens.TextPrimary,
                        unfocusedTextColor = SonaraTokens.TextPrimary,
                        focusedBorderColor = SonaraTokens.Accent,
                        unfocusedBorderColor = SonaraTokens.SurfaceChip
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newTitle.isNotBlank()) {
                            val created = database.createPlaylist(newTitle.trim())
                            database.addTrackToPlaylist(created.id, track)
                            playlists = database.getPlaylists()
                            addedToTitle = created.title
                            showCreateDialog = false
                            newTitle = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SonaraTokens.Accent)
                ) {
                    Text(strings.createAndAdd, color = SonaraTokens.TextOnAccent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text(strings.cancel, color = SonaraTokens.TextSecondary)
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SonaraTokens.SurfaceRaised,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strings.addToPlaylist,
                    color = SonaraTokens.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                TextButton(
                    onClick = { showCreateDialog = true }
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, tint = SonaraTokens.Accent, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New", color = SonaraTokens.Accent, fontWeight = FontWeight.Bold)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 350.dp)
            ) {
                if (addedToTitle != null) {
                    Surface(
                        color = SonaraTokens.Accent.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(10.dp)
                        ) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = SonaraTokens.Accent, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Added to $addedToTitle", color = SonaraTokens.Accent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                if (playlists.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No playlists created yet",
                            color = SonaraTokens.TextSecondary,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(playlists.size) { idx ->
                            val pl = playlists[idx]
                            Surface(
                                onClick = {
                                    database.addTrackToPlaylist(pl.id, track)
                                    addedToTitle = pl.title
                                },
                                color = SonaraTokens.Surface,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.QueueMusic,
                                        contentDescription = null,
                                        tint = SonaraTokens.Accent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = pl.title,
                                            color = SonaraTokens.TextPrimary,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${pl.trackCount} tracks",
                                            color = SonaraTokens.TextSecondary,
                                            fontSize = 12.sp
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Rounded.Add,
                                        contentDescription = "Add",
                                        tint = SonaraTokens.TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = SonaraTokens.Accent)
            ) {
                Text(strings.done, color = SonaraTokens.TextOnAccent)
            }
        }
    )
}

@Composable
fun AsyncArtwork(
    url: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    title: String? = null,
    artist: String? = null
) {
    val resolver = LocalArtworkResolver.current
    val bitmapState = produceState<ImageBitmap?>(null, url, title, artist) {
        var effectiveUrl = if (DesktopArtworkResolver.isRealArtwork(url)) url else null
        if (effectiveUrl == null && !title.isNullOrBlank() && !artist.isNullOrBlank() && resolver != null) {
            effectiveUrl = resolver.resolveArtwork(title, artist)
        }
        effectiveUrl = DesktopArtworkResolver.upgradeQuality(effectiveUrl)
        if (effectiveUrl.isNullOrBlank() || !DesktopArtworkResolver.isRealArtwork(effectiveUrl)) {
            value = null
            return@produceState
        }

        // Fast path: In-memory bitmap cache hit (instant 0ms render, no flicker)
        val cached = DesktopArtworkCache.get(effectiveUrl)
        if (cached != null) {
            value = cached
            return@produceState
        }

        value = withContext(Dispatchers.IO) {
            val loaded = loadHighResArtworkBitmap(effectiveUrl)
            if (loaded != null) {
                DesktopArtworkCache.put(effectiveUrl, loaded)
            }
            loaded
        }
    }

    val bitmap = bitmapState.value
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.High,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(SonaraTokens.SurfaceChip),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = SonaraTokens.Accent,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private fun fetchHttpBitmap(urlString: String): ImageBitmap? {
    return try {
        val conn = java.net.URL(urlString).openConnection() as java.net.HttpURLConnection
        conn.connectTimeout = 6000
        conn.readTimeout = 6000
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) SonaraStream/4.0.0")
        if (conn.responseCode == 200) {
            conn.inputStream.use { input ->
                loadImageBitmap(input)
            }
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }
}

private fun loadHighResArtworkBitmap(effectiveUrl: String): ImageBitmap? {
    val initial = fetchHttpBitmap(effectiveUrl)
    if (initial != null) return initial

    // YouTube 404 fallback: maxresdefault -> hqdefault
    if (effectiveUrl.contains("maxresdefault.jpg")) {
        val hqUrl = effectiveUrl.replace("maxresdefault.jpg", "hqdefault.jpg")
        val fallback = fetchHttpBitmap(hqUrl)
        if (fallback != null) return fallback
    }

    // Google usercontent fallback: w1200 -> w544
    if (effectiveUrl.contains("=w1200-h1200-l90-rj")) {
        val fallbackUrl = effectiveUrl.replace("=w1200-h1200-l90-rj", "=w544-h544-l90-rj")
        val fallback = fetchHttpBitmap(fallbackUrl)
        if (fallback != null) return fallback
    }

    return null
}

@Composable
fun TopAppHeader(
    title: String,
    subtitle: String? = null,
    onDiscoverClick: (() -> Unit)? = null,
    onSearchClick: (() -> Unit)? = null,
    onSettingsClick: (() -> Unit)? = null,
    customAction: (@Composable () -> Unit)? = null,
    onBackClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (onBackClick != null) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(SonaraTokens.SurfaceRaised)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = SonaraTokens.TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column {
                Text(
                    text = title,
                    color = SonaraTokens.TextPrimary,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        color = SonaraTokens.TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (customAction != null) {
                customAction()
            }

            if (onDiscoverClick != null) {
                IconButton(
                    onClick = onDiscoverClick,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(SonaraTokens.SurfaceRaised)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Explore,
                        contentDescription = "Discover",
                        tint = SonaraTokens.TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (onSearchClick != null) {
                IconButton(
                    onClick = onSearchClick,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(SonaraTokens.SurfaceRaised)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Search",
                        tint = SonaraTokens.TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (onSettingsClick != null) {
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(SonaraTokens.SurfaceRaised)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AccountCircle,
                        contentDescription = "Profile & Settings",
                        tint = SonaraTokens.Accent,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun FloatingBottomNavDock(
    currentNav: NavItem,
    onNavSelect: (NavItem) -> Unit,
    onOpenGenerator: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = SonaraTokens.Surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, SonaraTokens.SurfaceChip.copy(alpha = 0.5f)),
            shadowElevation = 8.dp,
            modifier = Modifier.height(58.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                // Tab 1: Feed
                val isFeed = currentNav == NavItem.FEED
                Surface(
                    onClick = { onNavSelect(NavItem.FEED) },
                    shape = RoundedCornerShape(26.dp),
                    color = if (isFeed) SonaraTokens.AccentStrong else Color.Transparent,
                    modifier = Modifier.height(46.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = if (isFeed) 18.dp else 14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Home,
                            contentDescription = strings.navFeed,
                            tint = if (isFeed) SonaraTokens.TextPrimary else SonaraTokens.TextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                        if (isFeed) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = strings.navFeed,
                                color = SonaraTokens.TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Tab 2: Stats
                val isStats = currentNav == NavItem.STATS
                Surface(
                    onClick = { onNavSelect(NavItem.STATS) },
                    shape = RoundedCornerShape(26.dp),
                    color = if (isStats) SonaraTokens.AccentStrong else Color.Transparent,
                    modifier = Modifier.height(46.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = if (isStats) 18.dp else 14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.BarChart,
                            contentDescription = strings.navStats,
                            tint = if (isStats) SonaraTokens.TextPrimary else SonaraTokens.TextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                        if (isStats) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = strings.navStats,
                                color = SonaraTokens.TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Tab 3: Playlists
                val isPlaylists = currentNav == NavItem.PLAYLISTS
                Surface(
                    onClick = { onNavSelect(NavItem.PLAYLISTS) },
                    shape = RoundedCornerShape(26.dp),
                    color = if (isPlaylists) SonaraTokens.AccentStrong else Color.Transparent,
                    modifier = Modifier.height(46.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = if (isPlaylists) 18.dp else 14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.QueueMusic,
                            contentDescription = strings.navPlaylists,
                            tint = if (isPlaylists) SonaraTokens.TextPrimary else SonaraTokens.TextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                        if (isPlaylists) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = strings.navPlaylists,
                                color = SonaraTokens.TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Floating ✨ Mix Button on Playlists tab
        if (currentNav == NavItem.PLAYLISTS) {
            Surface(
                onClick = onOpenGenerator,
                shape = CircleShape,
                color = SonaraTokens.AccentStrong,
                shadowElevation = 8.dp,
                modifier = Modifier.size(54.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = "Smart Mix",
                        tint = SonaraTokens.TextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun FloatingMiniPlayer(
    playerState: PlayerState,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    isLiked: Boolean,
    onToggleLike: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val track = playerState.track ?: return
    val progress = if (playerState.durationMs > 0) {
        (playerState.positionMs.toFloat() / playerState.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Surface(
        shape = RoundedCornerShape(32.dp),
        color = SonaraTokens.SurfaceRaised,
        border = androidx.compose.foundation.BorderStroke(1.dp, SonaraTokens.SurfaceChip.copy(alpha = 0.5f)),
        shadowElevation = 10.dp,
        modifier = modifier
            .clip(RoundedCornerShape(32.dp))
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cover Art
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(SonaraTokens.SurfaceChip)
                ) {
                    AsyncArtwork(
                        url = track.artworkUrl,
                        title = track.title,
                        artist = track.artist,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Title & Artist
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        color = SonaraTokens.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = track.artist,
                        color = SonaraTokens.TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Favorite Button
                IconButton(
                    onClick = onToggleLike,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = if (isLiked) "Unlike" else "Like",
                        tint = if (isLiked) SonaraTokens.Accent else SonaraTokens.TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Play / Pause Circle
                Surface(
                    onClick = onPlayPause,
                    shape = CircleShape,
                    color = SonaraTokens.Accent,
                    contentColor = SonaraTokens.TextOnAccent,
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (playerState.status == PlaybackStatus.BUFFERING) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = SonaraTokens.TextOnAccent
                            )
                        } else {
                            Icon(
                                imageVector = if (playerState.status == PlaybackStatus.PLAYING) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = SonaraTokens.TextOnAccent,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Next Button
                IconButton(
                    onClick = onNext,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(SonaraTokens.SurfaceChip)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Next",
                        tint = SonaraTokens.TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Bottom Progress Line
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp)
                    .height(2.5.dp)
                    .clip(CircleShape)
                    .background(SonaraTokens.SurfaceChip)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress)
                        .background(SonaraTokens.Accent)
                )
            }
        }
    }
}

@Composable
fun ExpandedPlayerModal(
    playerState: PlayerState,
    lyrics: List<SyncedLine>,
    queue: List<Track>,
    currentQueueIndex: Int,
    isLiked: Boolean,
    onToggleLike: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onToggleMute: () -> Unit,
    onCollapse: () -> Unit,
    onJumpToQueueIndex: (Int) -> Unit,
    onRemoveFromQueue: (Int) -> Unit,
    onMoveQueueItem: (Int, Int) -> Unit,
    onClearQueue: () -> Unit,
    onAddToPlaylist: ((Track) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val track = playerState.track ?: return
    var showLyrics by remember(track.id) { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }

    val boundedDuration = playerState.durationMs.coerceAtLeast(0L)
    val actualProgress = if (boundedDuration > 0) {
        (playerState.positionMs.toFloat() / boundedDuration.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val displayProgress = if (dragging) dragProgress else actualProgress
    val displayedMs = if (dragging) (dragProgress * boundedDuration).toLong() else playerState.positionMs

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SonaraTokens.Bg)
    ) {
        // Ambient background glow
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.radialGradient(
                        colors = listOf(
                            SonaraTokens.Accent.copy(alpha = 0.28f),
                            SonaraTokens.SurfaceRaised.copy(alpha = 0.15f),
                            Color.Transparent
                        ),
                        center = androidx.compose.ui.geometry.Offset(300f, 250f),
                        radius = 900f
                    )
                )
        )

        // Centered Content Max 640.dp
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 640.dp)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    IconButton(
                        onClick = onCollapse,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(SonaraTokens.SurfaceChip)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ExpandMore,
                            contentDescription = "Minimize player",
                            tint = SonaraTokens.TextPrimary,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (showLyrics) "LYRICS" else "NOW PLAYING",
                            color = SonaraTokens.Accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (track.isLocal) "Local Audio File" else (track.album.takeIf { it.isNotBlank() } ?: "Sonara Stream · Studio Master"),
                            color = SonaraTokens.TextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                        IconButton(
                            onClick = { showOptionsMenu = true },
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(SonaraTokens.SurfaceChip)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "Options",
                                tint = SonaraTokens.TextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showOptionsMenu,
                            onDismissRequest = { showOptionsMenu = false },
                            containerColor = SonaraTokens.SurfaceRaised,
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Add to Playlist", color = SonaraTokens.TextPrimary) },
                                leadingIcon = { Icon(Icons.Rounded.QueueMusic, null, tint = SonaraTokens.Accent) },
                                onClick = {
                                    showOptionsMenu = false
                                    onAddToPlaylist?.invoke(track)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Copy Song & Artist", color = SonaraTokens.TextPrimary) },
                                leadingIcon = { Icon(Icons.Rounded.ContentCopy, null, tint = SonaraTokens.TextSecondary) },
                                onClick = {
                                    showOptionsMenu = false
                                    try {
                                        val sel = java.awt.datatransfer.StringSelection("${track.title} - ${track.artist}")
                                        java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, sel)
                                    } catch (_: Exception) {}
                                }
                            )
                            if (track.isLocal && !track.localFilePath.isNullOrBlank()) {
                                DropdownMenuItem(
                                    text = { Text("Show in File Explorer", color = SonaraTokens.TextPrimary) },
                                    leadingIcon = { Icon(Icons.Rounded.FolderOpen, null, tint = SonaraTokens.TextSecondary) },
                                    onClick = {
                                        showOptionsMenu = false
                                        try {
                                            val f = java.io.File(track.localFilePath)
                                            if (f.exists()) {
                                                java.awt.Desktop.getDesktop().open(f.parentFile)
                                            }
                                        } catch (_: Exception) {}
                                    }
                                )
                            }
                        }
                    }
                }

                // 2. Center Stage (Artwork OR Synced Lyrics)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (showLyrics) {
                        Surface(
                            shape = RoundedCornerShape(28.dp),
                            color = SonaraTokens.SurfaceRaised.copy(alpha = 0.7f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SonaraTokens.SurfaceChip.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Synchronized Lyrics",
                                        color = SonaraTokens.Accent,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    TextButton(onClick = { showLyrics = false }) {
                                        Text("Artwork", color = SonaraTokens.TextSecondary, fontSize = 12.sp)
                                    }
                                }

                                if (lyrics.isEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "No synchronized lyrics available for this song",
                                            color = SonaraTokens.TextSecondary,
                                            fontSize = 14.sp
                                        )
                                    }
                                } else {
                                    val lyricsListState = rememberLazyListState()
                                    val activeIndex = remember(lyrics, playerState.positionMs) {
                                        var found = -1
                                        for (i in lyrics.indices) {
                                            if (lyrics[i].timeMs <= playerState.positionMs) {
                                                found = i
                                            } else break
                                        }
                                        found
                                    }

                                    LaunchedEffect(activeIndex) {
                                        if (activeIndex >= 0) {
                                            lyricsListState.animateScrollToItem((activeIndex - 2).coerceAtLeast(0))
                                        }
                                    }

                                    LazyColumn(
                                        state = lyricsListState,
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        itemsIndexed(lyrics) { idx, line ->
                                            val isActive = idx == activeIndex
                                            Text(
                                                text = line.text,
                                                color = if (isActive) SonaraTokens.Accent else SonaraTokens.TextSecondary.copy(alpha = 0.5f),
                                                fontSize = if (isActive) 18.sp else 15.sp,
                                                fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable { onSeek(line.timeMs) }
                                                    .padding(vertical = 4.dp, horizontal = 8.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Artwork View
                        Box(contentAlignment = Alignment.Center) {
                            // Ambient blur glow
                            Box(
                                modifier = Modifier
                                    .size(340.dp)
                                    .clip(CircleShape)
                                    .background(
                                        androidx.compose.ui.graphics.Brush.radialGradient(
                                            colors = listOf(
                                                SonaraTokens.Accent.copy(alpha = 0.35f),
                                                Color.Transparent
                                            )
                                        )
                                    )
                            )

                            Surface(
                                shape = RoundedCornerShape(28.dp),
                                color = SonaraTokens.SurfaceChip,
                                shadowElevation = 24.dp,
                                modifier = Modifier
                                    .size(320.dp)
                                    .clip(RoundedCornerShape(28.dp))
                            ) {
                                AsyncArtwork(
                                    url = track.artworkUrl,
                                    title = track.title,
                                    artist = track.artist,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }

                // 3. Track Info Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            color = SonaraTokens.TextPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = track.artist,
                            color = SonaraTokens.TextSecondary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Like Button
                        Surface(
                            onClick = onToggleLike,
                            shape = CircleShape,
                            color = if (isLiked) SonaraTokens.Accent.copy(alpha = 0.2f) else SonaraTokens.SurfaceChip,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                    contentDescription = if (isLiked) "Unlike" else "Like",
                                    tint = if (isLiked) SonaraTokens.Accent else SonaraTokens.TextSecondary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        // Lyrics Toggle Button
                        Surface(
                            onClick = { showLyrics = !showLyrics },
                            shape = CircleShape,
                            color = if (showLyrics) SonaraTokens.Accent.copy(alpha = 0.2f) else SonaraTokens.SurfaceChip,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.FormatQuote,
                                    contentDescription = "Lyrics",
                                    tint = if (showLyrics) SonaraTokens.Accent else SonaraTokens.TextSecondary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 4. Seekbar
                Column(modifier = Modifier.fillMaxWidth()) {
                    Slider(
                        value = displayProgress,
                        onValueChange = {
                            dragging = true
                            dragProgress = it
                        },
                        onValueChangeFinished = {
                            dragging = false
                            onSeek((dragProgress * boundedDuration).toLong())
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = SonaraTokens.Accent,
                            activeTrackColor = SonaraTokens.Accent,
                            inactiveTrackColor = SonaraTokens.SurfaceChip
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatDuration(displayedMs),
                            color = SonaraTokens.TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "-${formatDuration((boundedDuration - displayedMs).coerceAtLeast(0L))}",
                            color = SonaraTokens.TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 5. Main Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = onPrevious,
                        shape = CircleShape,
                        color = SonaraTokens.SurfaceChip,
                        modifier = Modifier.size(54.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.SkipPrevious,
                                contentDescription = "Previous",
                                tint = SonaraTokens.TextPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Surface(
                        onClick = onPlayPause,
                        shape = CircleShape,
                        color = SonaraTokens.Accent,
                        modifier = Modifier.size(72.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (playerState.status == PlaybackStatus.BUFFERING) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                    strokeWidth = 3.dp,
                                    color = SonaraTokens.TextOnAccent
                                )
                            } else {
                                Icon(
                                    imageVector = if (playerState.status == PlaybackStatus.PLAYING) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = SonaraTokens.TextOnAccent,
                                    modifier = Modifier.size(38.dp)
                                )
                            }
                        }
                    }

                    Surface(
                        onClick = onNext,
                        shape = CircleShape,
                        color = SonaraTokens.SurfaceChip,
                        modifier = Modifier.size(54.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.SkipNext,
                                contentDescription = "Next",
                                tint = SonaraTokens.TextPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 6. Utility Controls (Shuffle · Format Badge · Repeat)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onToggleShuffle,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (playerState.isShuffled) SonaraTokens.Accent else SonaraTokens.TextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = SonaraTokens.Accent.copy(alpha = 0.15f),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.HighQuality,
                                contentDescription = null,
                                tint = SonaraTokens.Accent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (track.isLocal) "LOCAL • FLAC" else "LOSSLESS • FLAC",
                                color = SonaraTokens.Accent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = onCycleRepeat,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = when (playerState.repeatMode) {
                                RepeatMode.ONE -> Icons.Rounded.RepeatOne
                                else -> Icons.Rounded.Repeat
                            },
                            contentDescription = "Repeat",
                            tint = if (playerState.repeatMode != RepeatMode.OFF) SonaraTokens.Accent else SonaraTokens.TextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // 7. Bottom Drawer Bar (Queue · Volume)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = { showQueueSheet = !showQueueSheet },
                        shape = RoundedCornerShape(18.dp),
                        color = if (showQueueSheet) SonaraTokens.Accent.copy(alpha = 0.2f) else SonaraTokens.SurfaceChip,
                        modifier = Modifier.height(36.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.QueueMusic,
                                contentDescription = "Queue",
                                tint = if (showQueueSheet) SonaraTokens.Accent else SonaraTokens.TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Queue (${queue.size})",
                                color = if (showQueueSheet) SonaraTokens.Accent else SonaraTokens.TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = onToggleMute,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (playerState.volume > 0.01f) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeOff,
                                contentDescription = "Mute/Unmute",
                                tint = SonaraTokens.TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Slider(
                            value = playerState.volume,
                            onValueChange = onVolumeChange,
                            colors = SliderDefaults.colors(
                                thumbColor = SonaraTokens.Accent,
                                activeTrackColor = SonaraTokens.Accent,
                                inactiveTrackColor = SonaraTokens.SurfaceChip
                            ),
                            modifier = Modifier.width(110.dp)
                        )
                    }
                }
            }
        }

        // Slide-over Queue Sheet when opened inside Expanded Player
        AnimatedVisibility(
            visible = showQueueSheet,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .zIndex(40f)
        ) {
            QueueSheet(
                queue = queue,
                currentIndex = currentQueueIndex,
                isPlaying = playerState.status == PlaybackStatus.PLAYING,
                onClose = { showQueueSheet = false },
                onTrackSelect = onJumpToQueueIndex,
                onRemoveFromQueue = onRemoveFromQueue,
                onMoveQueueItem = onMoveQueueItem,
                onClearQueue = onClearQueue
            )
        }
    }
}

@Composable
fun Sidebar(
    currentNav: NavItem,
    onNavSelect: (NavItem) -> Unit,
    lastFmUser: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(220.dp)
            .background(SonaraTokens.Bg)
            .border(width = 1.dp, color = SonaraTokens.SurfaceChip.copy(alpha = 0.5f), shape = RoundedCornerShape(0.dp))
            .padding(16.dp)
    ) {
        // App branding
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(SonaraTokens.RadiusSm))
                    .background(SonaraTokens.AccentStrong),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = SonaraTokens.TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column {
                Text(
                    text = "Sonara",
                    color = SonaraTokens.TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "STREAM",
                    color = SonaraTokens.Accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Navigation Sections
        val navSections = listOf(
            "LISTEN NOW" to listOf(
                Triple(NavItem.FEED, "Feed", Icons.Rounded.Home),
                Triple(NavItem.DISCOVER, "Discover", Icons.Rounded.Explore)
            ),
            "MY LIBRARY" to listOf(
                Triple(NavItem.SONGS, "Songs", Icons.Rounded.MusicNote),
                Triple(NavItem.FAVORITES, "Favorites", Icons.Rounded.Favorite),
                Triple(NavItem.PLAYLISTS, "Playlists", Icons.Rounded.QueueMusic)
            ),
            "ACTIVITY" to listOf(
                Triple(NavItem.STATS, "Stats", Icons.Rounded.BarChart),
                Triple(NavItem.SETTINGS, "Settings", Icons.Rounded.Settings)
            )
        )

        navSections.forEach { (sectionHeader, items) ->
            Text(
                text = sectionHeader,
                color = SonaraTokens.TextSecondary.copy(alpha = 0.6f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                modifier = Modifier.padding(start = 12.dp, top = 14.dp, bottom = 4.dp)
            )
            items.forEach { (item, label, icon) ->
                val isSelected = currentNav == item
                Surface(
                    onClick = { onNavSelect(item) },
                    color = if (isSelected) SonaraTokens.Accent else Color.Transparent,
                    shape = RoundedCornerShape(SonaraTokens.RadiusPill),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = if (isSelected) SonaraTokens.TextOnAccent else SonaraTokens.TextSecondary,
                            modifier = Modifier.size(19.dp)
                        )
                        Text(
                            text = label,
                            color = if (isSelected) SonaraTokens.TextOnAccent else SonaraTokens.TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Last.fm account chip at bottom of sidebar
        Surface(
            onClick = { onNavSelect(NavItem.SETTINGS) },
            color = SonaraTokens.Surface,
            shape = RoundedCornerShape(SonaraTokens.RadiusMd),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(SonaraTokens.AccentStrong),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = lastFmUser.take(1).uppercase(),
                        color = SonaraTokens.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = lastFmUser,
                        color = SonaraTokens.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Last.fm Active",
                        color = SonaraTokens.Accent,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
fun DensityTrackRow(
    track: Track,
    isPlaying: Boolean,
    isCurrent: Boolean,
    onPlay: () -> Unit,
    onPlayNext: ((Track) -> Unit)? = null,
    onAddToQueue: ((Track) -> Unit)? = null,
    onToggleLike: ((Track) -> Unit)? = null,
    isLiked: Boolean = false,
    onOverflowClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showDetailsDialog by remember { mutableStateOf(false) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }

    if (showDetailsDialog) {
        TrackDetailsDialog(
            track = track,
            onDismiss = { showDetailsDialog = false }
        )
    }

    if (showAddToPlaylistDialog) {
        AddToPlaylistDialog(
            track = track,
            onDismiss = { showAddToPlaylistDialog = false }
        )
    }

    Surface(
        onClick = onPlay,
        color = if (isCurrent) SonaraTokens.SurfaceRaised else Color.Transparent,
        shape = RoundedCornerShape(SonaraTokens.RadiusSm),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            AsyncArtwork(
                url = track.artworkUrl,
                title = track.title,
                artist = track.artist,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(SonaraTokens.RadiusSm))
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = track.title,
                        color = if (isCurrent) SonaraTokens.Accent else SonaraTokens.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (track.isLocal) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = SonaraTokens.Accent.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "LOCAL",
                                color = SonaraTokens.Accent,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = track.artist,
                    color = SonaraTokens.TextSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box {
                IconButton(
                    onClick = {
                        onOverflowClick?.invoke()
                        menuExpanded = true
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(SonaraTokens.SurfaceChip.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Options",
                        tint = SonaraTokens.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier.background(SonaraTokens.SurfaceRaised)
                ) {
                    if (onPlayNext != null) {
                        DropdownMenuItem(
                            text = { Text("Play Next", color = SonaraTokens.TextPrimary, fontSize = 14.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.QueuePlayNext,
                                    contentDescription = null,
                                    tint = SonaraTokens.Accent,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onPlayNext(track)
                            }
                        )
                    }

                    if (onAddToQueue != null) {
                        DropdownMenuItem(
                            text = { Text("Add to Queue", color = SonaraTokens.TextPrimary, fontSize = 14.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.PlaylistAdd,
                                    contentDescription = null,
                                    tint = SonaraTokens.Accent,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onAddToQueue(track)
                            }
                        )
                    }

                    if (onToggleLike != null) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (isLiked) "Remove from Favorites" else "Add to Favorites",
                                    color = SonaraTokens.TextPrimary,
                                    fontSize = 14.sp
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                    contentDescription = null,
                                    tint = if (isLiked) SonaraTokens.Accent else SonaraTokens.TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onToggleLike(track)
                            }
                        )
                    }

                    DropdownMenuItem(
                        text = { Text("Add to Playlist", color = SonaraTokens.TextPrimary, fontSize = 14.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.PlaylistAddCheck,
                                contentDescription = null,
                                tint = SonaraTokens.Accent,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            showAddToPlaylistDialog = true
                        }
                    )

                    if (track.isLocal && !track.localFilePath.isNullOrBlank()) {
                        DropdownMenuItem(
                            text = { Text("Show in Folder", color = SonaraTokens.TextPrimary, fontSize = 14.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.FolderOpen,
                                    contentDescription = null,
                                    tint = SonaraTokens.Accent,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                try {
                                    val f = java.io.File(track.localFilePath)
                                    val parent = f.parentFile ?: f
                                    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                                        Desktop.getDesktop().open(parent)
                                    }
                                } catch (_: Exception) {}
                            }
                        )
                    }

                    HorizontalDivider(color = SonaraTokens.SurfaceChip.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))

                    DropdownMenuItem(
                        text = { Text("Song Details", color = SonaraTokens.TextPrimary, fontSize = 14.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = null,
                                tint = SonaraTokens.TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            showDetailsDialog = true
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Open on Last.fm", color = SonaraTokens.TextPrimary, fontSize = 14.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.OpenInBrowser,
                                contentDescription = null,
                                tint = SonaraTokens.TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            try {
                                val encArtist = URLEncoder.encode(track.artist, "UTF-8").replace("+", "%20")
                                val encTitle = URLEncoder.encode(track.title, "UTF-8").replace("+", "%20")
                                val url = "https://www.last.fm/music/$encArtist/_/$encTitle"
                                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                                    Desktop.getDesktop().browse(URI(url))
                                }
                            } catch (_: Exception) {}
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Copy Song Info", color = SonaraTokens.TextPrimary, fontSize = 14.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = null,
                                tint = SonaraTokens.TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            try {
                                val info = "${track.title} - ${track.artist}"
                                val sel = StringSelection(info)
                                Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, sel)
                            } catch (_: Exception) {}
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun TrackDetailsDialog(
    track: Track,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SonaraTokens.SurfaceRaised,
        title = {
            Text(
                text = "Track Details",
                color = SonaraTokens.TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    AsyncArtwork(
                        url = track.artworkUrl,
                        title = track.title,
                        artist = track.artist,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(SonaraTokens.RadiusSm))
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            color = SonaraTokens.TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = track.artist,
                            color = SonaraTokens.Accent,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                HorizontalDivider(color = SonaraTokens.SurfaceChip)

                DetailRowItem(label = "Album", value = track.album.ifBlank { "Single / Unknown Album" })
                DetailRowItem(
                    label = "Duration",
                    value = if (track.durationMs > 0) {
                        val sec = (track.durationMs / 1000) % 60
                        val min = (track.durationMs / 1000) / 60
                        String.format("%d:%02d", min, sec)
                    } else "Unknown"
                )
                DetailRowItem(label = "Audio Quality", value = track.audioQuality.ifBlank { "Lossless FLAC (16-bit / 44.1 kHz)" })
                DetailRowItem(label = "Catalog Source", value = "Last.fm (Verified Official)")
                DetailRowItem(label = "Stream Engine", value = "Native Sonara Stream Cache")
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SonaraTokens.Accent,
                    contentColor = SonaraTokens.TextOnAccent
                )
            ) {
                Text("Close", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun DetailRowItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = SonaraTokens.TextSecondary, fontSize = 13.sp)
        Text(
            text = value,
            color = SonaraTokens.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

@Composable
fun NowPlayingBottomBar(
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
    isLiked: Boolean,
    onLikeTrack: () -> Unit,
    onToggleQueue: () -> Unit = {},
    isQueueOpen: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(
        color = SonaraTokens.Surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, SonaraTokens.SurfaceChip.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(76.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            // Left: Track details
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.width(260.dp)
            ) {
                AsyncArtwork(
                    url = playerState.track?.artworkUrl,
                    title = playerState.track?.title,
                    artist = playerState.track?.artist,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(SonaraTokens.RadiusSm))
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playerState.track?.title ?: "Nothing playing",
                        color = SonaraTokens.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = playerState.track?.artist ?: "Select a song to start",
                        color = SonaraTokens.TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (playerState.track != null) {
                    IconButton(
                        onClick = onLikeTrack,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = "Like",
                            tint = if (isLiked) SonaraTokens.Accent else SonaraTokens.TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Center: Playback controls and seekbar
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f)
            ) {
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
                            tint = if (playerState.isShuffled) SonaraTokens.Accent else SonaraTokens.TextSecondary,
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
                            tint = SonaraTokens.TextPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Play/Pause button in light sage accent
                    Surface(
                        onClick = onPlayPause,
                        shape = CircleShape,
                        color = SonaraTokens.Accent,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (playerState.status == PlaybackStatus.BUFFERING) {
                                CircularProgressIndicator(
                                    color = SonaraTokens.TextOnAccent,
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
                                    tint = SonaraTokens.TextOnAccent,
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
                            tint = SonaraTokens.TextPrimary,
                            modifier = Modifier.size(22.dp)
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
                            tint = if (playerState.repeatMode != RepeatMode.OFF) SonaraTokens.Accent else SonaraTokens.TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().height(22.dp)
                ) {
                    Text(
                        text = formatDuration(playerState.positionMs),
                        color = SonaraTokens.TextSecondary,
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
                            thumbColor = SonaraTokens.Accent,
                            activeTrackColor = SonaraTokens.Accent,
                            inactiveTrackColor = SonaraTokens.SurfaceChip
                        ),
                        modifier = Modifier.weight(1f).height(18.dp)
                    )

                    Text(
                        text = formatDuration(playerState.durationMs),
                        color = SonaraTokens.TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Right: Lyrics, Queue & Volume
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.width(250.dp)
            ) {
                IconButton(
                    onClick = onToggleLyrics,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lyrics,
                        contentDescription = "Lyrics",
                        tint = if (isLyricsOpen) SonaraTokens.Accent else SonaraTokens.TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onToggleQueue,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.QueueMusic,
                        contentDescription = "Queue",
                        tint = if (isQueueOpen) SonaraTokens.Accent else SonaraTokens.TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

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
                        tint = SonaraTokens.TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Slider(
                    value = if (playerState.isMuted) 0f else playerState.volume,
                    onValueChange = onVolumeChange,
                    colors = SliderDefaults.colors(
                        thumbColor = SonaraTokens.Accent,
                        activeTrackColor = SonaraTokens.Accent,
                        inactiveTrackColor = SonaraTokens.SurfaceChip
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

    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0 && lyrics.isNotEmpty()) {
            val target = (activeIndex - 2).coerceAtLeast(0)
            listState.animateScrollToItem(target)
        }
    }

    Surface(
        color = SonaraTokens.Surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, SonaraTokens.SurfaceChip),
        shape = RoundedCornerShape(SonaraTokens.RadiusMd),
        modifier = modifier
            .fillMaxHeight()
            .width(360.dp)
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
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
                        tint = SonaraTokens.Accent,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Synchronized Lyrics",
                        color = SonaraTokens.TextPrimary,
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
                        tint = SonaraTokens.TextSecondary,
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
                    Text(
                        text = "No synchronized lyrics found",
                        color = SonaraTokens.TextSecondary,
                        fontSize = 14.sp
                    )
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
                            color = if (isActive) SonaraTokens.Accent else SonaraTokens.TextSecondary.copy(alpha = 0.5f),
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

@Composable
fun QueueSheet(
    queue: List<Track>,
    currentIndex: Int,
    isPlaying: Boolean,
    onClose: () -> Unit,
    onTrackSelect: (Int) -> Unit,
    onRemoveFromQueue: (Int) -> Unit,
    onMoveQueueItem: (Int, Int) -> Unit,
    onClearQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(currentIndex) {
        if (currentIndex in queue.indices) {
            val target = (currentIndex - 1).coerceAtLeast(0)
            listState.animateScrollToItem(target)
        }
    }

    val totalDurationMs = remember(queue) {
        queue.sumOf { it.durationMs }
    }

    Surface(
        color = SonaraTokens.Surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, SonaraTokens.SurfaceChip),
        shape = RoundedCornerShape(SonaraTokens.RadiusMd),
        modifier = modifier
            .fillMaxHeight()
            .width(380.dp)
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
                        imageVector = Icons.Rounded.QueueMusic,
                        contentDescription = null,
                        tint = SonaraTokens.Accent,
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            text = "Play Queue",
                            color = SonaraTokens.TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${queue.size} songs • ${formatDuration(totalDurationMs)}",
                            color = SonaraTokens.TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (queue.isNotEmpty()) {
                        TextButton(
                            onClick = onClearQueue,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Clear", color = SonaraTokens.TextSecondary, fontSize = 12.sp)
                        }
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = SonaraTokens.TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (queue.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.QueueMusic,
                            contentDescription = null,
                            tint = SonaraTokens.TextSecondary.copy(alpha = 0.4f),
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "Queue is empty",
                            color = SonaraTokens.TextSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Add songs to start queuing up tracks",
                            color = SonaraTokens.TextSecondary.copy(alpha = 0.6f),
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(queue) { idx, track ->
                        val isCurrent = idx == currentIndex
                        Surface(
                            onClick = { onTrackSelect(idx) },
                            color = if (isCurrent) SonaraTokens.SurfaceRaised else Color.Transparent,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                // Index / Playing indicator
                                Box(
                                    modifier = Modifier.width(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isCurrent && isPlaying) {
                                        Icon(
                                            imageVector = Icons.Rounded.GraphicEq,
                                            contentDescription = null,
                                            tint = SonaraTokens.Accent,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    } else {
                                        Text(
                                            text = "${idx + 1}",
                                            color = if (isCurrent) SonaraTokens.Accent else SonaraTokens.TextSecondary,
                                            fontSize = 12.sp,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                AsyncArtwork(
                                    url = track.artworkUrl,
                                    title = track.title,
                                    artist = track.artist,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                )

                                Spacer(modifier = Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = track.title,
                                        color = if (isCurrent) SonaraTokens.Accent else SonaraTokens.TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = track.artist,
                                        color = SonaraTokens.TextSecondary,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // Reorder buttons
                                Column(verticalArrangement = Arrangement.Center) {
                                    IconButton(
                                        onClick = { onMoveQueueItem(idx, idx - 1) },
                                        enabled = idx > 0,
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.KeyboardArrowUp,
                                            contentDescription = "Move Up",
                                            tint = if (idx > 0) SonaraTokens.TextSecondary else SonaraTokens.TextSecondary.copy(alpha = 0.2f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { onMoveQueueItem(idx, idx + 1) },
                                        enabled = idx < queue.lastIndex,
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.KeyboardArrowDown,
                                            contentDescription = "Move Down",
                                            tint = if (idx < queue.lastIndex) SonaraTokens.TextSecondary else SonaraTokens.TextSecondary.copy(alpha = 0.2f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                // Remove button
                                IconButton(
                                    onClick = { onRemoveFromQueue(idx) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Remove",
                                        tint = SonaraTokens.TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
