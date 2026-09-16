package com.sonara.desktop.ui

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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonara.desktop.data.DesktopArtworkResolver
import com.sonara.desktop.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI
import java.net.URLEncoder

val LocalArtworkResolver = staticCompositionLocalOf<DesktopArtworkResolver?> { null }

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
        if (effectiveUrl.isNullOrBlank() || !DesktopArtworkResolver.isRealArtwork(effectiveUrl)) {
            value = null
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            try {
                val conn = java.net.URL(effectiveUrl).openConnection() as java.net.HttpURLConnection
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
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = SonaraTokens.Surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, SonaraTokens.SurfaceChip.copy(alpha = 0.5f)),
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
                            contentDescription = "Feed",
                            tint = if (isFeed) SonaraTokens.TextPrimary else SonaraTokens.TextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                        if (isFeed) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Feed",
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
                            contentDescription = "Stats",
                            tint = if (isStats) SonaraTokens.TextPrimary else SonaraTokens.TextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                        if (isStats) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Stats",
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
                            contentDescription = "Playlists",
                            tint = if (isPlaylists) SonaraTokens.TextPrimary else SonaraTokens.TextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                        if (isPlaylists) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Playlists",
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
                onClick = { /* Smart Mix action */ },
                shape = CircleShape,
                color = SonaraTokens.AccentStrong,
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

        // Navigation Items
        val navItems = listOf(
            Triple(NavItem.FEED, "Feed", Icons.Rounded.Home),
            Triple(NavItem.STATS, "Stats", Icons.Rounded.BarChart),
            Triple(NavItem.PLAYLISTS, "Playlists", Icons.Rounded.QueueMusic),
            Triple(NavItem.DISCOVER, "Discover", Icons.Rounded.Explore),
            Triple(NavItem.SETTINGS, "Settings", Icons.Rounded.Settings),
        )

        navItems.forEach { (item, label, icon) ->
            val isSelected = currentNav == item
            Surface(
                onClick = { onNavSelect(item) },
                color = if (isSelected) SonaraTokens.Accent else Color.Transparent,
                shape = RoundedCornerShape(SonaraTokens.RadiusPill),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = if (isSelected) SonaraTokens.TextOnAccent else SonaraTokens.TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = label,
                        color = if (isSelected) SonaraTokens.TextOnAccent else SonaraTokens.TextSecondary,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
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

    if (showDetailsDialog) {
        TrackDetailsDialog(
            track = track,
            onDismiss = { showDetailsDialog = false }
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
                Text(
                    text = track.title,
                    color = if (isCurrent) SonaraTokens.Accent else SonaraTokens.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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

            // Right: Lyrics & Volume
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.width(200.dp)
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
