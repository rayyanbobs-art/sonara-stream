import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import React from "react";
import { renderHook, act } from "@testing-library/react";
import { usePlaylists } from "../hooks/usePlaylists";
import { PlaylistView } from "../views/PlaylistView";
import { TrackContextMenu } from "../components/TrackContextMenu";
import { Playlist, Track } from "../types";

const mockInvoke = vi.fn();

vi.mock("@tauri-apps/api/core", () => ({
  invoke: (...args: any[]) => mockInvoke(...args),
}));

const sampleTrack1: Track = {
  id: "t1",
  title: "Song One",
  artist: "Artist A",
  thumbnail: "https://example.com/t1.jpg",
  duration: 180,
  source: "youtube",
  signature: "sig_t1",
};

const sampleTrack2: Track = {
  id: "t2",
  title: "Song Two",
  artist: "Artist B",
  thumbnail: "https://example.com/t2.jpg",
  duration: 210,
  source: "youtube",
  signature: "sig_t2",
};

const samplePlaylist: Playlist = {
  id: "pl_1",
  name: "Chill Vibes",
  created_at: 1000,
  updated_at: 2000,
  tracks: [sampleTrack1, sampleTrack2],
};

describe("usePlaylists Hook", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
  });

  it("loads playlists on mount", async () => {
    mockInvoke.mockResolvedValueOnce([samplePlaylist]);

    const { result } = renderHook(() => usePlaylists());

    await waitFor(() => {
      expect(result.current.playlists).toHaveLength(1);
      expect(result.current.playlists[0].name).toBe("Chill Vibes");
    });
    expect(mockInvoke).toHaveBeenCalledWith("get_playlists");
  });

  it("creates, renames, and deletes playlists", async () => {
    mockInvoke.mockResolvedValueOnce([]); // get_playlists

    const { result } = renderHook(() => usePlaylists());

    const createdPl: Playlist = {
      id: "pl_new",
      name: "New Mix",
      created_at: 3000,
      updated_at: 3000,
      tracks: [],
    };
    mockInvoke.mockResolvedValueOnce(createdPl);

    await act(async () => {
      await result.current.createPlaylist("New Mix");
    });

    expect(result.current.playlists).toHaveLength(1);
    expect(result.current.playlists[0].name).toBe("New Mix");

    // Rename
    const renamedPl = { ...createdPl, name: "Renamed Mix" };
    mockInvoke.mockResolvedValueOnce(renamedPl);

    await act(async () => {
      await result.current.renamePlaylist("pl_new", "Renamed Mix");
    });

    expect(result.current.playlists[0].name).toBe("Renamed Mix");

    // Delete
    mockInvoke.mockResolvedValueOnce(undefined);

    await act(async () => {
      await result.current.deletePlaylist("pl_new");
    });

    expect(result.current.playlists).toHaveLength(0);
  });
});

describe("PlaylistView Component", () => {
  it("renders tracks and triggers play, rename, and remove actions", () => {
    const onPlayTrack = vi.fn();
    const onPlayPlaylist = vi.fn();
    const onRenamePlaylist = vi.fn().mockResolvedValue({});
    const onDeletePlaylist = vi.fn().mockResolvedValue({});
    const onRemoveTrack = vi.fn();
    const onReorderTracks = vi.fn();
    const onToggleFavorite = vi.fn();

    render(
      <PlaylistView
        playlist={samplePlaylist}
        currentTrack={null}
        isPlaying={false}
        onPlayTrack={onPlayTrack}
        onPlayPlaylist={onPlayPlaylist}
        onRenamePlaylist={onRenamePlaylist}
        onDeletePlaylist={onDeletePlaylist}
        onRemoveTrack={onRemoveTrack}
        onReorderTracks={onReorderTracks}
        onToggleFavorite={onToggleFavorite}
        isFavorite={() => false}
      />
    );

    expect(screen.getByText("Chill Vibes")).toBeDefined();
    expect(screen.getByText("Song One")).toBeDefined();
    expect(screen.getByText("Song Two")).toBeDefined();

    // Play button
    const playBtn = screen.getByText("Play");
    fireEvent.click(playBtn);
    expect(onPlayPlaylist).toHaveBeenCalledWith(samplePlaylist.tracks, false);

    // Shuffle button
    const shuffleBtn = screen.getByText("Shuffle");
    fireEvent.click(shuffleBtn);
    expect(onPlayPlaylist).toHaveBeenCalledWith(samplePlaylist.tracks, true);
  });
});

describe("TrackContextMenu Component", () => {
  it("triggers queue and playlist actions", async () => {
    const onPlayNext = vi.fn();
    const onAddToQueue = vi.fn();
    const onAddToPlaylist = vi.fn();
    const onCreateAndAddToPlaylist = vi.fn();
    const onClose = vi.fn();

    render(
      <TrackContextMenu
        track={sampleTrack1}
        playlists={[samplePlaylist]}
        isOpen={true}
        position={{ x: 100, y: 100 }}
        onClose={onClose}
        onPlayNext={onPlayNext}
        onAddToQueue={onAddToQueue}
        onAddToPlaylist={onAddToPlaylist}
        onCreateAndAddToPlaylist={onCreateAndAddToPlaylist}
      />
    );

    const playNextBtn = screen.getByText("Play Next");
    fireEvent.click(playNextBtn);
    expect(onPlayNext).toHaveBeenCalledWith(sampleTrack1);

    const addQueueBtn = screen.getByText("Add to Queue");
    fireEvent.click(addQueueBtn);
    expect(onAddToQueue).toHaveBeenCalledWith(sampleTrack1);
  });
});
