package com.sonara.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import com.sonara.desktop.data.LastFmClient
import com.sonara.desktop.data.LosslessClient
import com.sonara.desktop.storage.DesktopDatabase
import com.sonara.desktop.update.DesktopUpdateManager
import com.sonara.desktop.update.DesktopUpdateState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    losslessClient: LosslessClient,
    database: DesktopDatabase,
    lastFmClient: LastFmClient,
    updateManager: DesktopUpdateManager? = null,
    onSignOut: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var lastFmUser by remember { mutableStateOf(database.getLastFmUser()) }
    var showUserDialog by remember { mutableStateOf(false) }
    var showDisconnectDialog by remember { mutableStateOf(false) }
    var tempUsername by remember { mutableStateOf(lastFmUser) }

    var isYtConnected by remember { mutableStateOf(database.isYtConnected()) }
    var ytAccountName by remember { mutableStateOf(database.getYtAccountName().orEmpty()) }
    var ytSyncEnabled by remember { mutableStateOf(database.isYtSyncEnabled()) }
    var showYtDialog by remember { mutableStateOf(false) }
    var tempYtAccount by remember { mutableStateOf(ytAccountName) }
    var tempYtCookies by remember { mutableStateOf(database.getYtCookies().orEmpty()) }

    var amoledMode by remember { mutableStateOf(false) }
    var dynamicColor by remember { mutableStateOf(false) }
    var dynamicNowPlaying by remember { mutableStateOf(true) }
    var bitPerfect by remember { mutableStateOf(true) }
    var crossfade by remember { mutableStateOf(false) }
    var selectedQuality by remember { mutableStateOf(LosslessClient.QUALITY_MAX_HI_RES) }
    var showQualityDialog by remember { mutableStateOf(false) }
    val updateState by (updateManager?.state ?: remember { MutableStateFlow(DesktopUpdateState()) }).collectAsState()

    if (showUserDialog) {
        AlertDialog(
            onDismissRequest = { showUserDialog = false },
            containerColor = SonaraTokens.SurfaceRaised,
            title = {
                Text("Last.fm Account", color = SonaraTokens.TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(
                        "Enter your Last.fm username to sync profile stats and scrobbles:",
                        color = SonaraTokens.TextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = tempUsername,
                        onValueChange = { tempUsername = it },
                        singleLine = true,
                        placeholder = { Text("e.g. Rayyanbobs", color = SonaraTokens.TextSecondary.copy(alpha = 0.5f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SonaraTokens.Accent,
                            unfocusedBorderColor = SonaraTokens.SurfaceChip,
                            focusedTextColor = SonaraTokens.TextPrimary,
                            unfocusedTextColor = SonaraTokens.TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = tempUsername.trim()
                        if (clean.isNotBlank()) {
                            database.setLastFmUser(clean)
                            lastFmUser = clean
                        }
                        showUserDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SonaraTokens.Accent, contentColor = SonaraTokens.TextOnAccent)
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUserDialog = false }) {
                    Text("Cancel", color = SonaraTokens.TextSecondary)
                }
            }
        )
    }

    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            containerColor = SonaraTokens.SurfaceRaised,
            title = {
                Text("Disconnect Account", color = SonaraTokens.TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Are you sure you want to disconnect $lastFmUser? You will be logged out and returned to the login screen.",
                    color = SonaraTokens.TextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        database.signOut()
                        showDisconnectDialog = false
                        onSignOut()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B2626), contentColor = Color.White)
                ) {
                    Text("Disconnect", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectDialog = false }) {
                    Text("Cancel", color = SonaraTokens.TextSecondary)
                }
            }
        )
    }

    if (showYtDialog && isYtConnected) {
        AlertDialog(
            onDismissRequest = { showYtDialog = false },
            containerColor = SonaraTokens.SurfaceRaised,
            title = {
                Text("YouTube Music Connected", color = SonaraTokens.TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Connected account:",
                        color = SonaraTokens.TextSecondary,
                        fontSize = 13.sp
                    )
                    Text(
                        ytAccountName.ifBlank { "YouTube Music User" },
                        color = SonaraTokens.Accent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Your playlists and library are synced with Sonara Stream desktop.",
                        color = SonaraTokens.TextSecondary,
                        fontSize = 13.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showYtDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = SonaraTokens.Accent, contentColor = SonaraTokens.TextOnAccent)
                ) {
                    Text("Done", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        database.clearYtConnection()
                        isYtConnected = false
                        ytAccountName = ""
                        ytSyncEnabled = false
                        showYtDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFD97070))
                ) {
                    Text("Disconnect Account")
                }
            }
        )
    } else if (showYtDialog && !isYtConnected) {
        AlertDialog(
            onDismissRequest = { showYtDialog = false },
            containerColor = SonaraTokens.SurfaceRaised,
            title = {
                Text("Connect YouTube Music", color = SonaraTokens.TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Sign in to access your YouTube Music library and enable two-way playlist sync:",
                        color = SonaraTokens.TextSecondary,
                        fontSize = 13.sp
                    )
                    Button(
                        onClick = {
                            try {
                                if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                                    java.awt.Desktop.getDesktop().browse(java.net.URI("https://music.youtube.com"))
                                }
                            } catch (_: Exception) {}
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SonaraTokens.SurfaceChip,
                            contentColor = SonaraTokens.TextPrimary
                        ),
                        shape = RoundedCornerShape(SonaraTokens.RadiusSm),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.OpenInBrowser,
                            contentDescription = null,
                            tint = SonaraTokens.Accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open music.youtube.com in Browser", fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    OutlinedTextField(
                        value = tempYtAccount,
                        onValueChange = { tempYtAccount = it },
                        label = { Text("Account Name / Email", fontSize = 12.sp) },
                        placeholder = { Text("e.g. My YTM Account", color = SonaraTokens.TextSecondary.copy(alpha = 0.5f)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SonaraTokens.Accent,
                            unfocusedBorderColor = SonaraTokens.SurfaceChip,
                            focusedTextColor = SonaraTokens.TextPrimary,
                            unfocusedTextColor = SonaraTokens.TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = tempYtCookies,
                        onValueChange = { tempYtCookies = it },
                        label = { Text("Session Cookie / Header (Optional)", fontSize = 12.sp) },
                        placeholder = { Text("Paste SAPISID or cookie (optional)", color = SonaraTokens.TextSecondary.copy(alpha = 0.5f)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SonaraTokens.Accent,
                            unfocusedBorderColor = SonaraTokens.SurfaceChip,
                            focusedTextColor = SonaraTokens.TextPrimary,
                            unfocusedTextColor = SonaraTokens.TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = tempYtAccount.trim().ifBlank { "YouTube Music User" }
                        database.saveYtConnection(name, tempYtCookies.trim())
                        database.setYtSyncEnabled(true)
                        isYtConnected = true
                        ytAccountName = name
                        ytSyncEnabled = true
                        showYtDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SonaraTokens.Accent, contentColor = SonaraTokens.TextOnAccent)
                ) {
                    Text("Connect", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showYtDialog = false }) {
                    Text("Cancel", color = SonaraTokens.TextSecondary)
                }
            }
        )
    }

    if (showQualityDialog) {
        AlertDialog(
            onDismissRequest = { showQualityDialog = false },
            containerColor = SonaraTokens.SurfaceRaised,
            title = {
                Text("Streaming Quality", color = SonaraTokens.TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val options = listOf(
                        LosslessClient.QUALITY_MAX_HI_RES to "Hi-Res Studio FLAC (24-bit / 96-192 kHz)",
                        LosslessClient.QUALITY_CD_LOSSLESS to "CD Lossless FLAC (16-bit / 44.1 kHz)",
                        LosslessClient.QUALITY_MP3_320 to "High Quality MP3 (320 kbps)",
                        LosslessClient.QUALITY_STANDARD to "Standard Stream (Adaptive)"
                    )
                    options.forEach { (id, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    selectedQuality = id
                                    showQualityDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp)
                        ) {
                            RadioButton(
                                selected = selectedQuality == id,
                                onClick = {
                                    selectedQuality = id
                                    showQualityDialog = false
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = SonaraTokens.Accent,
                                    unselectedColor = SonaraTokens.TextSecondary
                                )
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(label, color = SonaraTokens.TextPrimary, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQualityDialog = false }) {
                    Text("Close", color = SonaraTokens.Accent)
                }
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header matching Screenshot 5
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
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

                Text(
                    text = "Settings",
                    color = SonaraTokens.TextPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )
            }
        }

        // Section 1: Last.fm Account Card
        item {
            Surface(
                color = SonaraTokens.Surface,
                shape = RoundedCornerShape(SonaraTokens.RadiusMd),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Green circular avatar with "R"
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF3F6438)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = lastFmUser.firstOrNull()?.uppercase() ?: "R",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                tempUsername = lastFmUser
                                showUserDialog = true
                            }
                    ) {
                        Text(
                            text = "Last.fm Account",
                            color = SonaraTokens.TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = lastFmUser,
                            color = SonaraTokens.TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Red exit / disconnect button
                    IconButton(
                        onClick = {
                            showDisconnectDialog = true
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF482121))
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ExitToApp,
                            contentDescription = "Disconnect Account",
                            tint = Color(0xFFD97070),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Section 2: Language
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Language",
                    color = SonaraTokens.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                SettingsNavRow(
                    icon = Icons.Rounded.Language,
                    title = "App Language",
                    subtitle = "System default",
                    onClick = { /* System language */ }
                )
            }
        }

        // Section 3: YouTube Music
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "YouTube Music",
                    color = SonaraTokens.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                SettingsNavRow(
                    icon = Icons.Rounded.Sync,
                    title = if (isYtConnected) "YouTube Music Account" else "Connect YouTube Music",
                    subtitle = if (isYtConnected) "Connected: ${ytAccountName.ifBlank { "Active" }} · Sync ready" else "Sign in to see and sync every playlist",
                    badge = if (isYtConnected) "Connected" else null,
                    onClick = {
                        if (!isYtConnected && tempYtAccount.isBlank()) {
                            tempYtAccount = if (lastFmUser.isNotBlank()) "$lastFmUser (YTM)" else "My YouTube Music"
                        }
                        showYtDialog = true
                    }
                )

                SettingsToggleRow(
                    icon = Icons.Rounded.Sync,
                    title = "Two-way Playlist Sync",
                    subtitle = if (isYtConnected) "Automatically sync playlists between Sonara and YouTube Music" else "Connect an account first",
                    checked = ytSyncEnabled && isYtConnected,
                    enabled = isYtConnected,
                    onCheckedChange = {
                        if (isYtConnected) {
                            database.setYtSyncEnabled(it)
                            ytSyncEnabled = it
                        } else {
                            showYtDialog = true
                        }
                    }
                )
            }
        }

        // Section 4: SOLID GREEN FILLED ROW - Downloads & Offline Music
        item {
            Surface(
                color = Color(0xFF3F6438),
                shape = RoundedCornerShape(SonaraTokens.RadiusMd),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { /* Downloads view */ }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFC7E2B5)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowDownward,
                            contentDescription = "Download",
                            tint = Color(0xFF1B3117),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Downloads & Offline Music",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "No offline songs downloaded",
                            color = Color(0xFFC5DAC0),
                            fontSize = 13.sp
                        )
                    }

                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // Section 5: Appearance
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Appearance",
                    color = SonaraTokens.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                SettingsToggleRow(
                    icon = Icons.Rounded.BrightnessMedium,
                    title = "AMOLED Mode",
                    subtitle = "Pure black background",
                    checked = amoledMode,
                    onCheckedChange = { amoledMode = it }
                )

                SettingsToggleRow(
                    icon = Icons.Rounded.Palette,
                    title = "Dynamic Color",
                    subtitle = "Use your wallpaper's colors",
                    checked = dynamicColor,
                    onCheckedChange = { dynamicColor = it }
                )

                SettingsToggleRow(
                    icon = Icons.Rounded.Album,
                    title = "Dynamic Now Playing",
                    subtitle = "Match album colors",
                    checked = dynamicNowPlaying,
                    onCheckedChange = { dynamicNowPlaying = it }
                )
            }
        }

        // Section 6: Audio & Streaming
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Audio & Streaming",
                    color = SonaraTokens.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                val qualityTitle = when (selectedQuality) {
                    LosslessClient.QUALITY_MAX_HI_RES -> "Hi-Res Studio FLAC (24-bit / 96-192 kHz)"
                    LosslessClient.QUALITY_CD_LOSSLESS -> "CD Lossless FLAC (16-bit / 44.1 kHz)"
                    LosslessClient.QUALITY_MP3_320 -> "High Quality MP3 (320 kbps)"
                    else -> "Standard Stream (Adaptive)"
                }

                SettingsNavRow(
                    icon = Icons.Rounded.HighQuality,
                    title = "Streaming Quality",
                    subtitle = qualityTitle,
                    onClick = { showQualityDialog = true }
                )

                SettingsToggleRow(
                    icon = Icons.Rounded.GraphicEq,
                    title = "Bit-Perfect Output",
                    subtitle = "Bypass OS mixer resampling for pristine audio",
                    checked = bitPerfect,
                    onCheckedChange = { bitPerfect = it }
                )

                SettingsToggleRow(
                    icon = Icons.Rounded.FastForward,
                    title = "Gapless & Crossfade",
                    subtitle = "Smooth 3-second crossfade between tracks",
                    checked = crossfade,
                    onCheckedChange = { crossfade = it }
                )
            }
        }

        // Section 7: Updates
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Updates",
                    color = SonaraTokens.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                Surface(
                    color = SonaraTokens.Surface,
                    shape = RoundedCornerShape(SonaraTokens.RadiusMd),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(SonaraTokens.SurfaceChip),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SystemUpdate,
                                    contentDescription = null,
                                    tint = SonaraTokens.Accent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (updateState.isUpdateAvailable) "Update Available" else "App Version",
                                    color = SonaraTokens.TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = when {
                                        updateState.isUpdateAvailable -> "Sonara Stream v${updateState.latestVersion} is ready to install"
                                        updateState.isChecking -> "Checking for updates..."
                                        !updateState.message.isNullOrBlank() -> updateState.message!!
                                        else -> "You're on the latest version (v${DesktopUpdateManager.CURRENT_VERSION})"
                                    },
                                    color = if (updateState.isUpdateAvailable) SonaraTokens.Accent else SonaraTokens.TextSecondary,
                                    fontSize = 13.sp
                                )
                            }

                            if (updateState.isChecking) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = SonaraTokens.Accent,
                                    strokeWidth = 2.dp
                                )
                            } else if (updateState.isUpdateAvailable) {
                                Button(
                                    onClick = { updateManager?.downloadAndInstall() },
                                    colors = ButtonDefaults.buttonColors(containerColor = SonaraTokens.Accent),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        "Update Now",
                                        color = SonaraTokens.TextOnAccent,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { updateManager?.checkForUpdates(isSilent = false) },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SonaraTokens.TextPrimary),
                                    border = BorderStroke(1.dp, SonaraTokens.SurfaceChip),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text("Check Now", fontSize = 13.sp)
                                }
                            }
                        }

                        if (updateState.isDownloading) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = updateState.downloadStatus ?: "Downloading installer...",
                                        color = SonaraTokens.TextSecondary,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = "${(updateState.downloadProgress * 100).toInt()}%",
                                        color = SonaraTokens.Accent,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { updateState.downloadProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = SonaraTokens.Accent,
                                    trackColor = SonaraTokens.SurfaceChip
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section 8: About
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "About",
                    color = SonaraTokens.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                Surface(
                    color = SonaraTokens.Surface,
                    shape = RoundedCornerShape(SonaraTokens.RadiusMd),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(SonaraTokens.SurfaceChip),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MusicNote,
                                contentDescription = null,
                                tint = SonaraTokens.Accent,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Sonara Stream Desktop",
                                color = SonaraTokens.TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "v4.0.0 · GPL-3.0 License",
                                color = SonaraTokens.Accent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Based on LastWave-Native by Duxtami & Ajisth",
                                color = SonaraTokens.TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(84.dp))
        }
    }
}

