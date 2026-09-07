import { useEffect, useRef } from "react";
import { invoke } from "@tauri-apps/api/core";
import { listen, UnlistenFn } from "@tauri-apps/api/event";
import { Track } from "../types";
import { getOptimizedThumbnail } from "../utils/thumbnail";

interface UseMediaControlsProps {
  currentTrack: Track | null;
  isPlaying: boolean;
  currentTime: number;
  duration: number;
  onTogglePlay: () => void;
  onNext: () => void;
  onPrev: () => void;
  onSeek: (time: number) => void;
}

export function useMediaControls({
  currentTrack,
  isPlaying,
  currentTime,
  duration,
  onTogglePlay,
  onNext,
  onPrev,
  onSeek,
}: UseMediaControlsProps) {
  const isTauri = typeof window !== "undefined" && Boolean((window as any).__TAURI_INTERNALS__);

  const callbacksRef = useRef({ onTogglePlay, onNext, onPrev, onSeek, isPlaying });
  useEffect(() => {
    callbacksRef.current = { onTogglePlay, onNext, onPrev, onSeek, isPlaying };
  }, [onTogglePlay, onNext, onPrev, onSeek, isPlaying]);

  // 1. Listen to Desktop OS media keys (Windows SMTC / Linux MPRIS)
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
              if (!callbacksRef.current.isPlaying) callbacksRef.current.onTogglePlay();
              break;
            case "pause":
              if (callbacksRef.current.isPlaying) callbacksRef.current.onTogglePlay();
              break;
            case "toggle":
              callbacksRef.current.onTogglePlay();
              break;
            case "next":
              callbacksRef.current.onNext();
              break;
            case "prev":
              callbacksRef.current.onPrev();
              break;
            case "stop":
              if (callbacksRef.current.isPlaying) callbacksRef.current.onTogglePlay();
              break;
          }
        });

        unlistenSeek = await listen<number>("media-key-seek", (event) => {
          if (typeof event.payload === "number") {
            callbacksRef.current.onSeek(event.payload);
          }
        });
      } catch (err) {
        console.debug("Failed to set up media key listeners:", err);
      }
    };

    setupListeners();

    return () => {
      if (unlistenKey) unlistenKey();
      if (unlistenSeek) unlistenSeek();
    };
  }, [isTauri]);

  // 2. Listen to Android Native MediaSession & Notification Actions
  useEffect(() => {
    if (typeof window === "undefined") return;

    const handlePlay = () => {
      if (!callbacksRef.current.isPlaying) callbacksRef.current.onTogglePlay();
    };
    const handlePause = () => {
      if (callbacksRef.current.isPlaying) callbacksRef.current.onTogglePlay();
    };
    const handleToggle = () => {
      callbacksRef.current.onTogglePlay();
    };
    const handleNext = () => {
      callbacksRef.current.onNext();
    };
    const handlePrev = () => {
      callbacksRef.current.onPrev();
    };
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

  // 3. Update OS Media Metadata (Desktop Rust + Android Native MediaSession)
  useEffect(() => {
    if (!currentTrack) {
      if (isTauri) invoke("clear_media_controls").catch(() => {});
      return;
    }

    const coverUrl = getOptimizedThumbnail(currentTrack.thumbnail, "card");
    const durSecs = duration || currentTrack.duration;

    // Desktop
    if (isTauri) {
      invoke("update_media_metadata", {
        title: currentTrack.title,
        artist: currentTrack.artist,
        album: currentTrack.artist,
        coverUrl,
        durationSecs: durSecs,
        isPlaying,
        positionSecs: currentTime,
      }).catch((err) => console.debug("update_media_metadata error:", err));
    }

    // Android Native MediaSession bridge
    if (typeof window !== "undefined" && typeof (window as any).AndroidMedia?.updateMetadata === "function") {
      try {
        (window as any).AndroidMedia.updateMetadata(
          currentTrack.title,
          currentTrack.artist,
          currentTrack.artist,
          coverUrl,
          durSecs,
          isPlaying,
          currentTime
        );
      } catch (err) {
        console.debug("AndroidMedia updateMetadata error:", err);
      }
    }
  }, [isTauri, currentTrack?.id, currentTrack?.title, currentTrack?.artist]);

  // 4. Update Playback State (Playing / Paused)
  useEffect(() => {
    if (!currentTrack) return;

    if (isTauri) {
      invoke("update_playback_state", {
        isPlaying,
        positionSecs: currentTime,
      }).catch(() => {});
    }

    if (typeof window !== "undefined" && typeof (window as any).AndroidMedia?.updatePlaybackState === "function") {
      try {
        (window as any).AndroidMedia.updatePlaybackState(isPlaying, currentTime);
      } catch (err) {
        console.debug("AndroidMedia updatePlaybackState error:", err);
      }
    }
  }, [isTauri, isPlaying]);

  // 5. Periodic playback timeline sync
  useEffect(() => {
    if (!isPlaying || !currentTrack) return;

    const interval = setInterval(() => {
      if (isTauri) {
        invoke("update_playback_state", {
          isPlaying: true,
          positionSecs: currentTime,
        }).catch(() => {});
      }

      if (typeof window !== "undefined" && typeof (window as any).AndroidMedia?.updatePlaybackState === "function") {
        try {
          (window as any).AndroidMedia.updatePlaybackState(true, currentTime);
        } catch (_) {}
      }
    }, 5000);

    return () => clearInterval(interval);
  }, [isTauri, isPlaying, currentTrack?.id, currentTime]);
}
