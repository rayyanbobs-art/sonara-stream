//! YouTube audio search and streaming extraction service.
//!
//! DISCLAIMER & ARCHITECTURAL DEPENDENCY:
//! This service relies on invoking an external `yt-dlp` executable to query
//! YouTube endpoints and extract progressive audio stream URLs directly into memory.
//! This mechanism is subject to YouTube's Terms of Service and applicable copyright laws.
//! Because YouTube continuously updates player clients, internal obfuscation tokens,
//! and bot-detection heuristics, stream extraction is fragile and may experience
//! intermittent breakage whenever YouTube alters internal protocols until `yt-dlp`
//! releases a corresponding patch.

use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::path::PathBuf;
use std::sync::{Mutex, OnceLock};
use std::time::{Duration, Instant};

#[cfg(windows)]
const CREATE_NO_WINDOW: u32 = 0x08000000;

type StreamCacheMap = HashMap<String, (String, Instant)>;
type SearchCacheMap = HashMap<String, (Vec<Track>, Instant)>;
type InFlightSender = tokio::sync::broadcast::Sender<Result<String, String>>;
type InFlightMap = HashMap<String, InFlightSender>;

static STREAM_CACHE: OnceLock<Mutex<StreamCacheMap>> = OnceLock::new();
static SEARCH_CACHE: OnceLock<Mutex<SearchCacheMap>> = OnceLock::new();
static IN_FLIGHT: OnceLock<Mutex<InFlightMap>> = OnceLock::new();

fn get_stream_cache() -> &'static Mutex<StreamCacheMap> {
    STREAM_CACHE.get_or_init(|| Mutex::new(HashMap::new()))
}

fn get_search_cache() -> &'static Mutex<SearchCacheMap> {
    SEARCH_CACHE.get_or_init(|| Mutex::new(HashMap::new()))
}

fn get_in_flight() -> &'static Mutex<InFlightMap> {
    IN_FLIGHT.get_or_init(|| Mutex::new(HashMap::new()))
}

pub(crate) fn insert_bounded_cache<T>(
    cache_mutex: &Mutex<HashMap<String, (T, Instant)>>,
    key: String,
    value: T,
    max_capacity: usize,
    ttl: Duration,
    purge_count: usize,
) {
    if let Ok(mut guard) = cache_mutex.lock() {
        if guard.len() >= max_capacity {
            guard.retain(|_, (_, inst)| inst.elapsed() < ttl);
            if guard.len() >= max_capacity {
                let keys_to_remove: Vec<String> = guard.keys().take(purge_count).cloned().collect();
                for k in keys_to_remove {
                    guard.remove(&k);
                }
            }
        }
        guard.insert(key, (value, Instant::now()));
    }
}

fn insert_stream_cache(key: String, value: String) {
    insert_bounded_cache(
        get_stream_cache(),
        key,
        value,
        200,
        Duration::from_secs(3600),
        50,
    );
}

fn insert_search_cache(key: String, value: Vec<Track>) {
    insert_bounded_cache(
        get_search_cache(),
        key,
        value,
        100,
        Duration::from_secs(1800),
        30,
    );
}

