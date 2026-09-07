use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::fs;
use std::path::PathBuf;
use std::sync::Mutex;
use std::time::{SystemTime, UNIX_EPOCH};
use tauri::Manager;

const SEVEN_DAYS_SECS: u64 = 7 * 86400;
const USER_AGENT: &str = "SonaraStream/0.5.0 (https://github.com/rayyanbobs-art/sonara-stream)";

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct LyricsRecord {
    pub id: Option<u64>,
    pub plain_lyrics: Option<String>,
    pub synced_lyrics: Option<String>,
    pub instrumental: bool,
    pub cached_at_unix: u64,
}

#[derive(Debug, Clone, Deserialize)]
struct LrclibResponse {
    pub id: Option<u64>,
    #[serde(rename = "plainLyrics")]
    pub plain_lyrics: Option<String>,
    #[serde(rename = "syncedLyrics")]
    pub synced_lyrics: Option<String>,
    #[serde(default)]
    pub instrumental: bool,
}

static LYRICS_CACHE: Mutex<Option<HashMap<String, LyricsRecord>>> = Mutex::new(None);

fn get_now_unix() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_secs()
}

pub fn clean_title(title: &str) -> String {
    let mut cleaned = title.to_string();

    // Strip common YouTube/Music noise in brackets or parenthesis
    let patterns = [
        "(official video)",
        "(official music video)",
        "(official audio)",
        "(audio)",
        "(lyrics)",
        "(lyric video)",
        "[official video]",
        "[official music video]",
        "[official audio]",
        "[audio]",
        "[lyrics]",
        "[lyric video]",
        "(visualizer)",
        "[visualizer]",
        "(4k)",
        "(hd)",
    ];

    let lower = cleaned.to_lowercase();
    for pat in patterns {
        if let Some(pos) = lower.find(pat) {
            cleaned.replace_range(pos..pos + pat.len(), "");
        }
    }

    // Strip trailing or leading dashes/spaces
    cleaned = cleaned.trim().trim_matches('-').trim().to_string();

    // If title contains " - ", usually Artist - Title, take the second half
    if let Some(idx) = cleaned.find(" - ") {
        cleaned = cleaned[idx + 3..].trim().to_string();
    }

    // Strip "ft." or "feat." if present in title
    let lower_title = cleaned.to_lowercase();
    if let Some(idx) = lower_title.find(" ft.") {
        cleaned = cleaned[..idx].trim().to_string();
    } else if let Some(idx) = lower_title.find(" feat.") {
        cleaned = cleaned[..idx].trim().to_string();
    }

    cleaned
}

pub fn clean_artist(artist: &str) -> String {
    let mut cleaned = artist.trim().to_string();
    // Strip " - Topic" from YouTube auto-generated artists
    if cleaned.ends_with(" - Topic") {
        cleaned = cleaned.trim_end_matches(" - Topic").trim().to_string();
    }
    // Strip "VEVO"
    if cleaned.ends_with("VEVO") && cleaned.len() > 4 {
        cleaned = cleaned.trim_end_matches("VEVO").trim().to_string();
    }
    cleaned
}

fn get_cache_file_path(app_handle: &tauri::AppHandle) -> Result<PathBuf, String> {
    let base_dir = app_handle
        .path()
        .app_data_dir()
        .map_err(|e| format!("Failed to get app_data_dir: {}", e))?;
    if !base_dir.exists() {
        let _ = fs::create_dir_all(&base_dir);
    }
    Ok(base_dir.join("lyrics_cache.json"))
}

fn load_cache_from_disk(app_handle: &tauri::AppHandle) -> HashMap<String, LyricsRecord> {
    let mut map = HashMap::new();
    if let Ok(path) = get_cache_file_path(app_handle) {
        if path.exists() {
            if let Ok(data) = fs::read_to_string(&path) {
                if let Ok(loaded) = serde_json::from_str::<HashMap<String, LyricsRecord>>(&data) {
                    map = loaded;
                }
            }
        }
    }
    map
}

fn save_cache_to_disk(app_handle: &tauri::AppHandle, cache: &HashMap<String, LyricsRecord>) {
    if let Ok(path) = get_cache_file_path(app_handle) {
        let tmp_path = path.with_extension("json.tmp");
        if let Ok(json) = serde_json::to_string_pretty(cache) {
            if fs::write(&tmp_path, json).is_ok() {
                let _ = fs::rename(&tmp_path, &path);
            }
        }
    }
}

