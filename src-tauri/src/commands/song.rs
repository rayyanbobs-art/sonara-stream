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

