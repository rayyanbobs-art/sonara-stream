use rusqlite::{params, Connection};

use crate::models::song::SongResponse;

fn song_from_row(row: &rusqlite::Row) -> rusqlite::Result<SongResponse> {
    Ok(SongResponse {
        id: row.get("id")?,
        title: row.get("title")?,
        duration: row.get("duration")?,
        path: row.get("path")?,
        is_favorite: row.get::<_, i32>("is_favorite")? != 0,
        favorite_added_at: row.get("favorite_added_at")?,
        track_number: row.get("track_number")?,
        last_played_at: row.get("last_played_at")?,
        play_count: row.get("play_count")?,
        created_at: row.get("created_at")?,
        file_modified_at: row.get("file_modified_at")?,
        file_size: row.get("file_size")?,
        folder_id: row.get("folder_id")?,
        album_id: row.get("album_id")?,
        artist_id: row.get("artist_id")?,
        artist_name: row.get::<_, Option<String>>("artist_name")?.unwrap_or_else(|| "Unknown Artist".to_string()),
        album_name: row.get::<_, Option<String>>("album_name")?.unwrap_or_else(|| "Unknown Album".to_string()),
        album_cover_path: row.get("album_cover_path")?,
        album_artist_name: row.get::<_, Option<String>>("album_artist_name")?.unwrap_or_else(|| "Unknown Artist".to_string()),
    })
}

// get all the songs from the database
pub fn index(conn: &Connection) -> rusqlite::Result<Vec<SongResponse>> {
    let mut stmt = conn.prepare(
        "
            SELECT 
                s.*,
                a.name AS artist_name,
                al.name AS album_name,
                al.cover_path AS album_cover_path,
                aa.name AS album_artist_name
            FROM songs s
            LEFT JOIN artists a ON s.artist_id = a.id
            LEFT JOIN albums al ON s.album_id = al.id
            LEFT JOIN artists aa ON al.artist_id = aa.id
            ORDER BY s.created_at DESC;
    ",
    )?;
    let song_iter = stmt.query_map([], song_from_row)?;

    
    song_iter.collect()
}

// get songs by album id
pub fn get_by_album(conn: &Connection, album_id: i64) -> rusqlite::Result<Vec<SongResponse>> {
    let mut stmt = conn.prepare(
        "
    SELECT s.*, a.name as artist_name, al.name as album_name, al.cover_path as album_cover_path, aa.name as album_artist_name
    FROM songs s
    LEFT JOIN artists a ON s.artist_id = a.id
    LEFT JOIN albums al ON s.album_id = al.id
    LEFT JOIN artists aa ON al.artist_id = aa.id
    WHERE s.album_id = ?1
    ORDER BY s.track_number ASC, s.title ASC
    ",
    )?;
    let song_iter = stmt.query_map(params![album_id], song_from_row)?;

    
    song_iter.collect()
}

// get songs by artist id
pub fn get_by_artist(conn: &Connection, artist_id: i64) -> rusqlite::Result<Vec<SongResponse>> {
    let mut stmt = conn.prepare(
        "
    SELECT s.*, a.name as artist_name, al.name as album_name, al.cover_path as album_cover_path, aa.name as album_artist_name
    FROM songs s
    LEFT JOIN artists a ON s.artist_id = a.id
    LEFT JOIN albums al ON s.album_id = al.id
    LEFT JOIN artists aa ON al.artist_id = aa.id
    WHERE s.artist_id = ?1
    ",
    )?;
    let song_iter = stmt.query_map(params![artist_id], song_from_row)?;

    
    song_iter.collect()
}

// get songs by playlist id
pub fn get_by_playlist(conn: &Connection, playlist_id: i64) -> rusqlite::Result<Vec<SongResponse>> {
    let mut stmt = conn.prepare(
        "SELECT s.*, a.name as artist_name, al.name as album_name, al.cover_path as album_cover_path, aa.name as album_artist_name FROM songs s
         INNER JOIN playlist_songs ps ON s.id = ps.song_id
         LEFT JOIN artists a ON s.artist_id = a.id
         LEFT JOIN albums al ON s.album_id = al.id
         LEFT JOIN artists aa ON al.artist_id = aa.id
         WHERE ps.playlist_id = ?1
         ORDER BY ps.created_at ASC
         ",
    )?;
    let song_iter = stmt.query_map(params![playlist_id], song_from_row)?;

    
    song_iter.collect()
}