pub fn make_cache_key(artist: &str, title: &str) -> String {
    format!("{}:::{}", clean_artist(artist).to_lowercase(), clean_title(title).to_lowercase())
}

#[tauri::command]
pub async fn get_lyrics(
    app_handle: tauri::AppHandle,
    title: String,
    artist: String,
    duration: Option<f64>,
) -> Result<Option<LyricsRecord>, String> {
    let cleaned_title = clean_title(&title);
    let cleaned_artist = clean_artist(&artist);
    let cache_key = make_cache_key(&cleaned_artist, &cleaned_title);
    let now = get_now_unix();

    // 1. Check in-memory / disk cache
    {
        let mut guard = LYRICS_CACHE.lock().map_err(|e| e.to_string())?;
        if guard.is_none() {
            *guard = Some(load_cache_from_disk(&app_handle));
        }
        if let Some(ref cache) = *guard {
            if let Some(record) = cache.get(&cache_key) {
                if now.saturating_sub(record.cached_at_unix) < SEVEN_DAYS_SECS {
                    return Ok(Some(record.clone()));
                }
            }
        }
    }

    // 2. Fetch from LRCLIB
    let client = reqwest::Client::builder()
        .user_agent(USER_AGENT)
        .build()
        .map_err(|e| format!("Failed to build HTTP client: {}", e))?;

    // Attempt 1: Exact GET endpoint
    let mut url = format!(
        "https://lrclib.net/api/get?track_name={}&artist_name={}",
        urlencoding::encode(&cleaned_title),
        urlencoding::encode(&cleaned_artist)
    );
    if let Some(dur) = duration {
        if dur > 0.0 {
            url.push_str(&format!("&duration={}", dur.round() as u64));
        }
    }

    let mut fetched_lyrics: Option<LrclibResponse> = None;

    if let Ok(resp) = client.get(&url).send().await {
        if resp.status().is_success() {
            if let Ok(data) = resp.json::<LrclibResponse>().await {
                fetched_lyrics = Some(data);
            }
        }
    }

    // Attempt 2: Search endpoint fallback if exact match didn't find lyrics
    if fetched_lyrics.is_none() {
        let search_url = format!(
            "https://lrclib.net/api/search?q={}",
            urlencoding::encode(&format!("{} {}", cleaned_artist, cleaned_title))
        );
        if let Ok(resp) = client.get(&search_url).send().await {
            if resp.status().is_success() {
                if let Ok(items) = resp.json::<Vec<LrclibResponse>>().await {
                    if let Some(first) = items.into_iter().find(|i| i.synced_lyrics.is_some() || i.plain_lyrics.is_some()) {
                        fetched_lyrics = Some(first);
                    }
                }
            }
        }
    }

    let record = match fetched_lyrics {
        Some(item) => LyricsRecord {
            id: item.id,
            plain_lyrics: item.plain_lyrics,
            synced_lyrics: item.synced_lyrics,
            instrumental: item.instrumental,
            cached_at_unix: now,
        },
        None => LyricsRecord {
            id: None,
            plain_lyrics: None,
            synced_lyrics: None,
            instrumental: false,
            cached_at_unix: now,
        },
    };

    // 3. Store in cache (both in-memory and atomic disk write)
    {
        let mut guard = LYRICS_CACHE.lock().map_err(|e| e.to_string())?;
        if let Some(ref mut cache) = *guard {
            cache.insert(cache_key, record.clone());
            save_cache_to_disk(&app_handle, cache);
        }
    }

    Ok(Some(record))
}

#[cfg(test)]
pub mod tests {
    use super::*;

    #[test]
    fn test_clean_title() {
        assert_eq!(clean_title("Blinding Lights (Official Video)"), "Blinding Lights");
        assert_eq!(clean_title("Starboy [Official Audio]"), "Starboy");
        assert_eq!(clean_title("The Weeknd - Save Your Tears"), "Save Your Tears");
        assert_eq!(clean_title("Levitating ft. DaBaby"), "Levitating");
    }

    #[test]
    fn test_clean_artist() {
        assert_eq!(clean_artist("Dua Lipa - Topic"), "Dua Lipa");
        assert_eq!(clean_artist("TheWeekndVEVO"), "TheWeeknd");
        assert_eq!(clean_artist("Ed Sheeran"), "Ed Sheeran");
    }

    #[test]
    fn test_make_cache_key() {
        let k1 = make_cache_key("The Weeknd - Topic", "Blinding Lights (Official Video)");
        let k2 = make_cache_key("the weeknd", "blinding lights");
        assert_eq!(k1, k2);
    }
}
