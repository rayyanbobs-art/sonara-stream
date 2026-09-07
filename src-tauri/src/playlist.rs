//! Playlist storage and management service for Sonara Stream.
//!
//! Uses atomic JSON file persistence (`playlists.json`) in `app_data_dir`,
//! ensuring safe writes without external native C database dependencies.

use crate::youtube::Track;
use serde::{Deserialize, Serialize};
use std::fs;
use std::path::PathBuf;
use std::sync::{Mutex, OnceLock};
use std::time::{SystemTime, UNIX_EPOCH};

#[derive(Debug, Serialize, Deserialize, Clone, PartialEq)]
pub struct Playlist {
    pub id: String,
    pub name: String,
    pub tracks: Vec<Track>,
    pub created_at: u64,
    pub updated_at: u64,
}

static PLAYLISTS: OnceLock<Mutex<Vec<Playlist>>> = OnceLock::new();
static APP_DATA_DIR: OnceLock<PathBuf> = OnceLock::new();

fn get_now_unix() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs())
        .unwrap_or(0)
}

fn get_playlists_mutex() -> &'static Mutex<Vec<Playlist>> {
    PLAYLISTS.get_or_init(|| Mutex::new(Vec::new()))
}

pub fn init_playlists(app_data_dir: PathBuf) {
    let _ = APP_DATA_DIR.set(app_data_dir.clone());
    let playlists_file = app_data_dir.join("playlists.json");

    let loaded = if playlists_file.exists() {
        fs::read_to_string(&playlists_file)
            .ok()
            .and_then(|content| serde_json::from_str::<Vec<Playlist>>(&content).ok())
            .unwrap_or_default()
    } else {
        Vec::new()
    };

    if let Ok(mut guard) = get_playlists_mutex().lock() {
        *guard = loaded;
    }
}

fn persist_playlists_to_disk(playlists: &[Playlist]) {
    if let Some(dir) = APP_DATA_DIR.get() {
        let file_path = dir.join("playlists.json");
        let temp_path = dir.join("playlists.json.tmp");

        if let Ok(serialized) = serde_json::to_string_pretty(playlists) {
            if fs::write(&temp_path, serialized).is_ok() {
                let _ = fs::rename(&temp_path, &file_path);
            }
        }
    }
}

#[tauri::command]
pub fn get_playlists() -> Result<Vec<Playlist>, String> {
    let guard = get_playlists_mutex()
        .lock()
        .map_err(|_| "Playlists lock poisoned".to_string())?;
    Ok(guard.clone())
}

#[tauri::command]
pub fn create_playlist(name: String) -> Result<Playlist, String> {
    let clean_name = name.trim();
    if clean_name.is_empty() {
        return Err("Playlist name cannot be empty".into());
    }

    let now = get_now_unix();
    let new_playlist = Playlist {
        id: format!("pl_{}_{}", now, fastrand_id()),
        name: clean_name.to_string(),
        tracks: Vec::new(),
        created_at: now,
        updated_at: now,
    };

    let mut guard = get_playlists_mutex()
        .lock()
        .map_err(|_| "Playlists lock poisoned".to_string())?;

    guard.push(new_playlist.clone());
    persist_playlists_to_disk(&guard);

    Ok(new_playlist)
}

#[tauri::command]
pub fn rename_playlist(id: String, name: String) -> Result<Playlist, String> {
    let clean_name = name.trim();
    if clean_name.is_empty() {
        return Err("Playlist name cannot be empty".into());
    }

    let mut guard = get_playlists_mutex()
        .lock()
        .map_err(|_| "Playlists lock poisoned".to_string())?;

    if let Some(p) = guard.iter_mut().find(|p| p.id == id) {
        p.name = clean_name.to_string();
        p.updated_at = get_now_unix();
        let cloned = p.clone();
        persist_playlists_to_disk(&guard);
        Ok(cloned)
    } else {
        Err(format!("Playlist with ID '{}' not found", id))
    }
}

