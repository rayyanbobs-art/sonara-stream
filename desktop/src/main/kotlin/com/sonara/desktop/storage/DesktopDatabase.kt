package com.sonara.desktop.storage

import com.sonara.desktop.model.Playlist
import com.sonara.desktop.model.Track
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID

class DesktopDatabase {
    private val dbFile: File
    private val url: String

    init {
        val appData = System.getenv("APPDATA")
        val dir = if (!appData.isNullOrBlank()) {
            File(appData, "SonaraStream")
        } else {
            File(System.getProperty("user.home"), ".sonarastream")
        }
        if (!dir.exists()) {
            dir.mkdirs()
        }
        dbFile = File(dir, "sonara.db")
        url = "jdbc:sqlite:${dbFile.absolutePath}"
        initSchema()
    }

    private fun getConnection(): Connection = DriverManager.getConnection(url)

    private fun initSchema() {
        getConnection().use { conn ->
            conn.createStatement().use { stmt ->
                stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS favorites (
                        id TEXT PRIMARY KEY,
                        title TEXT NOT NULL,
                        artist TEXT NOT NULL,
                        album TEXT,
                        duration_ms INTEGER,
                        artwork_url TEXT,
                        video_id TEXT,
                        stream_url TEXT,
                        is_lossless INTEGER,
                        audio_quality TEXT,
                        added_at INTEGER
                    );
                """.trimIndent())

                stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS history (
                        id TEXT NOT NULL,
                        title TEXT NOT NULL,
                        artist TEXT NOT NULL,
                        album TEXT,
                        duration_ms INTEGER,
                        artwork_url TEXT,
                        video_id TEXT,
                        stream_url TEXT,
                        is_lossless INTEGER,
                        audio_quality TEXT,
                        played_at INTEGER
                    );
                """.trimIndent())

                stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS playlists (
                        id TEXT PRIMARY KEY,
                        title TEXT NOT NULL,
                        description TEXT,
                        cover_url TEXT,
                        created_at INTEGER
                    );
                """.trimIndent())

                stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS playlist_tracks (
                        playlist_id TEXT NOT NULL,
                        track_id TEXT NOT NULL,
                        title TEXT NOT NULL,
                        artist TEXT NOT NULL,
                        album TEXT,
                        duration_ms INTEGER,
                        artwork_url TEXT,
                        video_id TEXT,
                        stream_url TEXT,
                        position INTEGER,
                        PRIMARY KEY (playlist_id, track_id)
                    );
                """.trimIndent())

                stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS settings (
                        key TEXT PRIMARY KEY,
                        value TEXT
                    );
                """.trimIndent())
            }
        }
        seedDefaultPlaylistsIfEmpty()
    }

    private fun seedDefaultPlaylistsIfEmpty() {
        if (getPlaylists().isEmpty()) {
            val pl1 = createPlaylist("Hazy Valley", "Acoustic & warm melodies")
            val pl2 = createPlaylist("Velvet Whisper", "Deep night ambient tracks")

            val t1 = Track("seed_1", "Rap God", "Eminem", "The Marshall Mathers LP2", 363000L, "https://i.ytimg.com/vi/XbGs_qK2PQA/hqdefault.jpg")
            val t2 = Track("seed_2", "Lose Yourself", "Eminem", "8 Mile", 326000L, "https://i.ytimg.com/vi/_Yhyp_hAbW8/hqdefault.jpg")
            val t3 = Track("seed_3", "Thunderstruck", "AC/DC", "The Razors Edge", 292000L, "https://i.ytimg.com/vi/v2AC41dglnM/hqdefault.jpg")

            addTrackToPlaylist(pl1.id, t1)
            addTrackToPlaylist(pl1.id, t2)
            addTrackToPlaylist(pl2.id, t3)
        }
    }

    @Synchronized
    fun getSetting(key: String, defaultVal: String): String {
        getConnection().use { conn ->
            conn.prepareStatement("SELECT value FROM settings WHERE key = ?").use { ps ->
                ps.setString(1, key)
                ps.executeQuery().use { rs ->
                    if (rs.next()) return rs.getString("value")
                }
            }
        }
        return defaultVal
    }

    @Synchronized
    fun setSetting(key: String, value: String) {
        getConnection().use { conn ->
            conn.prepareStatement("INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)").use { ps ->
                ps.setString(1, key)
                ps.setString(2, value)
                ps.executeUpdate()
            }
        }
    }

    @Synchronized
    fun getLastFmUser(): String = getSetting("lastfm_user", "Rayyanbobs")

    @Synchronized
    fun setLastFmUser(username: String) = setSetting("lastfm_user", username)

    @Synchronized
    fun getFavorites(): List<Track> {
        val list = mutableListOf<Track>()
        getConnection().use { conn ->
            val sql = "SELECT * FROM favorites ORDER BY added_at DESC"
            conn.prepareStatement(sql).use { ps ->
                ps.executeQuery().use { rs ->
                    while (rs.next()) {
                        list.add(
                            Track(
                                id = rs.getString("id"),
                                title = rs.getString("title"),
                                artist = rs.getString("artist"),
                                album = rs.getString("album") ?: "",
                                durationMs = rs.getLong("duration_ms"),
                                artworkUrl = rs.getString("artwork_url"),
                                videoId = rs.getString("video_id"),
                                streamUrl = rs.getString("stream_url"),
                                isLossless = rs.getInt("is_lossless") == 1,
                                audioQuality = rs.getString("audio_quality") ?: "16-bit / 44.1 kHz"
                            )
                        )
                    }
                }
            }
        }
        return list
    }

    @Synchronized
    fun addFavorite(track: Track) {
        getConnection().use { conn ->
            val sql = """
                INSERT OR REPLACE INTO favorites 
                (id, title, artist, album, duration_ms, artwork_url, video_id, stream_url, is_lossless, audio_quality, added_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
            conn.prepareStatement(sql).use { ps ->
                ps.setString(1, track.id)
                ps.setString(2, track.title)
                ps.setString(3, track.artist)
                ps.setString(4, track.album)
                ps.setLong(5, track.durationMs)
                ps.setString(6, track.artworkUrl)
                ps.setString(7, track.videoId)
                ps.setString(8, track.streamUrl)
                ps.setInt(9, if (track.isLossless) 1 else 0)
                ps.setString(10, track.audioQuality)
                ps.setLong(11, System.currentTimeMillis())
                ps.executeUpdate()
            }
        }
    }

