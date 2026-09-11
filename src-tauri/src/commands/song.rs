use tauri::State;

use crate::{
    services::{self},
    DbState,
};

// get all songs
#[tauri::command]
pub fn get_all_songs(db: State<DbState>) -> Result<Vec<crate::models::song::SongResponse>, String> {
    let conn = db.0.lock().map_err(|e| e.to_string())?;

    services::song_service::get_all_songs(&conn).map_err(|e| e.to_string())
}

// get song by id
#[tauri::command]
pub fn get_song_by_id(
    db: State<DbState>,
    id: i64,
) -> Result<crate::models::song::SongResponse, String> {
    let conn = db.0.lock().map_err(|e| e.to_string())?;
    services::song_service::get_song_by_id(&conn, id).map_err(|e| e.to_string())
}

// get songs by search query
#[tauri::command]
pub fn get_songs_by_search(
    db: State<DbState>,
    query: String,
) -> Result<Vec<crate::models::song::SongResponse>, String> {
    let conn = db.0.lock().map_err(|e| e.to_string())?;
    services::song_service::get_songs_by_search(&conn, &query).map_err(|e| e.to_string())
}

// update song metadata
#[tauri::command]
pub fn update_song_metadata(
    db: State<DbState>,
    id: i64,
    title: &str,
    album_name: &str,
    artist_name: &str,
    album_artist_name: &str,
    track_number: Option<i32>,
) -> Result<(), String> {
    let mut conn = db.0.lock().map_err(|e| e.to_string())?;
    services::song_service::update_song_info(
        &mut conn,
        id,
        title,
        album_name,
        artist_name,
        album_artist_name,
        track_number,
    )
    .map_err(|e| e.to_string())
}

// get favorite songs
#[tauri::command]
pub fn get_favorite_songs(
    db: State<DbState>,
) -> Result<Vec<crate::models::song::SongResponse>, String> {
    let conn = db.0.lock().map_err(|e| e.to_string())?;
    services::song_service::get_favorite_songs(&conn).map_err(|e| e.to_string())
}

// set favorite song
#[tauri::command]
pub fn set_favorite_song(
    db: State<DbState>,
    song_id: i64,
    is_favorite: bool,
) -> Result<String, String> {
    let conn = db.0.lock().map_err(|e| e.to_string())?;
    services::song_service::set_favorite_song(&conn, song_id, is_favorite)
        .map_err(|e| e.to_string())
}

// record song play
#[tauri::command]
pub fn record_song_play(db: State<DbState>, song_id: i64) -> Result<(), String> {
    let conn = db.0.lock().map_err(|e| e.to_string())?;
    services::song_service::record_song_play(&conn, song_id).map_err(|e| e.to_string())
}

#[derive(serde::Deserialize)]
pub struct OnlineFavoriteInput {
    pub id: String,
    pub title: String,
    pub artist: String,
    pub duration: i64,
    pub thumbnail: Option<String>,
    pub is_favorite: bool,
}

