import { useState, useRef, useEffect, useCallback } from "react";
import { invoke } from "@tauri-apps/api/core";
import { Track, NavTab } from "../types";
import { isSameSong } from "../utils/trackSignature";

interface UseQueueProps {
  currentTrack: Track | null;
  activeTab: NavTab;
  tracks: Track[];
  favorites: Track[];
  onPlayTrack: (track: Track, preserveQueue?: boolean) => Promise<void>;
  onSelectTrack?: (track: Track) => void;
  audioRef?: React.RefObject<HTMLAudioElement | null>;
  preloadAudioRef?: React.RefObject<HTMLAudioElement | null>;
  preloadTrackRef?: React.RefObject<Track | null>;
}

export function useQueue({
  currentTrack,
  activeTab,
  tracks,
  favorites,
  onPlayTrack,
  onSelectTrack,
  audioRef,
  preloadAudioRef,
  preloadTrackRef,
}: UseQueueProps) {
  const [upNextMix, setUpNextMix] = useState<Track[]>([]);
  const [isQueueOpen, setIsQueueOpen] = useState(false);

  const [shuffleDefault, setShuffleDefault] = useState<boolean>(() => {
    return localStorage.getItem("sonara_shuffle_default") === "true";
  });
  const [isShuffle, setIsShuffle] = useState<boolean>(() => {
    return localStorage.getItem("sonara_shuffle_default") === "true";
  });

  const prefetchedIds = useRef<Set<string>>(new Set());
  const recentlyPlayedSignatures = useRef<Set<string>>(new Set());
  const historyRef = useRef<Track[]>([]);
  const debounceTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Keep fresh references to avoid stale closure issues
  const onPlayTrackRef = useRef(onPlayTrack);
  const onSelectTrackRef = useRef(onSelectTrack);
  const currentTrackRef = useRef(currentTrack);
  const activeTabRef = useRef(activeTab);
  const tracksRef = useRef(tracks);
  const favoritesRef = useRef(favorites);
  const isShuffleRef = useRef(isShuffle);
  const upNextMixRef = useRef(upNextMix);

  useEffect(() => {
    onPlayTrackRef.current = onPlayTrack;
    onSelectTrackRef.current = onSelectTrack;
    currentTrackRef.current = currentTrack;
    activeTabRef.current = activeTab;
    tracksRef.current = tracks;
    favoritesRef.current = favorites;
    isShuffleRef.current = isShuffle;
    upNextMixRef.current = upNextMix;
  });

  useEffect(() => {
    return () => {
      if (debounceTimeoutRef.current) {
        clearTimeout(debounceTimeoutRef.current);
      }
    };
  }, []);

  const handleShuffleDefaultChange = useCallback((val: boolean) => {
    setShuffleDefault(val);
    setIsShuffle(val);
    localStorage.setItem("sonara_shuffle_default", String(val));
  }, []);

  const toggleShuffle = useCallback(() => {
    setIsShuffle((prev) => !prev);
  }, []);

  const toggleQueue = useCallback(() => {
    setIsQueueOpen((prev) => !prev);
  }, []);

  const handlePrefetchTrack = useCallback((track: Track) => {
    if (!track.id || prefetchedIds.current.has(track.id)) return;
    prefetchedIds.current.add(track.id);
    invoke("get_stream_url", { id: track.id }).catch(() => {});
  }, []);

  const isRecentlyPlayed = useCallback((track: Track): boolean => {
    if (recentlyPlayedSignatures.current.has(track.id)) return true;
    return Boolean(track.signature && recentlyPlayedSignatures.current.has(track.signature));
  }, []);

  const markAsPlayed = useCallback((track: Track) => {
    recentlyPlayedSignatures.current.add(track.id);
    if (track.signature) {
      recentlyPlayedSignatures.current.add(track.signature);
    }
    if (recentlyPlayedSignatures.current.size > 100) {
      const arr = Array.from(recentlyPlayedSignatures.current);
      recentlyPlayedSignatures.current = new Set(arr.slice(arr.length - 60));
    }
  }, []);

  const updateQueueForTrack = useCallback((track: Track, preserveQueue: boolean) => {
    if (!preserveQueue) {
      // Build radio mix from recommendation engine when clicking a fresh song
      invoke<Track[]>("build_radio", { seedTrack: track, limit: 20 })
        .then((mix: Track[]) => {
          if (Array.isArray(mix) && mix.length > 0) {
            const freshMix = mix.filter(
              (m: Track) => !isSameSong(m, track) && !isRecentlyPlayed(m)
            );
            const finalMix = freshMix.length > 0 ? freshMix : mix.filter((m) => !isSameSong(m, track));
            setUpNextMix(finalMix);

            // Pre-fetch the first 3 songs of the mix immediately in the background
            finalMix.slice(0, 3).forEach((mTrack: Track) => {
              if (!prefetchedIds.current.has(mTrack.id)) {
                prefetchedIds.current.add(mTrack.id);
                invoke("get_stream_url", { id: mTrack.id }).catch(() => {});
              }
            });

            // Pre-buffer the 1st song of the genre mix into preload audio element
            if (finalMix.length > 0) {
              invoke("get_stream_url", { id: finalMix[0].id })
                .then((nextUrl) => {
                  if (preloadAudioRef?.current && typeof nextUrl === "string") {
                    preloadAudioRef.current.src = nextUrl;
                    if (preloadTrackRef) {
                      preloadTrackRef.current = finalMix[0];
                    }
                  }
                })
                .catch(() => {});
            }
          }
        })
        .catch((err) => console.error("Radio mix generation error:", err));
    } else {
      // preserveQueue is TRUE (auto-advancing): keep queue and top up if low (< 5 tracks)
      setUpNextMix((currentMix) => {
        if (currentMix.length < 5) {
          invoke<Track[]>("build_radio", { seedTrack: track, limit: 15 })
            .then((moreMix: Track[]) => {
              if (Array.isArray(moreMix) && moreMix.length > 0) {
                setUpNextMix((prev) => {
                  const existingIds = new Set(prev.map((t) => t.id));
                  const newItems = moreMix.filter(
                    (m: Track) =>
                      !existingIds.has(m.id) &&
                      !isSameSong(m, track) &&
                      !isRecentlyPlayed(m)
                  );
                  return [...prev, ...newItems];
                });
              }
            })
            .catch(() => {});
        }

        if (currentMix.length > 0) {
          const nextCandidate = currentMix[0];
          invoke("get_stream_url", { id: nextCandidate.id })
            .then((nextUrl) => {
              if (preloadAudioRef?.current && typeof nextUrl === "string") {
                preloadAudioRef.current.src = nextUrl;
                if (preloadTrackRef) {
                  preloadTrackRef.current = nextCandidate;
                }
              }
            })
            .catch(() => {});
        }

        return currentMix;
      });
    }

    // Speculatively pre-fetch and buffer the next track in background
    let nextCandidate: Track | null = null;
    if (upNextMixRef.current.length > 0) {
      nextCandidate = upNextMixRef.current[0];
    } else {
      const list = activeTabRef.current === "favorites" ? favoritesRef.current : tracksRef.current;
      const currIdx = list.findIndex((t) => t.id === track.id);
      if (currIdx !== -1 && list.length > 1) {
        nextCandidate = list[(currIdx + 1) % list.length];
      }
    }

    if (nextCandidate && !prefetchedIds.current.has(nextCandidate.id)) {
      prefetchedIds.current.add(nextCandidate.id);
      invoke("get_stream_url", { id: nextCandidate.id }).then((nextUrl) => {
        if (preloadAudioRef?.current && typeof nextUrl === "string") {
          preloadAudioRef.current.src = nextUrl;
          if (preloadTrackRef) {
            preloadTrackRef.current = nextCandidate;
          }
        }
      }).catch(() => {});
    }
  }, [isRecentlyPlayed, preloadAudioRef, preloadTrackRef]);

  const triggerDebouncedPlay = useCallback((track: Track, preserveQueue: boolean) => {
    currentTrackRef.current = track;
    historyRef.current.push(track);
    if (historyRef.current.length > 50) {
      historyRef.current.shift();
    }
    onSelectTrackRef.current?.(track);

    if (debounceTimeoutRef.current) {
      clearTimeout(debounceTimeoutRef.current);
    }
    debounceTimeoutRef.current = setTimeout(() => {
      onPlayTrackRef.current(track, preserveQueue);
    }, 200);
  }, []);

  const handleNext = useCallback(async () => {
    const activeCurrent = currentTrackRef.current;
    if (!activeCurrent) return;

    const currentMix = upNextMixRef.current;
    const currentTab = activeTabRef.current;
    const shuffle = isShuffleRef.current;

    // 1. Prioritize upcoming tracks from queue with a DIFFERENT song signature
    if (currentTab !== "favorites" && currentMix.length > 0) {
      let candidateIdx = -1;
      if (shuffle) {
        const candidates = currentMix
          .map((t, idx) => ({ t, idx }))
          .filter(({ t }) => !isSameSong(t, activeCurrent) && !isRecentlyPlayed(t));
        if (candidates.length > 0) {
          const rand = Math.floor(Math.random() * candidates.length);
          candidateIdx = candidates[rand].idx;
        }
      } else {
        candidateIdx = currentMix.findIndex(
          (t) => !isSameSong(t, activeCurrent) && !isRecentlyPlayed(t)
        );
      }

      // If all unplayed items share current song or are played, take any track with DIFFERENT song
      if (candidateIdx === -1) {
        candidateIdx = currentMix.findIndex((t) => !isSameSong(t, activeCurrent));
      }

      if (candidateIdx !== -1) {
        const nextTrack = currentMix[candidateIdx];
        setUpNextMix((prev) => prev.filter((_, idx) => idx > candidateIdx));
        triggerDebouncedPlay(nextTrack, true /* PRESERVE QUEUE! */);
        return;
      }
    }

    // 2. Check active tab list for a track with a DIFFERENT song signature
    const list = currentTab === "favorites" ? favoritesRef.current : tracksRef.current;
    if (list.length > 0) {
      if (shuffle) {
        const candidateTracks = list.filter(
          (t) => !isSameSong(t, activeCurrent) && !isRecentlyPlayed(t)
        );
        if (candidateTracks.length > 0) {
          const rand = Math.floor(Math.random() * candidateTracks.length);
          triggerDebouncedPlay(candidateTracks[rand], true);
          return;
        }
      }

      // Sequential: pick next distinct track with DIFFERENT song
      const currIdx = list.findIndex((t) => t.id === activeCurrent.id);
      let nextTrack: Track | null = null;

      for (let i = 1; i < list.length; i++) {
        const candidate = list[(currIdx + i) % list.length];
        if (!isSameSong(candidate, activeCurrent) && !isRecentlyPlayed(candidate)) {
          nextTrack = candidate;
          break;
        }
      }

      if (!nextTrack) {
        for (let i = 1; i < list.length; i++) {
          const candidate = list[(currIdx + i) % list.length];
          if (!isSameSong(candidate, activeCurrent)) {
            nextTrack = candidate;
            break;
          }
        }
      }

      if (nextTrack) {
        triggerDebouncedPlay(nextTrack, true);
        return;
      }
    }

    // 3. Queue / Playlist exhausted OR every item in queue shares current signature:
    // Build radio from the Rust recommendation engine. Show a brief loading state — never a silent no-op.
    try {
      onSelectTrackRef.current?.({
        ...activeCurrent,
        title: `${activeCurrent.title} (Loading Radio...)`,
      });

      const radioTracks: Track[] = await invoke("build_radio", {
        seedTrack: activeCurrent,
        limit: 20,
      });

      const freshTracks = radioTracks.filter(
        (r) => !isSameSong(r, activeCurrent) && !isRecentlyPlayed(r)
      );
      const usable = freshTracks.length > 0
        ? freshTracks
        : radioTracks.filter((r) => !isSameSong(r, activeCurrent));

      if (usable.length > 0) {
        const next = usable[0];
        setUpNextMix(usable.slice(1));
        triggerDebouncedPlay(next, true);
        return;
      }
    } catch (err) {
      console.error("Radio build error in handleNext:", err);
    }

    // 4. Fallback if build_radio returned empty: get_related_tracks
    try {
      const related: Track[] = await invoke("get_related_tracks", {
        artist: activeCurrent.artist,
        title: activeCurrent.title,
      });

      const freshTracks = related.filter((r) => !isSameSong(r, activeCurrent));
      if (freshTracks.length > 0) {
        const next = freshTracks[0];
        setUpNextMix(freshTracks.slice(1));
        triggerDebouncedPlay(next, true);
        return;
      }
    } catch (err) {
      console.error("Autoplay radio error:", err);
    }
  }, [isRecentlyPlayed, triggerDebouncedPlay]);

  const handlePrev = useCallback(() => {
    const activeCurrent = currentTrackRef.current;
    if (!activeCurrent) return;

    // 1. If playback position > 3s, restart current track
    if (audioRef?.current && audioRef.current.currentTime > 3) {
      audioRef.current.currentTime = 0;
      return;
    }

    // 2. Otherwise navigate to the previous track in history
    if (historyRef.current.length > 1) {
      historyRef.current.pop(); // Remove current track
      const prevTrack = historyRef.current.pop(); // Take previous track
      if (prevTrack) {
        triggerDebouncedPlay(prevTrack, false);
        return;
      }
    }

    // Fallback: active tab list
    const list = activeTabRef.current === "favorites" ? favoritesRef.current : tracksRef.current;
    if (list.length === 0) return;
    const idx = list.findIndex((t) => t.id === activeCurrent.id);
    const prevIdx = (idx - 1 + list.length) % list.length;
    triggerDebouncedPlay(list[prevIdx], false);
  }, [audioRef, triggerDebouncedPlay]);

  return {
    upNextMix,
    setUpNextMix,
    isQueueOpen,
    setIsQueueOpen,
    toggleQueue,
    isShuffle,
    setIsShuffle,
    toggleShuffle,
    shuffleDefault,
    handleShuffleDefaultChange,
    handleNext,
    handlePrev,
    handlePrefetchTrack,
    updateQueueForTrack,
    isRecentlyPlayed,
    markAsPlayed,
  };
}