    @Synchronized
    fun removeFavorite(trackId: String) {
        getConnection().use { conn ->
            val sql = "DELETE FROM favorites WHERE id = ?"
            conn.prepareStatement(sql).use { ps ->
                ps.setString(1, trackId)
                ps.executeUpdate()
            }
        }
    }

    @Synchronized
    fun isFavorite(trackId: String): Boolean {
        getConnection().use { conn ->
            val sql = "SELECT 1 FROM favorites WHERE id = ? LIMIT 1"
            conn.prepareStatement(sql).use { ps ->
                ps.setString(1, trackId)
                ps.executeQuery().use { rs ->
                    return rs.next()
                }
            }
        }
    }

    @Synchronized
    fun getHistory(limit: Int = 50): List<Track> {
        val list = mutableListOf<Track>()
        getConnection().use { conn ->
            val sql = "SELECT * FROM history ORDER BY played_at DESC LIMIT ?"
            conn.prepareStatement(sql).use { ps ->
                ps.setInt(1, limit)
                ps.executeQuery().use { rs ->
                    while (rs.next()) {
                        list.add(
                            Track(
                                id = rs.getString("id"),
                                title = rs.getString("title"),
                                artist = rs.getString("artist"),
                                album = rs.getString("album") ?: "",
                                durationMs = rs.getLong("duration_ms"),
                                artworkUrl = rs.getString("artwork_url"),
                                videoId = rs.getString("video_id"),
                                streamUrl = rs.getString("stream_url"),
                                isLossless = rs.getInt("is_lossless") == 1,
                                audioQuality = rs.getString("audio_quality") ?: "16-bit / 44.1 kHz"
                            )
                        )
                    }
                }
            }
        }
        return list
    }

