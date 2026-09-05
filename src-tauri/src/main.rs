#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

mod youtube;
mod spotify;
mod ytdlp_updater;
mod recommend;

use tauri::Manager;

fn main() {
    if std::env::var("SONARA_TRACE").map(|v| v == "1").unwrap_or(false) {
        let _ = tracing_subscriber::fmt()
            .with_env_filter("sonara_stream=trace")
            .try_init();
    }

    tauri::Builder::default()
        .plugin(tauri_plugin_updater::Builder::new().build())
        .plugin(tauri_plugin_process::init())
        .setup(|app| {
            if let Ok(app_data) = app.path().app_data_dir() {
                let manager = ytdlp_updater::init_manager(app_data.clone());
                recommend::init_recommendations(app_data);
                // Background update check on startup (non-blocking, delayed by 3s to not affect initial render)
                tauri::async_runtime::spawn(async move {
                    tokio::time::sleep(std::time::Duration::from_secs(3)).await;
                    let _ = manager.check_and_update(false).await;
                });
            }
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            youtube::search_youtube,
            youtube::get_stream_url,
            youtube::cancel_stream_request,
            youtube::get_search_suggestions,
            youtube::get_related_tracks,
            youtube::get_genre_mix,
            spotify::resolve_spotify_track,
            ytdlp_updater::get_ytdlp_status,
            ytdlp_updater::check_ytdlp_update,
            ytdlp_updater::set_ytdlp_auto_update,
            recommend::get_recommendations,
            recommend::build_radio,
            recommend::record_play_event,
        ])
        .run(tauri::generate_context!())
        .expect("error while running sonara-stream application");
}

