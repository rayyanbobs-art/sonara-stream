import React, { useState, useRef, useEffect } from "react";
import { invoke } from "@tauri-apps/api/core";
import { AlertCircle } from "lucide-react";
import { check, Update } from "@tauri-apps/plugin-updater";
import { UpdateBanner } from "./components/UpdateBanner";
import { Sidebar } from "./components/Sidebar";
import { Header } from "./components/Header";
import { Player } from "./components/Player";
import { UpNextDrawer } from "./components/UpNextDrawer";
import { HomeView } from "./views/HomeView";
import { SongsView } from "./views/SongsView";
import { FavoritesView } from "./views/FavoritesView";
import { SettingsView } from "./views/SettingsView";
import { SearchResultsView } from "./views/SearchResultsView";
import { Track, NavTab, AccentColor } from "./types";
import { useFavorites } from "./hooks/useFavorites";
import { useAudioPlayer } from "./hooks/useAudioPlayer";
import { useQueue } from "./hooks/useQueue";
import "./App.css";

export default function App() {
  const [activeTab, setActiveTab] = useState<NavTab>("home");
  const [accentColor, setAccentColor] = useState<AccentColor>(() => {
    return (localStorage.getItem("sonara_accent") as AccentColor) || "gold";
  });
  const [theme, setTheme] = useState<"dark" | "light">(() => {
    return (localStorage.getItem("sonara_theme") as "dark" | "light") || "dark";
  });

  // Decoupled state: Home recommendations vs Active Search Results
  const [recommendations, setRecommendations] = useState<Track[]>([]);
  const [recommendationTitle, setRecommendationTitle] = useState<string>("Made from your listening");
  const [searchResults, setSearchResults] = useState<Track[]>([]);
  const [allDiscoveredTracks, setAllDiscoveredTracks] = useState<Track[]>([]);
  const [lastPlayedTrack, setLastPlayedTrack] = useState<Track | null>(() => {
    try {
      const saved = localStorage.getItem("sonara_last_played");
      return saved ? JSON.parse(saved) : null;
    } catch {
      return null;
    }
  });

  const { favorites, isFavorite, toggleFavorite } = useFavorites();

  const [searchQuery, setSearchQuery] = useState("");
  const [source, setSource] = useState<"youtube" | "spotify">("youtube");
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [availableUpdate, setAvailableUpdate] = useState<Update | null>(null);

  useEffect(() => {
    const checkAppUpdate = async () => {
      const autoUpdate = localStorage.getItem("sonara_app_autoupdate") !== "false";
      if (!autoUpdate) return;
      try {
        const update = await check();
        if (update && update.available) {
          setAvailableUpdate(update);
        }
      } catch (err) {
        console.debug("Tauri app update check:", err);
      }
    };

    const timer = setTimeout(checkAppUpdate, 3000);
    return () => clearTimeout(timer);
  }, []);

  const handleNextRef = useRef<() => void>(() => {});
  const handlePrevRef = useRef<() => void>(() => {});
  const handlePlayTrackRef = useRef<(track: Track, preserveQueue?: boolean) => Promise<void>>(async () => {});

  const {
    currentTrack,
    isPlaying,
    isBuffering,
    currentTime,
    duration,
    volume,
    repeatMode,
    toggleRepeat,
    audioRef,
    preloadAudioRef,
    preloadTrackRef,
    playTrack,
    togglePlay,
    seek,
    setVolume,
  } = useAudioPlayer({
    onNext: () => handleNextRef.current(),
    onPrev: () => handlePrevRef.current(),
    onError: setErrorMessage,
  });

  const currentPool =
    activeTab === "search"
      ? searchResults
      : activeTab === "songs"
      ? (allDiscoveredTracks.length > 0 ? allDiscoveredTracks : recommendations)
      : activeTab === "favorites"
      ? favorites
      : recommendations;

  const {
    upNextMix,
    setUpNextMix,
    isQueueOpen,
    setIsQueueOpen,
    toggleQueue,
    isShuffle,
    toggleShuffle,
    shuffleDefault,
    handleShuffleDefaultChange,
    handleNext,
    handlePrev,
    handlePrefetchTrack,
    updateQueueForTrack,
    markAsPlayed,
  } = useQueue({
    currentTrack,
    activeTab,
    tracks: currentPool,
    favorites,
    onPlayTrack: (track, preserveQueue) => handlePlayTrackRef.current(track, preserveQueue),
    audioRef,
    preloadAudioRef,
    preloadTrackRef,
  });

  handleNextRef.current = handleNext;
  handlePrevRef.current = handlePrev;

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

  const mergeDiscoveredTracks = (newTracks: Track[]) => {
    setAllDiscoveredTracks((prev) => {
      const seen = new Set(prev.map((t) => t.signature || t.id));
      const additions = newTracks.filter((t) => !seen.has(t.signature || t.id));
      return additions.length > 0 ? [...prev, ...additions] : prev;
    });
  };

  // Initial load: Fetch personalized recommendations from Rust engine
  useEffect(() => {
    const loadRecommendations = async () => {
      setLoading(true);
      try {
        const res: { title: string; tracks: Track[] } = await invoke("get_recommendations", { limit: 24 });
        if (res && Array.isArray(res.tracks) && res.tracks.length > 0) {
          setRecommendations(res.tracks);
          if (res.title) setRecommendationTitle(res.title);
          mergeDiscoveredTracks(res.tracks);
          res.tracks.slice(0, 4).forEach((t) => {
            handlePrefetchTrack(t);
          });
        }
      } catch (err) {
        console.warn("Failed to load initial recommendations:", err);
      } finally {
        setLoading(false);
      }
    };
    loadRecommendations();
  }, []);

  const performSearch = async (queryText: string, searchSource: "youtube" | "spotify") => {
    const cleanQuery = queryText.trim();
    if (!cleanQuery) return;

    setLoading(true);
    setErrorMessage(null);

    try {
      if (cleanQuery.includes("spotify.com")) {
        const resolved: Track = await invoke("resolve_spotify_track", { url: cleanQuery });
        setSearchResults([resolved]);
        mergeDiscoveredTracks([resolved]);
        handlePlayTrack(resolved);
      } else {
        const q = searchSource === "spotify" ? `${cleanQuery} audio` : cleanQuery;
        const results: Track[] = await invoke("search_youtube", { query: q });
        const formatted = results.map((t) => ({ ...t, source: searchSource }));
        setSearchResults(formatted);
        mergeDiscoveredTracks(formatted);

        // Prefetch top search results
        if (formatted.length > 0) {
          formatted.slice(0, 4).forEach((t) => {
            handlePrefetchTrack(t);
          });
        }
      }
    } catch (err: unknown) {
      console.error(err);
      setErrorMessage(typeof err === "string" ? err : "Failed to load music. Check internet.");
    } finally {
      setLoading(false);
    }
  };

  const handlePlayTrack = async (track: Track, preserveQueue = false) => {
    if (currentTrack?.id === track.id) {
      togglePlay();
      return;
    }

    setLastPlayedTrack(track);
    try {
      localStorage.setItem("sonara_last_played", JSON.stringify(track));
    } catch {
      // Ignore local storage error
    }

    markAsPlayed(track);

    try {
      await playTrack(track);
      updateQueueForTrack(track, preserveQueue);
    } catch {
      // Playback errors handled in useAudioPlayer
    }
  };

  handlePlayTrackRef.current = handlePlayTrack;

  return (
    <div className={`sonara-layout ${theme}`}>
      {/* Sidebar */}
      <Sidebar
        activeTab={activeTab}
        onTabChange={(tab) => {
          setActiveTab(tab);
          setErrorMessage(null);
        }}
        onSelectMix={(mix) => {
          setSearchQuery(mix);
          performSearch(mix, "youtube");
          setActiveTab("search");
        }}
      />

      {/* Main Container */}
      <div className="sonara-main-area">
        <Header
          query={searchQuery}
          onQueryChange={setSearchQuery}
          onSearch={(customQuery) => {
            const q = customQuery || searchQuery;
            if (!q.trim()) return;
            performSearch(q, source);
            setActiveTab("search");
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
              tracks={recommendations}
              title={recommendationTitle}
              lastPlayedTrack={lastPlayedTrack}
              currentTrack={currentTrack}
              isPlaying={isPlaying}
              onPlayTrack={handlePlayTrack}
              onPrefetchTrack={handlePrefetchTrack}
              onVibeClick={(vibe) => {
                setSearchQuery(vibe);
                performSearch(vibe, "youtube");
                setActiveTab("search");
              }}
              loading={loading}
            />
          )}

          {activeTab === "search" && (
            <SearchResultsView
              query={searchQuery}
              rawResults={searchResults}
              currentTrack={currentTrack}
              isPlaying={isPlaying}
              onPlayTrack={handlePlayTrack}
              onPrefetchTrack={handlePrefetchTrack}
              loading={loading}
            />
          )}

          {activeTab === "songs" && (
            <SongsView
              tracks={allDiscoveredTracks.length > 0 ? allDiscoveredTracks : recommendations}
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
        onTogglePlay={togglePlay}
        onSeek={seek}
        onVolumeChange={setVolume}
        onNext={handleNext}
        onPrev={handlePrev}
        onToggleShuffle={toggleShuffle}
        repeatMode={repeatMode}
        onToggleRepeat={toggleRepeat}
        onToggleFavorite={() => currentTrack && toggleFavorite(currentTrack)}
        onToggleQueue={toggleQueue}
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

      {availableUpdate && (
        <UpdateBanner
          update={availableUpdate}
          isPlaying={isPlaying}
          onDismiss={() => setAvailableUpdate(null)}
        />
      )}
    </div>
  );
}