@Composable
fun SettingsNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badge: String? = null,
    onClick: () -> Unit
) {
    Surface(
        color = SonaraTokens.Surface,
        shape = RoundedCornerShape(SonaraTokens.RadiusMd),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(SonaraTokens.SurfaceChip),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = SonaraTokens.Accent,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        color = SonaraTokens.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (!badge.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = Color(0xFF2E7D32).copy(alpha = 0.25f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = badge,
                                color = Color(0xFF81C784),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = SonaraTokens.TextSecondary,
                    fontSize = 13.sp
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

@Composable
fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        color = SonaraTokens.Surface,
        shape = RoundedCornerShape(SonaraTokens.RadiusMd),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(SonaraTokens.SurfaceChip),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) SonaraTokens.Accent else SonaraTokens.TextSecondary.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = if (enabled) SonaraTokens.TextPrimary else SonaraTokens.TextSecondary.copy(alpha = 0.6f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = SonaraTokens.TextSecondary,
                    fontSize = 13.sp
                )
            }

            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = SonaraTokens.TextOnAccent,
                    checkedTrackColor = SonaraTokens.Accent,
                    uncheckedThumbColor = SonaraTokens.TextSecondary,
                    uncheckedTrackColor = SonaraTokens.SurfaceChip
                )
            )
        }
    }
}
