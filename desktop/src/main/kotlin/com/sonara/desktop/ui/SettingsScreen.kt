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
import com.sonara.desktop.data.BrowserCookieImporter
import com.sonara.desktop.data.DesktopYtMusicApi
import com.sonara.desktop.data.LastFmClient
import com.sonara.desktop.data.LosslessClient
import com.sonara.desktop.storage.DesktopDatabase
import com.sonara.desktop.update.DesktopUpdateManager
import com.sonara.desktop.update.DesktopUpdateState
import com.sonara.desktop.i18n.LocalStrings
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.net.URI

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    losslessClient: LosslessClient,
    database: DesktopDatabase,
    lastFmClient: LastFmClient,
    updateManager: DesktopUpdateManager? = null,
    onSignOut: () -> Unit = {},
    onAmoledChanged: (Boolean) -> Unit = {},
    onDynamicColorChanged: (Boolean) -> Unit = {},
    onDynamicNowPlayingChanged: (Boolean) -> Unit = {},
    onLanguageChanged: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val coroutineScope = rememberCoroutineScope()
    var lastFmUser by remember { mutableStateOf(database.getLastFmUser()) }
    var showUserDialog by remember { mutableStateOf(false) }
    var showDisconnectDialog by remember { mutableStateOf(false) }
    var tempUsername by remember { mutableStateOf(lastFmUser) }

    var isYtConnected by remember { mutableStateOf(database.isYtConnected()) }
    var ytAccountName by remember { mutableStateOf(database.getYtAccountName().orEmpty()) }
    var ytChannelHandle by remember { mutableStateOf(database.getYtChannelHandle().orEmpty()) }
    var ytSyncEnabled by remember { mutableStateOf(database.isYtSyncEnabled()) }
    var isYtConnecting by remember { mutableStateOf(false) }
    var showYtDetailsDialog by remember { mutableStateOf(false) }
    var showBrowserAuthDialog by remember { mutableStateOf(false) }
    val ytMusicApi = remember { DesktopYtMusicApi() }

    fun handleConnectYouTube() {
        if (isYtConnected) {
            showYtDetailsDialog = true
            return
        }
        isYtConnecting = true
        coroutineScope.launch {
            // First attempt: Check for active cookies in installed browsers (Firefox, Chrome, Edge, Brave, etc.)
            val existingCookies = BrowserCookieImporter.findYouTubeMusicCookies()
            if (existingCookies != null) {
                val accountInfo = ytMusicApi.fetchAccountInfo(existingCookies)
                if (accountInfo != null) {
                    val name = accountInfo.accountName.ifBlank { "YouTube Music User" }
                    database.saveYtConnection(
                        accountName = name,
                        cookies = existingCookies,
                        channelHandle = accountInfo.channelHandle,
                        photoUrl = accountInfo.photoUrl
                    )
                    database.setYtSyncEnabled(true)
                    isYtConnected = true
                    ytAccountName = name
                    ytChannelHandle = accountInfo.channelHandle.orEmpty()
                    ytSyncEnabled = true
                    isYtConnecting = false
                    return@launch
                }
            }

            // If not found in browser yet, open default system browser to music.youtube.com
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(URI("https://music.youtube.com"))
                }
            } catch (_: Exception) {}
            showBrowserAuthDialog = true
        }
    }

    var amoledMode by remember { mutableStateOf(database.getAmoledMode()) }
    var dynamicColor by remember { mutableStateOf(database.getDynamicColor()) }
    var dynamicNowPlaying by remember { mutableStateOf(database.getDynamicNowPlaying()) }
    var bitPerfect by remember { mutableStateOf(database.getBitPerfect()) }
    var crossfade by remember { mutableStateOf(database.getCrossfade()) }
    var selectedQuality by remember { mutableStateOf(database.getStreamingQuality()) }
    var showQualityDialog by remember { mutableStateOf(false) }

    var appLanguage by remember { mutableStateOf(database.getAppLanguage()) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    val updateState by (updateManager?.state ?: remember { MutableStateFlow(DesktopUpdateState()) }).collectAsState()

    if (showUserDialog) {
        AlertDialog(
            onDismissRequest = { showUserDialog = false },
            containerColor = SonaraTokens.SurfaceRaised,
            title = {
                Text(strings.lastFmAccount, color = SonaraTokens.TextPrimary, fontWeight = FontWeight.Bold)
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
                    Text(strings.save, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUserDialog = false }) {
                    Text(strings.cancel, color = SonaraTokens.TextSecondary)
                }
            }
        )
    }

    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            containerColor = SonaraTokens.SurfaceRaised,
            title = {
                Text(strings.disconnectAccount, color = SonaraTokens.TextPrimary, fontWeight = FontWeight.Bold)
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
                    Text(strings.disconnect, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectDialog = false }) {
                    Text(strings.cancel, color = SonaraTokens.TextSecondary)
                }
            }
        )
    }

    if (showYtDetailsDialog && isYtConnected) {
        AlertDialog(
            onDismissRequest = { showYtDetailsDialog = false },
            containerColor = SonaraTokens.SurfaceRaised,
            title = {
                Text(strings.youtubeAccount, color = SonaraTokens.TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Connected Google Account:",
                        color = SonaraTokens.TextSecondary,
                        fontSize = 13.sp
                    )
                    Text(
                        ytAccountName.ifBlank { "YouTube Music User" },
                        color = SonaraTokens.Accent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    if (ytChannelHandle.isNotBlank()) {
                        Text(
                            ytChannelHandle,
                            color = SonaraTokens.TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Your YouTube Music library playlists are synced with Sonara Stream and updated in real time.",
                        color = SonaraTokens.TextSecondary,
                        fontSize = 13.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showYtDetailsDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = SonaraTokens.Accent, contentColor = SonaraTokens.TextOnAccent)
                ) {
                    Text(strings.done, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        database.clearYtConnection()
                        isYtConnected = false
                        ytAccountName = ""
                        ytChannelHandle = ""
                        ytSyncEnabled = false
                        showYtDetailsDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFD97070))
                ) {
                    Text(strings.disconnectAccount)
                }
            }
        )
    }

    if (showBrowserAuthDialog && !isYtConnected) {
        LaunchedEffect(Unit) {
            while (showBrowserAuthDialog && !isYtConnected) {
                delay(2000)
                val cookies = BrowserCookieImporter.findYouTubeMusicCookies()
                if (cookies != null) {
                    val accountInfo = ytMusicApi.fetchAccountInfo(cookies)
                    if (accountInfo != null) {
                        val name = accountInfo.accountName.ifBlank { "YouTube Music User" }
                        database.saveYtConnection(
                            accountName = name,
                            cookies = cookies,
                            channelHandle = accountInfo.channelHandle,
                            photoUrl = accountInfo.photoUrl
                        )
                        database.setYtSyncEnabled(true)
                        isYtConnected = true
                        ytAccountName = name
                        ytChannelHandle = accountInfo.channelHandle.orEmpty()
                        ytSyncEnabled = true
                        isYtConnecting = false
                        showBrowserAuthDialog = false
                        break
                    }
                }
            }
        }

        AlertDialog(
            onDismissRequest = {
                showBrowserAuthDialog = false
                isYtConnecting = false
            },
            containerColor = SonaraTokens.SurfaceRaised,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        color = SonaraTokens.Accent,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp
                    )
                    Text(
                        "YouTube Music Sign-In",
                        color = SonaraTokens.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "We opened YouTube Music in your browser.",
                        color = SonaraTokens.TextPrimary,
                        fontSize = 14.sp
                    )
                    Text(
                        "Sign in to your Google account in the browser. Sonara Stream will automatically detect your login and connect your account here.",
                        color = SonaraTokens.TextSecondary,
                        fontSize = 13.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        try {
                            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                                Desktop.getDesktop().browse(URI("https://music.youtube.com"))
                            }
                        } catch (_: Exception) {}
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SonaraTokens.Accent,
                        contentColor = SonaraTokens.TextOnAccent
                    )
                ) {
                    Text("Re-open Browser", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showBrowserAuthDialog = false
                        isYtConnecting = false
                    }
                ) {
                    Text("Cancel", color = SonaraTokens.TextSecondary)
                }
            }
        )
    }

    if (showLanguageDialog) {
        val languages = listOf(
            "System default",
            "English",
            "Türkçe",
            "Español",
            "Deutsch",
            "Français",
            "日本語",
            "한국어",
            "中文"
        )
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            containerColor = SonaraTokens.SurfaceRaised,
            title = {
                Text(strings.chooseLanguage, color = SonaraTokens.TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    languages.forEach { lang ->
                        val displayLabel = if (lang == "System default") strings.systemDefault else lang
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    appLanguage = lang
                                    database.setAppLanguage(lang)
                                    onLanguageChanged(lang)
                                    showLanguageDialog = false
                                }
                                .padding(vertical = 8.dp, horizontal = 8.dp)
                        ) {
                            RadioButton(
                                selected = appLanguage == lang,
                                onClick = {
                                    appLanguage = lang
                                    database.setAppLanguage(lang)
                                    onLanguageChanged(lang)
                                    showLanguageDialog = false
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = SonaraTokens.Accent,
                                    unselectedColor = SonaraTokens.TextSecondary
                                )
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(displayLabel, color = SonaraTokens.TextPrimary, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(strings.close, color = SonaraTokens.Accent)
                }
            }
        )
    }

    if (showQualityDialog) {
        AlertDialog(
            onDismissRequest = { showQualityDialog = false },
            containerColor = SonaraTokens.SurfaceRaised,
            title = {
                Text(strings.streamingQuality, color = SonaraTokens.TextPrimary, fontWeight = FontWeight.Bold)
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
                                    database.setStreamingQuality(id)
                                    showQualityDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp)
                        ) {
                            RadioButton(
                                selected = selectedQuality == id,
                                onClick = {
                                    selectedQuality = id
                                    database.setStreamingQuality(id)
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
                    Text(strings.close, color = SonaraTokens.Accent)
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
                        contentDescription = strings.back,
                        tint = SonaraTokens.TextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    text = strings.settings,
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
                    // Dynamic circular avatar with user initial
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(SonaraTokens.AccentStrong),
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
                            text = strings.lastFmAccount,
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
                            contentDescription = strings.disconnectAccount,
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
                    text = strings.sectionLanguage,
                    color = SonaraTokens.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                SettingsNavRow(
                    icon = Icons.Rounded.Language,
                    title = strings.appLanguage,
                    subtitle = if (appLanguage == "System default") strings.systemDefault else appLanguage,
                    onClick = { showLanguageDialog = true }
                )
            }
        }

        // Section 3: YouTube Music
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = strings.sectionYouTube,
                    color = SonaraTokens.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                SettingsNavRow(
                    icon = Icons.Rounded.Sync,
                    title = if (isYtConnected) strings.youtubeAccount else strings.connectYouTube,
                    subtitle = if (isYtConnected) {
                        "${strings.connected}: ${ytAccountName.ifBlank { "Active" }}${if (ytChannelHandle.isNotBlank()) " ($ytChannelHandle)" else ""} · ${strings.realtimeSyncActive}"
                    } else if (isYtConnecting) {
                        strings.connectingYouTube
                    } else {
                        strings.connectYouTubeSub
                    },
                    badge = if (isYtConnected) strings.connected else if (isYtConnecting) strings.connectingBadge else null,
                    onClick = { handleConnectYouTube() }
                )

                SettingsToggleRow(
                    icon = Icons.Rounded.Sync,
                    title = strings.twoWayPlaylistSync,
                    subtitle = if (isYtConnected) strings.twoWayPlaylistSyncSub else strings.connectAccountFirst,
                    checked = ytSyncEnabled && isYtConnected,
                    enabled = isYtConnected,
                    onCheckedChange = {
                        if (isYtConnected) {
                            database.setYtSyncEnabled(it)
                            ytSyncEnabled = it
                        } else {
                            handleConnectYouTube()
                        }
                    }
                )
            }
        }

        // Section 4: Downloads & Offline Music
        item {
            Surface(
                color = SonaraTokens.AccentStrong,
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
                            .background(SonaraTokens.Accent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowDownward,
                            contentDescription = "Download",
                            tint = SonaraTokens.TextOnAccent,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.downloadsAndOffline,
                            color = SonaraTokens.TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = strings.noOfflineSongs,
                            color = SonaraTokens.TextPrimary.copy(alpha = 0.8f),
                            fontSize = 13.sp
                        )
                    }

                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = SonaraTokens.TextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // Section 5: Appearance
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = strings.sectionAppearance,
                    color = SonaraTokens.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                SettingsToggleRow(
                    icon = Icons.Rounded.BrightnessMedium,
                    title = strings.amoledMode,
                    subtitle = strings.amoledModeSub,
                    checked = amoledMode,
                    onCheckedChange = {
                        amoledMode = it
                        database.setAmoledMode(it)
                        onAmoledChanged(it)
                    }
                )

                SettingsToggleRow(
                    icon = Icons.Rounded.Palette,
                    title = strings.dynamicColor,
                    subtitle = strings.dynamicColorSub,
                    checked = dynamicColor,
                    onCheckedChange = {
                        dynamicColor = it
                        database.setDynamicColor(it)
                        onDynamicColorChanged(it)
                    }
                )

                SettingsToggleRow(
                    icon = Icons.Rounded.Album,
                    title = strings.dynamicNowPlaying,
                    subtitle = strings.dynamicNowPlayingSub,
                    checked = dynamicNowPlaying,
                    onCheckedChange = {
                        dynamicNowPlaying = it
                        database.setDynamicNowPlaying(it)
                        onDynamicNowPlayingChanged(it)
                    }
                )
            }
        }

        // Section 6: Audio & Streaming
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = strings.sectionAudio,
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
                    title = strings.streamingQuality,
                    subtitle = qualityTitle,
                    onClick = { showQualityDialog = true }
                )

                SettingsToggleRow(
                    icon = Icons.Rounded.GraphicEq,
                    title = strings.bitPerfect,
                    subtitle = strings.bitPerfectSub,
                    checked = bitPerfect,
                    onCheckedChange = {
                        bitPerfect = it
                        database.setBitPerfect(it)
                    }
                )

                SettingsToggleRow(
                    icon = Icons.Rounded.FastForward,
                    title = strings.crossfade,
                    subtitle = strings.crossfadeSub,
                    checked = crossfade,
                    onCheckedChange = {
                        crossfade = it
                        database.setCrossfade(it)
                    }
                )
            }
        }

        // Section 7: Updates
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = strings.sectionUpdates,
                    color = SonaraTokens.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                Surface(
                    color = SonaraTokens.Surface,
                    shape = RoundedCornerShape(SonaraTokens.RadiusMd),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(SonaraTokens.RadiusMd))
                        .clickable(enabled = !updateState.isDownloading && !updateState.isChecking) {
                            if (updateState.isUpdateAvailable) {
                                updateManager?.downloadAndInstall()
                            } else {
                                updateManager?.downloadAndInstall(force = true)
                            }
                        }
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
                                    text = if (updateState.isUpdateAvailable) strings.updateAvailable else strings.appVersion,
                                    color = SonaraTokens.TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = when {
                                        updateState.isUpdateAvailable -> "Sonara Stream v${updateState.latestVersion} is ready to install"
                                        updateState.isChecking -> strings.checkingForUpdates
                                        !updateState.message.isNullOrBlank() -> updateState.message!!
                                        else -> "${strings.onLatestVersion} (v${DesktopUpdateManager.CURRENT_VERSION})"
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
                                        strings.updateNow,
                                        color = SonaraTokens.TextOnAccent,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = { updateManager?.checkForUpdates(isSilent = false) },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SonaraTokens.TextPrimary),
                                        border = BorderStroke(1.dp, SonaraTokens.SurfaceChip),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(strings.check, fontSize = 13.sp)
                                    }
                                    Button(
                                        onClick = { updateManager?.downloadAndInstall(force = true) },
                                        colors = ButtonDefaults.buttonColors(containerColor = SonaraTokens.Accent),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            strings.update,
                                            color = SonaraTokens.TextOnAccent,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
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
                    text = strings.about,
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
                                text = "v${DesktopUpdateManager.CURRENT_VERSION} · GPL-3.0 License",
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
