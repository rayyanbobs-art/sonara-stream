import React, { useState, useRef, useEffect } from "react";
import { invoke } from "@tauri-apps/api/core";
import { AlertCircle } from "lucide-react";
import { Sidebar } from "./components/Sidebar";
import { Header } from "./components/Header";
import { Player } from "./components/Player";
import { UpNextDrawer } from "./components/UpNextDrawer";
import { HomeView } from "./views/HomeView";
import { SongsView } from "./views/SongsView";
import { FavoritesView } from "./views/FavoritesView";
import { SettingsView } from "./views/SettingsView";
import { Track, NavTab, AccentColor } from "./types";
import { isSameSong } from "./utils/trackSignature";
import "./App.css";

export default function App() {
  const [activeTab, setActiveTab] = useState<NavTab>("home");
  const [accentColor, setAccentColor] = useState<AccentColor>(() => {
    return (localStorage.getItem("sonara_accent") as AccentColor) || "gold";
  });
  const [theme, setTheme] = useState<"dark" | "light">(() => {
    return (localStorage.getItem("sonara_theme") as "dark" | "light") || "dark";
  });
  const [shuffleDefault, setShuffleDefault] = useState<boolean>(() => {
    return localStorage.getItem("sonara_shuffle_default") === "true";
  });
  const [isShuffle, setIsShuffle] = useState<boolean>(() => {
    return localStorage.getItem("sonara_shuffle_default") === "true";
  });

  const [tracks, setTracks] = useState<Track[]>([]);
  const [upNextMix, setUpNextMix] = useState<Track[]>([]);
  const [isQueueOpen, setIsQueueOpen] = useState(false);
  const [favorites, setFavorites] = useState<Track[]>(() => {
    try {
      const saved = localStorage.getItem("sonara_favorites");
      return saved ? JSON.parse(saved) : [];
    } catch {
      return [];
    }
  });

  const [currentTrack, setCurrentTrack] = useState<Track | null>(null);
  const [isPlaying, setIsPlaying] = useState(false);
  const [isBuffering, setIsBuffering] = useState(false);
  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState(0);
  const [volume, setVolume] = useState(0.85);

  const [searchQuery, setSearchQuery] = useState("");
  const [source, setSource] = useState<"youtube" | "spotify">("youtube");
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const audioRef = useRef<HTMLAudioElement | null>(null);
  const preloadAudioRef = useRef<HTMLAudioElement | null>(null);
  const preloadTrackRef = useRef<Track | null>(null);
  const prefetchedIds = useRef<Set<string>>(new Set());

  // Mutable refs to prevent stale closure bugs in audio events & track retries
  const currentTrackRef = useRef<Track | null>(null);
  const retryCountRef = useRef<number>(0);
  const handleNextRef = useRef<() => void>(() => {});
  const handleTogglePlayRef = useRef<() => void>(() => {});
  const handlePrevRef = useRef<() => void>(() => {});

  const handlePrefetchTrack = (track: Track) => {
    if (!track.id || prefetchedIds.current.has(track.id)) return;
    prefetchedIds.current.add(track.id);
    invoke("get_stream_url", { id: track.id }).catch(() => {});
  };

  const recentlyPlayedSignatures = useRef<Set<string>>(new Set());

  const isRecentlyPlayed = (track: Track): boolean => {
    if (recentlyPlayedSignatures.current.has(track.id)) return true;
    return Boolean(track.signature && recentlyPlayedSignatures.current.has(track.signature));
  };

  const markAsPlayed = (track: Track) => {
    recentlyPlayedSignatures.current.add(track.id);
    if (track.signature) {
      recentlyPlayedSignatures.current.add(track.signature);
    }
    if (recentlyPlayedSignatures.current.size > 100) {
      const arr = Array.from(recentlyPlayedSignatures.current);
      recentlyPlayedSignatures.current = new Set(arr.slice(arr.length - 60));
    }
  };

  // Sync accent color to document
  useEffect(() => {
    document.documentElement.setAttribute("data-accent", accentColor);
    localStorage.setItem("sonara_accent", accentColor);
  }, [accentColor]);

  // Sync theme to document & localStorage
  useEffect(() => {
    document.documentElement.setAttribute("data-theme", theme);
    localStorage.setItem("sonara_theme", theme);
  }, [theme]);

  // Sync favorites
  useEffect(() => {
    localStorage.setItem("sonara_favorites", JSON.stringify(favorites));
  }, [favorites]);

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

    // Auto-advance to next song on completion using fresh ref
    audio.onended = () => {
      handleNextRef.current();
    };

    audio.onerror = async () => {
      const activeTrack = currentTrackRef.current;
      // Exactly 1 retry bypassing the cache before surfacing failure
      if (activeTrack && retryCountRef.current === 0) {
        retryCountRef.current += 1;
        setIsBuffering(true);
        setErrorMessage("Playback error: refreshing stream URL...");
        try {
          const freshUrl: string = await invoke("get_stream_url", {
            id: activeTrack.id,
            bypassCache: true,
          });
          if (audioRef.current && typeof freshUrl === "string" && freshUrl.length > 0) {
            audioRef.current.src = freshUrl;
            await audioRef.current.play();
            setErrorMessage(null);
            return;
          }
        } catch (retryErr) {
          console.error("Audio stream retry failed:", retryErr);
        }
      }

      setIsBuffering(false);
      setIsPlaying(false);
      setErrorMessage("Stream playback failed. The audio stream could not be loaded. Please choose another track.");
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

  // Initial load
  useEffect(() => {
    performSearch("Top Hits 2026", "youtube");
  }, []);

  // Windows MediaSession integration
  useEffect(() => {
    if ("mediaSession" in navigator && currentTrack) {
      navigator.mediaSession.metadata = new MediaMetadata({
        title: currentTrack.title,
        artist: currentTrack.artist,
        artwork: [{ src: currentTrack.thumbnail }],
      });

      navigator.mediaSession.setActionHandler("play", () => handleTogglePlayRef.current());
      navigator.mediaSession.setActionHandler("pause", () => handleTogglePlayRef.current());
      navigator.mediaSession.setActionHandler("nexttrack", () => handleNextRef.current());
      navigator.mediaSession.setActionHandler("previoustrack", () => handlePrevRef.current());
    }
  }, [currentTrack]);

  const performSearch = async (queryText: string, searchSource: "youtube" | "spotify") => {
    setLoading(true);
    setErrorMessage(null);

    try {
      if (queryText.includes("spotify.com")) {
        const resolved: Track = await invoke("resolve_spotify_track", { url: queryText });
        setTracks([resolved]);
        handlePlayTrack(resolved);
      } else {
        const q = searchSource === "spotify" ? `${queryText} audio` : queryText;
        const results: Track[] = await invoke("search_youtube", { query: q });
        const formatted = results.map((t) => ({ ...t, source: searchSource }));
        setTracks(formatted);

        // Predictive zero-latency pre-fetching for top tracks
        if (formatted.length > 0) {
          formatted.slice(0, 6).forEach((t) => {
            if (!prefetchedIds.current.has(t.id)) {
              prefetchedIds.current.add(t.id);
              invoke("get_stream_url", { id: t.id }).catch(() => {});
            }
          });
        }
      }
    } catch (err: any) {
      console.error(err);
      setErrorMessage(typeof err === "string" ? err : "Failed to load music. Check internet.");
    } finally {
      setLoading(false);
    }
  };

  const handlePlayTrack = async (track: Track, preserveQueue = false) => {
    if (!audioRef.current) return;

    if (currentTrack?.id === track.id) {
      handleTogglePlay();
      return;
    }

    markAsPlayed(track);
    currentTrackRef.current = track;
    retryCountRef.current = 0;
    setCurrentTrack(track);
    setIsBuffering(true);
    setErrorMessage(null);

    try {
      let streamUrl: string;
      // If preloadAudioRef has this exact track buffered already, reuse it immediately for zero-wait play!
      if (
        preloadAudioRef.current &&
        preloadAudioRef.current.src &&
        preloadTrackRef.current?.id === track.id
      ) {
        streamUrl = preloadAudioRef.current.src;
        audioRef.current.src = streamUrl;
      } else {
        streamUrl = await invoke("get_stream_url", { id: track.id });
        audioRef.current.src = streamUrl;
      }

      audioRef.current.currentTime = 0;
      await audioRef.current.play();
      setIsPlaying(true);
      setDuration(track.duration || 0);

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
                    if (preloadAudioRef.current && typeof nextUrl === "string") {
                      preloadAudioRef.current.src = nextUrl;
                      preloadTrackRef.current = freshMix[0];
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
                if (preloadAudioRef.current && typeof nextUrl === "string") {
                  preloadAudioRef.current.src = nextUrl;
                  preloadTrackRef.current = nextCandidate;
                }
              })
              .catch(() => {});
          }

          return currentMix;
        });
      }

      // Speculatively pre-fetch and buffer the next track in background
      const list = activeTab === "favorites" ? favorites : tracks;
      const currIdx = list.findIndex((t) => t.id === track.id);
      if (currIdx !== -1 && list.length > 1) {
        const nextTrack = list[(currIdx + 1) % list.length];
        if (nextTrack && !prefetchedIds.current.has(nextTrack.id)) {
          prefetchedIds.current.add(nextTrack.id);
          invoke("get_stream_url", { id: nextTrack.id }).then((nextUrl) => {
            if (preloadAudioRef.current && typeof nextUrl === "string") {
              preloadAudioRef.current.src = nextUrl;
              preloadTrackRef.current = nextTrack;
            }
          }).catch(() => {});
        }
      }
    } catch (err: any) {
      console.error("Playback error:", err);
      setIsBuffering(false);
      setIsPlaying(false);
      setErrorMessage(typeof err === "string" ? err : "Failed to stream track.");
    }
  };

  const handleTogglePlay = () => {
    if (!audioRef.current || !currentTrack) return;
    if (isPlaying) {
      audioRef.current.pause();
    } else {
      audioRef.current.play().catch(console.error);
    }
  };

  const handleSeek = (time: number) => {
    if (!audioRef.current) return;
    audioRef.current.currentTime = time;
    setCurrentTime(time);
  };

  const handleVolumeChange = (vol: number) => {
    setVolume(vol);
    if (audioRef.current) {
      audioRef.current.volume = vol;
    }
  };

  const handleNext = async () => {
    if (!currentTrack) return;

    // 1. YouTube Music-style Genre Autoplay: Prioritize upcoming tracks from the genre mix
    if (activeTab !== "favorites" && upNextMix.length > 0) {
      let candidateIdx = -1;
      if (isShuffle) {
        const candidates = upNextMix
          .map((t, idx) => ({ t, idx }))
          .filter(({ t }) => !isSameSong(t, currentTrack) && !isRecentlyPlayed(t));
        if (candidates.length > 0) {
          const rand = Math.floor(Math.random() * candidates.length);
          candidateIdx = candidates[rand].idx;
        }
      } else {
        candidateIdx = upNextMix.findIndex(
          (t) => !isSameSong(t, currentTrack) && !isRecentlyPlayed(t)
        );
      }

      // If all tracks were marked as recently played, take any track that is not the same song
      if (candidateIdx === -1) {
        candidateIdx = upNextMix.findIndex((t) => !isSameSong(t, currentTrack));
      }

      if (candidateIdx !== -1) {
        const nextTrack = upNextMix[candidateIdx];
        // Advance queue: drop this track and any skipped before it
        setUpNextMix((prev) => prev.filter((_, idx) => idx > candidateIdx));
        handlePlayTrack(nextTrack, true /* PRESERVE QUEUE! */);
        return;
      }
    }

    const list = activeTab === "favorites" ? favorites : tracks;
    if (list.length > 0) {
      if (isShuffle) {
        const candidateTracks = list.filter(
          (t) => !isSameSong(t, currentTrack) && !isRecentlyPlayed(t)
        );
        if (candidateTracks.length > 0) {
          const rand = Math.floor(Math.random() * candidateTracks.length);
          handlePlayTrack(candidateTracks[rand], true);
          return;
        }
      }

      // Sequential: pick next distinct unplayed track
      const currIdx = list.findIndex((t) => t.id === currentTrack.id);
      let nextTrack: Track | null = null;

      for (let i = 1; i < list.length; i++) {
        const candidate = list[(currIdx + i) % list.length];
        if (!isSameSong(candidate, currentTrack) && !isRecentlyPlayed(candidate)) {
          nextTrack = candidate;
          break;
        }
      }

      if (nextTrack) {
        handlePlayTrack(nextTrack, true);
        return;
      }
    }

    // 3. Queue / Playlist exhausted: Fetch FRESH never-before-played related tracks!
    try {
      setIsBuffering(true);
      const related: Track[] = await invoke("get_related_tracks", {
        artist: currentTrack.artist,
        title: currentTrack.title,
      });

      const freshTracks = related.filter(
        (r) => !isSameSong(r, currentTrack) && !isRecentlyPlayed(r)
      );
      if (freshTracks.length > 0) {
        const next = freshTracks[0];
        setUpNextMix(freshTracks.slice(1));
        handlePlayTrack(next, true);
        return;
      }
    } catch (err) {
      console.error("Autoplay radio error:", err);
    }

    // 4. Fallback: pick any track from list that is not the same song
    const fallbackList = activeTab === "favorites" ? favorites : tracks;
    if (fallbackList.length > 1) {
      const candidate = fallbackList.find((t) => !isSameSong(t, currentTrack));
      if (candidate) {
        handlePlayTrack(candidate, true);
        return;
      }
    }
  };

  const handlePrev = () => {
    const list = activeTab === "favorites" ? favorites : tracks;
    if (!currentTrack || list.length === 0) return;
    const idx = list.findIndex((t) => t.id === currentTrack.id);
    const prevIdx = (idx - 1 + list.length) % list.length;
    handlePlayTrack(list[prevIdx], false);
  };

  // Keep refs synchronized on every render
  handleNextRef.current = handleNext;
  handleTogglePlayRef.current = handleTogglePlay;
  handlePrevRef.current = handlePrev;

  const handleShuffleDefaultChange = (val: boolean) => {
    setShuffleDefault(val);
    setIsShuffle(val);
    localStorage.setItem("sonara_shuffle_default", String(val));
  };

  const isFavorite = (id: string) => favorites.some((f) => f.id === id);

  const toggleFavorite = (track: Track) => {
    if (isFavorite(track.id)) {
      setFavorites((prev) => prev.filter((f) => f.id !== track.id));
    } else {
      setFavorites((prev) => [...prev, track]);
    }
  };

  return (
    <div className={`sonara-layout ${theme}`}>
      {/* Sidebar */}
      <Sidebar
        activeTab={activeTab}
        onTabChange={(tab) => {
          setActiveTab(tab);
          setErrorMessage(null);
        }}
        playlistCount={3}
      />

      {/* Main Container */}
      <div className="sonara-main-area">
        <Header
          query={searchQuery}
          onQueryChange={setSearchQuery}
          onSearch={(customQuery) => {
            const q = customQuery || searchQuery;
            performSearch(q, source);
            setActiveTab("songs");
          }}
          source={source}
          onSourceChange={setSource}
          loading={loading}
          onBack={() => setActiveTab("home")}
        />

        {errorMessage && (
          <div className="error-toast">
            <AlertCircle size={15} />
            <span>{errorMessage}</span>
            <button type="button" onClick={() => setErrorMessage(null)}>✕</button>
          </div>
        )}

        <div className="sonara-content-scroll">
          {activeTab === "home" && (
            <HomeView
              tracks={tracks}
              currentTrack={currentTrack}
              isPlaying={isPlaying}
              onPlayTrack={handlePlayTrack}
              onPrefetchTrack={handlePrefetchTrack}
              onVibeClick={(vibe) => {
                setSearchQuery(vibe);
                performSearch(vibe, "youtube");
              }}
              loading={loading}
            />
          )}

          {activeTab === "songs" && (
            <SongsView
              tracks={tracks}
              currentTrack={currentTrack}
              isPlaying={isPlaying}
              onPlayTrack={handlePlayTrack}
              onPrefetchTrack={handlePrefetchTrack}
              onToggleFavorite={toggleFavorite}
              isFavorite={isFavorite}
              onBrowse={() => {
                performSearch("Top Hits", "youtube");
                setActiveTab("home");
              }}
            />
          )}

          {(activeTab === "artists" || activeTab === "albums") && (
            <SongsView
              tracks={tracks}
              currentTrack={currentTrack}
              isPlaying={isPlaying}
              onPlayTrack={handlePlayTrack}
              onPrefetchTrack={handlePrefetchTrack}
              onToggleFavorite={toggleFavorite}
              isFavorite={isFavorite}
              onBrowse={() => setActiveTab("home")}
            />
          )}

          {activeTab === "favorites" && (
            <FavoritesView
              favorites={favorites}
              currentTrack={currentTrack}
              isPlaying={isPlaying}
              onPlayTrack={handlePlayTrack}
              onPrefetchTrack={handlePrefetchTrack}
              onToggleFavorite={toggleFavorite}
              onBrowse={() => setActiveTab("home")}
            />
          )}

          {activeTab === "settings" && (
            <SettingsView
              theme={theme}
              onThemeChange={setTheme}
              accent={accentColor}
              onAccentChange={setAccentColor}
              shuffleDefault={shuffleDefault}
              onShuffleDefaultChange={handleShuffleDefaultChange}
            />
          )}
        </div>
      </div>

      {/* Floating Bottom Player */}
      <Player
        currentTrack={currentTrack}
        isPlaying={isPlaying}
        isBuffering={isBuffering}
        currentTime={currentTime}
        duration={duration}
        volume={volume}
        isShuffle={isShuffle}
        isFavorite={currentTrack ? isFavorite(currentTrack.id) : false}
        onTogglePlay={handleTogglePlay}
        onSeek={handleSeek}
        onVolumeChange={handleVolumeChange}
        onNext={handleNext}
        onPrev={handlePrev}
        onToggleShuffle={() => setIsShuffle((prev) => !prev)}
        onToggleFavorite={() => currentTrack && toggleFavorite(currentTrack)}
        onToggleQueue={() => setIsQueueOpen((prev) => !prev)}
        isQueueOpen={isQueueOpen}
      />

      {/* Up Next Genre Mix Drawer */}
      <UpNextDrawer
        isOpen={isQueueOpen}
        onClose={() => setIsQueueOpen(false)}
        currentTrack={currentTrack}
        upNextTracks={upNextMix}
        onPlayTrack={(track) => {
          handlePlayTrack(track);
          setUpNextMix((prev) => prev.filter((t) => t.id !== track.id));
        }}
        onPrefetchTrack={handlePrefetchTrack}
        isPlaying={isPlaying}
      />
    </div>
  );
}
