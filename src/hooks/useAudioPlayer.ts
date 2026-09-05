import { useState, useRef, useEffect, useCallback } from "react";
import { invoke } from "@tauri-apps/api/core";
import { Track } from "../types";

interface UseAudioPlayerOptions {
  onEnded?: () => void;
  onNext?: () => void;
  onPrev?: () => void;
  onError?: (msg: string | null) => void;
}

export function useAudioPlayer(options: UseAudioPlayerOptions = {}) {
  const { onEnded, onNext, onPrev, onError } = options;

  const [currentTrack, setCurrentTrack] = useState<Track | null>(null);
  const [isPlaying, setIsPlaying] = useState(false);
  const [isBuffering, setIsBuffering] = useState(false);
  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState(0);
  const [volume, setVolumeState] = useState(0.85);

  const audioRef = useRef<HTMLAudioElement | null>(null);
  const preloadAudioRef = useRef<HTMLAudioElement | null>(null);
  const preloadTrackRef = useRef<Track | null>(null);

  const currentTrackRef = useRef<Track | null>(null);
  const retryCountRef = useRef<number>(0);
  const playGenerationRef = useRef<number>(0);

  // Fresh callbacks in refs to avoid stale closures in audio events
  const onEndedRef = useRef(onEnded);
  const onNextRef = useRef(onNext);
  const onPrevRef = useRef(onPrev);
  const onErrorRef = useRef(onError);

  useEffect(() => {
    onEndedRef.current = onEnded;
    onNextRef.current = onNext;
    onPrevRef.current = onPrev;
    onErrorRef.current = onError;
  });

  // Audio element setup (mount once)
  useEffect(() => {
    const audio = new Audio();
    audio.preload = "auto";
    audio.volume = volume;
    audioRef.current = audio;

    audio.ontimeupdate = () => {
      setCurrentTime(audio.currentTime);
    };

    audio.onloadedmetadata = () => {
      setDuration(audio.duration || 0);
      setIsBuffering(false);
    };

    audio.onwaiting = () => {
      setIsBuffering(true);
    };

    audio.onplaying = () => {
      setIsBuffering(false);
      setIsPlaying(true);
    };

    audio.onpause = () => {
      setIsPlaying(false);
    };

    audio.onended = () => {
      if (onEndedRef.current) {
        onEndedRef.current();
      } else if (onNextRef.current) {
        onNextRef.current();
      }
    };

    audio.onerror = async () => {
      const activeTrack = currentTrackRef.current;
      const gen = playGenerationRef.current;
      if (!activeTrack) return;

      // Exactly 1 retry bypassing the cache before surfacing failure
      if (retryCountRef.current === 0) {
        retryCountRef.current += 1;
        setIsBuffering(true);
        onErrorRef.current?.("Playback error: refreshing stream URL...");
        try {
          const freshUrl: string = await invoke("get_stream_url", {
            id: activeTrack.id,
            bypassCache: true,
          });
          if (gen !== playGenerationRef.current) {
            return;
          }
          if (audioRef.current && typeof freshUrl === "string" && freshUrl.length > 0) {
            audioRef.current.src = freshUrl;
            try {
              await audioRef.current.play();
            } catch (playErr: unknown) {
              if (gen !== playGenerationRef.current) return;
              if (
                playErr instanceof DOMException &&
                (playErr.name === "AbortError" || playErr.name === "NotAllowedError")
              ) {
                return;
              }
              throw playErr;
            }
            if (gen !== playGenerationRef.current) return;
            onErrorRef.current?.(null);
            return;
          }
        } catch (retryErr: unknown) {
          if (gen !== playGenerationRef.current) return;
          if (
            retryErr instanceof DOMException &&
            (retryErr.name === "AbortError" || retryErr.name === "NotAllowedError")
          ) {
            return;
          }
          console.error("Audio stream retry failed:", retryErr);
        }
      }

      if (gen !== playGenerationRef.current) {
        return;
      }

      setIsBuffering(false);
      setIsPlaying(false);
      onErrorRef.current?.(
        "Stream playback failed. The audio stream could not be loaded. Please choose another track."
      );
    };

    return () => {
      audio.pause();
      audio.src = "";
    };
  }, []);

  // Pre-load audio engine initialization
  useEffect(() => {
    const pAudio = new Audio();
    pAudio.preload = "auto";
    preloadAudioRef.current = pAudio;
    return () => {
      pAudio.src = "";
    };
  }, []);

  const togglePlay = useCallback(() => {
    if (!audioRef.current || !currentTrackRef.current) return;
    if (isPlaying) {
      audioRef.current.pause();
    } else {
      audioRef.current.play().catch(console.error);
    }
  }, [isPlaying]);

  // MediaSession integration
  useEffect(() => {
    if ("mediaSession" in navigator && currentTrack) {
      navigator.mediaSession.metadata = new MediaMetadata({
        title: currentTrack.title,
        artist: currentTrack.artist,
        artwork: [{ src: currentTrack.thumbnail }],
      });

      navigator.mediaSession.setActionHandler("play", () => togglePlay());
      navigator.mediaSession.setActionHandler("pause", () => togglePlay());
      navigator.mediaSession.setActionHandler("nexttrack", () => onNextRef.current?.());
      navigator.mediaSession.setActionHandler("previoustrack", () => onPrevRef.current?.());
    }
  }, [currentTrack, togglePlay]);

  const seek = useCallback((time: number) => {
    if (!audioRef.current) return;
    audioRef.current.currentTime = time;
    setCurrentTime(time);
  }, []);

  const setVolume = useCallback((vol: number) => {
    setVolumeState(vol);
    if (audioRef.current) {
      audioRef.current.volume = vol;
    }
  }, []);

  const playTrack = useCallback(async (track: Track): Promise<void> => {
    if (!audioRef.current) return;

    if (currentTrackRef.current?.id === track.id) {
      togglePlay();
      return;
    }

    const generation = ++playGenerationRef.current;
    const previousTrack = currentTrackRef.current;
    currentTrackRef.current = track;
    retryCountRef.current = 0;
    setCurrentTrack(track);
    setIsBuffering(true);
    onErrorRef.current?.(null);

    // Cancel superseded backend stream request if any
    if (previousTrack && previousTrack.id !== track.id) {
      invoke("cancel_stream_request", { id: previousTrack.id }).catch(() => {});
    }

    try {
      let streamUrl: string;
      if (
        preloadAudioRef.current &&
        preloadAudioRef.current.src &&
        preloadTrackRef.current?.id === track.id
      ) {
        streamUrl = preloadAudioRef.current.src;
      } else {
        streamUrl = await invoke("get_stream_url", { id: track.id });
      }

      // If a newer generation started while waiting for streamUrl, discard this result
      if (generation !== playGenerationRef.current) {
        return;
      }

      audioRef.current.src = streamUrl;
      audioRef.current.currentTime = 0;

      try {
        await audioRef.current.play();
      } catch (playErr: unknown) {
        // Check generation first
        if (generation !== playGenerationRef.current) {
          return;
        }
        // Treat AbortError / NotAllowedError (autoplay policy) as non-errors
        if (
          playErr instanceof DOMException &&
          (playErr.name === "AbortError" || playErr.name === "NotAllowedError")
        ) {
          return;
        }
        throw playErr;
      }

      if (generation !== playGenerationRef.current) {
        return;
      }

      setIsPlaying(true);
      setDuration(track.duration || 0);
    } catch (err: unknown) {
      if (generation !== playGenerationRef.current) {
        return;
      }
      if (
        err instanceof DOMException &&
        (err.name === "AbortError" || err.name === "NotAllowedError")
      ) {
        return;
      }
      console.error("Playback error:", err);
      setIsBuffering(false);
      setIsPlaying(false);
      onErrorRef.current?.(typeof err === "string" ? err : "Failed to stream track.");
      throw err;
    }
  }, [togglePlay]);

  return {
    currentTrack,
    setCurrentTrack,
    currentTrackRef,
    isPlaying,
    isBuffering,
    setIsBuffering,
    currentTime,
    duration,
    setDuration,
    volume,
    audioRef,
    preloadAudioRef,
    preloadTrackRef,
    playTrack,
    togglePlay,
    seek,
    setVolume,
  };
}