pub struct SongRecord {
    pub id: i64,
    pub path: String,
    pub file_modified_at: i64,
    pub file_size: i64,
}

// get songs by folder path
pub fn get_by_folder(conn: &Connection, folder_id: i64) -> rusqlite::Result<Vec<SongRecord>> {
    let mut stmt = conn.prepare(
        "
    SELECT id, path, file_modified_at, file_size
    FROM songs
    WHERE folder_id = ?1
    ",
    )?;
    let song_iter = stmt.query_map(params![folder_id], |row| {
        Ok(SongRecord {
            id: row.get(0)?,
            path: row.get(1)?,
            file_modified_at: row.get(2)?,
            file_size: row.get(3)?,
        })
    })?;

    
    song_iter.collect()
}

// get a song by id
pub fn get(conn: &Connection, id: i64) -> rusqlite::Result<SongResponse> {
    let mut stmt = conn.prepare(
        "
    SELECT s.*, a.name as artist_name, al.name as album_name, al.cover_path as album_cover_path, aa.name as album_artist_name
    FROM songs s
    LEFT JOIN artists a ON s.artist_id = a.id
    LEFT JOIN albums al ON s.album_id = al.id
    LEFT JOIN artists aa ON al.artist_id = aa.id
    WHERE s.id = ?1
    ",
    )?;
    let song = stmt.query_row(params![id], song_from_row)?;
    Ok(song)
}

// create a new song
#[allow(clippy::too_many_arguments)]
pub fn create(
    conn: &Connection,
    title: &str,
    duration: i64,
    path: &str,
    track_number: Option<i32>,
    folder_id: i64,
    album_id: i64,
    artist_id: i64,
    file_modified_at: i64,
    file_size: i64,
) -> rusqlite::Result<()> {
    let created_at = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap()
        .as_secs() as i64;

    conn.execute(
        "INSERT INTO songs (title, duration, path, track_number, created_at, folder_id, album_id, artist_id, file_modified_at, file_size) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10)",
        params![title, duration, path, track_number, created_at, folder_id, album_id, artist_id, file_modified_at, file_size],
    )?;
    Ok(())
}

// update song
#[allow(clippy::too_many_arguments)]
pub fn update(
    conn: &Connection,
    id: i64,
    title: &str,
    duration: i64,
    path: &str,
    track_number: Option<i32>,
    folder_id: i64,
    album_id: i64,
    artist_id: i64,
    file_modified_at: i64,
    file_size: i64,
) -> rusqlite::Result<()> {
    conn.execute(
        "UPDATE songs SET title = ?1, duration = ?2, path = ?3, track_number = ?4, folder_id = ?5, album_id = ?6, artist_id = ?7, file_modified_at = ?8, file_size = ?9 WHERE id = ?10",
        params![title, duration, path, track_number, folder_id, album_id, artist_id, file_modified_at, file_size, id],
    )?;
    Ok(())
}

// Update song information by id
pub fn update_info(
    conn: &Connection,
    id: i64,
    title: &str,
    album_id: i64,
    artist_id: i64,
    track_number: Option<i32>,
) -> rusqlite::Result<()> {
    conn.execute(
        "UPDATE songs SET title = ?1, album_id = ?2, artist_id = ?3, track_number = ?4 WHERE id = ?5",
        params![title, album_id, artist_id, track_number, id],
    )?;
    Ok(())
}

// delete song by id
pub fn delete(conn: &Connection, id: i64) -> rusqlite::Result<()> {
    conn.execute("DELETE FROM songs WHERE id = ?1", params![id])?;
    Ok(())
}

