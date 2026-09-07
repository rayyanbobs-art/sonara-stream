#[cfg(target_os = "windows")]
use souvlaki::{
    MediaControlEvent, MediaControls, MediaMetadata, MediaPlayback, MediaPosition, PlatformConfig,
};
use std::sync::Mutex;
use std::time::Duration;
use tauri::{Emitter, Manager};

#[cfg(target_os = "windows")]
static CONTROLS: Mutex<Option<MediaControls>> = Mutex::new(None);

pub fn init_media_controls(app: &mut tauri::App) -> Result<(), Box<dyn std::error::Error>> {
    #[cfg(target_os = "windows")]
    {
        let main_window = app.get_webview_window("main");
        let hwnd = main_window
            .and_then(|w| w.hwnd().ok())
            .map(|h| h.0);

        let config = PlatformConfig {
            dbus_name: "sonara_stream",
            display_name: "Sonara Stream",
            hwnd,
        };

        match MediaControls::new(config) {
            Ok(mut controls) => {
                let app_handle = app.handle().clone();
                let attach_res = controls.attach(move |event: MediaControlEvent| match event {
                    MediaControlEvent::Play => {
                        let _ = app_handle.emit("media-key", "play");
                    }
                    MediaControlEvent::Pause => {
                        let _ = app_handle.emit("media-key", "pause");
                    }
                    MediaControlEvent::Toggle => {
                        let _ = app_handle.emit("media-key", "toggle");
                    }
                    MediaControlEvent::Next => {
                        let _ = app_handle.emit("media-key", "next");
                    }
                    MediaControlEvent::Previous => {
                        let _ = app_handle.emit("media-key", "prev");
                    }
                    MediaControlEvent::Stop => {
                        let _ = app_handle.emit("media-key", "stop");
                    }
                    MediaControlEvent::SetPosition(MediaPosition(dur)) => {
                        let _ = app_handle.emit("media-key-seek", dur.as_secs_f64());
                    }
                    _ => {}
                });

                if attach_res.is_ok() {
                    if let Ok(mut guard) = CONTROLS.lock() {
                        *guard = Some(controls);
                    }
                }
            }
            Err(e) => {
                eprintln!("Failed to initialize Windows SMTC media controls: {:?}", e);
            }
        }
    }
    #[cfg(not(target_os = "windows"))]
    {
        let _ = app;
    }
    Ok(())
}

#[tauri::command]
pub fn update_media_metadata(
    title: String,
    artist: String,
    album: Option<String>,
    cover_url: Option<String>,
    duration_secs: Option<f64>,
    is_playing: bool,
    position_secs: Option<f64>,
) -> Result<(), String> {
    #[cfg(target_os = "windows")]
    {
        let mut guard = CONTROLS.lock().map_err(|e| e.to_string())?;
        if let Some(ref mut controls) = *guard {
            let dur = duration_secs.and_then(|s| {
                if s > 0.0 {
                    Some(Duration::from_secs_f64(s))
                } else {
                    None
                }
            });
            let alb = album.as_deref().unwrap_or(artist.as_str());

            let _ = controls.set_metadata(MediaMetadata {
                title: Some(&title),
                artist: Some(&artist),
                album: Some(alb),
                cover_url: cover_url.as_deref(),
                duration: dur,
            });

            let pos = position_secs.map(|s| MediaPosition(Duration::from_secs_f64(s.max(0.0))));
            let playback = if is_playing {
                MediaPlayback::Playing { progress: pos }
            } else {
                MediaPlayback::Paused { progress: pos }
            };
            let _ = controls.set_playback(playback);
        }
    }
    #[cfg(not(target_os = "windows"))]
    {
        let _ = (title, artist, album, cover_url, duration_secs, is_playing, position_secs);
    }
    Ok(())
}

#[tauri::command]
pub fn update_playback_state(
    is_playing: bool,
    position_secs: Option<f64>,
) -> Result<(), String> {
    #[cfg(target_os = "windows")]
    {
        let mut guard = CONTROLS.lock().map_err(|e| e.to_string())?;
        if let Some(ref mut controls) = *guard {
            let pos = position_secs.map(|s| MediaPosition(Duration::from_secs_f64(s.max(0.0))));
            let playback = if is_playing {
                MediaPlayback::Playing { progress: pos }
            } else {
                MediaPlayback::Paused { progress: pos }
            };
            let _ = controls.set_playback(playback);
        }
    }
    #[cfg(not(target_os = "windows"))]
    {
        let _ = (is_playing, position_secs);
    }
    Ok(())
}

#[tauri::command]
pub fn clear_media_controls() -> Result<(), String> {
    #[cfg(target_os = "windows")]
    {
        let mut guard = CONTROLS.lock().map_err(|e| e.to_string())?;
        if let Some(ref mut controls) = *guard {
            let _ = controls.set_playback(MediaPlayback::Stopped);
        }
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_media_controls_commands_safe_without_init() {
        // Calling update without initialized controls should return Ok(()) gracefully
        assert!(update_media_metadata(
            "Test Track".into(),
            "Test Artist".into(),
            None,
            None,
            Some(180.0),
            true,
            Some(10.0),
        ).is_ok());

        assert!(update_playback_state(false, Some(15.0)).is_ok());
        assert!(clear_media_controls().is_ok());
    }
}
