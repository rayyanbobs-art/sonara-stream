import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import React from "react";
import { NowPlayingModal } from "../components/NowPlayingModal";
import { getDominantColor, clearDominantColorCache } from "../utils/dominantColor";
import { Track } from "../types";

const mockInvoke = vi.fn();

vi.mock("@tauri-apps/api/core", () => ({
  invoke: (...args: any[]) => mockInvoke(...args),
}));

const mockTrack: Track = {
  id: "track_fullscreen_1",
  title: "Ambient Sunset",
  artist: "Solaris",
  thumbnail: "https://example.com/art.jpg",
  duration: 240,
  source: "youtube",
};

describe("getDominantColor", () => {
  beforeEach(() => {
    clearDominantColorCache();
  });

  it("returns fallback color on empty url or canvas error", async () => {
    const color = await getDominantColor("t_empty", "");
    expect(color).toContain("rgba");
  });
});

describe("NowPlayingModal Component", () => {
  const defaultProps = {
    isOpen: true,
    onClose: vi.fn(),
    currentTrack: mockTrack,
    isPlaying: true,
    isBuffering: false,
    currentTime: 60,
    duration: 240,
    volume: 0.8,
    isShuffle: false,
    repeatMode: "off" as const,
    isFavorite: false,
    onTogglePlay: vi.fn(),
    onSeek: vi.fn(),
    onVolumeChange: vi.fn(),
    onNext: vi.fn(),
    onPrev: vi.fn(),
    onToggleShuffle: vi.fn(),
    onToggleRepeat: vi.fn(),
    onToggleFavorite: vi.fn(),
    upNextTracks: [],
    userQueue: [],
    onPlayTrack: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders track details, progress, and controls when open", () => {
    render(<NowPlayingModal {...defaultProps} />);

    expect(screen.getByText("Ambient Sunset")).toBeDefined();
    expect(screen.getByText("Solaris")).toBeDefined();
    expect(screen.getByText("1:00")).toBeDefined();
    expect(screen.getByText("4:00")).toBeDefined();
  });

  it("switches tabs between Up Next, Lyrics, and Related", async () => {
    mockInvoke.mockResolvedValueOnce([
      {
        id: "rel_1",
        title: "Related Song",
        artist: "Solaris",
        thumbnail: "https://example.com/rel.jpg",
        duration: 200,
        source: "youtube",
      },
    ]);

    render(<NowPlayingModal {...defaultProps} />);

    // Click Lyrics tab
    const lyricsTab = screen.getByText("Lyrics");
    fireEvent.click(lyricsTab);
    expect(screen.getByText("Synced and plain lyrics will be displayed here.")).toBeDefined();

    // Click Related tab
    const relatedTab = screen.getByText("Related");
    fireEvent.click(relatedTab);

    await waitFor(() => {
      expect(screen.getByText("Related Song")).toBeDefined();
    });
  });

  it("closes on Escape key press", () => {
    const onClose = vi.fn();
    render(<NowPlayingModal {...defaultProps} onClose={onClose} />);

    fireEvent.keyDown(window, { key: "Escape" });
    expect(onClose).toHaveBeenCalled();
  });
});