// search
pub fn search(conn: &Connection, query: &str) -> rusqlite::Result<Vec<SongResponse>> {
    let mut stmt = conn.prepare(
        "
    SELECT s.*, a.name as artist_name, al.name as album_name, al.cover_path as album_cover_path, aa.name as album_artist_name
    FROM songs s
    LEFT JOIN artists a ON s.artist_id = a.id
    LEFT JOIN albums al ON s.album_id = al.id
    LEFT JOIN artists aa ON al.artist_id = aa.id
    WHERE s.title LIKE ?1
    ",
    )?;
    let song_iter = stmt.query_map(params![format!("%{}%", query)], song_from_row)?;

    
    song_iter.collect()
}

// update favorite status of a song by id
pub fn update_favorite_status(
    conn: &Connection,
    id: i64,
    is_favorite: bool,
) -> rusqlite::Result<String> {
    let favorite_added_at = if is_favorite {
        Some(
            std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .unwrap()
                .as_secs() as i64,
        )
    } else {
        None
    };

    conn.execute(
        "UPDATE songs SET is_favorite = ?1, favorite_added_at = ?2 WHERE id = ?3",
        params![is_favorite as i32, favorite_added_at, id],
    )?;
    Ok(if is_favorite {
        "Song marked as favorite".to_string()
    } else {
        "Song removed from favorites".to_string()
    })
}

// get lyrics by song id
// pub fn get_lyrics(conn: &Connection, id: i64) -> rusqlite::Result<Option<String>> {
//     // get song by id
//     // check if lyrics_path, return lyrics_path if exists,
//     // else check lyrics_status, status == "not_found", return null
//     // else go file_service to get lyrics from api, create file and save, update lyrics_path and lyrics_status, return lyrics_path
//     let song: SongResponse = song_repository::get(conn, id)?;

//     if let Some(path) = song.lyrics_path {
//         return Ok(Some(path));
//     }
// }

// update last played at and play count of a song by id
pub fn record_play(conn: &Connection, id: i64) -> rusqlite::Result<()> {
    let last_played_at = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap()
        .as_secs() as i64;

    conn.execute(
        "UPDATE songs SET last_played_at = ?1, play_count = play_count + 1 WHERE id = ?2",
        params![last_played_at, id],
    )?;
    Ok(())
}

// get all favorite songs
pub fn get_favorites(conn: &Connection) -> rusqlite::Result<Vec<SongResponse>> {
    let mut stmt = conn.prepare(
        "
    SELECT s.*, a.name as artist_name, al.name as album_name, al.cover_path as album_cover_path, aa.name as album_artist_name
    FROM songs s
    LEFT JOIN artists a ON s.artist_id = a.id
    LEFT JOIN albums al ON s.album_id = al.id
    LEFT JOIN artists aa ON al.artist_id = aa.id
    WHERE s.is_favorite = 1
    ORDER BY s.favorite_added_at DESC
    ",
    )?;
    let song_iter = stmt.query_map([], song_from_row)?;

    
    song_iter.collect()
}

// get recently played songs
pub fn get_recently_played(conn: &Connection, limit: usize) -> rusqlite::Result<Vec<SongResponse>> {
    let mut stmt = conn.prepare(
        "
    SELECT s.*, a.name as artist_name, al.name as album_name, al.cover_path as album_cover_path, aa.name as album_artist_name
    FROM songs s
    LEFT JOIN artists a ON s.artist_id = a.id
    LEFT JOIN albums al ON s.album_id = al.id
    LEFT JOIN artists aa ON al.artist_id = aa.id
    WHERE s.last_played_at IS NOT NULL
    ORDER BY s.last_played_at DESC
    LIMIT ?1
    ",
    )?;
    let song_iter = stmt.query_map([limit], song_from_row)?;

    
    song_iter.collect()
}

// get recently added songs
pub fn get_recently_added(conn: &Connection, limit: usize) -> rusqlite::Result<Vec<SongResponse>> {
    let mut stmt = conn.prepare(
        "
    SELECT s.*, a.name as artist_name, al.name as album_name, al.cover_path as album_cover_path, aa.name as album_artist_name
    FROM songs s
    LEFT JOIN artists a ON s.artist_id = a.id
    LEFT JOIN albums al ON s.album_id = al.id
    LEFT JOIN artists aa ON al.artist_id = aa.id
    ORDER BY s.created_at DESC
    LIMIT ?1
    ",
    )?;
    let song_iter = stmt.query_map([limit], song_from_row)?;

    
    song_iter.collect()
}

