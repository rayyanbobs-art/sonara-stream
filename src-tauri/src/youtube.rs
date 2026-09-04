use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::path::PathBuf;
use std::process::Command;
use std::sync::{Mutex, OnceLock};
use std::time::{Duration, Instant};

#[cfg(windows)]
use std::os::windows::process::CommandExt;

#[cfg(windows)]
const CREATE_NO_WINDOW: u32 = 0x08000000;

static STREAM_CACHE: OnceLock<Mutex<HashMap<String, (String, Instant)>>> = OnceLock::new();
static SEARCH_CACHE: OnceLock<Mutex<HashMap<String, (Vec<Track>, Instant)>>> = OnceLock::new();

fn get_stream_cache() -> &'static Mutex<HashMap<String, (String, Instant)>> {
    STREAM_CACHE.get_or_init(|| Mutex::new(HashMap::new()))
}

fn get_search_cache() -> &'static Mutex<HashMap<String, (Vec<Track>, Instant)>> {
    SEARCH_CACHE.get_or_init(|| Mutex::new(HashMap::new()))
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct Track {
    pub id: String,
    pub title: String,
    pub artist: String,
    pub duration: u64,
    pub thumbnail: String,
    pub source: String,
}

pub fn get_ytdlp_path() -> PathBuf {
    // 1. Check relative to binary
    if let Ok(mut exe_path) = std::env::current_exe() {
        exe_path.pop();
        let candidate = exe_path.join("yt-dlp.exe");
        if candidate.exists() {
            return candidate;
        }
        let sidecar_candidate = exe_path.join("yt-dlp-x86_64-pc-windows-msvc.exe");
        if sidecar_candidate.exists() {
            return sidecar_candidate;
        }
    }

    // 2. Check development path
    let dev_path = PathBuf::from("src-tauri/binaries/yt-dlp-x86_64-pc-windows-msvc.exe");
    if dev_path.exists() {
        return dev_path;
    }

    let dev_path_plain = PathBuf::from("binaries/yt-dlp-x86_64-pc-windows-msvc.exe");
    if dev_path_plain.exists() {
        return dev_path_plain;
    }

    // 3. Fallback to PATH
    PathBuf::from("yt-dlp.exe")
}

#[tauri::command]
pub async fn search_youtube(query: String) -> Result<Vec<Track>, String> {
    let q = query.trim().to_lowercase();
    if let Ok(guard) = get_search_cache().lock() {
        if let Some((cached_tracks, instant)) = guard.get(&q) {
            if instant.elapsed() < Duration::from_secs(3600) {
                return Ok(cached_tracks.clone());
            }
        }
    }

    tokio::task::spawn_blocking(move || {
        let binary = get_ytdlp_path();
        let search_arg = format!("ytsearch10:{}", query);

        let mut cmd = Command::new(&binary);
        #[cfg(windows)]
        cmd.creation_flags(CREATE_NO_WINDOW);

        let output = cmd
            .args([
                &search_arg,
                "--dump-json",
                "--flat-playlist",
                "--no-warnings",
                "--no-check-certificates",
                "--extractor-args",
                "youtube:player_client=android",
            ])
            .output()
            .map_err(|e| format!("Failed to execute yt-dlp at {:?}: {}", binary, e))?;

        if !output.status.success() {
            let err = String::from_utf8_lossy(&output.stderr);
            return Err(format!("yt-dlp search failed: {}", err));
        }

        let stdout = String::from_utf8_lossy(&output.stdout);
        let mut tracks = Vec::new();

        for line in stdout.lines() {
            if let Ok(json) = serde_json::from_str::<serde_json::Value>(line) {
                let id = json["id"].as_str().unwrap_or("").to_string();
                let title = json["title"].as_str().unwrap_or("").to_string();
                let artist = json["uploader"].as_str().unwrap_or("Unknown Artist").to_string();
                let duration = json["duration"].as_f64().unwrap_or(0.0) as u64;
                
                let mut thumbnail = String::new();
                if let Some(thumbs) = json["thumbnails"].as_array() {
                    if let Some(first) = thumbs.first() {
                        if let Some(url) = first["url"].as_str() {
                            thumbnail = url.to_string();
                        }
                    }
                }
                if thumbnail.is_empty() {
                    thumbnail = format!("https://i.ytimg.com/vi/{}/hqdefault.jpg", id);
                }

                if !id.is_empty() && !title.is_empty() {
                    tracks.push(Track {
                        id,
                        title,
                        artist,
                        duration,
                        thumbnail,
                        source: "youtube".into(),
                    });
                }
            }
        }

        if let Ok(mut guard) = get_search_cache().lock() {
            guard.insert(q, (tracks.clone(), Instant::now()));
        }

        Ok(tracks)
    })
    .await
    .map_err(|e| e.to_string())?
}

#[tauri::command]
pub async fn get_stream_url(id: String) -> Result<String, String> {
    let clean_id = id.trim().to_string();
    if let Ok(guard) = get_stream_cache().lock() {
        if let Some((url, instant)) = guard.get(&clean_id) {
            if instant.elapsed() < Duration::from_secs(4 * 3600) {
                return Ok(url.clone());
            }
        }
    }

    tokio::task::spawn_blocking(move || {
        let binary = get_ytdlp_path();
        let video_url = if clean_id.starts_with("http") {
            clean_id.clone()
        } else {
            format!("https://www.youtube.com/watch?v={}", clean_id)
        };

        let mut cmd = Command::new(&binary);
        #[cfg(windows)]
        cmd.creation_flags(CREATE_NO_WINDOW);

        let output = cmd
            .args([
                &video_url,
                "-f",
                "ba/b",
                "--get-url",
                "--no-playlist",
                "--skip-download",
                "--no-warnings",
                "--no-check-certificates",
                "--extractor-args",
                "youtube:player_client=android",
            ])
            .output()
            .map_err(|e| format!("Failed to resolve stream URL: {}", e))?;

        if !output.status.success() {
            let err = String::from_utf8_lossy(&output.stderr);
            return Err(format!("Stream resolution failed: {}", err));
        }

        let url = String::from_utf8_lossy(&output.stdout).trim().to_string();
        if url.is_empty() {
            return Err("Resolved stream URL was empty".into());
        }

        if let Ok(mut guard) = get_stream_cache().lock() {
            guard.insert(clean_id, (url.clone(), Instant::now()));
        }

        Ok(url)
    })
    .await
    .map_err(|e| e.to_string())?
}
