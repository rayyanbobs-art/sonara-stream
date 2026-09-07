use rusqlite::Connection;
use std::fs;
use std::path::PathBuf;
use tauri::{AppHandle, Manager};

pub fn get_connection(app_handle: &AppHandle) -> Result<Connection, rusqlite::Error> {
    let db_path: PathBuf = if cfg!(all(debug_assertions, not(any(target_os = "android", target_os = "ios")))) {
        // Save in the project root directory during desktop dev
        match std::env::current_dir() {
            Ok(dir) => dir.join("dev_database.db"),
            Err(_) => {
                let app_dir = app_handle
                    .path()
                    .app_data_dir()
                    .unwrap_or_else(|_| PathBuf::from("."));
                let _ = fs::create_dir_all(&app_dir);
                app_dir.join("dev_database.db")
            }
        }
    } else {
        // Save in the proper system Application Data folder
        let app_dir = app_handle
            .path()
            .app_data_dir()
            .unwrap_or_else(|_| {
                #[cfg(target_os = "android")]
                {
                    PathBuf::from("/data/data/com.sonara.stream/files")
                }
                #[cfg(not(target_os = "android"))]
                {
                    PathBuf::from(".")
                }
            });
        let _ = fs::create_dir_all(&app_dir);
        app_dir.join("app_database.db")
    };

    Connection::open(db_path)
}
