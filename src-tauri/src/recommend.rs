//! Recommendation and Radio Engine for Sonara Stream.
//!
//! Provides personalized recommendations and smart queue radio generation based on
//! local play history signals (completed plays, skips, and recency) with zero telemetry
//! or external analytics servers.

use crate::youtube::{
    execute_ytdlp_search, get_http_client, get_title_signature_string, get_ytdlp_path, Track,
};
use serde::{Deserialize, Serialize};
use std::collections::{HashMap, HashSet};
use std::path::PathBuf;
use std::sync::{Mutex, OnceLock};
use std::time::{SystemTime, UNIX_EPOCH};

const MAX_HISTORY_ENTRIES: usize = 500;
const SEVEN_DAYS_SECS: u64 = 7 * 24 * 60 * 60;

#[derive(Debug, Serialize, Deserialize, Clone, PartialEq)]
pub struct PlayHistoryEntry {
    pub signature: String,
    pub track: Track,
    pub artist: String,
    pub played_at_unix: u64,
    pub completed: bool,
    pub skipped_before_seconds: Option<u64>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct RecommendationsResult {
    pub title: String,
    pub tracks: Vec<Track>,
}

static HISTORY: OnceLock<Mutex<Vec<PlayHistoryEntry>>> = OnceLock::new();
static APP_DATA_DIR: OnceLock<PathBuf> = OnceLock::new();

pub fn init_recommendations(app_data_dir: PathBuf) {
    let _ = APP_DATA_DIR.set(app_data_dir.clone());
    let history_file = app_data_dir.join("listening_history.json");

    let loaded = if history_file.exists() {
        std::fs::read_to_string(&history_file)
            .ok()
            .and_then(|s| serde_json::from_str::<Vec<PlayHistoryEntry>>(&s).ok())
            .unwrap_or_default()
    } else {
        Vec::new()
    };

    let _ = HISTORY.set(Mutex::new(loaded));
}

fn get_history() -> &'static Mutex<Vec<PlayHistoryEntry>> {
    HISTORY.get_or_init(|| Mutex::new(Vec::new()))
}

fn save_history_to_disk(entries: &[PlayHistoryEntry]) {
    if let Some(dir) = APP_DATA_DIR.get() {
        let history_file = dir.join("listening_history.json");
        if let Ok(json) = serde_json::to_string(entries) {
            let _ = std::fs::write(history_file, json);
        }
    }
}

pub fn add_history_entry(entry: PlayHistoryEntry) {
    if let Ok(mut guard) = get_history().lock() {
        guard.push(entry);
        if guard.len() > MAX_HISTORY_ENTRIES {
            let overflow = guard.len() - MAX_HISTORY_ENTRIES;
            guard.drain(0..overflow);
        }
        save_history_to_disk(&guard);
    }
}

#[tauri::command]
pub fn record_play_event(
    track: Track,
    completed: bool,
    skipped_before_seconds: Option<u64>,
) -> Result<(), String> {
    let now = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs())
        .unwrap_or(0);

    let signature = if track.signature.is_empty() {
        get_title_signature_string(&track.title, &track.artist)
    } else {
        track.signature.clone()
    };

    let entry = PlayHistoryEntry {
        signature,
        artist: track.artist.clone(),
        track,
        played_at_unix: now,
        completed,
        skipped_before_seconds,
    };

    add_history_entry(entry);
    Ok(())
}

#[derive(Debug, Deserialize)]
struct ITunesSearchResult {
    #[serde(rename = "primaryGenreName")]
    primary_genre_name: Option<String>,
}

#[derive(Debug, Deserialize)]
struct ITunesSearchResponse {
    results: Vec<ITunesSearchResult>,
}

async fn detect_genre_for_artist(artist: &str) -> Option<String> {
    let client = get_http_client();
    let url = format!(
        "https://itunes.apple.com/search?term={}&entity=song&limit=5",
        urlencoding::encode(artist)
    );

    if let Ok(res) = client.get(&url).send().await {
        if let Ok(data) = res.json::<ITunesSearchResponse>().await {
            for item in data.results {
                if let Some(genre) = item.primary_genre_name {
                    if !genre.trim().is_empty() {
                        return Some(genre);
                    }
                }
            }
        }
    }
    None
}

