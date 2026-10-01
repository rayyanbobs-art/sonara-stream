package com.sonara.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
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
import com.sonara.desktop.data.DesktopYtMusicApi
import com.sonara.desktop.data.LocalLibraryScanner
import com.sonara.desktop.model.Playlist
import com.sonara.desktop.model.Track
import com.sonara.desktop.storage.DesktopDatabase
import com.sonara.desktop.i18n.LocalStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.JFileChooser

@Composable
fun PlaylistsScreen(
    onPlayTrack: (Track, List<Track>) -> Unit,
    currentTrack: Track?,
    isPlaying: Boolean,
    database: DesktopDatabase,
    onPlayNext: ((Track) -> Unit)? = null,
    onAddToQueue: ((Track) -> Unit)? = null,
    onToggleLike: ((Track) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    var playlists by remember { mutableStateOf(database.getPlaylists()) }
    var favorites by remember { mutableStateOf(database.getFavorites()) }
    var localFolders by remember { mutableStateOf(database.getLibraryFolders()) }
    var localSongs by remember { mutableStateOf(database.getLocalSongs()) }
    var isScanningLocal by remember { mutableStateOf(false) }

    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistTitle by remember { mutableStateOf("") }
    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var viewingFavorites by remember { mutableStateOf(false) }
    var viewingLocalLibrary by remember { mutableStateOf(false) }
    var localSearchQuery by remember { mutableStateOf("") }

    val isYtConnected = database.isYtConnected()
    val ytCookies = database.getYtCookies().orEmpty()
    val ytMusicApi = remember { DesktopYtMusicApi() }
    var remotePlaylists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var isLoadingRemote by remember { mutableStateOf(false) }
    var isLoadingTracks by remember { mutableStateOf(false) }

    fun refresh() {
        playlists = database.getPlaylists()
        favorites = database.getFavorites()
        localFolders = database.getLibraryFolders()
        localSongs = database.getLocalSongs()
        selectedPlaylist = selectedPlaylist?.let { cur ->
            playlists.find { it.id == cur.id } ?: remotePlaylists.find { it.id == cur.id }
        }
    }

    fun syncYtPlaylists() {
        if (!isYtConnected || ytCookies.isBlank()) return
        scope.launch {
            isLoadingRemote = true
            try {
                val fetched = ytMusicApi.fetchLibraryPlaylists(ytCookies)
                if (fetched.isNotEmpty()) {
                    remotePlaylists = fetched
                }
            } catch (_: Exception) {}
            isLoadingRemote = false
        }
    }

    fun selectPlaylist(pl: Playlist) {
        selectedPlaylist = pl
        if (pl.isRemote && pl.tracks.isEmpty()) {
            scope.launch {
                isLoadingTracks = true
                try {
                    val tracks = ytMusicApi.fetchPlaylistTracks(pl.id, ytCookies)
                    selectedPlaylist = pl.copy(tracks = tracks, trackCount = tracks.size)
                } catch (_: Exception) {}
                isLoadingTracks = false
            }
        }
    }

    fun importLocalFolder() {
        scope.launch(Dispatchers.IO) {
            val chooser = JFileChooser().apply {
                fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                dialogTitle = "Select Music Folder to Scan"
            }
            val res = chooser.showOpenDialog(null)
            if (res == JFileChooser.APPROVE_OPTION) {
                val selected = chooser.selectedFile
                if (selected != null && selected.isDirectory) {
                    isScanningLocal = true
                    val folderEntry = database.addLibraryFolder(selected.absolutePath)
                    LocalLibraryScanner.scanFolder(selected, folderId = folderEntry.id, database = database)
                    isScanningLocal = false
                    refresh()
                }
            }
        }
    }

    fun rescanLocalFolders() {
        scope.launch(Dispatchers.IO) {
            isScanningLocal = true
            LocalLibraryScanner.rescanAll(database)
            isScanningLocal = false
            refresh()
        }
    }

    LaunchedEffect(isYtConnected) {
        if (isYtConnected) {
            syncYtPlaylists()
        }
    }

    val totalTracks = favorites.size + playlists.sumOf { it.tracks.size } + remotePlaylists.sumOf { it.tracks.size } + localSongs.size
    val totalCount = playlists.size + remotePlaylists.size + 2 // +2 for Liked Songs & Local Library

    val bottomPadding = if (currentTrack != null) 160.dp else 90.dp

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(strings.newPlaylist, color = SonaraTokens.TextPrimary) },
            text = {
                OutlinedTextField(
                    value = newPlaylistTitle,
                    onValueChange = { newPlaylistTitle = it },
                    label = { Text(strings.playlistName) },
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
                        if (newPlaylistTitle.isNotBlank()) {
                            database.createPlaylist(newPlaylistTitle.trim())
                            newPlaylistTitle = ""
                            showCreateDialog = false
                            refresh()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SonaraTokens.Accent, contentColor = SonaraTokens.TextOnAccent)
                ) {
                    Text(strings.createAndAdd, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text(strings.cancel, color = SonaraTokens.TextSecondary)
                }
            },
            containerColor = SonaraTokens.Surface
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            // SUB-VIEW: Liked Songs
            viewingFavorites -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp),
                    contentPadding = PaddingValues(bottom = bottomPadding),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                IconButton(
                                    onClick = { viewingFavorites = false },
                                    modifier = Modifier.size(40.dp).clip(CircleShape).background(SonaraTokens.SurfaceRaised)
                                ) {
                                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = SonaraTokens.TextPrimary, modifier = Modifier.size(20.dp))
                                }
                                Column {
                                    Text(
                                        text = "Liked Songs",
                                        color = SonaraTokens.TextPrimary,
                                        fontSize = 28.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = (-0.5).sp
                                    )
                                    Text(
                                        text = "${favorites.size} Saved Tracks",
                                        color = SonaraTokens.TextSecondary,
                                        fontSize = 13.sp
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                IconButton(
                                    onClick = {
                                        if (favorites.isNotEmpty()) onPlayTrack(favorites.first(), favorites)
                                    },
                                    modifier = Modifier.size(40.dp).clip(CircleShape).background(SonaraTokens.Accent)
                                ) {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = "Play all", tint = SonaraTokens.TextOnAccent, modifier = Modifier.size(22.dp))
                                }
                                IconButton(
                                    onClick = {
                                        if (favorites.isNotEmpty()) {
                                            val shuffled = favorites.shuffled()
                                            onPlayTrack(shuffled.first(), shuffled)
                                        }
                                    },
                                    modifier = Modifier.size(40.dp).clip(CircleShape).background(SonaraTokens.SurfaceChip)
                                ) {
                                    Icon(Icons.Rounded.Shuffle, contentDescription = "Shuffle", tint = SonaraTokens.TextPrimary, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }

                    if (favorites.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                                Text("No liked songs yet. Tap the heart on any song to save it here.", color = SonaraTokens.TextSecondary, fontSize = 14.sp)
                            }
                        }
                    } else {
                        items(favorites) { track ->
                            DensityTrackRow(
                                track = track,
                                isPlaying = isPlaying,
                                isCurrent = currentTrack?.id == track.id,
                                onPlay = { onPlayTrack(track, favorites) },
                                onPlayNext = onPlayNext,
                                onAddToQueue = onAddToQueue,
                                onToggleLike = {
                                    onToggleLike?.invoke(it)
                                    refresh()
                                },
                                isLiked = true
                            )
                        }
                    }
                }
            }

            // SUB-VIEW: Local Music Library
            viewingLocalLibrary -> {
                val filteredLocal = remember(localSongs, localSearchQuery) {
                    if (localSearchQuery.isBlank()) localSongs
                    else localSongs.filter {
                        it.title.contains(localSearchQuery, ignoreCase = true) ||
                        it.artist.contains(localSearchQuery, ignoreCase = true) ||
                        it.album.contains(localSearchQuery, ignoreCase = true)
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp),
                    contentPadding = PaddingValues(bottom = bottomPadding),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                IconButton(
                                    onClick = { viewingLocalLibrary = false },
                                    modifier = Modifier.size(40.dp).clip(CircleShape).background(SonaraTokens.SurfaceRaised)
                                ) {
                                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = SonaraTokens.TextPrimary, modifier = Modifier.size(20.dp))
                                }
                                Column {
                                    Text(
                                        text = "Local Music Library",
                                        color = SonaraTokens.TextPrimary,
                                        fontSize = 28.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = (-0.5).sp
                                    )
                                    Text(
                                        text = "${localSongs.size} Tracks · ${localFolders.size} Folders",
                                        color = SonaraTokens.TextSecondary,
                                        fontSize = 13.sp
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { importLocalFolder() },
                                    colors = ButtonDefaults.buttonColors(containerColor = SonaraTokens.Accent),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, tint = SonaraTokens.TextOnAccent, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Add Folder", color = SonaraTokens.TextOnAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                IconButton(
                                    onClick = { rescanLocalFolders() },
                                    modifier = Modifier.size(40.dp).clip(CircleShape).background(SonaraTokens.SurfaceChip)
                                ) {
                                    if (isScanningLocal) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = SonaraTokens.Accent)
                                    } else {
                                        Icon(Icons.Rounded.Sync, contentDescription = "Rescan", tint = SonaraTokens.Accent, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }

                    // Scanned Folders Chips
                    if (localFolders.isNotEmpty()) {
                        item {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                items(localFolders) { folder ->
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = SonaraTokens.SurfaceRaised,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, SonaraTokens.SurfaceChip.copy(alpha = 0.5f))
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Icon(Icons.Rounded.Folder, contentDescription = null, tint = SonaraTokens.Accent, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            val folderName = remember(folder.path) { File(folder.path).name.ifEmpty { folder.path } }
                                            Text(folderName, color = SonaraTokens.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            IconButton(
                                                onClick = {
                                                    database.removeLibraryFolder(folder.id)
                                                    refresh()
                                                },
                                                modifier = Modifier.size(20.dp)
                                            ) {
                                                Icon(Icons.Rounded.Close, contentDescription = "Remove", tint = SonaraTokens.TextSecondary, modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Search box inside local library
                    item {
                        OutlinedTextField(
                            value = localSearchQuery,
                            onValueChange = { localSearchQuery = it },
                            placeholder = { Text("Filter local songs...", color = SonaraTokens.TextSecondary) },
                            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = SonaraTokens.TextSecondary, modifier = Modifier.size(20.dp)) },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = SonaraTokens.TextPrimary,
                                unfocusedTextColor = SonaraTokens.TextPrimary,
                                focusedBorderColor = SonaraTokens.Accent,
                                unfocusedBorderColor = SonaraTokens.SurfaceChip,
                                focusedContainerColor = SonaraTokens.Surface,
                                unfocusedContainerColor = SonaraTokens.Surface
                            ),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        )
                    }

                    if (filteredLocal.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    if (localFolders.isEmpty()) "No local music folders added yet. Click 'Add Folder' above to scan your files."
                                    else "No songs found matching your search.",
                                    color = SonaraTokens.TextSecondary,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    } else {
                        items(filteredLocal) { track ->
                            DensityTrackRow(
                                track = track,
                                isPlaying = isPlaying,
                                isCurrent = currentTrack?.id == track.id,
                                onPlay = { onPlayTrack(track, filteredLocal) },
                                onPlayNext = onPlayNext,
                                onAddToQueue = onAddToQueue,
                                onToggleLike = {
                                    onToggleLike?.invoke(it)
                                    refresh()
                                },
                                isLiked = favorites.any { it.id == track.id }
                            )
                        }
                    }
                }
            }

            // SUB-VIEW: Selected Playlist
            selectedPlaylist != null -> {
                val pl = selectedPlaylist!!
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp),
                    contentPadding = PaddingValues(bottom = bottomPadding),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                IconButton(
                                    onClick = { selectedPlaylist = null },
                                    modifier = Modifier.size(40.dp).clip(CircleShape).background(SonaraTokens.SurfaceRaised)
                                ) {
                                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = SonaraTokens.TextPrimary, modifier = Modifier.size(20.dp))
                                }
                                Column {
                                    Text(
                                        text = pl.title,
                                        color = SonaraTokens.TextPrimary,
                                        fontSize = 28.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = (-0.5).sp
                                    )
                                    Text(
                                        text = "${pl.tracks.size} Tracks",
                                        color = SonaraTokens.TextSecondary,
                                        fontSize = 13.sp
                                    )
                                }
                            }

                            if (pl.tracks.isNotEmpty()) {
                                IconButton(
                                    onClick = { onPlayTrack(pl.tracks.first(), pl.tracks) },
                                    modifier = Modifier.size(40.dp).clip(CircleShape).background(SonaraTokens.Accent)
                                ) {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = "Play all", tint = SonaraTokens.TextOnAccent, modifier = Modifier.size(22.dp))
                                }
                            }
                        }
                    }

                    if (isLoadingTracks) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = SonaraTokens.Accent)
                            }
                        }
                    } else if (pl.tracks.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                                Text("No tracks in this playlist yet.", color = SonaraTokens.TextSecondary, fontSize = 14.sp)
                            }
                        }
                    } else {
                        items(pl.tracks) { track ->
                            DensityTrackRow(
                                track = track,
                                isPlaying = isPlaying,
                                isCurrent = currentTrack?.id == track.id,
                                onPlay = { onPlayTrack(track, pl.tracks) },
                                onPlayNext = onPlayNext,
                                onAddToQueue = onAddToQueue,
                                onToggleLike = {
                                    onToggleLike?.invoke(it)
                                    refresh()
                                },
                                isLiked = favorites.any { it.id == track.id }
                            )
                        }
                    }
                }
            }

            // PRIMARY VIEW: Playlists Overview with Liked Songs & Local Library Hero Cards
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp),
                    contentPadding = PaddingValues(bottom = bottomPadding),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Top App Header
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 14.dp)
                        ) {
                            Column {
                                Text(
                                    text = strings.navPlaylists,
                                    color = SonaraTokens.TextPrimary,
                                    fontSize = 30.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.5).sp
                                )
                                Text(
                                    text = "$totalCount Collections · $totalTracks Tracks",
                                    color = SonaraTokens.TextSecondary,
                                    fontSize = 13.sp
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (isYtConnected) {
                                    IconButton(
                                        onClick = { syncYtPlaylists() },
                                        modifier = Modifier.size(40.dp).clip(CircleShape).background(SonaraTokens.SurfaceRaised)
                                    ) {
                                        if (isLoadingRemote) {
                                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = SonaraTokens.Accent)
                                        } else {
                                            Icon(Icons.Rounded.Sync, contentDescription = "Sync YouTube", tint = SonaraTokens.Accent, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                }

                                IconButton(
                                    onClick = { showCreateDialog = true },
                                    modifier = Modifier.size(40.dp).clip(CircleShape).background(SonaraTokens.SurfaceRaised)
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = strings.newPlaylist, tint = SonaraTokens.TextPrimary, modifier = Modifier.size(22.dp))
                                }
                            }
                        }
                    }

                    // HERO CARD 1: Liked Songs 📌
                    item {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = SonaraTokens.SurfaceRaised,
                            border = androidx.compose.foundation.BorderStroke(1.dp, SonaraTokens.SurfaceChip.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .clickable { viewingFavorites = true }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(
                                            androidx.compose.ui.graphics.Brush.linearGradient(
                                                colors = listOf(SonaraTokens.AccentStrong, SonaraTokens.Accent)
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Favorite,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Liked Songs",
                                            color = SonaraTokens.TextPrimary,
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(text = "📌", fontSize = 12.sp)
                                    }
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = "${favorites.size} favorite tracks",
                                        color = SonaraTokens.TextSecondary,
                                        fontSize = 13.sp
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    IconButton(
                                        onClick = {
                                            if (favorites.isNotEmpty()) onPlayTrack(favorites.first(), favorites)
                                        },
                                        modifier = Modifier.size(42.dp).clip(CircleShape).background(SonaraTokens.Accent)
                                    ) {
                                        Icon(Icons.Rounded.PlayArrow, contentDescription = "Play", tint = SonaraTokens.TextOnAccent, modifier = Modifier.size(24.dp))
                                    }
                                    IconButton(
                                        onClick = {
                                            if (favorites.isNotEmpty()) {
                                                val shuffled = favorites.shuffled()
                                                onPlayTrack(shuffled.first(), shuffled)
                                            }
                                        },
                                        modifier = Modifier.size(42.dp).clip(CircleShape).background(SonaraTokens.SurfaceChip)
                                    ) {
                                        Icon(Icons.Rounded.Shuffle, contentDescription = "Shuffle", tint = SonaraTokens.TextPrimary, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }

                    // HERO CARD 2: Local Music Library 📁
                    item {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = SonaraTokens.SurfaceRaised,
                            border = androidx.compose.foundation.BorderStroke(1.dp, SonaraTokens.SurfaceChip.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .clickable { viewingLocalLibrary = true }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(
                                            androidx.compose.ui.graphics.Brush.linearGradient(
                                                colors = listOf(Color(0xFF2E4B3E), Color(0xFF1B3326))
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.FolderCopy,
                                        contentDescription = null,
                                        tint = SonaraTokens.Accent,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Local Music Library",
                                            color = SonaraTokens.TextPrimary,
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(text = "📁", fontSize = 12.sp)
                                    }
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = "${localFolders.size} Folders · ${localSongs.size} Songs",
                                        color = SonaraTokens.TextSecondary,
                                        fontSize = 13.sp
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    IconButton(
                                        onClick = { importLocalFolder() },
                                        modifier = Modifier.size(42.dp).clip(CircleShape).background(SonaraTokens.SurfaceChip)
                                    ) {
                                        Icon(Icons.Rounded.CreateNewFolder, contentDescription = "Import Folder", tint = SonaraTokens.TextPrimary, modifier = Modifier.size(20.dp))
                                    }
                                    IconButton(
                                        onClick = { rescanLocalFolders() },
                                        modifier = Modifier.size(42.dp).clip(CircleShape).background(SonaraTokens.SurfaceChip)
                                    ) {
                                        if (isScanningLocal) {
                                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = SonaraTokens.Accent)
                                        } else {
                                            Icon(Icons.Rounded.Sync, contentDescription = "Rescan", tint = SonaraTokens.Accent, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Section: Custom Playlists
                    if (playlists.isNotEmpty()) {
                        item {
                            Text(
                                text = "Your Playlists",
                                color = SonaraTokens.TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                            )
                        }

                        items(playlists) { pl ->
                            val dateFormatted = remember(pl.createdAt) {
                                LocalDateFormatter(pl.createdAt)
                            }

                            Surface(
                                color = SonaraTokens.Surface,
                                shape = RoundedCornerShape(SonaraTokens.RadiusMd),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedPlaylist = pl }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(14.dp)
                                ) {
                                    AsyncArtwork(
                                        url = pl.coverUrl,
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(SonaraTokens.RadiusSm))
                                    )

                                    Spacer(modifier = Modifier.width(16.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = pl.title,
                                            color = SonaraTokens.TextPrimary,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "${pl.tracks.size} tracks · $dateFormatted",
                                            color = SonaraTokens.TextSecondary,
                                            fontSize = 12.sp
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            pl.tracks.firstOrNull()?.let { onPlayTrack(it, pl.tracks) }
                                        },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(SonaraTokens.AccentStrong)
                                    ) {
                                        Icon(Icons.Rounded.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(20.dp))
                                    }

                                    Spacer(modifier = Modifier.width(6.dp))

                                    IconButton(
                                        onClick = {
                                            database.deletePlaylist(pl.id)
                                            refresh()
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete", tint = SonaraTokens.TextSecondary, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }

                    // Section: YouTube Music Playlists
                    if (remotePlaylists.isNotEmpty()) {
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                            ) {
                                Text(
                                    text = "YouTube Music Library",
                                    color = SonaraTokens.TextPrimary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Surface(
                                    color = Color(0xFF421C1C),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "${remotePlaylists.size}",
                                        color = Color(0xFFFF6B6B),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        items(remotePlaylists) { pl ->
                            Surface(
                                color = SonaraTokens.Surface,
                                shape = RoundedCornerShape(SonaraTokens.RadiusMd),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectPlaylist(pl) }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(14.dp)
                                ) {
                                    AsyncArtwork(
                                        url = pl.coverUrl,
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(SonaraTokens.RadiusSm))
                                    )

                                    Spacer(modifier = Modifier.width(16.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = pl.title,
                                            color = SonaraTokens.TextPrimary,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "${pl.trackCount} tracks · YouTube Music",
                                            color = SonaraTokens.TextSecondary,
                                            fontSize = 12.sp
                                        )
                                    }

                                    Icon(
                                        imageVector = Icons.Rounded.ChevronRight,
                                        contentDescription = null,
                                        tint = SonaraTokens.TextSecondary,
                                        modifier = Modifier.size(20.dp)
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

private fun LocalDateFormatter(epochMs: Long): String {
    return try {
        Instant.ofEpochMilli(epochMs)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
    } catch (_: Exception) {
        "Sep 15, 2026"
    }
}
