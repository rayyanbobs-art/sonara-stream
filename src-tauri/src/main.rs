#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

mod youtube;
mod spotify;

fn main() {
    tauri::Builder::default()
        .invoke_handler(tauri::generate_handler![
            youtube::search_youtube,
            youtube::get_stream_url,
            spotify::resolve_spotify_track,
        ])
        .run(tauri::generate_context!())
        .expect("error while running sonara-stream application");
}