/// Applies recommendation diversity rules:
/// - Max 2 tracks per artist
/// - Max 1 track per signature
/// - Exclude signatures played in the last 7 days (unless favorites)
pub fn filter_diverse_tracks(
    candidates: Vec<Track>,
    excluded_signatures: &HashSet<String>,
    max_per_artist: usize,
    limit: usize,
) -> Vec<Track> {
    let mut result = Vec::new();
    let mut seen_signatures = excluded_signatures.clone();
    let mut artist_counts: HashMap<String, usize> = HashMap::new();

    for track in candidates {
        let sig = if track.signature.is_empty() {
            get_title_signature_string(&track.title, &track.artist)
        } else {
            track.signature.clone()
        };

        if sig.is_empty() {
            continue;
        }

        let is_same = seen_signatures.iter().any(|ex| {
            crate::youtube::signatures_are_same_song(ex, &sig)
        });
        if is_same {
            continue;
        }

        let artist_key = track.artist.trim().to_lowercase();
        let count = artist_counts.entry(artist_key).or_insert(0);
        if *count >= max_per_artist {
            continue;
        }

        *count += 1;
        seen_signatures.insert(sig);
        result.push(track);

        if result.len() >= limit {
            break;
        }
    }

    result
}

/// Orders tracks so that the same artist never plays twice in a row.
pub fn order_no_consecutive_artists(tracks: Vec<Track>) -> Vec<Track> {
    if tracks.len() <= 1 {
        return tracks;
    }

    let mut remaining = tracks;
    let mut ordered = Vec::with_capacity(remaining.len());

    while !remaining.is_empty() {
        let last_artist = ordered.last().map(|t: &Track| t.artist.trim().to_lowercase());

        let next_idx = remaining
            .iter()
            .position(|t| {
                if let Some(ref prev) = last_artist {
                    t.artist.trim().to_lowercase() != *prev
                } else {
                    true
                }
            })
            .unwrap_or(0); // If unavoidable, take the first available

        ordered.push(remaining.remove(next_idx));
    }

    ordered
}

#[tauri::command]
pub async fn get_recommendations(limit: Option<usize>) -> Result<RecommendationsResult, String> {
    let target_limit = limit.unwrap_or(24).clamp(8, 50);
    let binary = get_ytdlp_path();

    let (history_entries, now) = {
        let guard = get_history()
            .lock()
            .map_err(|_| "History lock poisoned".to_string())?;
        let now = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .map(|d| d.as_secs())
            .unwrap_or(0);
        (guard.clone(), now)
    };

    // Determine recent signatures played in the last 7 days
    let recent_7d_signatures: HashSet<String> = history_entries
        .iter()
        .filter(|e| now.saturating_sub(e.played_at_unix) < SEVEN_DAYS_SECS)
        .map(|e| e.signature.clone())
        .collect();

    // Cold start check: if history is empty or too few plays
    if history_entries.is_empty() {
        let seed_queries = [
            "ytsearch10:Trending Hits 2026",
            "ytsearch10:Pop Music Hits",
            "ytsearch10:Rock Classics",
            "ytsearch10:Lofi Beats to relax",
        ];

        let mut pool = Vec::new();
        for q in seed_queries {
            if let Ok(mut tracks) = execute_ytdlp_search(q, &binary).await {
                pool.append(&mut tracks);
            }
            if pool.len() >= target_limit * 2 {
                break;
            }
        }

        let filtered = filter_diverse_tracks(pool, &HashSet::new(), 2, target_limit);
        return Ok(RecommendationsResult {
            title: "Start here".into(),
            tracks: filtered,
        });
    }

    // Warm start: compute artist affinity scores
    // completed = +3.0, skip < 30s = -2.0, play > 30s = +1.0
    let mut artist_scores: HashMap<String, f64> = HashMap::new();
    let mut original_artist_names: HashMap<String, String> = HashMap::new();

    for entry in &history_entries {
        let key = entry.artist.trim().to_lowercase();
        if key.is_empty() || key == "unknown artist" {
            continue;
        }
        original_artist_names
            .entry(key.clone())
            .or_insert_with(|| entry.artist.clone());

        let score = if entry.completed {
            3.0
        } else if entry.skipped_before_seconds.unwrap_or(0) < 30 {
            -2.0
        } else {
            1.0
        };

        *artist_scores.entry(key).or_insert(0.0) += score;
    }

    let mut ranked_artists: Vec<(String, f64)> = artist_scores.into_iter().collect();
    ranked_artists.sort_by(|a, b| b.1.partial_cmp(&a.1).unwrap_or(std::cmp::Ordering::Equal));

    let top_artists: Vec<String> = ranked_artists
        .into_iter()
        .filter(|(_, score)| *score > 0.0)
        .take(4)
        .map(|(k, _)| original_artist_names.get(&k).cloned().unwrap_or(k))
        .collect();

    let mut pool = Vec::new();

    // 1. Top artist tracks
    for artist in &top_artists {
        let q = format!("ytsearch8:{} best songs", artist);
        if let Ok(mut tracks) = execute_ytdlp_search(&q, &binary).await {
            pool.append(&mut tracks);
        }
    }

    // 2. Genre tracks from iTunes integration
    if let Some(first_artist) = top_artists.first() {
        if let Some(genre) = detect_genre_for_artist(first_artist).await {
            let q = format!("ytsearch8:{} music hits", genre);
            if let Ok(mut tracks) = execute_ytdlp_search(&q, &binary).await {
                pool.append(&mut tracks);
            }
        }
    }

    // 3. Apply diversity rules: max 2 per artist, max 1 per signature, exclude last 7d
    let diverse = filter_diverse_tracks(pool, &recent_7d_signatures, 2, target_limit);

    // If recency filtering was overly strict (e.g. fewer than 6 tracks), relax recency constraint
    let final_tracks = if diverse.len() < 6 {
        filter_diverse_tracks(diverse, &HashSet::new(), 2, target_limit)
    } else {
        diverse
    };

    Ok(RecommendationsResult {
        title: "Made from your listening".into(),
        tracks: final_tracks,
    })
}

