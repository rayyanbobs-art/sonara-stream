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

pub fn get_title_signature(title: &str, artist: &str) -> std::collections::BTreeSet<String> {
    let combined = format!("{} {}", title, artist).to_lowercase();
    let noise: std::collections::HashSet<&str> = [
        "official", "video", "audio", "music", "lyrics", "lyric", "remastered",
        "remaster", "hd", "hq", "4k", "version", "original", "stem", "edit",
        "visualizer", "soundtrack", "ost", "theme", "full", "song", "records",
        "vevo", "channel", "topic", "special", "mix", "extended", "lyrics", "audio"
    ]
    .into_iter()
    .collect();

    let mut in_paren = 0;
    let mut in_bracket = 0;
    let mut filtered = String::new();

    for c in combined.chars() {
        match c {
            '(' => in_paren += 1,
            ')' => {
                if in_paren > 0 {
                    in_paren -= 1;
                }
            }
            '[' => in_bracket += 1,
            ']' => {
                if in_bracket > 0 {
                    in_bracket -= 1;
                }
            }
            _ => {
                if in_paren == 0 && in_bracket == 0 {
                    if c.is_alphanumeric() || c.is_whitespace() {
                        filtered.push(c);
                    } else {
                        filtered.push(' ');
                    }
                }
            }
        }
    }

    let mut words = std::collections::BTreeSet::new();
    for word in filtered.split_whitespace() {
        if word.len() >= 2 && !noise.contains(word) {
            words.insert(word.to_string());
        }
    }
    words
}

fn execute_ytdlp_search(search_arg: &str, binary: &PathBuf) -> Result<Vec<Track>, String> {
    let mut cmd = Command::new(binary);
    #[cfg(windows)]
    cmd.creation_flags(CREATE_NO_WINDOW);

    let output = cmd
        .args([
            search_arg,
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

    Ok(tracks)
}

#[tauri::command]
pub async fn get_search_suggestions(query: String) -> Result<Vec<String>, String> {
    let q = query.trim();
    if q.is_empty() {
        return Ok(Vec::new());
    }

    let url = format!(
        "https://suggestqueries.google.com/complete/search?client=firefox&ds=yt&q={}",
        urlencoding::encode(q)
    );

    let client = reqwest::Client::builder()
        .timeout(Duration::from_millis(1500))
        .build()
        .map_err(|e| e.to_string())?;

    let res = client.get(&url).send().await.map_err(|e| e.to_string())?;
    let json: serde_json::Value = res.json().await.map_err(|e| e.to_string())?;

    if let Some(items) = json.get(1).and_then(|v| v.as_array()) {
        let suggestions = items
            .iter()
            .filter_map(|item| item.as_str().map(|s| s.to_string()))
            .take(8)
            .collect();
        return Ok(suggestions);
    }

    Ok(Vec::new())
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
        let search_arg = format!("ytsearch25:{}", query);

        let raw_tracks = execute_ytdlp_search(&search_arg, &binary)?;

        // Deduplicate songs based on clean keyword signatures (no repetitive uploads)
        let mut seen_signatures = std::collections::HashSet::new();
        let mut unique_tracks = Vec::new();

        for track in raw_tracks {
            let sig = get_title_signature(&track.title, &track.artist);
            if !sig.is_empty() && !seen_signatures.contains(&sig) {
                seen_signatures.insert(sig);
                unique_tracks.push(track);
            }
        }

        // If after deduplication we have fewer than 7 distinct tracks (e.g. searching a single song name),
        // enrich the playlist with more distinct hits from that artist / genre
        if unique_tracks.len() < 7 {
            let fallback_artist = if let Some(first) = unique_tracks.first() {
                if first.artist != "Unknown Artist" && !first.artist.is_empty() {
                    first.artist.clone()
                } else {
                    query.clone()
                }
            } else {
                query.clone()
            };

            let expand_arg = format!("ytsearch15:{} greatest hits", fallback_artist);
            if let Ok(extra_tracks) = execute_ytdlp_search(&expand_arg, &binary) {
                for track in extra_tracks {
                    let sig = get_title_signature(&track.title, &track.artist);
                    if !sig.is_empty() && !seen_signatures.contains(&sig) {
                        seen_signatures.insert(sig);
                        unique_tracks.push(track);
                    }
                    if unique_tracks.len() >= 15 {
                        break;
                    }
                }
            }
        }

        if let Ok(mut guard) = get_search_cache().lock() {
            guard.insert(q, (unique_tracks.clone(), Instant::now()));
        }

        Ok(unique_tracks)
    })
    .await
    .map_err(|e| e.to_string())?
}

#[tauri::command]
pub async fn get_related_tracks(artist: String, title: String) -> Result<Vec<Track>, String> {
    tokio::task::spawn_blocking(move || {
        let binary = get_ytdlp_path();
        let query_term = if !artist.is_empty() && artist != "Unknown Artist" {
            format!("ytsearch15:{} songs", artist)
        } else {
            format!("ytsearch15:{} mix", title)
        };

        let raw = execute_ytdlp_search(&query_term, &binary)?;
        let mut seen = std::collections::HashSet::new();
        let target_sig = get_title_signature(&title, &artist);
        seen.insert(target_sig);

        let mut related = Vec::new();
        for t in raw {
            let sig = get_title_signature(&t.title, &t.artist);
            if !sig.is_empty() && !seen.contains(&sig) {
                seen.insert(sig);
                related.push(t);
            }
            if related.len() >= 10 {
                break;
            }
        }

        Ok(related)
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
