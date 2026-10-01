package com.sonara.desktop.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.sql.DriverManager
import java.util.Locale

object BrowserCookieImporter {

    suspend fun findYouTubeMusicCookies(): String? = withContext(Dispatchers.IO) {
        // 1. Try Firefox & Gecko-based browsers (Firefox, LibreWolf, Waterfox, Floorp)
        findFirefoxCookies()?.let { return@withContext it }

        // 2. Try Chromium-based browsers (Chrome, Edge, Brave, Portable)
        findChromiumCookies()?.let { return@withContext it }

        null
    }

    private fun findFirefoxCookies(): String? {
        val appData = System.getenv("APPDATA") ?: return null
        val candidates = listOf(
            File(appData, "Mozilla/Firefox/Profiles"),
            File(appData, "Waterfox/Profiles"),
            File(appData, "LibreWolf/Profiles"),
            File(appData, "Floorp/Profiles")
        )

        for (baseDir in candidates) {
            if (!baseDir.exists() || !baseDir.isDirectory) continue
            val profileDirs = baseDir.listFiles() ?: continue
            for (profile in profileDirs) {
                val dbFile = File(profile, "cookies.sqlite")
                if (dbFile.exists() && dbFile.length() > 0) {
                    val cookies = extractFromFirefoxSqlite(dbFile)
                    if (cookies != null && isValidYouTubeSession(cookies)) {
                        return cookies
                    }
                }
            }
        }
        return null
    }

