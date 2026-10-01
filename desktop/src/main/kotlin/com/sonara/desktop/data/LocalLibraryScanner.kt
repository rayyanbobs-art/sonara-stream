package com.sonara.desktop.data

import com.sonara.desktop.model.Track
import com.sonara.desktop.storage.DesktopDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.util.Locale

object LocalLibraryScanner {

    private val SUPPORTED_EXTENSIONS = setOf("mp3", "flac", "wav", "m4a", "ogg", "aac", "wma")

    data class ScanResult(
        val totalFilesScanned: Int,
        val newTracksFound: Int,
        val tracks: List<Track>
    )

    suspend fun scanFolder(
        folder: File,
        folderId: String? = null,
        database: DesktopDatabase? = null,
        onProgress: (scanned: Int, currentFile: String) -> Unit = { _, _ -> }
    ): ScanResult = withContext(Dispatchers.IO) {
        if (!folder.exists() || !folder.isDirectory) {
            return@withContext ScanResult(0, 0, emptyList())
        }

        val audioFiles = mutableListOf<File>()
        folder.walkTopDown()
            .maxDepth(10)
            .filter { it.isFile && it.extension.lowercase(Locale.ROOT) in SUPPORTED_EXTENSIONS }
            .forEach { audioFiles.add(it) }

        val tracks = mutableListOf<Track>()
        var count = 0
        for (file in audioFiles) {
            count++
            onProgress(count, file.name)
            try {
                val track = extractTrackMetadata(file)
                tracks.add(track)
            } catch (_: Exception) {}
        }

        if (database != null && tracks.isNotEmpty()) {
            database.saveLocalSongs(tracks, folderId)
            if (folderId != null) {
                database.updateFolderSongCount(folderId, tracks.size)
            }
        }

        ScanResult(
            totalFilesScanned = audioFiles.size,
            newTracksFound = tracks.size,
            tracks = tracks
        )
    }

    suspend fun rescanAll(database: DesktopDatabase): Int = withContext(Dispatchers.IO) {
        val folders = database.getLibraryFolders()
        var total = 0
        for (folderEntry in folders) {
            val f = File(folderEntry.path)
            if (f.exists() && f.isDirectory) {
                val result = scanFolder(f, folderId = folderEntry.id, database = database)
                total += result.newTracksFound
            }
        }
        total
    }

    fun extractTrackMetadata(file: File): Track {
        val fileNameWithoutExt = file.nameWithoutExtension.trim()
        val parentDir = file.parentFile

        // Look for local album art in same folder (cover.jpg, folder.jpg, album.jpg, etc.)
        val localArtworkUrl = findLocalArtworkUrl(parentDir)

        var title = fileNameWithoutExt
        var artist = "Unknown Artist"
        var album = parentDir?.name ?: "Unknown Album"
        var durationMs = estimateDuration(file)

        // Try extracting metadata based on extension
        when (file.extension.lowercase(Locale.ROOT)) {
            "mp3" -> {
                val tags = readMp3Tags(file)
                if (tags.title.isNotBlank()) title = tags.title
                if (tags.artist.isNotBlank()) artist = tags.artist
                if (tags.album.isNotBlank()) album = tags.album
            }
        }

        // If title still matches raw filename, clean up common numbering patterns: "01 - Title", "01. Title"
        if (title == fileNameWithoutExt) {
            val clean = cleanFileName(fileNameWithoutExt)
            title = clean.title
            if (clean.artist.isNotBlank() && artist == "Unknown Artist") {
                artist = clean.artist
            }
        }

        val id = "local_${file.absolutePath.hashCode().toUInt().toString(16)}"

        return Track(
            id = id,
            title = title.ifBlank { file.nameWithoutExtension },
            artist = artist.ifBlank { "Unknown Artist" },
            album = album.ifBlank { "Local Library" },
            durationMs = durationMs,
            artworkUrl = localArtworkUrl,
            videoId = null,
            streamUrl = null,
            isLossless = file.extension.lowercase(Locale.ROOT) in setOf("flac", "wav"),
            audioQuality = if (file.extension.lowercase(Locale.ROOT) == "flac") "Lossless FLAC" else "Local Audio",
            isDownloaded = true,
            localFilePath = file.absolutePath,
            isLocal = true
        )
    }

    private data class ParsedTags(val title: String = "", val artist: String = "", val album: String = "")

