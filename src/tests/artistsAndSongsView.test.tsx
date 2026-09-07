import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import React from "react";
import { ArtistsView } from "../views/ArtistsView";
import { SongsView } from "../views/SongsView";
import { Track } from "../types";

const mockInvoke = vi.fn();

vi.mock("@tauri-apps/api/core", () => ({
  invoke: (...args: any[]) => mockInvoke(...args),
}));

const mockTracks: Track[] = [
  {
    id: "t1",
    title: "Alpha Song",
    artist: "The Beatles",
    thumbnail: "https://example.com/beatles.jpg",
    duration: 180,
    source: "youtube",
  },
  {
    id: "t2",
    title: "Beta Song",
    artist: "The Beatles",
    thumbnail: "https://example.com/beatles2.jpg",
    duration: 210,
    source: "youtube",
  },
  {
    id: "t3",
    title: "Gamma Song",
    artist: "Dua Lipa",
    thumbnail: "https://example.com/dualipa.jpg",
    duration: 240,
    source: "youtube",
  },
];

describe("ArtistsView Component", () => {
  it("groups tracks by artist and renders track counts", () => {
    const onSelectArtist = vi.fn();
    const onPlayTrack = vi.fn();

    render(
      <ArtistsView
        tracks={mockTracks}
        onSelectArtist={onSelectArtist}
        onPlayTrack={onPlayTrack}
        onBrowse={vi.fn()}
      />
    );

    expect(screen.getByText("The Beatles")).toBeDefined();
    expect(screen.getByText("2 tracks")).toBeDefined();
    expect(screen.getByText("Dua Lipa")).toBeDefined();
    expect(screen.getByText("1 track")).toBeDefined();

    // Clicking an artist triggers navigation
    fireEvent.click(screen.getByText("The Beatles"));
    expect(onSelectArtist).toHaveBeenCalledWith("The Beatles");
  });

  it("filters artists with search input", () => {
    render(
      <ArtistsView
        tracks={mockTracks}
        onSelectArtist={vi.fn()}
        onPlayTrack={vi.fn()}
        onBrowse={vi.fn()}
      />
    );

    const searchInput = screen.getByPlaceholderText("Search artists...");
    fireEvent.change(searchInput, { target: { value: "Dua" } });

    expect(screen.getByText("Dua Lipa")).toBeDefined();
    expect(screen.queryByText("The Beatles")).toBeNull();
  });
});

describe("SongsView Component Enhanced Sorting and Filtering", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockInvoke.mockResolvedValue([
      { track: { id: "t1" }, signature: "t1", played_at_unix: 1000 },
      { track: { id: "t1" }, signature: "t1", played_at_unix: 2000 },
      { track: { id: "t3" }, signature: "t3", played_at_unix: 3000 },
    ]);
  });

  it("filters tracks by artist when filterArtist prop is provided", () => {
    const onClear = vi.fn();

    render(
      <SongsView
        tracks={mockTracks}
        currentTrack={null}
        isPlaying={false}
        onPlayTrack={vi.fn()}
        onToggleFavorite={vi.fn()}
        isFavorite={() => false}
        onBrowse={vi.fn()}
        filterArtist="The Beatles"
        onClearArtistFilter={onClear}
      />
    );

    expect(screen.getByText("Artist: The Beatles")).toBeDefined();
    expect(screen.getByText("Alpha Song")).toBeDefined();
    expect(screen.getByText("Beta Song")).toBeDefined();
    expect(screen.queryByText("Gamma Song")).toBeNull();

    // Click clear artist filter
    const clearBtn = screen.getByTitle("Clear artist filter");
    fireEvent.click(clearBtn);
    expect(onClear).toHaveBeenCalled();
  });

  it("filters songs in real-time with search input", () => {
    render(
      <SongsView
        tracks={mockTracks}
        currentTrack={null}
        isPlaying={false}
        onPlayTrack={vi.fn()}
        onToggleFavorite={vi.fn()}
        isFavorite={() => false}
        onBrowse={vi.fn()}
      />
    );

    const searchInput = screen.getByPlaceholderText("Search songs or artists...");
    fireEvent.change(searchInput, { target: { value: "Gamma" } });

    expect(screen.getByText("Gamma Song")).toBeDefined();
    expect(screen.queryByText("Alpha Song")).toBeNull();
  });
});
