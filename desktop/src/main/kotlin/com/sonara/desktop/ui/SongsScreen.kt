package com.sonara.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonara.desktop.data.LocalLibraryScanner
import com.sonara.desktop.model.LibraryFolder
import com.sonara.desktop.model.Track
import com.sonara.desktop.storage.DesktopDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.JFileChooser
import javax.swing.SwingUtilities

@Composable
fun SongsScreen(
    onPlayTrack: (Track, List<Track>) -> Unit,
    currentTrack: Track?,
    isPlaying: Boolean,
    database: DesktopDatabase,
    onPlayNext: ((Track) -> Unit)? = null,
    onAddToQueue: ((Track) -> Unit)? = null,
    onToggleLike: ((Track) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var libraryFolders by remember { mutableStateOf(database.getLibraryFolders()) }
    var allSongs by remember { mutableStateOf(database.getAllLibrarySongs()) }
    var searchQuery by remember { mutableStateOf("") }
    var isScanning by remember { mutableStateOf(false) }
    var scanStatusMessage by remember { mutableStateOf<String?>(null) }
    val favorites = remember { mutableStateOf(database.getFavorites().map { it.id }.toSet()) }

    fun refreshData() {
        libraryFolders = database.getLibraryFolders()
        allSongs = database.getAllLibrarySongs()
        favorites.value = database.getFavorites().map { it.id }.toSet()
    }

    fun startFolderImport() {
        SwingUtilities.invokeLater {
            try {
                val chooser = JFileChooser().apply {
                    dialogTitle = "Select Music Folder to Import"
                    fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                    isAcceptAllFileFilterUsed = false
                }
                val result = chooser.showOpenDialog(null)
                if (result == JFileChooser.APPROVE_OPTION && chooser.selectedFile != null) {
                    val folder = chooser.selectedFile
                    scope.launch(Dispatchers.IO) {
                        isScanning = true
                        scanStatusMessage = "Importing ${folder.name}..."
                        val folderRecord = database.addLibraryFolder(folder.absolutePath)
                        LocalLibraryScanner.scanFolder(folder, folderRecord.id, database)
                        withContext(Dispatchers.Main) {
                            refreshData()
                            isScanning = false
                            scanStatusMessage = null
                        }
                    }
                }
            } catch (_: Exception) {
                isScanning = false
                scanStatusMessage = null
            }
        }
    }

    fun rescanAllFolders() {
        if (isScanning || libraryFolders.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            isScanning = true
            for (folderRecord in libraryFolders) {
                val dir = File(folderRecord.path)
                if (dir.exists() && dir.isDirectory) {
                    scanStatusMessage = "Rescanning ${dir.name}..."
                    LocalLibraryScanner.scanFolder(dir, folderRecord.id, database)
                }
            }
            withContext(Dispatchers.Main) {
                refreshData()
                isScanning = false
                scanStatusMessage = null
            }
        }
    }

    fun removeFolder(folder: LibraryFolder) {
        scope.launch(Dispatchers.IO) {
            database.removeLibraryFolder(folder.id)
            withContext(Dispatchers.Main) {
                refreshData()
            }
        }
    }

    val filteredSongs = remember(allSongs, searchQuery) {
        if (searchQuery.isBlank()) allSongs
        else {
            val q = searchQuery.trim().lowercase()
            allSongs.filter {
                it.title.lowercase().contains(q) ||
                it.artist.lowercase().contains(q) ||
                it.album.lowercase().contains(q)
            }
        }
    }

    val totalDurationMs = remember(filteredSongs) {
        filteredSongs.sumOf { it.durationMs }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 20.dp)
    ) {
        // Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Songs & Local Library",
                    color = SonaraTokens.TextPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${filteredSongs.size} tracks • ${formatDuration(totalDurationMs)} total",
                    color = SonaraTokens.TextSecondary,
                    fontSize = 13.sp
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isScanning) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        CircularProgressIndicator(
                            color = SonaraTokens.Accent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = scanStatusMessage ?: "Scanning...",
                            color = SonaraTokens.Accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else if (libraryFolders.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { rescanAllFolders() },
                        shape = RoundedCornerShape(SonaraTokens.RadiusPill),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SonaraTokens.TextPrimary)
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Rescan", fontSize = 13.sp)
                    }
                }

                Button(
                    onClick = { startFolderImport() },
                    shape = RoundedCornerShape(SonaraTokens.RadiusPill),
                    colors = ButtonDefaults.buttonColors(containerColor = SonaraTokens.Accent)
                ) {
                    Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, tint = SonaraTokens.TextOnAccent, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Import Folder", color = SonaraTokens.TextOnAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Search bar & Play/Shuffle controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Filter songs, artists, or albums...", color = SonaraTokens.TextSecondary, fontSize = 13.sp) },
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Rounded.Search, contentDescription = null, tint = SonaraTokens.TextSecondary, modifier = Modifier.size(18.dp))
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear", tint = SonaraTokens.TextSecondary, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = SonaraTokens.TextPrimary,
                    unfocusedTextColor = SonaraTokens.TextPrimary,
                    focusedBorderColor = SonaraTokens.Accent,
                    unfocusedBorderColor = SonaraTokens.SurfaceChip,
                    focusedContainerColor = SonaraTokens.Surface,
                    unfocusedContainerColor = SonaraTokens.Surface
                ),
                shape = RoundedCornerShape(SonaraTokens.RadiusPill),
                modifier = Modifier.weight(1f).height(48.dp)
            )

            if (filteredSongs.isNotEmpty()) {
                Surface(
                    onClick = {
                        val first = filteredSongs.firstOrNull()
                        if (first != null) onPlayTrack(first, filteredSongs)
                    },
                    shape = RoundedCornerShape(SonaraTokens.RadiusPill),
                    color = SonaraTokens.AccentStrong,
                    modifier = Modifier.height(44.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = SonaraTokens.TextPrimary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Play All", color = SonaraTokens.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Surface(
                    onClick = {
                        val shuffled = filteredSongs.shuffled()
                        val first = shuffled.firstOrNull()
                        if (first != null) onPlayTrack(first, shuffled)
                    },
                    shape = RoundedCornerShape(SonaraTokens.RadiusPill),
                    color = SonaraTokens.SurfaceRaised,
                    modifier = Modifier.height(44.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Icon(Icons.Rounded.Shuffle, contentDescription = null, tint = SonaraTokens.TextSecondary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Shuffle", color = SonaraTokens.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        // Folders Chips Row
        if (libraryFolders.isNotEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    Text(
                        text = "FOLDERS:",
                        color = SonaraTokens.TextSecondary.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
                items(libraryFolders) { folder ->
                    Surface(
                        color = SonaraTokens.SurfaceChip.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(SonaraTokens.RadiusPill),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SonaraTokens.SurfaceChip.copy(alpha = 0.6f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Folder,
                                contentDescription = null,
                                tint = SonaraTokens.Accent,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            val folderName = File(folder.path).name.ifBlank { folder.path }
                            Text(
                                text = "$folderName (${folder.songCount})",
                                color = SonaraTokens.TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = { removeFolder(folder) },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Remove folder",
                                    tint = SonaraTokens.TextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Songs list or empty state
        if (filteredSongs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(SonaraTokens.SurfaceRaised),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (searchQuery.isNotEmpty()) Icons.Rounded.SearchOff else Icons.Rounded.FolderOpen,
                            contentDescription = null,
                            tint = SonaraTokens.Accent,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Text(
                        text = if (searchQuery.isNotEmpty()) "No songs match \"$searchQuery\"" else "Your music library is empty",
                        color = SonaraTokens.TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = if (searchQuery.isNotEmpty()) "Try a different search term" else "Import your local music folders (MP3, FLAC, WAV, M4A, OGG) to listen offline with zero latency.",
                        color = SonaraTokens.TextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.widthIn(max = 420.dp)
                    )

                    if (searchQuery.isEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = { startFolderImport() },
                            colors = ButtonDefaults.buttonColors(containerColor = SonaraTokens.Accent),
                            shape = RoundedCornerShape(SonaraTokens.RadiusPill)
                        ) {
                            Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, tint = SonaraTokens.TextOnAccent, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Import Music Folder", color = SonaraTokens.TextOnAccent, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxSize().weight(1f)
            ) {
                items(filteredSongs) { track ->
                    val isCurrent = currentTrack?.id == track.id
                    val isLiked = favorites.value.contains(track.id)

                    DensityTrackRow(
                        track = track,
                        isPlaying = isPlaying,
                        isCurrent = isCurrent,
                        onPlay = { onPlayTrack(track, filteredSongs) },
                        onPlayNext = onPlayNext,
                        onAddToQueue = onAddToQueue,
                        onToggleLike = {
                            onToggleLike?.invoke(it)
                            favorites.value = database.getFavorites().map { f -> f.id }.toSet()
                        },
                        isLiked = isLiked
                    )
                }
            }
        }
    }
}