#[tauri::command]
pub async fn build_radio(seed_track: Track, limit: Option<usize>) -> Result<Vec<Track>, String> {
    let target_limit = limit.unwrap_or(20).clamp(15, 25);
    let binary = get_ytdlp_path();

    // Get last 50 played signatures to avoid immediate repeats
    let recent_50_signatures: HashSet<String> = {
        let guard = get_history()
            .lock()
            .map_err(|_| "History lock poisoned".to_string())?;
        guard
            .iter()
            .rev()
            .take(50)
            .map(|e| e.signature.clone())
            .collect()
    };

    let mut excluded_signatures = recent_50_signatures;
    let seed_sig = if seed_track.signature.is_empty() {
        get_title_signature_string(&seed_track.title, &seed_track.artist)
    } else {
        seed_track.signature.clone()
    };
    if !seed_sig.is_empty() {
        excluded_signatures.insert(seed_sig);
    }

    let mut radio_pool = Vec::new();

    // A. 40% same artist
    let same_artist_target = (target_limit * 40) / 100;
    let artist_clean = seed_track.artist.trim().to_string();
    if !artist_clean.is_empty() && artist_clean != "Unknown Artist" {
        let q = format!("ytsearch12:{} greatest hits songs", artist_clean);
        if let Ok(tracks) = execute_ytdlp_search(&q, &binary).await {
            let artist_slice =
                filter_diverse_tracks(tracks, &excluded_signatures, 3, same_artist_target);
            for t in &artist_slice {
                excluded_signatures.insert(t.signature.clone());
            }
            radio_pool.extend(artist_slice);
        }
    }

    // B. 40% same genre via iTunes detection
    let genre_target = (target_limit * 40) / 100;
    let detected_genre = detect_genre_for_artist(&artist_clean).await;
    let genre_query = if let Some(ref g) = detected_genre {
        format!("ytsearch12:{} hits songs", g)
    } else {
        format!("ytsearch12:{} mix songs", seed_track.title)
    };

    if let Ok(tracks) = execute_ytdlp_search(&genre_query, &binary).await {
        let genre_slice = filter_diverse_tracks(tracks, &excluded_signatures, 2, genre_target);
        for t in &genre_slice {
            excluded_signatures.insert(t.signature.clone());
        }
        radio_pool.extend(genre_slice);
    }

    // C. 20% discovery slice (remaining needed)
    let needed = target_limit.saturating_sub(radio_pool.len());
    if needed > 0 {
        let discovery_query = format!("ytsearch10:{} related songs", artist_clean);
        if let Ok(tracks) = execute_ytdlp_search(&discovery_query, &binary).await {
            let discovery_slice = filter_diverse_tracks(tracks, &excluded_signatures, 2, needed);
            radio_pool.extend(discovery_slice);
        }
    }

    // Ensure we meet the minimum target count (at least 15 tracks)
    if radio_pool.len() < 15 {
        let fallback_query = "ytsearch15:top hits music";
        if let Ok(tracks) = execute_ytdlp_search(fallback_query, &binary).await {
            let extra = filter_diverse_tracks(
                tracks,
                &excluded_signatures,
                2,
                15 - radio_pool.len(),
            );
            radio_pool.extend(extra);
        }
    }

    // Interleave so no two consecutive tracks have the exact same artist
    let ordered_radio = order_no_consecutive_artists(radio_pool);

    Ok(ordered_radio)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn dummy_track(id: &str, title: &str, artist: &str, sig: &str) -> Track {
        Track {
            id: id.into(),
            title: title.into(),
            artist: artist.into(),
            duration: 180,
            thumbnail: "https://example.com/thumb.jpg".into(),
            source: "youtube".into(),
            signature: sig.into(),
        }
    }

    #[test]
    fn test_diversity_rules_caps_per_artist_and_signature() {
        let tracks = vec![
            dummy_track("1", "Song 1", "Artist A", "sig_a_1"),
            dummy_track("2", "Song 1 Copy", "Artist A", "sig_a_1"), // Duplicate signature
            dummy_track("3", "Song 2", "Artist A", "sig_a_2"),
            dummy_track("4", "Song 3", "Artist A", "sig_a_3"), // Exceeds max 2 per artist
            dummy_track("5", "Song 4", "Artist B", "sig_b_1"),
        ];

        let filtered = filter_diverse_tracks(tracks, &HashSet::new(), 2, 10);
        assert_eq!(filtered.len(), 3);
        assert_eq!(filtered[0].id, "1");
        assert_eq!(filtered[1].id, "3");
        assert_eq!(filtered[2].id, "5");
    }

    #[test]
    fn test_diversity_rules_excludes_recent_signatures() {
        let tracks = vec![
            dummy_track("1", "Song 1", "Artist A", "sig_recent"),
            dummy_track("2", "Song 2", "Artist B", "sig_fresh"),
        ];

        let mut excluded = HashSet::new();
        excluded.insert("sig_recent".into());

        let filtered = filter_diverse_tracks(tracks, &excluded, 2, 10);
        assert_eq!(filtered.len(), 1);
        assert_eq!(filtered[0].id, "2");
    }

    #[test]
    fn test_diversity_rules_excludes_same_song_variations() {
        let tracks = vec![
            dummy_track("1", "Woh Lamhe", "Atif Aslam", "aslam atif lamhe woh"),
            dummy_track("2", "Woh Lamhe Woh Baatein", "Atif Aslam", "aslam atif baatein lamhe woh"),
            dummy_track("3", "Aadat", "Atif Aslam", "aadat aslam atif"),
        ];

        let filtered = filter_diverse_tracks(tracks, &HashSet::new(), 2, 10);
        // Track 2 is a variation of Track 1, so it must be excluded. Track 3 is a different song by the same artist.
        assert_eq!(filtered.len(), 2);
        assert_eq!(filtered[0].id, "1");
        assert_eq!(filtered[1].id, "3");
    }

    #[test]
    fn test_order_no_consecutive_artists() {
        let tracks = vec![
            dummy_track("1", "Song A1", "Artist A", "sig_1"),
            dummy_track("2", "Song A2", "Artist A", "sig_2"),
            dummy_track("3", "Song B1", "Artist B", "sig_3"),
            dummy_track("4", "Song B2", "Artist B", "sig_4"),
        ];

        let ordered = order_no_consecutive_artists(tracks);
        assert_eq!(ordered.len(), 4);

        for i in 0..ordered.len() - 1 {
            assert_ne!(
                ordered[i].artist.to_lowercase(),
                ordered[i + 1].artist.to_lowercase(),
                "Consecutive tracks must not share the same artist at index {}",
                i
            );
        }
    }

    #[test]
    fn test_history_bounded_capacity() {
        // Clear history for test
        {
            let mut guard = get_history().lock().unwrap();
            guard.clear();
        }

        for i in 0..600 {
            add_history_entry(PlayHistoryEntry {
                signature: format!("sig_{}", i),
                track: dummy_track(&format!("{}", i), "T", "A", &format!("sig_{}", i)),
                artist: "A".into(),
                played_at_unix: i as u64,
                completed: true,
                skipped_before_seconds: None,
            });
        }

        let guard = get_history().lock().unwrap();
        assert_eq!(guard.len(), MAX_HISTORY_ENTRIES);
        // Oldest (0..99) should have been pruned
        assert_eq!(guard.first().unwrap().played_at_unix, 100);
        assert_eq!(guard.last().unwrap().played_at_unix, 599);
    }
}