// toggle or create favorite for online track
#[tauri::command]
pub fn toggle_online_favorite(
    db: State<DbState>,
    input: OnlineFavoriteInput,
) -> Result<crate::models::song::SongResponse, String> {
    let conn = db.0.lock().map_err(|e| e.to_string())?;
    services::song_service::toggle_online_favorite(
        &conn,
        &input.id,
        &input.title,
        &input.artist,
        input.duration,
        input.thumbnail.as_deref(),
        input.is_favorite,
    ).map_err(|e| e.to_string())
}
// read audio file directly into binary response for zero-overhead local playback
#[tauri::command]
pub async fn read_audio_file(path: String) -> Result<tauri::ipc::Response, String> {
    let clean_path = if let Some(stripped) = path.strip_prefix("file://") {
        stripped.to_string()
    } else {
        path
    };

    let path_ref = std::path::Path::new(&clean_path);
    let valid_ext = path_ref
        .extension()
        .and_then(|ext| ext.to_str())
        .map(|ext| {
            matches!(
                ext.to_ascii_lowercase().as_str(),
                "m4a" | "mp3" | "flac" | "wav" | "ogg" | "opus" | "aac" | "webm"
            )
        })
        .unwrap_or(false);

    if !valid_ext {
        return Err(format!("Invalid or disallowed audio file extension for '{}'", clean_path));
    }

    let bytes = tokio::fs::read(&clean_path)
        .await
        .map_err(|e| format!("Failed to read audio file at '{}': {}", clean_path, e))?;
    Ok(tauri::ipc::Response::new(bytes))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_read_audio_file_success() {
        let temp_dir = std::env::temp_dir();
        let test_file = temp_dir.join(format!("test_audio_{}.m4a", std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap().as_nanos()));
        let sample_bytes = b"ftypM4A \x00\x00\x00\x00M4A mp42isom\x00\x00\x00\x08mdattestaudiopayload";
        tokio::fs::write(&test_file, sample_bytes).await.unwrap();

        let path_str = test_file.to_string_lossy().to_string();
        let res = read_audio_file(path_str).await;
        match res {
            Ok(_) => (),
            Err(e) => panic!("Expected Ok, got error: {e}"),
        }

        let _ = tokio::fs::remove_file(&test_file).await;
    }

    #[tokio::test]
    async fn test_read_audio_file_not_found() {
        let nonexistent = "C:/path/does/not/exist/track_never_exists.m4a".to_string();
        let res = read_audio_file(nonexistent).await;
        match res {
            Err(e) => assert!(e.contains("Failed to read audio file")),
            Ok(_) => panic!("Expected error for nonexistent file"),
        }
    }

    #[tokio::test]
    async fn test_read_audio_file_empty() {
        let temp_dir = std::env::temp_dir();
        let test_file = temp_dir.join(format!("test_empty_{}.mp3", std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap().as_nanos()));
        tokio::fs::write(&test_file, b"").await.unwrap();

        let path_str = test_file.to_string_lossy().to_string();
        let res = read_audio_file(path_str).await;
        match res {
            Ok(_) => (),
            Err(e) => panic!("Expected Ok, got error: {e}"),
        }

        let _ = tokio::fs::remove_file(&test_file).await;
    }

    #[tokio::test]
    async fn test_read_audio_file_large_payload() {
        let temp_dir = std::env::temp_dir();
        let test_file = temp_dir.join(format!("test_large_{}.m4a", std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap().as_nanos()));
        let large_payload = vec![0xABu8; 1024 * 1024 * 4]; // 4 MB simulated audio file
        tokio::fs::write(&test_file, &large_payload).await.unwrap();

        let path_str = test_file.to_string_lossy().to_string();
        let res = read_audio_file(path_str).await;
        match res {
            Ok(_) => (),
            Err(e) => panic!("Expected Ok, got error: {e}"),
        }

        let _ = tokio::fs::remove_file(&test_file).await;
    }

    #[tokio::test]
    async fn test_read_audio_file_file_uri_prefix() {
        let temp_dir = std::env::temp_dir();
        let test_file = temp_dir.join(format!("test_uri_{}.m4a", std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap().as_nanos()));
        tokio::fs::write(&test_file, b"sample data").await.unwrap();

        let path_str = format!("file://{}", test_file.to_string_lossy());
        let res = read_audio_file(path_str).await;
        match res {
            Ok(_) => (),
            Err(e) => panic!("Expected Ok, got error: {e}"),
        }

        let _ = tokio::fs::remove_file(&test_file).await;
    }

    #[tokio::test]
    async fn test_read_audio_file_unicode_and_spaces() {
        let temp_dir = std::env::temp_dir();
        let test_file = temp_dir.join(format!("Artist ♫ - Title [test_{}].m4a", std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap().as_nanos()));
        tokio::fs::write(&test_file, b"unicode payload").await.unwrap();

        let path_str = test_file.to_string_lossy().to_string();
        let res = read_audio_file(path_str).await;
        match res {
            Ok(_) => (),
            Err(e) => panic!("Expected Ok, got error: {e}"),
        }

        let _ = tokio::fs::remove_file(&test_file).await;
    }
}

