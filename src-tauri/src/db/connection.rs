use rusqlite::Connection;
use std::fs;
use std::path::PathBuf;
use std::time::Duration;
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

    let conn = Connection::open(db_path)?;
    conn.busy_timeout(Duration::from_secs(5))?;
    let _ = conn.pragma_update(None, "journal_mode", "WAL");
    let _ = conn.pragma_update(None, "synchronous", "NORMAL");
    Ok(conn)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_wal_mode_and_busy_timeout() {
        let temp_dir = std::env::temp_dir();
        let db_file = temp_dir.join(format!(
            "test_wal_{}.db",
            std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .unwrap()
                .as_nanos()
        ));

        let conn = Connection::open(&db_file).unwrap();
        conn.busy_timeout(Duration::from_secs(5)).unwrap();
        conn.pragma_update(None, "journal_mode", "WAL").unwrap();
        conn.pragma_update(None, "synchronous", "NORMAL").unwrap();

        let mode: String = conn
            .query_row("PRAGMA journal_mode", [], |row| row.get(0))
            .unwrap();
        assert_eq!(mode.to_lowercase(), "wal");

        let timeout: i64 = conn
            .query_row("PRAGMA busy_timeout", [], |row| row.get(0))
            .unwrap();
        assert_eq!(timeout, 5000);

        let _ = std::fs::remove_file(&db_file);
        let _ = std::fs::remove_file(format!("{}-wal", db_file.display()));
        let _ = std::fs::remove_file(format!("{}-shm", db_file.display()));
    }
}
