package com.sonara.desktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonara.desktop.data.LosslessClient
import com.sonara.desktop.storage.DesktopDatabase

@Composable
fun SettingsScreen(
    losslessClient: LosslessClient,
    database: DesktopDatabase,
    modifier: Modifier = Modifier
) {
    var selectedQuality by remember { mutableStateOf(LosslessClient.QUALITY_MAX_HI_RES) }
    var backendUrl by remember { mutableStateOf(System.getenv("LOSSLESS_BACKEND_URL") ?: "") }
    var apiKey by remember { mutableStateOf(System.getenv("LOSSLESS_API_KEY") ?: "") }
    var savedMessage by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Text("Settings & Configuration", color = SonaraTheme.TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("Fine-tune playback engine, audio fidelity, and local storage", color = SonaraTheme.TextSecondary, fontSize = 13.sp)
        }

        // Quality Preset Card
        item {
            Surface(
                color = SonaraTheme.CardSurface,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SonaraTheme.CardBorder),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Rounded.HighQuality, contentDescription = null, tint = SonaraTheme.Secondary)
                        Text("Streaming Fidelity Preference", color = SonaraTheme.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(14.dp))

                    val tiers = listOf(
                        LosslessClient.QUALITY_MAX_HI_RES to "Hi-Res Studio FLAC (24-bit / 96-192 kHz)",
                        LosslessClient.QUALITY_CD_LOSSLESS to "CD Lossless FLAC (16-bit / 44.1 kHz)",
                        LosslessClient.QUALITY_MP3_320 to "High Quality MP3 (320 kbps)",
                        LosslessClient.QUALITY_STANDARD to "Standard Web Audio (Adaptive)"
                    )

                    tiers.forEach { (id, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedQuality = id }
                                .padding(vertical = 8.dp, horizontal = 4.dp)
                        ) {
                            RadioButton(
                                selected = selectedQuality == id,
                                onClick = { selectedQuality = id },
                                colors = RadioButtonDefaults.colors(selectedColor = SonaraTheme.Secondary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, color = SonaraTheme.TextPrimary, fontSize = 14.sp)
                        }
                    }
                }
            }
        }

        // Backend Configuration Card
        item {
            Surface(
                color = SonaraTheme.CardSurface,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SonaraTheme.CardBorder),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Rounded.CloudSync, contentDescription = null, tint = SonaraTheme.Primary)
                        Text("Lossless Streaming Backend (Clashflac)", color = SonaraTheme.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Configure a custom Clashflac lossless backend server for unlimited direct FLAC streams.",
                        color = SonaraTheme.TextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = backendUrl,
                        onValueChange = { backendUrl = it },
                        label = { Text("Backend URL") },
                        placeholder = { Text("https://your-lossless-server.com") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = SonaraTheme.TextPrimary,
                            unfocusedTextColor = SonaraTheme.TextPrimary,
                            focusedBorderColor = SonaraTheme.Secondary,
                            unfocusedBorderColor = SonaraTheme.CardBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API Key (Optional)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = SonaraTheme.TextPrimary,
                            unfocusedTextColor = SonaraTheme.TextPrimary,
                            focusedBorderColor = SonaraTheme.Secondary,
                            unfocusedBorderColor = SonaraTheme.CardBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            losslessClient.configure(backendUrl, apiKey)
                            savedMessage = "Configuration saved successfully!"
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SonaraTheme.Primary)
                    ) {
                        Text("Save Configuration")
                    }

                    if (savedMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(savedMessage!!, color = SonaraTheme.Success, fontSize = 12.sp)
                    }
                }
            }
        }

        // About & GPL-3.0 License
        item {
            Surface(
                color = SonaraTheme.CardSurface,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SonaraTheme.CardBorder),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("About Sonara Stream Desktop", color = SonaraTheme.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Version: 4.0.0 (Native Compose Multiplatform)", color = SonaraTheme.TextSecondary, fontSize = 13.sp)
                    Text("Architecture: 100% Kotlin JVM + Jetpack Compose Desktop", color = SonaraTheme.TextSecondary, fontSize = 13.sp)
                    Text("Audio Engine: Hardware-accelerated JavaFX Media with FLAC support", color = SonaraTheme.TextSecondary, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "License: GNU General Public License v3.0 (GPL-3.0). Original LastWave-Native by Duxtami & Ajisth (Clash-Projects).",
                        color = SonaraTheme.TextMuted,
                        fontSize = 12.sp
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}
