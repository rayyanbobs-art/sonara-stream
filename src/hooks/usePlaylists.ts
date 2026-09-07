import { useState, useEffect, useCallback, useMemo } from "react";
import { invoke } from "@tauri-apps/api/core";
import { Playlist, Track } from "../types";

export function usePlaylists() {
  const [playlists, setPlaylists] = useState<Playlist[]>([]);
  const [activePlaylistId, setActivePlaylistId] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const [recentPlaylistIds, setRecentPlaylistIds] = useState<string[]>(() => {
    try {
      const saved = localStorage.getItem("sonara_recent_playlists");
      return saved ? JSON.parse(saved) : [];
    } catch {
      return [];
    }
  });

  const recordRecentPlaylist = useCallback((id: string) => {
    setRecentPlaylistIds((prev) => {
      const filtered = prev.filter((pId) => pId !== id);
      const updated = [id, ...filtered].slice(0, 8);
      try {
        localStorage.setItem("sonara_recent_playlists", JSON.stringify(updated));
      } catch (e) {
        console.warn("Failed to persist recent playlists:", e);
      }
      return updated;
    });
  }, []);

  const loadPlaylists = useCallback(async () => {
    setLoading(true);
    try {
      const data = await invoke<Playlist[]>("get_playlists");
      setPlaylists(Array.isArray(data) ? data : []);
    } catch (e) {
      console.error("Failed to load playlists:", e);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadPlaylists();
    window.addEventListener("sonara_playlists_updated", loadPlaylists);
    return () => {
      window.removeEventListener("sonara_playlists_updated", loadPlaylists);
    };
  }, [loadPlaylists]);

  const createPlaylist = useCallback(
    async (name: string): Promise<Playlist> => {
      const newPlaylist = await invoke<Playlist>("create_playlist", { name });
      setPlaylists((prev) => [...prev, newPlaylist]);
      recordRecentPlaylist(newPlaylist.id);
      return newPlaylist;
    },
    [recordRecentPlaylist]
  );

  const renamePlaylist = useCallback(
    async (id: string, name: string): Promise<Playlist> => {
      const updated = await invoke<Playlist>("rename_playlist", { id, name });
      setPlaylists((prev) => prev.map((p) => (p.id === id ? updated : p)));
      return updated;
    },
    []
  );

  const deletePlaylist = useCallback(
    async (id: string): Promise<void> => {
      await invoke("delete_playlist", { id });
      setPlaylists((prev) => prev.filter((p) => p.id !== id));
      if (activePlaylistId === id) {
        setActivePlaylistId(null);
      }
      setRecentPlaylistIds((prev) => prev.filter((pId) => pId !== id));
    },
    [activePlaylistId]
  );

  const addTrackToPlaylist = useCallback(
    async (playlistId: string, track: Track): Promise<void> => {
      const updated = await invoke<Playlist>("add_track_to_playlist", {
        playlistId,
        track,
      });
      setPlaylists((prev) => prev.map((p) => (p.id === playlistId ? updated : p)));
      recordRecentPlaylist(playlistId);
    },
    [recordRecentPlaylist]
  );

  const removeTrackFromPlaylist = useCallback(
    async (playlistId: string, trackId: string): Promise<void> => {
      const updated = await invoke<Playlist>("remove_track_from_playlist", {
        playlistId,
        trackId,
      });
      setPlaylists((prev) => prev.map((p) => (p.id === playlistId ? updated : p)));
    },
    []
  );

  const reorderPlaylistTracks = useCallback(
    async (playlistId: string, fromIndex: number, toIndex: number): Promise<void> => {
      const updated = await invoke<Playlist>("reorder_playlist_tracks", {
        playlistId,
        fromIndex,
        toIndex,
      });
      setPlaylists((prev) => prev.map((p) => (p.id === playlistId ? updated : p)));
    },
    []
  );

  const activePlaylist = useMemo(() => {
    return playlists.find((p) => p.id === activePlaylistId) || null;
  }, [playlists, activePlaylistId]);

  // Playlists ordered for menus: recently used first, then remaining
  const playlistsRecentFirst = useMemo(() => {
    const recentSet = new Set(recentPlaylistIds);
    const recents: Playlist[] = [];
    for (const rId of recentPlaylistIds) {
      const found = playlists.find((p) => p.id === rId);
      if (found) recents.push(found);
    }
    const others = playlists.filter((p) => !recentSet.has(p.id));
    return [...recents, ...others];
  }, [playlists, recentPlaylistIds]);

  return {
    playlists,
    activePlaylistId,
    setActivePlaylistId,
    activePlaylist,
    loading,
    createPlaylist,
    renamePlaylist,
    deletePlaylist,
    addTrackToPlaylist,
    removeTrackFromPlaylist,
    reorderPlaylistTracks,
    playlistsRecentFirst,
    reloadPlaylists: loadPlaylists,
  };
}