#[tauri::command]
pub fn delete_playlist(id: String) -> Result<(), String> {
    let mut guard = get_playlists_mutex()
        .lock()
        .map_err(|_| "Playlists lock poisoned".to_string())?;

    let initial_len = guard.len();
    guard.retain(|p| p.id != id);

    if guard.len() < initial_len {
        persist_playlists_to_disk(&guard);
        Ok(())
    } else {
        Err(format!("Playlist with ID '{}' not found", id))
    }
}

#[tauri::command]
pub fn add_track_to_playlist(playlist_id: String, track: Track) -> Result<Playlist, String> {
    let mut guard = get_playlists_mutex()
        .lock()
        .map_err(|_| "Playlists lock poisoned".to_string())?;

    if let Some(p) = guard.iter_mut().find(|p| p.id == playlist_id) {
        // Prevent duplicate addition of the same exact track ID in the same playlist
        if !p.tracks.iter().any(|t| t.id == track.id) {
            p.tracks.push(track);
            p.updated_at = get_now_unix();
        }
        let cloned = p.clone();
        persist_playlists_to_disk(&guard);
        Ok(cloned)
    } else {
        Err(format!("Playlist with ID '{}' not found", playlist_id))
    }
}

#[tauri::command]
pub fn remove_track_from_playlist(playlist_id: String, track_id: String) -> Result<Playlist, String> {
    let mut guard = get_playlists_mutex()
        .lock()
        .map_err(|_| "Playlists lock poisoned".to_string())?;

    if let Some(p) = guard.iter_mut().find(|p| p.id == playlist_id) {
        p.tracks.retain(|t| t.id != track_id);
        p.updated_at = get_now_unix();
        let cloned = p.clone();
        persist_playlists_to_disk(&guard);
        Ok(cloned)
    } else {
        Err(format!("Playlist with ID '{}' not found", playlist_id))
    }
}

#[tauri::command]
pub fn reorder_playlist_tracks(
    playlist_id: String,
    from_index: usize,
    to_index: usize,
) -> Result<Playlist, String> {
    let mut guard = get_playlists_mutex()
        .lock()
        .map_err(|_| "Playlists lock poisoned".to_string())?;

    if let Some(p) = guard.iter_mut().find(|p| p.id == playlist_id) {
        if from_index < p.tracks.len() && to_index < p.tracks.len() && from_index != to_index {
            let moved = p.tracks.remove(from_index);
            p.tracks.insert(to_index, moved);
            p.updated_at = get_now_unix();
        }
        let cloned = p.clone();
        persist_playlists_to_disk(&guard);
        Ok(cloned)
    } else {
        Err(format!("Playlist with ID '{}' not found", playlist_id))
    }
}

#[tauri::command]
pub fn merge_imported_playlists(imported: Vec<Playlist>) -> Result<Vec<Playlist>, String> {
    let mut guard = get_playlists_mutex().lock().map_err(|e| e.to_string())?;
    for imp in imported {
        let trimmed_name = imp.name.trim();
        if trimmed_name.is_empty() {
            continue;
        }
        if let Some(existing) = guard.iter_mut().find(|p| p.name.trim().eq_ignore_ascii_case(trimmed_name)) {
            let mut seen_sigs: std::collections::HashSet<String> = existing
                .tracks
                .iter()
                .map(|t| if t.signature.is_empty() { t.id.clone() } else { t.signature.clone() })
                .collect();
            for t in imp.tracks {
                let sig = if t.signature.is_empty() { t.id.clone() } else { t.signature.clone() };
                if !seen_sigs.contains(&sig) {
                    seen_sigs.insert(sig);
                    existing.tracks.push(t);
                }
            }
            existing.updated_at = get_now_unix();
        } else {
            let mut deduped_tracks = Vec::new();
            let mut seen_sigs = std::collections::HashSet::new();
            for t in imp.tracks {
                let sig = if t.signature.is_empty() { t.id.clone() } else { t.signature.clone() };
                if !seen_sigs.contains(&sig) {
                    seen_sigs.insert(sig);
                    deduped_tracks.push(t);
                }
            }
            let new_pl = Playlist {
                id: format!("pl_{}_{:x}", get_now_unix(), fastrand_id()),
                name: imp.name,
                tracks: deduped_tracks,
                created_at: if imp.created_at > 0 { imp.created_at } else { get_now_unix() },
                updated_at: get_now_unix(),
            };
            guard.push(new_pl);
        }
    }
    persist_playlists_to_disk(&guard);
    Ok(guard.clone())
}

