import React, { useState, useRef, useEffect } from "react";
import { invoke } from "@tauri-apps/api/core";
import { AlertCircle } from "lucide-react";
import { Sidebar } from "./components/Sidebar";
import { Header } from "./components/Header";
import { Player } from "./components/Player";
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

  // Mutable refs to prevent stale closure bugs in audio events
  const handleNextRef = useRef<() => void>(() => {});
  const handleTogglePlayRef = useRef<() => void>(() => {});
  const handlePrevRef = useRef<() => void>(() => {});

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
          invoke("get_stream_url", { id: formatted[0].id }).catch(() => {});
          if (formatted.length > 1) {
            invoke("get_stream_url", { id: formatted[1].id }).catch(() => {});
          }
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
      const streamUrl: string = await invoke("get_stream_url", { id: track.id });
      audioRef.current.src = streamUrl;
      audioRef.current.currentTime = 0;
      await audioRef.current.play();
      setIsPlaying(true);
      setDuration(track.duration || 0);

      // Speculatively pre-fetch and buffer the next track in background
      const list = activeTab === "favorites" ? favorites : tracks;
      const currIdx = list.findIndex((t) => t.id === track.id);
      if (currIdx !== -1 && list.length > 1) {
        const nextTrack = list[(currIdx + 1) % list.length];
        if (nextTrack) {
          invoke("get_stream_url", { id: nextTrack.id }).then((nextUrl) => {
            if (preloadAudioRef.current && typeof nextUrl === "string") {
              preloadAudioRef.current.src = nextUrl;
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

  const handleNext = () => {
    const list = activeTab === "favorites" ? favorites : tracks;
    if (!currentTrack || list.length === 0) return;

    if (isShuffle) {
      let rand = Math.floor(Math.random() * list.length);
      if (list.length > 1 && list[rand].id === currentTrack.id) {
        rand = (rand + 1) % list.length;
      }
      handlePlayTrack(list[rand]);
      return;
    }

    const idx = list.findIndex((t) => t.id === currentTrack.id);
    const nextIdx = (idx + 1) % list.length;
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
          onSearch={() => {
            performSearch(searchQuery, source);
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
      />
    </div>
  );
}