// get most played songs
pub fn get_most_played(conn: &Connection, limit: usize) -> rusqlite::Result<Vec<SongResponse>> {
    let mut stmt = conn.prepare(
        "
    SELECT s.*, a.name as artist_name, al.name as album_name, al.cover_path as album_cover_path, aa.name as album_artist_name
    FROM songs s
    LEFT JOIN artists a ON s.artist_id = a.id
    LEFT JOIN albums al ON s.album_id = al.id
    LEFT JOIN artists aa ON al.artist_id = aa.id
    WHERE s.play_count > 0
    ORDER BY s.play_count DESC, s.last_played_at DESC
    LIMIT ?1
    ",
    )?;
    let song_iter = stmt.query_map([limit], song_from_row)?;

    
    song_iter.collect()
}

// get song by id
pub fn get_by_id(conn: &Connection, id: i64) -> rusqlite::Result<SongResponse> {
    let mut stmt = conn.prepare(
        "
        SELECT 
            s.*,
            a.name AS artist_name,
            al.name AS album_name,
            al.cover_path AS album_cover_path,
            aa.name AS album_artist_name
        FROM songs s
        LEFT JOIN artists a ON s.artist_id = a.id
        LEFT JOIN albums al ON s.album_id = al.id
        LEFT JOIN artists aa ON al.artist_id = aa.id
        WHERE s.id = ?1;
        ",
    )?;
    stmt.query_row(params![id], song_from_row)
}

// get song by path
pub fn get_by_path(conn: &Connection, path: &str) -> rusqlite::Result<Option<SongResponse>> {
    let mut stmt = conn.prepare(
        "
        SELECT 
            s.*,
            a.name AS artist_name,
            al.name AS album_name,
            al.cover_path AS album_cover_path,
            aa.name AS album_artist_name
        FROM songs s
        LEFT JOIN artists a ON s.artist_id = a.id
        LEFT JOIN albums al ON s.album_id = al.id
        LEFT JOIN artists aa ON al.artist_id = aa.id
        WHERE s.path = ?1;
        ",
    )?;
    match stmt.query_row(params![path], song_from_row) {
        Ok(song) => Ok(Some(song)),
        Err(rusqlite::Error::QueryReturnedNoRows) => Ok(None),
        Err(e) => Err(e),
    }
}

// toggle or create favorite for online track
pub fn toggle_online_favorite(
    conn: &Connection,
    id: &str,
    title: &str,
    artist: &str,
    duration: i64,
    thumbnail: Option<&str>,
    is_favorite: bool,
) -> rusqlite::Result<SongResponse> {
    let online_path = format!("online://youtube/{}", id);
    if let Some(existing) = get_by_path(conn, &online_path)? {
        update_favorite_status(conn, existing.id, is_favorite)?;
        get_by_id(conn, existing.id)
    } else {
        let (artist_id, _) = crate::repositories::artist_repository::find_or_create(conn, artist)?;
        let (album_id, _) = crate::repositories::album_repository::find_or_create(conn, "YouTube Music", artist_id)?;
        if let Some(thumb) = thumbnail {
            if !thumb.is_empty() {
                let _ = crate::repositories::album_repository::update_cover_path(conn, album_id, thumb, "found");
            }
        }
        let now = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap()
            .as_secs() as i64;
        conn.execute(
            "INSERT INTO songs (title, duration, path, is_favorite, favorite_added_at, track_number, created_at, folder_id, album_id, artist_id, file_modified_at, file_size)
             VALUES (?1, ?2, ?3, ?4, ?5, 1, ?6, NULL, ?7, ?8, ?6, 0)",
            params![
                title,
                duration,
                online_path,
                is_favorite as i32,
                if is_favorite { Some(now) } else { None },
                now,
                album_id,
                artist_id
            ],
        )?;
        let new_id = conn.last_insert_rowid();
        get_by_id(conn, new_id)
    }
}
