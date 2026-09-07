use tauri::{AppHandle, Manager, State};
use std::fs;
use crate::DbState;
use crate::models::song::SongResponse;

#[derive(serde::Deserialize)]
pub struct DownloadTrackInput {
    pub id: String,
    pub title: String,
    pub artist: String,
    pub album: Option<String>,
    pub thumbnail: Option<String>,
    pub duration: i64,
}

fn resolve_downloads_dir(app_handle: &AppHandle) -> std::path::PathBuf {
    let app_dir = app_handle.path().app_data_dir().unwrap_or_else(|_| {
        #[cfg(target_os = "android")]
        {
            std::path::PathBuf::from("/data/data/com.sonara.stream/files")
        }
        #[cfg(not(target_os = "android"))]
        {
            std::path::PathBuf::from(".")
        }
    });
    app_dir.join("downloads")
}

#[tauri::command]
pub async fn download_online_track(
    app_handle: AppHandle,
    db: State<'_, DbState>,
    input: DownloadTrackInput,
) -> Result<SongResponse, String> {
    // 1. Resolve direct stream URL
    let stream_url = crate::youtube::resolve_stream_url_internal(input.id.clone(), None, std::time::Duration::from_secs(25)).await?;

    // 2. Prepare downloads directory
    let downloads_dir = resolve_downloads_dir(&app_handle);
    if !downloads_dir.exists() {
        fs::create_dir_all(&downloads_dir)
            .map_err(|e| format!("Failed to create downloads directory: {}", e))?;
    }

    // 3. Clean filenames
    let safe_artist: String = input.artist
        .chars()
        .map(|c| if r#"\/:*?"<>|"#.contains(c) { '_' } else { c })
        .collect();
    let safe_title: String = input.title
        .chars()
        .map(|c| if r#"\/:*?"<>|"#.contains(c) { '_' } else { c })
        .collect();
    let file_base = format!("{} - {} [{}]", safe_artist.trim(), safe_title.trim(), input.id.trim());
    let audio_file_name = format!("{}.m4a", file_base);
    let dest_audio_path = downloads_dir.join(&audio_file_name);

    // 4. Download audio stream
    let client = crate::youtube::get_http_client();
    let resp = client
        .get(&stream_url)
        .send()
        .await
        .map_err(|e| format!("Audio stream request failed: {}", e))?;
    if !resp.status().is_success() {
        return Err(format!("Download stream returned status {}", resp.status()));
    }
    let bytes = resp.bytes().await.map_err(|e| format!("Failed to read stream bytes: {}", e))?;
    let file_size = bytes.len() as i64;
    fs::write(&dest_audio_path, &bytes).map_err(|e| format!("Failed to write audio file: {}", e))?;

    // 5. Download artwork if available
    let mut local_cover_path: Option<String> = None;
    if let Some(ref thumb_url) = input.thumbnail {
        if thumb_url.starts_with("http") {
            let cover_file_name = format!("{}.jpg", file_base);
            let dest_cover_path = downloads_dir.join(&cover_file_name);
            if let Ok(cover_resp) = client.get(thumb_url).send().await {
                if let Ok(cover_bytes) = cover_resp.bytes().await {
                    if fs::write(&dest_cover_path, &cover_bytes).is_ok() {
                        local_cover_path = Some(dest_cover_path.to_string_lossy().to_string());
                    }
                }
            }
        }
    }

    // 6. Update database record
    let conn = db.0.lock().map_err(|e| e.to_string())?;

    let (artist_id, _) = crate::repositories::artist_repository::find_or_create(&conn, &input.artist)
        .map_err(|e| e.to_string())?;

    let album_name = input.album.unwrap_or_else(|| "Downloads".to_string());
    let (album_id, _) = crate::repositories::album_repository::find_or_create(&conn, &album_name, artist_id)
        .map_err(|e| e.to_string())?;

    if let Some(ref cover) = local_cover_path {
        let _ = crate::repositories::album_repository::update_cover_path(&conn, album_id, cover, "found");
    }

    let now = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap()
        .as_secs() as i64;

    let local_path = dest_audio_path.to_string_lossy().to_string();
    let online_path = format!("online://youtube/{}", input.id);

    // If an online entry for this track already existed, update its path to local offline path!
    let updated_rows = conn.execute(
        "UPDATE songs 
         SET path = ?1, file_size = ?2, file_modified_at = ?3, album_id = ?4, artist_id = ?5 
         WHERE path = ?6",
        rusqlite::params![local_path, file_size, now, album_id, artist_id, online_path],
    ).unwrap_or(0);

    let final_id = if updated_rows > 0 {
        conn.query_row("SELECT id FROM songs WHERE path = ?1", rusqlite::params![local_path], |r| r.get::<_, i64>(0))
            .map_err(|e| e.to_string())?
    } else {
        conn.execute(
            "INSERT INTO songs (title, duration, path, is_favorite, favorite_added_at, track_number, created_at, folder_id, album_id, artist_id, file_modified_at, file_size)
             VALUES (?1, ?2, ?3, 0, NULL, 1, ?4, NULL, ?5, ?6, ?4, ?7)",
            rusqlite::params![input.title, input.duration, local_path, now, album_id, artist_id, file_size],
        ).map_err(|e| format!("Failed to insert downloaded song into DB: {}", e))?;
        conn.last_insert_rowid()
    };

    crate::repositories::song_repository::get_by_id(&conn, final_id).map_err(|e| e.to_string())
}

#[tauri::command]
pub fn is_track_downloaded(
    app_handle: AppHandle,
    id: String,
) -> Result<bool, String> {
    let downloads_dir = resolve_downloads_dir(&app_handle);
    if !downloads_dir.exists() {
        return Ok(false);
    }
    if let Ok(entries) = fs::read_dir(downloads_dir) {
        for entry in entries.flatten() {
            let name = entry.file_name().to_string_lossy().to_string();
            if name.contains(&format!("[{}]", id)) {
                return Ok(true);
            }
        }
    }
    Ok(false)
}
