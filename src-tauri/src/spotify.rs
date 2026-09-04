use serde::Deserialize;
use std::time::Duration;
use crate::youtube::Track;

#[derive(Debug, Deserialize)]
struct SpotifyOEmbed {
    title: Option<String>,
    thumbnail_url: Option<String>,
}

#[tauri::command]
pub async fn resolve_spotify_track(url: String) -> Result<Track, String> {
    let clean_url = url.trim();
    if !clean_url.starts_with("https://open.spotify.com/track/") && 
       !clean_url.starts_with("http://open.spotify.com/track/") &&
       !clean_url.starts_with("https://spotify.com/track/") {
        return Err("Invalid Spotify track URL. Only open.spotify.com/track/ links are permitted.".into());
    }

    let client = reqwest::Client::builder()
        .timeout(Duration::from_secs(4))
        .build()
        .map_err(|e| e.to_string())?;

    let oembed_url = format!("https://open.spotify.com/oembed?url={}", urlencoding::encode(clean_url));

    let res = client
        .get(&oembed_url)
        .send()
        .await
        .map_err(|e| format!("Spotify request failed: {}", e))?;

    let oembed: SpotifyOEmbed = res
        .json()
        .await
        .map_err(|e| format!("Failed to parse Spotify metadata: {}", e))?;

    let title = oembed.title.unwrap_or_else(|| "Unknown Track".into());
    let thumbnail = oembed.thumbnail_url.unwrap_or_default();

    // Now find the best matching stream on YouTube
    let yt_results = crate::youtube::search_youtube(title.clone()).await?;
    if let Some(best) = yt_results.first() {
        Ok(Track {
            id: best.id.clone(),
            title: title,
            artist: best.artist.clone(),
            duration: best.duration,
            thumbnail: if !thumbnail.is_empty() { thumbnail } else { best.thumbnail.clone() },
            source: "spotify".into(),
        })
    } else {
        Err("Could not find matching stream for Spotify track".into())
    }
}
