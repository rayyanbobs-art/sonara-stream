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
import com.sonara.desktop.data.LastFmClient
import com.sonara.desktop.data.LosslessClient
import com.sonara.desktop.storage.DesktopDatabase
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    losslessClient: LosslessClient,
    database: DesktopDatabase,
    lastFmClient: LastFmClient,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var lastFmUser by remember { mutableStateOf(database.getLastFmUser()) }
    var showUserDialog by remember { mutableStateOf(false) }
    var tempUsername by remember { mutableStateOf(lastFmUser) }

    var amoledMode by remember { mutableStateOf(false) }
    var dynamicColor by remember { mutableStateOf(false) }
    var dynamicNowPlaying by remember { mutableStateOf(true) }
    var ytSyncEnabled by remember { mutableStateOf(false) }
    var bitPerfect by remember { mutableStateOf(true) }
    var crossfade by remember { mutableStateOf(false) }
    var selectedQuality by remember { mutableStateOf(LosslessClient.QUALITY_MAX_HI_RES) }
    var showQualityDialog by remember { mutableStateOf(false) }

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
                            tempUsername = ""
                            showUserDialog = true
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF482121))
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ExitToApp,
                            contentDescription = "Switch Account",
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
                    title = "Connect YouTube Music",
                    subtitle = "Sign in to see and sync every playlist",
                    onClick = { /* YouTube Music connect */ }
                )

                SettingsToggleRow(
                    icon = Icons.Rounded.Sync,
                    title = "Two-way Playlist Sync",
                    subtitle = "Connect an account first",
                    checked = ytSyncEnabled,
                    onCheckedChange = { ytSyncEnabled = it }
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

        // Section 7: About
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
                Text(
                    text = title,
                    color = SonaraTokens.TextPrimary,
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
                    tint = SonaraTokens.Accent,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = SonaraTokens.TextPrimary,
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
