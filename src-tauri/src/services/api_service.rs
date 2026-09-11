use reqwest::blocking::Client;
use serde_json::Value;
use std::sync::OnceLock;
use std::time::Duration;

use crate::models::song::SongResponse;

static SHARED_BLOCKING_CLIENT: OnceLock<Client> = OnceLock::new();

fn get_client() -> &'static Client {
    SHARED_BLOCKING_CLIENT.get_or_init(|| {
        Client::builder()
            .timeout(Duration::from_secs(10))
            .connect_timeout(Duration::from_secs(5))
            .pool_idle_timeout(Duration::from_secs(90))
            .build()
            .unwrap_or_else(|_| Client::new())
    })
}

// get cover art from music brain api
pub fn get_cover_art_from_music_brainz(
    artist: &str,
    album: &str,
) -> Result<Option<String>, String> {
    let client = get_client();

    let query = format!("artist:\"{}\" AND release:\"{}\"", artist, album);

    let url = format!(
        "https://musicbrainz.org/ws/2/release/?query={}&fmt=json",
        urlencoding::encode(&query)
    );

    let mb_json: Value = client
        .get(&url)
        .header(
            "User-Agent",
            "Sonara/0.2.1 (https://github.com/kaungset03/sonara)",
        )
        .send()
        .map_err(|e| e.to_string())?
        .error_for_status()
        .map_err(|e| e.to_string())?
        .json()
        .map_err(|e| e.to_string())?;

    let Some(mbid) = mb_json["releases"]
        .as_array()
        .and_then(|releases| releases.first())
        .and_then(|release| release.get("id"))
        .and_then(|id| id.as_str())
    else {
        return Ok(None);
    };

    let cover_art_url = format!("https://coverartarchive.org/release/{}/front-500", mbid);
    Ok(Some(cover_art_url))
}

// get artist image from audio db api
pub fn get_artist_image_from_audio_db(artist: &str) -> Result<Option<String>, String> {
    let client = get_client();

    let url = format!(
        "https://theaudiodb.com/api/v1/json/2/search.php?s={}",
        urlencoding::encode(artist)
    );

    let audio_db_json: Value = client
        .get(&url)
        .header(
            "User-Agent",
            "Sonara/0.2.1 (https://github.com/kaungset03/sonara)",
        )
        .send()
        .map_err(|e| e.to_string())?
        .error_for_status()
        .map_err(|e| e.to_string())?
        .json()
        .map_err(|e| e.to_string())?;

    let Some(image_url) = audio_db_json["artists"]
        .as_array()
        .and_then(|artists| artists.first())
        .and_then(|artist| artist.get("strArtistThumb"))
        .and_then(|thumb| thumb.as_str())
    else {
        return Ok(None);
    };

    Ok(Some(image_url.to_string()))
}

pub fn get_song_lyrics_from_lrclib(song: &SongResponse) -> Result<Option<String>, String> {
    // Implementation for fetching lyrics from LRCLib
    let client = get_client();

    let url = format!(
        "https://lrclib.net/api/get?artist_name={}&track_name={}&album_name={}&duration={}",
        urlencoding::encode(&song.artist_name),
        urlencoding::encode(&song.title),
        urlencoding::encode(&song.album_name),
        song.duration
    );

    let response = client
        .get(&url)
        .header(
            "User-Agent",
            "Sonara/0.2.1 (https://github.com/kaungset03/sonara)",
        )
        .send()
        .map_err(|e| e.to_string())?;

    if response.status() == reqwest::StatusCode::NOT_FOUND {
        return Ok(None);
    }

    let response = response.error_for_status().map_err(|e| e.to_string())?;

    let lrclib_json: Value = response.json().map_err(|e| e.to_string())?;

    let Some(lyrics) = lrclib_json["syncedLyrics"].as_str() else {
        return Ok(None);
    };

    Ok(Some(lyrics.to_string()))
}
