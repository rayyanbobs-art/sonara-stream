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
  preloadAudioRef?: React.RefObject<HTMLAudioElement | null>;
  preloadTrackRef?: React.RefObject<Track | null>;
}

export function useQueue({
  currentTrack,
  activeTab,
  tracks,
  favorites,
  onPlayTrack,
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

  // Keep fresh references to avoid stale closure issues
  const onPlayTrackRef = useRef(onPlayTrack);
  const currentTrackRef = useRef(currentTrack);
  const activeTabRef = useRef(activeTab);
  const tracksRef = useRef(tracks);
  const favoritesRef = useRef(favorites);
  const isShuffleRef = useRef(isShuffle);
  const upNextMixRef = useRef(upNextMix);

  useEffect(() => {
    onPlayTrackRef.current = onPlayTrack;
    currentTrackRef.current = currentTrack;
    activeTabRef.current = activeTab;
    tracksRef.current = tracks;
    favoritesRef.current = favorites;
    isShuffleRef.current = isShuffle;
    upNextMixRef.current = upNextMix;
  });

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
    // Only rebuild entire genre mix when clicking a fresh song from Search/Home (preserveQueue is false)
    if (!preserveQueue) {
      invoke("get_genre_mix", { artist: track.artist, title: track.title })
        .then((mix: any) => {
          if (Array.isArray(mix) && mix.length > 0) {
            const freshMix = mix.filter(
              (m: Track) => !isSameSong(m, track) && !isRecentlyPlayed(m)
            );
            setUpNextMix(freshMix);

            // Pre-fetch the first 3 songs of the mix immediately in the background
            freshMix.slice(0, 3).forEach((mTrack: Track) => {
              if (!prefetchedIds.current.has(mTrack.id)) {
                prefetchedIds.current.add(mTrack.id);
                invoke("get_stream_url", { id: mTrack.id }).catch(() => {});
              }
            });

            // Pre-buffer the 1st song of the genre mix into preload audio element
            if (freshMix.length > 0) {
              invoke("get_stream_url", { id: freshMix[0].id })
                .then((nextUrl) => {
                  if (preloadAudioRef?.current && typeof nextUrl === "string") {
                    preloadAudioRef.current.src = nextUrl;
                    if (preloadTrackRef) {
                      preloadTrackRef.current = freshMix[0];
                    }
                  }
                })
                .catch(() => {});
            }
          }
        })
        .catch((err) => console.error("Genre mix error:", err));
    } else {
      // preserveQueue is TRUE (auto-advancing): keep queue and top up if low (< 4 tracks)
      setUpNextMix((currentMix) => {
        if (currentMix.length < 4) {
          invoke("get_genre_mix", { artist: track.artist, title: track.title })
            .then((moreMix: any) => {
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
    const list = activeTabRef.current === "favorites" ? favoritesRef.current : tracksRef.current;
    const currIdx = list.findIndex((t) => t.id === track.id);
    if (currIdx !== -1 && list.length > 1) {
      const nextTrack = list[(currIdx + 1) % list.length];
      if (nextTrack && !prefetchedIds.current.has(nextTrack.id)) {
        prefetchedIds.current.add(nextTrack.id);
        invoke("get_stream_url", { id: nextTrack.id }).then((nextUrl) => {
          if (preloadAudioRef?.current && typeof nextUrl === "string") {
            preloadAudioRef.current.src = nextUrl;
            if (preloadTrackRef) {
              preloadTrackRef.current = nextTrack;
            }
          }
        }).catch(() => {});
      }
    }
  }, [isRecentlyPlayed, preloadAudioRef, preloadTrackRef]);

  const handleNext = useCallback(async () => {
    const activeCurrent = currentTrackRef.current;
    if (!activeCurrent) return;

    const currentMix = upNextMixRef.current;
    const currentTab = activeTabRef.current;
    const shuffle = isShuffleRef.current;

    // 1. YouTube Music-style Genre Autoplay: Prioritize upcoming tracks from the genre mix
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

      // If all tracks were marked as recently played, take any track that is not the same song
      if (candidateIdx === -1) {
        candidateIdx = currentMix.findIndex((t) => !isSameSong(t, activeCurrent));
      }

      if (candidateIdx !== -1) {
        const nextTrack = currentMix[candidateIdx];
        // Advance queue: drop this track and any skipped before it
        setUpNextMix((prev) => prev.filter((_, idx) => idx > candidateIdx));
        onPlayTrackRef.current(nextTrack, true /* PRESERVE QUEUE! */);
        return;
      }
    }

    const list = currentTab === "favorites" ? favoritesRef.current : tracksRef.current;
    if (list.length > 0) {
      if (shuffle) {
        const candidateTracks = list.filter(
          (t) => !isSameSong(t, activeCurrent) && !isRecentlyPlayed(t)
        );
        if (candidateTracks.length > 0) {
          const rand = Math.floor(Math.random() * candidateTracks.length);
          onPlayTrackRef.current(candidateTracks[rand], true);
          return;
        }
      }

      // Sequential: pick next distinct unplayed track
      const currIdx = list.findIndex((t) => t.id === activeCurrent.id);
      let nextTrack: Track | null = null;

      for (let i = 1; i < list.length; i++) {
        const candidate = list[(currIdx + i) % list.length];
        if (!isSameSong(candidate, activeCurrent) && !isRecentlyPlayed(candidate)) {
          nextTrack = candidate;
          break;
        }
      }

      if (nextTrack) {
        onPlayTrackRef.current(nextTrack, true);
        return;
      }
    }

    // 3. Queue / Playlist exhausted: Fetch FRESH never-before-played related tracks!
    try {
      const related: Track[] = await invoke("get_related_tracks", {
        artist: activeCurrent.artist,
        title: activeCurrent.title,
      });

      const freshTracks = related.filter(
        (r) => !isSameSong(r, activeCurrent) && !isRecentlyPlayed(r)
      );
      if (freshTracks.length > 0) {
        const next = freshTracks[0];
        setUpNextMix(freshTracks.slice(1));
        onPlayTrackRef.current(next, true);
        return;
      }
    } catch (err) {
      console.error("Autoplay radio error:", err);
    }

    // 4. Fallback: pick any track from list that is not the same song
    const fallbackList = currentTab === "favorites" ? favoritesRef.current : tracksRef.current;
    if (fallbackList.length > 1) {
      const candidate = fallbackList.find((t) => !isSameSong(t, activeCurrent));
      if (candidate) {
        onPlayTrackRef.current(candidate, true);
        return;
      }
    }
  }, [isRecentlyPlayed]);

  const handlePrev = useCallback(() => {
    const activeCurrent = currentTrackRef.current;
    const list = activeTabRef.current === "favorites" ? favoritesRef.current : tracksRef.current;
    if (!activeCurrent || list.length === 0) return;
    const idx = list.findIndex((t) => t.id === activeCurrent.id);
    const prevIdx = (idx - 1 + list.length) % list.length;
    onPlayTrackRef.current(list[prevIdx], false);
  }, []);

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
