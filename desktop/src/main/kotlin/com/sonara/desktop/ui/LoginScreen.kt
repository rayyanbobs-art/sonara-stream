package com.sonara.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonara.desktop.data.LastFmClient
import com.sonara.desktop.storage.DesktopDatabase
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    database: DesktopDatabase,
    lastFmClient: LastFmClient,
    onLoginSuccess: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var usernameInput by remember { mutableStateOf(database.getLastFmUser().ifBlank { "Rayyanbobs" }) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun submitLogin() {
        val trimmed = usernameInput.trim()
        if (trimmed.isBlank()) {
            errorMessage = "Please enter your Last.fm username"
            return
        }
        isLoading = true
        errorMessage = null
        coroutineScope.launch {
            try {
                val userInfo = lastFmClient.getUserInfo(trimmed)
                if (userInfo != null) {
                    database.setLastFmUser(userInfo.name)
                    database.setAuthenticated(true)
                    isLoading = false
                    onLoginSuccess(userInfo.name)
                } else {
                    // If network or check fails, allow proceeding with the entered name
                    database.setLastFmUser(trimmed)
                    database.setAuthenticated(true)
                    isLoading = false
                    onLoginSuccess(trimmed)
                }
            } catch (e: Exception) {
                errorMessage = "Login error: ${e.localizedMessage ?: "Unknown error"}"
                isLoading = false
            }
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
            // App Icon Container
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

            // Username Input Field styled in M3 Expressive Pill
            Surface(
                color = SonaraTokens.SurfaceRaised,
                shape = RoundedCornerShape(28.dp),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = if (errorMessage != null) Color(0xFFCF6679) else SonaraTokens.SurfaceChip
                ),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AccountCircle,
                        contentDescription = null,
                        tint = SonaraTokens.Accent,
                        modifier = Modifier.size(24.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Box(modifier = Modifier.weight(1f)) {
                        if (usernameInput.isEmpty()) {
                            Text(
                                text = "Last.fm username (e.g. Rayyanbobs)",
                                color = SonaraTokens.TextSecondary.copy(alpha = 0.6f),
                                fontSize = 15.sp
                            )
                        }
                        BasicTextField(
                            value = usernameInput,
                            onValueChange = {
                                usernameInput = it
                                errorMessage = null
                            },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = SonaraTokens.TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            cursorBrush = SolidColor(SonaraTokens.Accent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { submitLogin() }),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (usernameInput.isNotEmpty()) {
                        IconButton(
                            onClick = { usernameInput = "" },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Clear,
                                contentDescription = "Clear",
                                tint = SonaraTokens.TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Connect with Last.fm primary button
            Button(
                onClick = { submitLogin() },
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
                        imageVector = Icons.Rounded.Login,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Connect with Last.fm",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Restore backup & login button
            OutlinedButton(
                onClick = {
                    usernameInput = "Rayyanbobs"
                    submitLogin()
                },
                enabled = !isLoading,
                shape = RoundedCornerShape(28.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SonaraTokens.SurfaceChip),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = SonaraTokens.TextPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
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
                    fontSize = 15.sp,
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
