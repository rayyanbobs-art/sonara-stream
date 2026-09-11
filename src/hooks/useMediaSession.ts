import { convertFileSrc, invoke } from "@tauri-apps/api/core";
import { listen, UnlistenFn } from "@tauri-apps/api/event";
import { useEffect, useRef } from "react";
import { getOptimizedThumbnail } from "@/utils/thumbnail";

type useMediaSessionProps = {
  song: Song;
  position: number;
  duration: number;
  isPlaying: boolean;
  onPlay: () => void;
  onPause: () => void;
  onNext: () => void;
  onPrevious: () => void;
  onSeek: (position: number) => void;
};

interface WindowWithAndroidMedia extends Window {
  __TAURI_INTERNALS__?: unknown;
  AndroidMedia?: {
    updateMetadata?: (
      title: string,
      artist: string,
      album: string,
      artwork: string,
      duration: number,
      isPlaying: boolean,
      position: number
    ) => void;
    updatePlaybackState?: (isPlaying: boolean, position: number) => void;
    stopPlayback?: () => void;
  };
}

const useMediaSession = ({
  song,
  position,
  duration,
  isPlaying,
  onPlay,
  onPause,
  onNext,
  onPrevious,
  onSeek,
}: useMediaSessionProps) => {
  const isTauri = typeof window !== "undefined" && "__TAURI_INTERNALS__" in window;

  const rawArtwork = song?.album_cover_path
    ? song.album_cover_path.startsWith("http://") || song.album_cover_path.startsWith("https://")
      ? song.album_cover_path
      : convertFileSrc(song.album_cover_path)
    : "/128x128@2x.png";

  const artworkPath = rawArtwork.startsWith("http")
    ? getOptimizedThumbnail(rawArtwork, "card")
    : rawArtwork;

  const callbacksRef = useRef({ onPlay, onPause, onNext, onPrevious, onSeek, isPlaying });
  useEffect(() => {
    callbacksRef.current = { onPlay, onPause, onNext, onPrevious, onSeek, isPlaying };
  }, [onPlay, onPause, onNext, onPrevious, onSeek, isPlaying]);

  // 1. Standard HTML5 MediaSession API
  useEffect(() => {
    if ("mediaSession" in navigator && song) {
      navigator.mediaSession.metadata = new MediaMetadata({
        title: song.title || "Unknown Title",
        artist: song.artist_name || "Unknown Artist",
        album: song.album_name || "Unknown Album",
        artwork: [
          {
            src: artworkPath,
            sizes: "256x256",
            type: "image/png",
          },
        ],
      });
    }
  }, [song, artworkPath]);

  useEffect(() => {
    if ("mediaSession" in navigator) {
      navigator.mediaSession.playbackState = isPlaying ? "playing" : "paused";
    }
  }, [isPlaying]);

  useEffect(() => {
    if ("mediaSession" in navigator && duration > 0) {
      try {
        navigator.mediaSession.setPositionState({
          duration: duration,
          playbackRate: 1,
          position: position,
        });
      } catch (e) {
        console.warn("Could not set OS position state:", e);
      }
    }
  }, [duration, isPlaying, position]);

  useEffect(() => {
    if ("mediaSession" in navigator) {
      if (onPlay) navigator.mediaSession.setActionHandler("play", onPlay);
      if (onPause) navigator.mediaSession.setActionHandler("pause", onPause);
      if (onNext) navigator.mediaSession.setActionHandler("nexttrack", onNext);
      if (onPrevious)
        navigator.mediaSession.setActionHandler("previoustrack", onPrevious);

      if (onSeek) {
        navigator.mediaSession.setActionHandler("seekto", (details) => {
          if (details.seekTime !== undefined) {
            onSeek(details.seekTime);
          }
        });
      }

      return () => {
        navigator.mediaSession.setActionHandler("play", null);
        navigator.mediaSession.setActionHandler("pause", null);
        navigator.mediaSession.setActionHandler("nexttrack", null);
        navigator.mediaSession.setActionHandler("previoustrack", null);
        navigator.mediaSession.setActionHandler("seekto", null);
      };
    }
  }, [onPlay, onPause, onNext, onPrevious, onSeek]);

  // 2. Desktop OS Media Keys (Windows SMTC / Linux)
  useEffect(() => {
    if (!isTauri) return;

    let unlistenKey: UnlistenFn | null = null;
    let unlistenSeek: UnlistenFn | null = null;

    const setupListeners = async () => {
      try {
        unlistenKey = await listen<string>("media-key", (event) => {
          const action = event.payload;
          switch (action) {
            case "play":
              if (!callbacksRef.current.isPlaying) callbacksRef.current.onPlay();
              break;
            case "pause":
              if (callbacksRef.current.isPlaying) callbacksRef.current.onPause();
              break;
            case "toggle":
              if (callbacksRef.current.isPlaying) callbacksRef.current.onPause();
              else callbacksRef.current.onPlay();
              break;
            case "next":
              callbacksRef.current.onNext();
              break;
            case "prev":
              callbacksRef.current.onPrevious();
              break;
            case "stop":
              if (callbacksRef.current.isPlaying) callbacksRef.current.onPause();
              break;
          }
        });

        unlistenSeek = await listen<number>("media-key-seek", (event) => {
          if (typeof event.payload === "number") {
            callbacksRef.current.onSeek(event.payload);
          }
        });
      } catch (err) {
        console.debug("Failed to set up desktop media key listeners:", err);
      }
    };

    setupListeners();

    return () => {
      if (unlistenKey) unlistenKey();
      if (unlistenSeek) unlistenSeek();
    };
  }, [isTauri]);

  // 3. Android Native Bridge Media Events (Lockscreen & Notification action taps)
  useEffect(() => {
    if (typeof window === "undefined") return;

    const handlePlay = () => {
      if (!callbacksRef.current.isPlaying) callbacksRef.current.onPlay();
    };
    const handlePause = () => {
      if (callbacksRef.current.isPlaying) callbacksRef.current.onPause();
    };
    const handleToggle = () => {
      if (callbacksRef.current.isPlaying) callbacksRef.current.onPause();
      else callbacksRef.current.onPlay();
    };
    const handleNext = () => callbacksRef.current.onNext();
    const handlePrev = () => callbacksRef.current.onPrevious();
    const handleSeek = (e: Event) => {
      const detail = (e as CustomEvent)?.detail;
      if (typeof detail === "number") {
        callbacksRef.current.onSeek(detail);
      }
    };

    window.addEventListener("sonara-media-play", handlePlay);
    window.addEventListener("sonara-media-pause", handlePause);
    window.addEventListener("sonara-media-toggle", handleToggle);
    window.addEventListener("sonara-media-next", handleNext);
    window.addEventListener("sonara-media-prev", handlePrev);
    window.addEventListener("sonara-media-seek", handleSeek);

    return () => {
      window.removeEventListener("sonara-media-play", handlePlay);
      window.removeEventListener("sonara-media-pause", handlePause);
      window.removeEventListener("sonara-media-toggle", handleToggle);
      window.removeEventListener("sonara-media-next", handleNext);
      window.removeEventListener("sonara-media-prev", handlePrev);
      window.removeEventListener("sonara-media-seek", handleSeek);
    };
  }, []);

  // 4. Update OS Media Metadata (Desktop SMTC + Android Native MediaSession)
  useEffect(() => {
    if (!song) {
      if (isTauri) invoke("clear_media_controls").catch(() => {});
      return;
    }

    const title = song.title || "Unknown Title";
    const artist = song.artist_name || "Unknown Artist";
    const album = song.album_name || artist;
    const durSecs = duration > 0 ? duration : song.duration || 0;

    // Desktop SMTC
    if (isTauri) {
      invoke("update_media_metadata", {
        title,
        artist,
        album,
        coverUrl: artworkPath,
        durationSecs: durSecs,
        isPlaying,
        positionSecs: position,
      }).catch((err) => console.debug("update_media_metadata error:", err));
    }

    // Android Native MediaSession bridge
    if (typeof window !== "undefined") {
      const androidWindow = window as unknown as WindowWithAndroidMedia;
      if (typeof androidWindow.AndroidMedia?.updateMetadata === "function") {
        try {
          androidWindow.AndroidMedia.updateMetadata(
            title,
            artist,
            album,
            song?.album_cover_path || artworkPath,
            durSecs,
            isPlaying,
            position
          );
        } catch (err) {
          console.debug("AndroidMedia updateMetadata error:", err);
        }
      }
    }
  }, [isTauri, song?.id, song?.title, song?.artist_name, artworkPath, isPlaying, duration]);

  // 5. Update Playback State (Playing / Paused)
  useEffect(() => {
    if (!song) return;

    if (isTauri) {
      invoke("update_playback_state", {
        isPlaying,
        positionSecs: position,
      }).catch(() => {});
    }

    if (typeof window !== "undefined") {
      const androidWindow = window as unknown as WindowWithAndroidMedia;
      if (typeof androidWindow.AndroidMedia?.updatePlaybackState === "function") {
        try {
          androidWindow.AndroidMedia.updatePlaybackState(isPlaying, position);
        } catch (err) {
          console.debug("AndroidMedia updatePlaybackState error:", err);
        }
      }
    }
  }, [isTauri, isPlaying]);

  // 6. Periodic playback timeline sync
  useEffect(() => {
    if (!isPlaying || !song) return;

    const interval = setInterval(() => {
      if (isTauri) {
        invoke("update_playback_state", {
          isPlaying: true,
          positionSecs: position,
        }).catch(() => {});
      }

      if (typeof window !== "undefined") {
        const androidWindow = window as unknown as WindowWithAndroidMedia;
        if (typeof androidWindow.AndroidMedia?.updatePlaybackState === "function") {
          try {
            androidWindow.AndroidMedia.updatePlaybackState(true, position);
          } catch {
            // ignore periodic sync error
          }
        }
      }
    }, 5000);

    return () => clearInterval(interval);
  }, [isTauri, isPlaying, song?.id, position]);
};

export default useMediaSession;