    private fun extractFromFirefoxSqlite(sourceDb: File): String? {
        var tempDir: File? = null
        return try {
            try { Class.forName("org.sqlite.JDBC") } catch (_: Throwable) {}

            tempDir = Files.createTempDirectory("sonara_ff_").toFile()
            val tempDb = File(tempDir, "cookies.sqlite")
            Files.copy(sourceDb.toPath(), tempDb.toPath(), StandardCopyOption.REPLACE_EXISTING)

            // Copy WAL and SHM journal files if present so uncheckpointed browser writes are included
            val walFile = File(sourceDb.parentFile, "${sourceDb.name}-wal")
            if (walFile.exists() && walFile.length() > 0) {
                try {
                    Files.copy(walFile.toPath(), File(tempDir, "cookies.sqlite-wal").toPath(), StandardCopyOption.REPLACE_EXISTING)
                } catch (_: Exception) {}
            }
            val shmFile = File(sourceDb.parentFile, "${sourceDb.name}-shm")
            if (shmFile.exists() && shmFile.length() > 0) {
                try {
                    Files.copy(shmFile.toPath(), File(tempDir, "cookies.sqlite-shm").toPath(), StandardCopyOption.REPLACE_EXISTING)
                } catch (_: Exception) {}
            }

            val url = "jdbc:sqlite:${tempDb.absolutePath}"
            val map = LinkedHashMap<String, String>()

            DriverManager.getConnection(url).use { conn ->
                // Crucial: Query google.com cookies first, then youtube.com cookies last.
                // This ensures youtube.com session cookies (HSID, SSID, SID, SIDTS) cleanly
                // override domain-level google.com cookies which would otherwise cause YouTube
                // InnerTube to treat the session as logged_in = 0.
                val sql = """
                    SELECT name, value FROM moz_cookies 
                    WHERE host LIKE '%google.com' OR host LIKE '%youtube.com'
                    ORDER BY CASE WHEN host LIKE '%youtube.com' THEN 1 ELSE 0 END ASC, lastAccessed ASC
                """.trimIndent()
                conn.prepareStatement(sql).use { ps ->
                    ps.executeQuery().use { rs ->
                        while (rs.next()) {
                            val name = rs.getString("name")
                            val value = rs.getString("value")
                            if (!name.isNullOrBlank() && !value.isNullOrBlank()) {
                                map[name] = value
                            }
                        }
                    }
                }
            }

            if (map.containsKey("SAPISID") || map.containsKey("__Secure-3PAPISID")) {
                map.entries.joinToString("; ") { "${it.key}=${it.value}" }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            try { tempDir?.deleteRecursively() } catch (_: Exception) {}
        }
    }

    private fun findChromiumCookies(): String? {
        // Run a lightweight Windows PowerShell probe to read and decrypt Chromium cookies if present
        return try {
            val script = """
                Add-Type -AssemblyName System.Security
                function Get-MasterKey([string]${'$'}localStatePath) {
                    if (!(Test-Path ${'$'}localStatePath)) { return ${'$'}null }
                    try {
                        ${'$'}json = Get-Content ${'$'}localStatePath -Raw | ConvertFrom-Json
                        ${'$'}b64 = ${'$'}json.os_crypt.encrypted_key
                        if (!${'$'}b64) { return ${'$'}null }
                        ${'$'}bytes = [Convert]::FromBase64String(${'$'}b64)
                        ${'$'}key = ${'$'}bytes[5..(${'$'}bytes.Length - 1)]
                        return [System.Security.Cryptography.ProtectedData]::Unprotect(${'$'}key, ${'$'}null, [System.Security.Cryptography.DataProtectionScope]::CurrentUser)
                    } catch { return ${'$'}null }
                }

                function Get-CookiesFromChromium([string]${'$'}userDataDir) {
                    ${'$'}localState = Join-Path ${'$'}userDataDir "Local State"
                    ${'$'}masterKey = Get-MasterKey ${'$'}localState
                    if (!${'$'}masterKey) { return ${'$'}null }

                    ${'$'}cookieFiles = @(
                        (Join-Path ${'$'}userDataDir "Default\Network\Cookies"),
                        (Join-Path ${'$'}userDataDir "Profile 1\Network\Cookies"),
                        (Join-Path ${'$'}userDataDir "Default\Cookies")
                    )

                    foreach (${'$'}cf in ${'$'}cookieFiles) {
                        if (Test-Path ${'$'}cf) {
                            ${'$'}tmp = [System.IO.Path]::GetTempFileName()
                            try {
                                Copy-Item -Path ${'$'}cf -Destination ${'$'}tmp -Force
                                ${'$'}conn = New-Object System.Data.SQLite.SQLiteConnection("Data Source=${'$'}tmp;Version=3;")
                                ${'$'}conn.Open()
                                ${'$'}cmd = ${'$'}conn.CreateCommand()
                                ${'$'}cmd.CommandText = "SELECT name, encrypted_value FROM cookies WHERE host_key LIKE '%youtube.com' AND (name = 'SAPISID' OR name = '__Secure-3PAPISID' OR name = 'SID')"
                                ${'$'}reader = ${'$'}cmd.ExecuteReader()
                                ${'$'}cookieMap = @{}
                                while (${'$'}reader.Read()) {
                                    ${'$'}name = ${'$'}reader.GetString(0)
                                    ${'$'}encBytes = ${'$'}reader.GetValue(1)
                                    # Chromium v10 format: starts with 'v10' or 'v11'
                                    if (${'$'}encBytes -and ${'$'}encBytes.Length -gt 31) {
                                        ${'$'}nonce = ${'$'}encBytes[3..14]
                                        ${'$'}ciphertext = ${'$'}encBytes[15..(${'$'}encBytes.Length - 17)]
                                        ${'$'}tag = ${'$'}encBytes[(${'$'}encBytes.Length - 16)..(${'$'}encBytes.Length - 1)]
                                        ${'$'}cipher = [System.Security.Cryptography.AesGcm]::new(${'$'}masterKey)
                                        ${'$'}plain = New-Object byte[] ${'$'}ciphertext.Length
                                        ${'$'}cipher.Decrypt(${'$'}nonce, ${'$'}ciphertext, ${'$'}tag, ${'$'}plain)
                                        ${'$'}val = [System.Text.Encoding]::UTF8.GetString(${'$'}plain)
                                        ${'$'}cookieMap[${'$'}name] = ${'$'}val
                                    }
                                }
                                ${'$'}conn.Close()
                                if (${'$'}cookieMap.ContainsKey("SAPISID") -or ${'$'}cookieMap.ContainsKey("__Secure-3PAPISID")) {
                                    return (${'$'}cookieMap.GetEnumerator() | ForEach-Object { "${'$'}(${'$'}_.Key)=${'$'}(${'$'}_.Value)" }) -join "; "
                                }
                            } catch {} finally {
                                Remove-Item -Path ${'$'}tmp -Force -ErrorAction SilentlyContinue
                            }
                        }
                    }
                    return ${'$'}null
                }

                ${'$'}dirs = @(
                    "${'$'}env:LOCALAPPDATA\Google\Chrome\User Data",
                    "${'$'}env:LOCALAPPDATA\Microsoft\Edge\User Data",
                    "${'$'}env:LOCALAPPDATA\BraveSoftware\Brave-Browser\User Data"
                )
                foreach (${'$'}d in ${'$'}dirs) {
                    ${'$'}res = Get-CookiesFromChromium ${'$'}d
                    if (${'$'}res) {
                        Write-Output ${'$'}res
                        break
                    }
                }
            """.trimIndent()

            val process = ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", script)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
            process.waitFor()

            if (isValidYouTubeSession(output)) {
                output
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun isValidYouTubeSession(cookies: String?): Boolean {
        if (cookies.isNullOrBlank()) return false
        return cookies.contains("SAPISID=") || cookies.contains("__Secure-3PAPISID=")
    }
}