#[derive(Debug, Serialize, Deserialize, Clone, PartialEq)]
pub struct Track {
    pub id: String,
    pub title: String,
    pub artist: String,
    pub duration: u64,
    pub thumbnail: String,
    pub source: String,
    pub signature: String,
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

pub fn get_title_signature_string(title: &str, artist: &str) -> String {
    let words = get_title_signature(title, artist);
    words.into_iter().collect::<Vec<_>>().join(" ")
}

// Invokes external yt-dlp binary to search YouTube; subject to YouTube ToS and breakage on UI/API changes.
async fn execute_ytdlp_search(search_arg: &str, binary: &PathBuf) -> Result<Vec<Track>, String> {
    let mut cmd = tokio::process::Command::new(binary);
    #[cfg(windows)]
    cmd.creation_flags(CREATE_NO_WINDOW);
    cmd.kill_on_drop(true);

    let output_res = tokio::time::timeout(
        Duration::from_secs(15),
        cmd.args([
            "--dump-json",
            "--flat-playlist",
            "--no-warnings",
            "--extractor-args",
            "youtube:player_client=android",
            "--",
            search_arg,
        ])
        .output(),
    )
    .await;

    let output = match output_res {
        Ok(res) => res.map_err(|e| format!("Failed to execute yt-dlp at {:?}: {}", binary, e))?,
        Err(_) => return Err("yt-dlp search timed out after 15s".into()),
    };

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
                let signature = get_title_signature_string(&title, &artist);
                tracks.push(Track {
                    id,
                    title,
                    artist,
                    duration,
                    thumbnail,
                    source: "youtube".into(),
                    signature,
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
    let raw_q = query.trim();
    if raw_q.is_empty() {
        return Ok(Vec::new());
    }

    // Sanitize input: limit length and strip leading CLI flag dashes
    let sanitized_query: String = raw_q
        .chars()
        .take(250)
        .collect::<String>()
        .trim_start_matches('-')
        .trim()
        .to_string();

    if sanitized_query.is_empty() {
        return Ok(Vec::new());
    }

    let q = sanitized_query.to_lowercase();
    if let Ok(mut guard) = get_search_cache().lock() {
        if let Some((cached_tracks, instant)) = guard.get(&q) {
            if instant.elapsed() < Duration::from_secs(1800) {
                return Ok(cached_tracks.clone());
            } else {
                guard.remove(&q);
            }
        }
    }

    let binary = get_ytdlp_path();
    let search_arg = format!("ytsearch25:{}", sanitized_query);

    let raw_tracks = execute_ytdlp_search(&search_arg, &binary).await?;

    // Deduplicate songs based on clean keyword signatures (no repetitive uploads)
    let mut seen_signatures = std::collections::HashSet::new();
    let mut unique_tracks = Vec::new();

    for track in raw_tracks {
        if !track.signature.is_empty() && !seen_signatures.contains(&track.signature) {
            seen_signatures.insert(track.signature.clone());
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
                sanitized_query.clone()
            }
        } else {
            sanitized_query.clone()
        };

        let expand_arg = format!("ytsearch15:{} greatest hits", fallback_artist);
        if let Ok(extra_tracks) = execute_ytdlp_search(&expand_arg, &binary).await {
            for track in extra_tracks {
                if !track.signature.is_empty() && !seen_signatures.contains(&track.signature) {
                    seen_signatures.insert(track.signature.clone());
                    unique_tracks.push(track);
                }
                if unique_tracks.len() >= 15 {
                    break;
                }
            }
        }
    }

    insert_search_cache(q, unique_tracks.clone());

    Ok(unique_tracks)
}

#[tauri::command]
pub async fn get_related_tracks(artist: String, title: String) -> Result<Vec<Track>, String> {
    let clean_artist = artist.trim().trim_start_matches('-').to_string();
    let clean_title = title.trim().trim_start_matches('-').to_string();

    let binary = get_ytdlp_path();
    let query_term = if !clean_artist.is_empty() && clean_artist != "Unknown Artist" {
        format!("ytsearch15:{} songs", clean_artist)
    } else {
        format!("ytsearch15:{} mix", clean_title)
    };

    let raw = execute_ytdlp_search(&query_term, &binary).await?;
    let mut seen = std::collections::HashSet::new();
    let target_sig = get_title_signature_string(&clean_title, &clean_artist);
    if !target_sig.is_empty() {
        seen.insert(target_sig);
    }

    let mut related = Vec::new();
    for t in raw {
        if !t.signature.is_empty() && !seen.contains(&t.signature) {
            seen.insert(t.signature.clone());
            related.push(t);
        }
        if related.len() >= 10 {
            break;
        }
    }

    Ok(related)
}

#[derive(Debug, Deserialize)]
struct ITunesSong {
    #[serde(rename = "trackName")]
    track_name: Option<String>,
    #[serde(rename = "artistName")]
    artist_name: Option<String>,
    #[serde(rename = "artworkUrl100")]
    artwork_url: Option<String>,
    #[serde(rename = "trackTimeMillis")]
    track_time_millis: Option<u64>,
    #[serde(rename = "primaryGenreName")]
    primary_genre_name: Option<String>,
}

#[derive(Debug, Deserialize)]
struct ITunesResponse {
    results: Vec<ITunesSong>,
}

#[tauri::command]
pub async fn get_genre_mix(artist: String, title: String) -> Result<Vec<Track>, String> {
    let clean_artist = artist.trim();
    let clean_title = title.trim();

    let client = reqwest::Client::builder()
        .timeout(Duration::from_millis(3500))
        .build()
        .map_err(|e| e.to_string())?;

    let search_term = if !clean_artist.is_empty() && clean_artist != "Unknown Artist" {
        format!("{} {}", clean_artist, clean_title)
    } else {
        clean_title.to_string()
    };

    let itunes_url = format!(
        "https://itunes.apple.com/search?term={}&entity=song&limit=6",
        urlencoding::encode(&search_term)
    );

    let mut mix_tracks: Vec<Track> = Vec::new();
    let mut seen_sigs = std::collections::HashSet::new();
    let seed_sig = get_title_signature_string(clean_title, clean_artist);
    if !seed_sig.is_empty() {
        seen_sigs.insert(seed_sig);
    }

    let mut detected_genre = "Alternative".to_string();

    if let Ok(res) = client.get(&itunes_url).send().await {
        if let Ok(data) = res.json::<ITunesResponse>().await {
            for s in data.results {
                if let (Some(t_name), Some(a_name)) = (s.track_name, s.artist_name) {
                    if let Some(g) = s.primary_genre_name {
                        detected_genre = g;
                    }
                    let sig = get_title_signature_string(&t_name, &a_name);
                    if !sig.is_empty() && !seen_sigs.contains(&sig) {
                        seen_sigs.insert(sig.clone());
                        let duration = s.track_time_millis.map(|ms| ms / 1000).unwrap_or(210);
                        let thumb = s.artwork_url.unwrap_or_default().replace("100x100", "600x600");
                        mix_tracks.push(Track {
                            id: format!("search:{} - {}", a_name, t_name),
                            title: t_name,
                            artist: a_name,
                            duration,
                            thumbnail: thumb,
                            source: "youtube".into(),
                            signature: sig,
                        });
                    }
                }
            }
        }
    }

    // Fetch iconic diverse tracks from that exact genre (max 2 per artist for variety)
    let genre_url = format!(
        "https://itunes.apple.com/search?term={}+hits&entity=song&limit=30",
        urlencoding::encode(&detected_genre)
    );

    if let Ok(res) = client.get(&genre_url).send().await {
        if let Ok(data) = res.json::<ITunesResponse>().await {
            let mut artist_count: std::collections::HashMap<String, usize> = std::collections::HashMap::new();
            for s in data.results {
                if let (Some(t_name), Some(a_name)) = (s.track_name, s.artist_name) {
                    let a_clean = a_name.trim().to_lowercase();
                    let count = artist_count.entry(a_clean).or_insert(0);
                    if *count >= 2 {
                        continue;
                    }
                    let sig = get_title_signature_string(&t_name, &a_name);
                    if !sig.is_empty() && !seen_sigs.contains(&sig) {
                        seen_sigs.insert(sig.clone());
                        *count += 1;
                        let duration = s.track_time_millis.map(|ms| ms / 1000).unwrap_or(210);
                        let thumb = s.artwork_url.unwrap_or_default().replace("100x100", "600x600");
                        mix_tracks.push(Track {
                            id: format!("search:{} - {}", a_name, t_name),
                            title: t_name,
                            artist: a_name,
                            duration,
                            thumbnail: thumb,
                            source: "youtube".into(),
                            signature: sig,
                        });
                    }
                }
                if mix_tracks.len() >= 20 {
                    break;
                }
            }
        }
    }

    Ok(mix_tracks)
}

pub async fn resolve_stream_url_internal(
    id: String,
    bypass_cache: Option<bool>,
    timeout_duration: Duration,
) -> Result<String, String> {
    let clean_id = id.trim().to_string();
    if clean_id.is_empty() || clean_id.len() > 300 {
        return Err("Invalid track identifier".into());
    }

    // SSRF & Malicious Scheme Protection: Only permit legitimate YouTube URLs
    if clean_id.starts_with("http:") || clean_id.starts_with("https:") {
        let is_valid_youtube = clean_id.starts_with("https://www.youtube.com/")
            || clean_id.starts_with("https://youtube.com/")
            || clean_id.starts_with("https://youtu.be/")
            || clean_id.starts_with("https://music.youtube.com/");
        if !is_valid_youtube {
            return Err("Invalid external streaming host".into());
        }
    }

    const STREAM_CACHE_TTL: Duration = Duration::from_secs(3600); // 1-hour TTL
    let should_bypass = bypass_cache.unwrap_or(false);

    if !should_bypass {
        if let Ok(mut guard) = get_stream_cache().lock() {
            if let Some((url, instant)) = guard.get(&clean_id) {
                if instant.elapsed() < STREAM_CACHE_TTL {
                    return Ok(url.clone());
                } else {
                    guard.remove(&clean_id);
                }
            }
        }
    } else if let Ok(mut guard) = get_stream_cache().lock() {
        guard.remove(&clean_id);
    }

    // Check if another async task is already resolving this exact ID
    let mut rx = {
        let mut flight_guard = get_in_flight()
            .lock()
            .map_err(|_| "In-flight mutex poisoned".to_string())?;
        if let Some(tx) = flight_guard.get(&clean_id) {
            Some(tx.subscribe())
        } else {
            let (tx, _) = tokio::sync::broadcast::channel(2);
            flight_guard.insert(clean_id.clone(), tx);
            None
        }
    };

    if let Some(ref mut receiver) = rx {
        if let Ok(res) = receiver.recv().await {
            return res;
        }
    }

    let id_clone = clean_id.clone();
    let stream_resolve_task = async move {
        let binary = get_ytdlp_path();
        let video_url = if let Some(search_term) = id_clone.strip_prefix("search:") {
            let safe_term = search_term.trim_start_matches('-');
            format!("ytsearch1:{}", safe_term)
        } else if id_clone.starts_with("http") {
            id_clone.clone()
        } else {
            // Strip any leading dashes from video ID
            let safe_id = id_clone.trim_start_matches('-');
            format!("https://www.youtube.com/watch?v={}", safe_id)
        };

        // Shells out to external yt-dlp to extract raw media stream URL.
        // Dependent on YouTube's player JS & cipher formats; subject to YouTube ToS.
        let mut cmd = tokio::process::Command::new(&binary);
        #[cfg(windows)]
        cmd.creation_flags(CREATE_NO_WINDOW);
        cmd.kill_on_drop(true);

        let output = cmd
            .args([
                "-f",
                "18/ba/b",
                "--get-url",
                "--no-playlist",
                "--skip-download",
                "--no-warnings",
                "--no-cache-dir",
                "--socket-timeout",
                "4",
                "--extractor-args",
                "youtube:player_client=android;player_skip=webpage,configs",
                "--",
                &video_url,
            ])
            .output()
            .await
            .map_err(|e| format!("Failed to resolve stream URL: {}", e))?;

        if !output.status.success() {
            let err = String::from_utf8_lossy(&output.stderr);
            return Err(format!("Stream resolution failed: {}", err));
        }

        let url = String::from_utf8_lossy(&output.stdout).trim().to_string();
        if url.is_empty() {
            return Err("Resolved stream URL was empty".into());
        }

        insert_stream_cache(id_clone, url.clone());

        Ok(url)
    };

    // Enforce timeout on stream resolution
    let result = match tokio::time::timeout(timeout_duration, stream_resolve_task).await {
        Ok(res) => res,
        Err(_) => Err(format!("Stream resolution timed out after {:?}", timeout_duration)),
    };

    // CRITICAL: Always remove from in-flight (even on timeout) and notify waiters
    if let Ok(mut flight_guard) = get_in_flight().lock() {
        if let Some(tx) = flight_guard.remove(&clean_id) {
            let _ = tx.send(result.clone());
        }
    }

    result
}

#[tauri::command]
pub async fn get_stream_url(id: String, bypass_cache: Option<bool>) -> Result<String, String> {
    resolve_stream_url_internal(id, bypass_cache, Duration::from_secs(20)).await
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_signature_strips_noise_and_brackets() {
        let sig1 = get_title_signature_string("Blinding Lights (Official Music Video)", "The Weeknd");
        let sig2 = get_title_signature_string("Blinding Lights [Lyrics Audio HD]", "The Weeknd");
        assert_eq!(sig1, sig2);
        assert!(sig1.contains("blinding"));
        assert!(sig1.contains("lights"));
        assert!(sig1.contains("weeknd"));
        assert!(!sig1.contains("official"));
        assert!(!sig1.contains("video"));
    }

    #[test]
    fn test_signature_deterministic_ordering() {
        let sig = get_title_signature_string("Starboy feat Daft Punk", "The Weeknd");
        let words: Vec<&str> = sig.split_whitespace().collect();
        let mut sorted = words.clone();
        sorted.sort();
        assert_eq!(words, sorted);
    }

    #[test]
    fn test_bounded_cache_capacity_eviction() {
        let cache = Mutex::new(HashMap::new());
        for i in 0..15 {
            insert_bounded_cache(
                &cache,
                format!("key_{}", i),
                format!("val_{}", i),
                10,
                Duration::from_secs(60),
                3,
            );
        }
        let guard = cache.lock().unwrap();
        assert!(guard.len() <= 10);
    }

    #[test]
    fn test_bounded_cache_ttl_expiry() {
        let cache = Mutex::new(HashMap::new());
        {
            let mut guard = cache.lock().unwrap();
            let past_instant = Instant::now() - Duration::from_secs(100);
            guard.insert("stale_key".to_string(), ("stale_val".to_string(), past_instant));
        }

        insert_bounded_cache(
            &cache,
            "fresh_key".to_string(),
            "fresh_val".to_string(),
            1,
            Duration::from_secs(10),
            1,
        );

        let guard = cache.lock().unwrap();
        assert!(!guard.contains_key("stale_key"));
        assert!(guard.contains_key("fresh_key"));
    }

    /// Requires the pinned `yt-dlp` sidecar binary to exist on disk at `binaries/yt-dlp*`.
    /// Run with `cargo test -- --ignored` when the sidecar binary is present.
    #[tokio::test]
    #[ignore = "requires local yt-dlp sidecar binary"]
    async fn test_timed_out_ytdlp_removes_in_flight_entry() {
        let test_id = "test_timeout_removal_id".to_string();

        // Ensure in-flight is clean initially
        {
            let mut guard = get_in_flight().lock().unwrap();
            guard.remove(&test_id);
        }

        // Call with an ultra-short timeout (1 microsecond) so it times out
        let res = resolve_stream_url_internal(test_id.clone(), Some(true), Duration::from_micros(1)).await;
        assert!(res.is_err());
        let err_msg = res.unwrap_err();
        assert!(err_msg.contains("timed out"), "Expected timeout error, got: {}", err_msg);

        // Verify in-flight entry was removed
        {
            let guard = get_in_flight().lock().unwrap();
            assert!(
                !guard.contains_key(&test_id),
                "in-flight entry must be cleaned up on timeout"
            );
        }

        // Actually call resolve_stream_url_internal again for the same ID and assert it proceeds past in-flight check
        let second_call_res = tokio::time::timeout(
            Duration::from_secs(2),
            resolve_stream_url_internal(test_id.clone(), Some(true), Duration::from_micros(1)),
        )
        .await;

        assert!(
            second_call_res.is_ok(),
            "Second call must not deadlock waiting on abandoned in-flight channel"
        );
        let res2 = second_call_res.unwrap();
        assert!(res2.is_err());
        let err2 = res2.unwrap_err();
        assert!(
            err2.contains("timed out"),
            "Second call must proceed past in-flight check to execution task, got: {}",
            err2
        );

        // Cleanup
        let mut guard = get_in_flight().lock().unwrap();
        guard.remove(&test_id);
    }
}