fn fastrand_id() -> u32 {
    use std::collections::hash_map::RandomState;
    use std::hash::{BuildHasher, Hasher};
    let s = RandomState::new();
    let mut hasher = s.build_hasher();
    hasher.write_u64(get_now_unix());
    (hasher.finish() & 0xFFFFFF) as u32
}

#[cfg(test)]
mod tests {
    use super::*;

    fn dummy_track(id: &str) -> Track {
        Track {
            id: id.into(),
            title: format!("Title {}", id),
            artist: "Artist".into(),
            duration: 180,
            thumbnail: "thumb".into(),
            source: "youtube".into(),
            signature: format!("sig_{}", id),
        }
    }

    #[test]
    fn test_playlist_crud_lifecycle() {
        let p = create_playlist("My Workout Mix".into()).unwrap();
        assert_eq!(p.name, "My Workout Mix");
        assert!(p.tracks.is_empty());

        let renamed = rename_playlist(p.id.clone(), "Intense Workout".into()).unwrap();
        assert_eq!(renamed.name, "Intense Workout");

        let with_track = add_track_to_playlist(p.id.clone(), dummy_track("t1")).unwrap();
        assert_eq!(with_track.tracks.len(), 1);

        // Deduplication
        let dedup = add_track_to_playlist(p.id.clone(), dummy_track("t1")).unwrap();
        assert_eq!(dedup.tracks.len(), 1);

        let with_second = add_track_to_playlist(p.id.clone(), dummy_track("t2")).unwrap();
        assert_eq!(with_second.tracks.len(), 2);
        assert_eq!(with_second.tracks[0].id, "t1");
        assert_eq!(with_second.tracks[1].id, "t2");

        // Reorder
        let reordered = reorder_playlist_tracks(p.id.clone(), 0, 1).unwrap();
        assert_eq!(reordered.tracks[0].id, "t2");
        assert_eq!(reordered.tracks[1].id, "t1");

        // Remove track
        let removed = remove_track_from_playlist(p.id.clone(), "t2".into()).unwrap();
        assert_eq!(removed.tracks.len(), 1);
        assert_eq!(removed.tracks[0].id, "t1");

        // Delete playlist
        assert!(delete_playlist(p.id.clone()).is_ok());
        let remaining = get_playlists().unwrap();
        assert!(!remaining.iter().any(|pl| pl.id == p.id));
    }

    #[test]
    fn test_merge_imported_playlists_deduplication() {
        // Create initial playlist
        let p = create_playlist("Chill Vibes".into()).unwrap();
        add_track_to_playlist(p.id.clone(), dummy_track("track_1")).unwrap();

        // Import matching playlist name with 1 existing track and 1 new track
        let imported = vec![
            Playlist {
                id: "imp_1".into(),
                name: "chill vibes".into(), // case-insensitive match
                tracks: vec![dummy_track("track_1"), dummy_track("track_2")],
                created_at: 100,
                updated_at: 100,
            },
            Playlist {
                id: "imp_2".into(),
                name: "Upbeat".into(),
                tracks: vec![dummy_track("track_3"), dummy_track("track_3")], // duplicate inside
                created_at: 200,
                updated_at: 200,
            },
        ];

        let merged = merge_imported_playlists(imported).unwrap();
        let chill = merged.iter().find(|pl| pl.name.eq_ignore_ascii_case("chill vibes")).unwrap();
        assert_eq!(chill.tracks.len(), 2);
        assert_eq!(chill.tracks[0].id, "track_1");
        assert_eq!(chill.tracks[1].id, "track_2");

        let upbeat = merged.iter().find(|pl| pl.name.eq_ignore_ascii_case("upbeat")).unwrap();
        assert_eq!(upbeat.tracks.len(), 1);
        assert_eq!(upbeat.tracks[0].id, "track_3");

        // Clean up
        let _ = delete_playlist(chill.id.clone());
        let _ = delete_playlist(upbeat.id.clone());
    }
}
