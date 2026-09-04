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

  // Mutable refs to prevent stale closure bugs in audio events
  const handleNextRef = useRef<() => void>(() => {});
  const handleTogglePlayRef = useRef<() => void>(() => {});
  const handlePrevRef = useRef<() => void>(() => {});

  const handlePrefetchTrack = (track: Track) => {
    if (!track.id || prefetchedIds.current.has(track.id)) return;
    prefetchedIds.current.add(track.id);
    invoke("get_stream_url", { id: track.id }).catch(() => {});
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

    audio.onerror = () => {
      setIsBuffering(false);
      setIsPlaying(false);
      setErrorMessage("Stream error or link expired. Retrying or picking another song...");
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

  const handlePlayTrack = async (track: Track) => {
    if (!audioRef.current) return;

    if (currentTrack?.id === track.id) {
      handleTogglePlay();
      return;
    }

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

      // Fetch dynamic genre radio mix for this song
      invoke("get_genre_mix", { artist: track.artist, title: track.title })
        .then((mix: any) => {
          if (Array.isArray(mix) && mix.length > 0) {
            setUpNextMix(mix);
            // Pre-fetch the first 3 songs of the mix immediately in the background
            mix.slice(0, 3).forEach((mTrack: Track) => {
              if (!prefetchedIds.current.has(mTrack.id)) {
                prefetchedIds.current.add(mTrack.id);
                invoke("get_stream_url", { id: mTrack.id }).catch(() => {});
              }
            });

            // Pre-buffer the 1st song of the genre mix into preload audio element
            invoke("get_stream_url", { id: mix[0].id })
              .then((nextUrl) => {
                if (preloadAudioRef.current && typeof nextUrl === "string") {
                  preloadAudioRef.current.src = nextUrl;
                  preloadTrackRef.current = mix[0];
                }
              })
              .catch(() => {});
          }
        })
        .catch((err) => console.error("Genre mix error:", err));

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

  const getCleanWords = (track: Track): Set<string> => {
    const combined = `${track.title} ${track.artist}`.toLowerCase();
    const noise = new Set([
      "official", "video", "audio", "music", "lyrics", "lyric", "remastered",
      "remaster", "hd", "hq", "4k", "version", "original", "stem", "edit",
      "visualizer", "soundtrack", "ost", "theme", "full", "song", "records",
      "vevo", "channel", "topic", "special", "mix", "extended"
    ]);
    const cleaned = combined
      .replace(/\([^)]*\)/g, " ")
      .replace(/\[[^\]]*\]/g, " ")
      .replace(/[^a-z0-9\s]/g, " ");
    return new Set(
      cleaned.split(/\s+/).filter((w) => w.length >= 2 && !noise.has(w))
    );
  };

  const isSameSong = (a: Track, b: Track): boolean => {
    if (a.id === b.id) return true;
    const wordsA = getCleanWords(a);
    const wordsB = getCleanWords(b);
    if (wordsA.size === 0 || wordsB.size === 0) return false;
    let intersection = 0;
    for (const w of wordsA) {
      if (wordsB.has(w)) intersection++;
    }
    const overlap = intersection / Math.min(wordsA.size, wordsB.size);
    return overlap >= 0.7;
  };

  const handleNext = async () => {
    // 1. YouTube Music-style Genre Autoplay: Prioritize upcoming tracks from the genre mix
    if (activeTab !== "favorites" && upNextMix.length > 0) {
      let candidateIdx = -1;
      if (isShuffle) {
        const candidates = upNextMix
          .map((t, idx) => ({ t, idx }))
          .filter(({ t }) => !isSameSong(t, currentTrack!));
        if (candidates.length > 0) {
          const rand = Math.floor(Math.random() * candidates.length);
          candidateIdx = candidates[rand].idx;
        }
      } else {
        candidateIdx = upNextMix.findIndex((t) => !isSameSong(t, currentTrack!));
      }

      if (candidateIdx !== -1) {
        const nextTrack = upNextMix[candidateIdx];
        setUpNextMix((prev) => prev.filter((_, idx) => idx !== candidateIdx));
        handlePlayTrack(nextTrack);
        return;
      }
    }

    const list = activeTab === "favorites" ? favorites : tracks;
    if (!currentTrack || list.length === 0) return;

    if (isShuffle) {
      const candidateTracks = list.filter((t) => !isSameSong(t, currentTrack));
      if (candidateTracks.length > 0) {
        const rand = Math.floor(Math.random() * candidateTracks.length);
        handlePlayTrack(candidateTracks[rand]);
        return;
      }
    }

    // Sequential: pick next distinct track (skip any duplicate upload)
    const currIdx = list.findIndex((t) => t.id === currentTrack.id);
    let nextTrack: Track | null = null;

    for (let i = 1; i < list.length; i++) {
      const candidate = list[(currIdx + i) % list.length];
      if (!isSameSong(candidate, currentTrack)) {
        nextTrack = candidate;
        break;
      }
    }

    if (nextTrack) {
      handlePlayTrack(nextTrack);
      return;
    }

    // Smart Continuous Radio Autoplay:
    // If all remaining tracks are duplicates or queue reached end, fetch new related songs!
    try {
      setIsBuffering(true);
      const related: Track[] = await invoke("get_related_tracks", {
        artist: currentTrack.artist,
        title: currentTrack.title,
      });

      const freshTracks = related.filter((r) => !isSameSong(r, currentTrack));
      if (freshTracks.length > 0) {
        setTracks((prev) => [...prev, ...freshTracks]);
        handlePlayTrack(freshTracks[0]);
        return;
      }
    } catch (err) {
      console.error("Autoplay radio error:", err);
    }

    // Fallback: pick the next item
    const nextIdx = (currIdx + 1) % list.length;
    handlePlayTrack(list[nextIdx]);
  };

  const handlePrev = () => {
    const list = activeTab === "favorites" ? favorites : tracks;
    if (!currentTrack || list.length === 0) return;
    const idx = list.findIndex((t) => t.id === currentTrack.id);
    const prevIdx = (idx - 1 + list.length) % list.length;
    handlePlayTrack(list[prevIdx]);
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
