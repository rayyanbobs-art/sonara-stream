import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import React from "react";
import { LyricsView } from "../components/LyricsView";
import { Track } from "../types";

const mockInvoke = vi.fn();

vi.mock("@tauri-apps/api/core", () => ({
  invoke: (...args: any[]) => mockInvoke(...args),
}));

const mockTrack: Track = {
  id: "t_lyric_1",
  title: "Bohemian Rhapsody",
  artist: "Queen",
  thumbnail: "https://example.com/art.jpg",
  duration: 354,
  source: "youtube",
  signature: "sig_lyric_1",
};

describe("LyricsView Component", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
  });

  it("renders synced lyrics and highlights active line based on currentTime", async () => {
    mockInvoke.mockResolvedValueOnce({
      id: 101,
      plain_lyrics: null,
      synced_lyrics: "[00:02.50] Is this the real life?\n[00:05.80] Is this just fantasy?\n[00:10.00] Caught in a landslide",
      instrumental: false,
      cached_at_unix: 1000,
    });

    const onSeek = vi.fn();

    const { rerender } = render(
      <LyricsView currentTrack={mockTrack} currentTime={3.0} onSeek={onSeek} />
    );

    await waitFor(() => {
      expect(screen.getByText("Is this the real life?")).toBeDefined();
    });

    // Line 1 should be active at 3.0s
    const line1 = screen.getByText("Is this the real life?");
    expect(line1.classList.contains("active")).toBe(true);

    // Clicking line 2 triggers seek to 5.8s
    const line2 = screen.getByText("Is this just fantasy?");
    fireEvent.click(line2);
    expect(onSeek).toHaveBeenCalledWith(5.8);

    // Advance playback time to 11.0s
    rerender(<LyricsView currentTrack={mockTrack} currentTime={11.0} onSeek={onSeek} />);
    const line3 = screen.getByText("Caught in a landslide");
    expect(line3.classList.contains("active")).toBe(true);
  });

  it("renders plain lyrics fallback when synced lyrics are absent", async () => {
    mockInvoke.mockResolvedValueOnce({
      id: 102,
      plain_lyrics: "First verse line one\nFirst verse line two",
      synced_lyrics: null,
      instrumental: false,
      cached_at_unix: 1000,
    });

    render(<LyricsView currentTrack={mockTrack} currentTime={0} onSeek={vi.fn()} />);

    await waitFor(() => {
      expect(screen.getByText("First verse line one")).toBeDefined();
      expect(screen.getByText("First verse line two")).toBeDefined();
    });
  });

  it("renders honest empty state when no lyrics are found", async () => {
    mockInvoke.mockResolvedValueOnce({
      id: null,
      plain_lyrics: null,
      synced_lyrics: null,
      instrumental: false,
      cached_at_unix: 1000,
    });

    render(<LyricsView currentTrack={mockTrack} currentTime={0} onSeek={vi.fn()} />);

    await waitFor(() => {
      expect(screen.getByText("No Lyrics Found")).toBeDefined();
      expect(
        screen.getByText("We couldn't find synced or plain lyrics for this track on LRCLIB.")
      ).toBeDefined();
    });
  });

  it("changes font size and saves to localStorage", async () => {
    mockInvoke.mockResolvedValueOnce({
      id: 103,
      plain_lyrics: "Simple song",
      synced_lyrics: null,
      instrumental: false,
      cached_at_unix: 1000,
    });

    render(<LyricsView currentTrack={mockTrack} currentTime={0} onSeek={vi.fn()} />);

    await waitFor(() => {
      expect(screen.getByText("Simple song")).toBeDefined();
    });

    const largeBtn = screen.getByTitle("Large font size");
    fireEvent.click(largeBtn);

    expect(localStorage.getItem("sonara_lyrics_fontsize")).toBe("large");
  });
});