    @Synchronized
    fun addToHistory(track: Track) {
        getConnection().use { conn ->
            val sql = """
                INSERT INTO history 
                (id, title, artist, album, duration_ms, artwork_url, video_id, stream_url, is_lossless, audio_quality, played_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
            conn.prepareStatement(sql).use { ps ->
                ps.setString(1, track.id)
                ps.setString(2, track.title)
                ps.setString(3, track.artist)
                ps.setString(4, track.album)
                ps.setLong(5, track.durationMs)
                ps.setString(6, track.artworkUrl)
                ps.setString(7, track.videoId)
                ps.setString(8, track.streamUrl)
                ps.setInt(9, if (track.isLossless) 1 else 0)
                ps.setString(10, track.audioQuality)
                ps.setLong(11, System.currentTimeMillis())
                ps.executeUpdate()
            }
        }
    }

    @Synchronized
    fun getPlaylists(): List<Playlist> {
        val list = mutableListOf<Playlist>()
        getConnection().use { conn ->
            val sql = "SELECT * FROM playlists ORDER BY created_at DESC"
            conn.prepareStatement(sql).use { ps ->
                ps.executeQuery().use { rs ->
                    while (rs.next()) {
                        val playlistId = rs.getString("id")
                        val tracks = getPlaylistTracks(playlistId)
                        list.add(
                            Playlist(
                                id = playlistId,
                                title = rs.getString("title"),
                                description = rs.getString("description") ?: "",
                                trackCount = tracks.size,
                                coverUrl = rs.getString("cover_url") ?: tracks.firstOrNull()?.artworkUrl,
                                tracks = tracks,
                                createdAt = rs.getLong("created_at")
                            )
                        )
                    }
                }
            }
        }
        return list
    }

    @Synchronized
    fun createPlaylist(title: String, description: String = ""): Playlist {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        getConnection().use { conn ->
            val sql = "INSERT INTO playlists (id, title, description, created_at) VALUES (?, ?, ?, ?)"
            conn.prepareStatement(sql).use { ps ->
                ps.setString(1, id)
                ps.setString(2, title)
                ps.setString(3, description)
                ps.setLong(4, now)
                ps.executeUpdate()
            }
        }
        return Playlist(id = id, title = title, description = description, createdAt = now)
    }

    @Synchronized
    fun deletePlaylist(playlistId: String) {
        getConnection().use { conn ->
            conn.prepareStatement("DELETE FROM playlists WHERE id = ?").use {
                it.setString(1, playlistId)
                it.executeUpdate()
            }
            conn.prepareStatement("DELETE FROM playlist_tracks WHERE playlist_id = ?").use {
                it.setString(1, playlistId)
                it.executeUpdate()
            }
        }
    }

    @Synchronized
    fun addTrackToPlaylist(playlistId: String, track: Track) {
        getConnection().use { conn ->
            val countSql = "SELECT COUNT(*) FROM playlist_tracks WHERE playlist_id = ?"
            var pos = 0
            conn.prepareStatement(countSql).use { ps ->
                ps.setString(1, playlistId)
                ps.executeQuery().use { rs -> if (rs.next()) pos = rs.getInt(1) }
            }

            val sql = """
                INSERT OR REPLACE INTO playlist_tracks 
                (playlist_id, track_id, title, artist, album, duration_ms, artwork_url, video_id, stream_url, position)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
            conn.prepareStatement(sql).use { ps ->
                ps.setString(1, playlistId)
                ps.setString(2, track.id)
                ps.setString(3, track.title)
                ps.setString(4, track.artist)
                ps.setString(5, track.album)
                ps.setLong(6, track.durationMs)
                ps.setString(7, track.artworkUrl)
                ps.setString(8, track.videoId)
                ps.setString(9, track.streamUrl)
                ps.setInt(10, pos)
                ps.executeUpdate()
            }
        }
    }

    @Synchronized
    fun getPlaylistTracks(playlistId: String): List<Track> {
        val list = mutableListOf<Track>()
        getConnection().use { conn ->
            val sql = "SELECT * FROM playlist_tracks WHERE playlist_id = ? ORDER BY position ASC"
            conn.prepareStatement(sql).use { ps ->
                ps.setString(1, playlistId)
                ps.executeQuery().use { rs ->
                    while (rs.next()) {
                        list.add(
                            Track(
                                id = rs.getString("track_id"),
                                title = rs.getString("title"),
                                artist = rs.getString("artist"),
                                album = rs.getString("album") ?: "",
                                durationMs = rs.getLong("duration_ms"),
                                artworkUrl = rs.getString("artwork_url"),
                                videoId = rs.getString("video_id"),
                                streamUrl = rs.getString("stream_url"),
                                isLossless = false,
                                audioQuality = "Lossless"
                            )
                        )
                    }
                }
            }
        }
        return list
    }
}
