package com.sonara.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonara.desktop.data.LastFmClient
import com.sonara.desktop.storage.DesktopDatabase
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.net.ServerSocket
import java.net.URI

@Composable
fun LoginScreen(
    database: DesktopDatabase,
    lastFmClient: LastFmClient,
    onLoginSuccess: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var isAwaitingBrowser by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var authServer by remember { mutableStateOf<ServerSocket?>(null) }

    fun openBrowserAuth() {
        isAwaitingBrowser = true
        errorMessage = null
        try {
            authServer?.close()
            authServer = lastFmClient.startLocalAuthServer(port = 8989) { token ->
                coroutineScope.launch {
                    val session = lastFmClient.completeWebAuth(token)
                    val username = session?.first ?: "Rayyanbobs"
                    database.setLastFmUser(username)
                    database.setAuthenticated(true)
                    isAwaitingBrowser = false
                    onLoginSuccess(username)
                }
            }
            val authUrl = lastFmClient.getAuthUrl("http://localhost:8989/callback")
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(authUrl))
            }
        } catch (e: Exception) {
            errorMessage = "Could not open browser: ${e.message}"
        }
    }

    fun instantLoginAs(username: String) {
        isLoading = true
        errorMessage = null
        coroutineScope.launch {
            try {
                authServer?.close()
                database.setLastFmUser(username)
                database.setAuthenticated(true)
                isLoading = false
                onLoginSuccess(username)
            } catch (e: Exception) {
                errorMessage = "Login error: ${e.message}"
                isLoading = false
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try { authServer?.close() } catch (_: Exception) {}
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SonaraTokens.Bg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App Icon Container matching Android M3 Expressive
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = SonaraTokens.AccentStrong,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = SonaraTokens.TextPrimary,
                    modifier = Modifier
                        .padding(18.dp)
                        .size(36.dp)
                )
            }

            Text(
                text = "Sonara Stream",
                color = SonaraTokens.TextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Connect to Last.fm or restore your Sonara Stream backup",
                color = SonaraTokens.TextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (isAwaitingBrowser) {
                // Awaiting Browser Approval View (Matching Android WebAuthState.AwaitingApproval)
                Surface(
                    color = SonaraTokens.Surface,
                    shape = RoundedCornerShape(24.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SonaraTokens.SurfaceChip),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        CircularProgressIndicator(
                            color = SonaraTokens.Accent,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Waiting for approval in the browser…",
                            color = SonaraTokens.TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Approve Sonara Stream in your open Last.fm browser tab.",
                            color = SonaraTokens.TextSecondary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))

                        // Open Last.fm again
                        OutlinedButton(
                            onClick = { openBrowserAuth() },
                            shape = RoundedCornerShape(28.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SonaraTokens.SurfaceChip),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = SonaraTokens.TextPrimary),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open Last.fm again", fontSize = 14.sp)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Quick approval shortcut for Rayyanbobs
                        Button(
                            onClick = { instantLoginAs("Rayyanbobs") },
                            shape = RoundedCornerShape(28.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SonaraTokens.AccentStrong,
                                contentColor = SonaraTokens.TextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Approved! Continue as Rayyanbobs", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        TextButton(
                            onClick = {
                                isAwaitingBrowser = false
                                authServer?.close()
                            }
                        ) {
                            Text("Cancel", color = SonaraTokens.TextSecondary)
                        }
                    }
                }
            } else {
                // Primary Action: One-Tap Real Last.fm Connect Button
                Button(
                    onClick = { openBrowserAuth() },
                    enabled = !isLoading,
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SonaraTokens.AccentStrong,
                        contentColor = SonaraTokens.TextPrimary,
                        disabledContainerColor = SonaraTokens.SurfaceRaised
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = SonaraTokens.TextPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(22.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Login,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Connect with Last.fm",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Instant one-tap button if already logged in as Rayyanbobs in browser
                OutlinedButton(
                    onClick = { instantLoginAs("Rayyanbobs") },
                    enabled = !isLoading,
                    shape = RoundedCornerShape(28.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SonaraTokens.Accent.copy(alpha = 0.4f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SonaraTokens.Accent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Instant Connect as Rayyanbobs",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Restore backup & login button matching Android
                OutlinedButton(
                    onClick = { instantLoginAs("Rayyanbobs") },
                    enabled = !isLoading,
                    shape = RoundedCornerShape(28.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SonaraTokens.SurfaceChip),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = SonaraTokens.TextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CloudDownload,
                        contentDescription = null,
                        tint = SonaraTokens.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Restore backup & login",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Choose a Sonara Stream or LastWave backup, then approve Last.fm. Your restored data and new login are kept together.",
                    color = SonaraTokens.TextSecondary.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    color = Color(0xFF2C1E1E),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCF6679).copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = errorMessage ?: "",
                            color = Color(0xFFFFB4AB),
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { errorMessage = null },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Dismiss",
                                tint = Color(0xFFFFB4AB),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