    private fun readMp3Tags(file: File): ParsedTags {
        var title = ""
        var artist = ""
        var album = ""

        try {
            RandomAccessFile(file, "r").use { raf ->
                // Check ID3v2 at start of file
                val header = ByteArray(10)
                raf.readFully(header)
                if (header[0] == 'I'.code.toByte() && header[1] == 'D'.code.toByte() && header[2] == '3'.code.toByte()) {
                    val tagSize = ((header[6].toInt() and 0x7F) shl 21) or
                            ((header[7].toInt() and 0x7F) shl 14) or
                            ((header[8].toInt() and 0x7F) shl 7) or
                            (header[9].toInt() and 0x7F)

                    var pos = 10
                    while (pos < tagSize + 10 && (title.isBlank() || artist.isBlank() || album.isBlank())) {
                        raf.seek(pos.toLong())
                        val frameHeader = ByteArray(10)
                        if (raf.read(frameHeader) < 10) break
                        val frameId = String(frameHeader, 0, 4, StandardCharsets.ISO_8859_1)
                        if (!frameId.all { it.isLetterOrDigit() }) break

                        val frameSize = ((frameHeader[4].toInt() and 0xFF) shl 24) or
                                ((frameHeader[5].toInt() and 0xFF) shl 16) or
                                ((frameHeader[6].toInt() and 0xFF) shl 8) or
                                (frameHeader[7].toInt() and 0xFF)

                        if (frameSize <= 0 || frameSize > 65536) break

                        if (frameId in setOf("TIT2", "TPE1", "TALB")) {
                            val frameBytes = ByteArray(frameSize)
                            raf.readFully(frameBytes)
                            val encoding = frameBytes.firstOrNull()?.toInt() ?: 0
                            val text = decodeFrameText(frameBytes, 1, encoding)
                            when (frameId) {
                                "TIT2" -> if (title.isBlank()) title = text
                                "TPE1" -> if (artist.isBlank()) artist = text
                                "TALB" -> if (album.isBlank()) album = text
                            }
                        }
                        pos += 10 + frameSize
                    }
                }

                // If not found in ID3v2, fallback to ID3v1 at end of file (128 bytes)
                if (title.isBlank() && raf.length() >= 128) {
                    raf.seek(raf.length() - 128)
                    val id3v1 = ByteArray(128)
                    raf.readFully(id3v1)
                    if (id3v1[0] == 'T'.code.toByte() && id3v1[1] == 'A'.code.toByte() && id3v1[2] == 'G'.code.toByte()) {
                        val v1Title = String(id3v1, 3, 30, StandardCharsets.ISO_8859_1).trim().trim('\u0000')
                        val v1Artist = String(id3v1, 33, 30, StandardCharsets.ISO_8859_1).trim().trim('\u0000')
                        val v1Album = String(id3v1, 63, 30, StandardCharsets.ISO_8859_1).trim().trim('\u0000')
                        if (v1Title.isNotBlank()) title = v1Title
                        if (v1Artist.isNotBlank()) artist = v1Artist
                        if (v1Album.isNotBlank()) album = v1Album
                    }
                }
            }
        } catch (_: Exception) {}

        return ParsedTags(title.trim(), artist.trim(), album.trim())
    }

    private fun decodeFrameText(bytes: ByteArray, offset: Int, encoding: Int): String {
        if (offset >= bytes.size) return ""
        val length = bytes.size - offset
        return try {
            when (encoding) {
                1, 2 -> String(bytes, offset, length, StandardCharsets.UTF_16).trim().trim('\u0000')
                3 -> String(bytes, offset, length, StandardCharsets.UTF_8).trim().trim('\u0000')
                else -> String(bytes, offset, length, StandardCharsets.ISO_8859_1).trim().trim('\u0000')
            }
        } catch (_: Exception) {
            ""
        }
    }

    private data class CleanResult(val title: String, val artist: String = "")

    private fun cleanFileName(name: String): CleanResult {
        var clean = name
        // Strip leading track numbers: "01 - ", "01. ", "01 "
        clean = clean.replace(Regex("""^\d{1,3}[\s._-]+"""), "")

        // Check for "Artist - Title" format
        if (clean.contains(" - ")) {
            val parts = clean.split(" - ", limit = 2)
            if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                return CleanResult(title = parts[1].trim(), artist = parts[0].trim())
            }
        }

        return CleanResult(title = clean.trim())
    }

    private fun findLocalArtworkUrl(dir: File?): String? {
        if (dir == null || !dir.exists() || !dir.isDirectory) return null
        val candidates = listOf("cover.jpg", "cover.png", "folder.jpg", "folder.png", "album.jpg", "album.png", "art.jpg", "front.jpg")
        for (name in candidates) {
            val img = File(dir, name)
            if (img.exists() && img.isFile) {
                return img.toURI().toString()
            }
        }
        val firstImg = dir.listFiles { f ->
            f.isFile && f.extension.lowercase(Locale.ROOT) in setOf("jpg", "jpeg", "png", "webp")
        }?.firstOrNull()

        return firstImg?.toURI()?.toString()
    }

    private fun estimateDuration(file: File): Long {
        val len = file.length()
        if (len <= 0) return 180000L
        return when (file.extension.lowercase(Locale.ROOT)) {
            "mp3" -> ((len * 8L) / (192L * 1000L)) * 1000L
            "flac" -> ((len * 8L) / (800L * 1000L)) * 1000L
            "wav" -> ((len * 8L) / (1411L * 1000L)) * 1000L
            else -> 210000L
        }.coerceIn(10000L, 7200000L)
    }
}
