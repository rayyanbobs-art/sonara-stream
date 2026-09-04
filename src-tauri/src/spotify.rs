use serde::Deserialize;
use crate::youtube::Track;

#[derive(Debug, Deserialize)]
struct SpotifyOEmbed {
    title: Option<String>,
    thumbnail_url: Option<String>,
}

#[tauri::command]
pub async fn resolve_spotify_track(url: String) -> Result<Track, String> {
    let client = reqwest::Client::new();
    let oembed_url = format!("https://open.spotify.com/oembed?url={}", urlencoding::encode(&url));

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
