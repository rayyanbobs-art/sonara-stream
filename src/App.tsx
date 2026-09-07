import React, { useState, useRef, useEffect, useCallback, useMemo } from "react";
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
import { SearchResultsView } from "./views/SearchResultsView";

const SettingsView = React.lazy(() =>
  import("./views/SettingsView").then((m) => ({ default: m.SettingsView }))
);
import { Track, NavTab, AccentColor } from "./types";
import { useFavorites } from "./hooks/useFavorites";
import { useAudioPlayer } from "./hooks/useAudioPlayer";
import { useQueue } from "./hooks/useQueue";
import { usePlaylists } from "./hooks/usePlaylists";
import { PlaylistView } from "./views/PlaylistView";
import { TrackContextMenu } from "./components/TrackContextMenu";
import { NowPlayingModal } from "./components/NowPlayingModal";
import { PerfOverlay } from "./components/PerfOverlay";
import { perf } from "./utils/perf";
import "./App.css";

export default function App() {
  const [activeTab, setActiveTab] = useState<NavTab>("home");
  const [isFullscreenNowPlaying, setIsFullscreenNowPlaying] = useState(false);
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
  const {
    playlists,
    activePlaylistId,
    setActivePlaylistId,
    activePlaylist,
    createPlaylist,
    renamePlaylist,
    deletePlaylist,
    addTrackToPlaylist,
    removeTrackFromPlaylist,
    reorderPlaylistTracks,
    playlistsRecentFirst,
  } = usePlaylists();

  const [contextMenu, setContextMenu] = useState<{
    track: Track;
    x: number;
    y: number;
  } | null>(null);

  const [searchQuery, setSearchQuery] = useState("");
  const [source, setSource] = useState<"youtube" | "spotify">("youtube");
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [availableUpdate, setAvailableUpdate] = useState<Update | null>(null);
  const [showPerfOverlay, setShowPerfOverlay] = useState<boolean>(() => {
    return localStorage.getItem("sonara_perf_overlay") === "true";
  });

  // B9: Button feedback measurement (< 16ms target)
  useEffect(() => {
    const onPointerDown = (e: PointerEvent) => {
      const target = e.target as HTMLElement | null;
      if (target?.closest?.("button, [role='button'], .track-card, .track-item, .vibe-chip")) {
        const start = performance.now();
        requestAnimationFrame(() => {
          const elapsed = Math.round(performance.now() - start);
          perf.recordButtonFeedback(elapsed);
        });
      }
    };
    window.addEventListener("pointerdown", onPointerDown, { passive: true });
    return () => window.removeEventListener("pointerdown", onPointerDown);
  }, []);

  // B10: Memory tracking
  useEffect(() => {
    const checkMem = () => {
      const mem = (performance as any)?.memory;
      if (mem?.usedJSHeapSize) {
        perf.recordMemory(Math.round(mem.usedJSHeapSize / (1024 * 1024)));
      }
    };
    checkMem();
    const interval = setInterval(checkMem, 5000);
    return () => clearInterval(interval);
  }, []);



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

  const currentTrackRef = useRef<Track | null>(currentTrack);
  useEffect(() => {
    currentTrackRef.current = currentTrack;
  }, [currentTrack]);

  const songsList = useMemo(
    () => (allDiscoveredTracks.length > 0 ? allDiscoveredTracks : recommendations),
    [allDiscoveredTracks, recommendations]
  );

  const currentPool = useMemo(() => {
    switch (activeTab) {
      case "search":
        return searchResults;
      case "songs":
        return songsList;
      case "favorites":
        return favorites;
      case "playlist":
        return activePlaylist?.tracks || [];
      default:
        return recommendations;
    }
  }, [activeTab, searchResults, songsList, favorites, activePlaylist, recommendations]);

  const {
    upNextMix,
    setUpNextMix,
    userQueue,
    setUserQueue,
    addToUserQueue,
    playNextInQueue,
    removeFromUserQueue,
    clearUserQueue,
    reorderUserQueue,
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
    onPlayTrack: useCallback((track: Track, preserveQueue?: boolean) => handlePlayTrackRef.current(track, preserveQueue), []),
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

  const mergeDiscoveredTracks = useCallback((newTracks: Track[]) => {
    setAllDiscoveredTracks((prev) => {
      const seen = new Set(prev.map((t) => t.signature || t.id));
      const additions = newTracks.filter((t) => !seen.has(t.signature || t.id));
      return additions.length > 0 ? [...prev, ...additions] : prev;
    });
  }, []);

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
        // B1: Cold start to interactive UI with recommendations visible
        perf.recordColdStart(Math.round(performance.now()));
      }
    };
    loadRecommendations();
  }, [handlePrefetchTrack, mergeDiscoveredTracks]);

  const handlePlayTrack = useCallback(async (track: Track, preserveQueue = false) => {
    if (currentTrackRef.current?.id === track.id) {
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
  }, [togglePlay, markAsPlayed, playTrack, updateQueueForTrack]);

  handlePlayTrackRef.current = handlePlayTrack;

  const performSearch = useCallback(async (queryText: string, searchSource: "youtube" | "spotify") => {
    const cleanQuery = queryText.trim();
    if (!cleanQuery) return;

    setLoading(true);
    setErrorMessage(null);
    const searchStartTime = performance.now();

    try {
      if (cleanQuery.includes("spotify.com")) {
        const resolved: Track = await invoke("resolve_spotify_track", { url: cleanQuery });
        const elapsed = Math.round(performance.now() - searchStartTime);
        perf.recordFirstResult(elapsed);
        perf.recordAllResults(elapsed);
        setSearchResults([resolved]);
        mergeDiscoveredTracks([resolved]);
        handlePlayTrack(resolved);
      } else {
        const q = searchSource === "spotify" ? `${cleanQuery} audio` : cleanQuery;
        const results: Track[] = await invoke("search_youtube", { query: q });
        const elapsed = Math.round(performance.now() - searchStartTime);
        perf.recordFirstResult(elapsed);
        perf.recordAllResults(elapsed);
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
      const isTauri = typeof window !== "undefined" && Boolean((window as any).__TAURI_INTERNALS__);
      if (!isTauri) {
        setErrorMessage(
          "Running in web browser. Please use the native Sonara Stream desktop app window on your taskbar."
        );
      } else {
        const msg = err instanceof Error ? err.message : typeof err === "string" ? err : "";
        setErrorMessage(msg || "Failed to load music. Check internet connection.");
      }
    } finally {
      setLoading(false);
    }
  }, [mergeDiscoveredTracks, handlePlayTrack, handlePrefetchTrack]);

  const handleTabChange = useCallback((tab: NavTab) => {
    setActiveTab(tab);
    setErrorMessage(null);
  }, []);

  const handleSelectMix = useCallback((mix: string) => {
    setSearchQuery(mix);
    performSearch(mix, "youtube");
    setActiveTab("search");
  }, [performSearch]);

  const handleHeaderSearch = useCallback((customQuery?: string) => {
    const q = customQuery || searchQuery;
    if (!q.trim()) return;
    performSearch(q, source);
    setActiveTab("search");
  }, [searchQuery, source, performSearch]);

  const handleBackToHome = useCallback(() => {
    setActiveTab("home");
  }, []);

  const handleVibeClick = useCallback((vibe: string) => {
    setSearchQuery(vibe);
    performSearch(vibe, "youtube");
    setActiveTab("search");
  }, [performSearch]);

  const handleCloseQueue = useCallback(() => {
    setIsQueueOpen(false);
  }, [setIsQueueOpen]);

  const handleDrawerPlayTrack = useCallback((track: Track) => {
    handlePlayTrack(track);
    setUpNextMix((prev) => prev.filter((t) => t.id !== track.id));
  }, [handlePlayTrack, setUpNextMix]);

  const handlePlayerToggleFavorite = useCallback(() => {
    if (currentTrackRef.current) {
      toggleFavorite(currentTrackRef.current);
    }
  }, [toggleFavorite]);

  const isCurrentFavorite = useMemo(
    () => (currentTrack ? isFavorite(currentTrack.id) : false),
    [currentTrack, isFavorite]
  );

  const handleClearError = useCallback(() => {
    setErrorMessage(null);
  }, []);

  const handleOpenContextMenu = useCallback((track: Track, e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setContextMenu({ track, x: e.clientX, y: e.clientY });
  }, []);

  const handleCloseContextMenu = useCallback(() => {
    setContextMenu(null);
  }, []);

  const handleSelectPlaylist = useCallback((id: string) => {
    setActivePlaylistId(id);
    setActiveTab("playlist");
  }, [setActivePlaylistId]);

  const handleCreatePlaylist = useCallback(async () => {
    const defaultName = `My Playlist #${playlists.length + 1}`;
    const name = window.prompt("Playlist name:", defaultName);
    if (name && name.trim()) {
      const created = await createPlaylist(name.trim());
      if (created) {
        setActivePlaylistId(created.id);
        setActiveTab("playlist");
      }
    }
  }, [playlists.length, createPlaylist, setActivePlaylistId]);

  const handlePlayPlaylist = useCallback((tracks: Track[], shuffle = false) => {
    if (tracks.length === 0) return;
    let listToPlay = [...tracks];
    if (shuffle) {
      for (let i = listToPlay.length - 1; i > 0; i--) {
        const j = Math.floor(Math.random() * (i + 1));
        [listToPlay[i], listToPlay[j]] = [listToPlay[j], listToPlay[i]];
      }
    }
    const [first, ...rest] = listToPlay;
    handlePlayTrack(first);
    setUpNextMix(rest);
  }, [handlePlayTrack, setUpNextMix]);

  const handleSaveQueueAsPlaylist = useCallback(async (tracks: Track[]) => {
    if (tracks.length === 0) return;
    const dateStr = new Date().toLocaleDateString(undefined, { month: "short", day: "numeric" });
    const name = window.prompt("Save queue as playlist:", `Queue ${dateStr}`);
    if (!name || !name.trim()) return;
    const created = await createPlaylist(name.trim());
    if (created) {
      for (const t of tracks) {
        await addTrackToPlaylist(created.id, t);
      }
      setActivePlaylistId(created.id);
      setActiveTab("playlist");
    }
  }, [createPlaylist, addTrackToPlaylist, setActivePlaylistId]);

  const handleDismissUpdate = useCallback(() => {
    setAvailableUpdate(null);
  }, []);

  const isTauri = typeof window !== "undefined" && Boolean((window as any).__TAURI_INTERNALS__);

  return (
    <div className={`sonara-layout ${theme}`}>
      {/* Sidebar */}
      <Sidebar
        activeTab={activeTab}
        onTabChange={handleTabChange}
        playlists={playlists}
        activePlaylistId={activePlaylistId}
        onSelectPlaylist={handleSelectPlaylist}
        onCreatePlaylist={handleCreatePlaylist}
      />

      {/* Main Container */}
      <div className="sonara-main-area">
        {!isTauri && (
          <div
            style={{
              background: "rgba(245, 184, 0, 0.15)",
              borderBottom: "1px solid rgba(245, 184, 0, 0.3)",
              color: "var(--accent)",
              padding: "7px 16px",
              fontSize: "12px",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              gap: "8px",
              fontWeight: 500,
            }}
          >
            <AlertCircle size={14} />
            <span>
              You are viewing Sonara Stream in a web browser. Native streaming and search require the <strong>Sonara Stream desktop window</strong>.
            </span>
          </div>
        )}
        <Header
          query={searchQuery}
          onQueryChange={setSearchQuery}
          onSearch={handleHeaderSearch}
          source={source}
          onSourceChange={setSource}
          loading={loading}
          onBack={handleBackToHome}
        />

        {errorMessage && (
          <div className="error-toast">
            <AlertCircle size={15} />
            <span>{errorMessage}</span>
            <button type="button" onClick={handleClearError}>✕</button>
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
              onVibeClick={handleVibeClick}
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
              tracks={songsList}
              currentTrack={currentTrack}
              isPlaying={isPlaying}
              onPlayTrack={handlePlayTrack}
              onPrefetchTrack={handlePrefetchTrack}
              onToggleFavorite={toggleFavorite}
              isFavorite={isFavorite}
              onBrowse={handleBackToHome}
              onOpenContextMenu={handleOpenContextMenu}
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
              isFavorite={isFavorite}
              onBrowse={handleBackToHome}
              onOpenContextMenu={handleOpenContextMenu}
            />
          )}

          {activeTab === "playlist" && (
            activePlaylist ? (
              <PlaylistView
                playlist={activePlaylist}
                currentTrack={currentTrack}
                isPlaying={isPlaying}
                onPlayTrack={handlePlayTrack}
                onPlayPlaylist={handlePlayPlaylist}
                onRenamePlaylist={renamePlaylist}
                onDeletePlaylist={async (id) => {
                  await deletePlaylist(id);
                  setActiveTab("home");
                }}
                onRemoveTrack={removeTrackFromPlaylist}
                onReorderTracks={reorderPlaylistTracks}
                onToggleFavorite={toggleFavorite}
                isFavorite={isFavorite}
                onOpenContextMenu={handleOpenContextMenu}
              />
            ) : (
              <div className="sonara-empty-panel">
                <h3 className="empty-panel-title">No Playlist Selected</h3>
                <p className="empty-panel-desc">Select a playlist from the sidebar or create a new one.</p>
                <button type="button" className="empty-action-btn" onClick={handleCreatePlaylist}>
                  + Create Playlist
                </button>
              </div>
            )
          )}

          {activeTab === "settings" && (
            <React.Suspense fallback={null}>
              <SettingsView
                theme={theme}
                onThemeChange={setTheme}
                accent={accentColor}
                onAccentChange={setAccentColor}
                shuffleDefault={shuffleDefault}
                onShuffleDefaultChange={handleShuffleDefaultChange}
                perfOverlay={showPerfOverlay}
                onPerfOverlayChange={(enabled) => {
                  setShowPerfOverlay(enabled);
                  localStorage.setItem("sonara_perf_overlay", enabled ? "true" : "false");
                }}
              />
            </React.Suspense>
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
        isFavorite={isCurrentFavorite}
        onTogglePlay={togglePlay}
        onSeek={seek}
        onVolumeChange={setVolume}
        onNext={handleNext}
        onPrev={handlePrev}
        onToggleShuffle={toggleShuffle}
        repeatMode={repeatMode}
        onToggleRepeat={toggleRepeat}
        onToggleFavorite={handlePlayerToggleFavorite}
        onToggleQueue={toggleQueue}
        isQueueOpen={isQueueOpen}
        onToggleFullscreen={() => setIsFullscreenNowPlaying((prev) => !prev)}
      />

      {/* Fullscreen Now Playing Modal */}
      <NowPlayingModal
        isOpen={isFullscreenNowPlaying}
        onClose={() => setIsFullscreenNowPlaying(false)}
        currentTrack={currentTrack}
        isPlaying={isPlaying}
        isBuffering={isBuffering}
        currentTime={currentTime}
        duration={duration}
        volume={volume}
        isShuffle={isShuffle}
        repeatMode={repeatMode}
        isFavorite={isCurrentFavorite}
        onTogglePlay={togglePlay}
        onSeek={seek}
        onVolumeChange={setVolume}
        onNext={handleNext}
        onPrev={handlePrev}
        onToggleShuffle={toggleShuffle}
        onToggleRepeat={toggleRepeat}
        onToggleFavorite={handlePlayerToggleFavorite}
        upNextTracks={upNextMix}
        userQueue={userQueue}
        onPlayTrack={handlePlayTrack}
        onRemoveFromUserQueue={removeFromUserQueue}
        onClearUserQueue={clearUserQueue}
      />

      {/* Slide-in Queue Panel */}
      <UpNextDrawer
        isOpen={isQueueOpen}
        onClose={handleCloseQueue}
        currentTrack={currentTrack}
        upNextTracks={upNextMix}
        userQueue={userQueue}
        onPlayTrack={handleDrawerPlayTrack}
        onPrefetchTrack={handlePrefetchTrack}
        onRemoveFromUserQueue={removeFromUserQueue}
        onClearUserQueue={clearUserQueue}
        onReorderUserQueue={reorderUserQueue}
        onSaveQueueAsPlaylist={handleSaveQueueAsPlaylist}
        isPlaying={isPlaying}
      />

      {/* Real-time Performance Budget HUD Overlay */}
      <PerfOverlay
        visible={showPerfOverlay}
        onClose={() => {
          setShowPerfOverlay(false);
          localStorage.setItem("sonara_perf_overlay", "false");
        }}
      />

      {availableUpdate && (
        <UpdateBanner
          update={availableUpdate}
          isPlaying={isPlaying}
          onDismiss={handleDismissUpdate}
        />
      )}

      {/* Track Context Menu */}
      {contextMenu && (
        <TrackContextMenu
          track={contextMenu.track}
          playlists={playlistsRecentFirst}
          isOpen={true}
          position={{ x: contextMenu.x, y: contextMenu.y }}
          onClose={handleCloseContextMenu}
          onPlayNext={playNextInQueue}
          onAddToQueue={addToUserQueue}
          onAddToPlaylist={addTrackToPlaylist}
          onCreateAndAddToPlaylist={async (name, track) => {
            const created = await createPlaylist(name);
            if (created) {
              await addTrackToPlaylist(created.id, track);
            }
          }}
          isFavorite={isFavorite(contextMenu.track.id)}
          onToggleFavorite={toggleFavorite}
        />
      )}
    </div>
  );
}
