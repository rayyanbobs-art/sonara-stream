import { useEffect, useRef } from "react";
import { invoke } from "@tauri-apps/api/core";
import { listen, UnlistenFn } from "@tauri-apps/api/event";
import { Track } from "../types";

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

  // Listen to OS media key events emitted from Rust backend
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
              if (!callbacksRef.current.isPlaying) {
                callbacksRef.current.onTogglePlay();
              }
              break;
            case "pause":
              if (callbacksRef.current.isPlaying) {
                callbacksRef.current.onTogglePlay();
              }
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
              if (callbacksRef.current.isPlaying) {
                callbacksRef.current.onTogglePlay();
              }
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

  // Update OS media metadata when current track changes
  useEffect(() => {
    if (!isTauri) return;

    if (!currentTrack) {
      invoke("clear_media_controls").catch(() => {});
      return;
    }

    invoke("update_media_metadata", {
      title: currentTrack.title,
      artist: currentTrack.artist,
      album: currentTrack.artist,
      coverUrl: currentTrack.thumbnail,
      durationSecs: duration || currentTrack.duration,
      isPlaying,
      positionSecs: currentTime,
    }).catch((err) => console.debug("update_media_metadata error:", err));
  }, [isTauri, currentTrack?.id]);

  // Update OS playback state immediately when play/pause changes
  useEffect(() => {
    if (!isTauri || !currentTrack) return;

    invoke("update_playback_state", {
      isPlaying,
      positionSecs: currentTime,
    }).catch(() => {});
  }, [isTauri, isPlaying]);

  // Update OS playback timeline position every ~5s while playing
  useEffect(() => {
    if (!isTauri || !isPlaying || !currentTrack) return;

    const interval = setInterval(() => {
      invoke("update_playback_state", {
        isPlaying: true,
        positionSecs: currentTime,
      }).catch(() => {});
    }, 5000);

    return () => clearInterval(interval);
  }, [isTauri, isPlaying, currentTrack?.id, currentTime]);
}
