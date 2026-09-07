import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { useMediaControls } from "../hooks/useMediaControls";
import { Track } from "../types";

const mockInvoke = vi.fn();
let eventListeners: Record<string, (event: any) => void> = {};

vi.mock("@tauri-apps/api/core", () => ({
  invoke: (...args: any[]) => mockInvoke(...args),
}));

vi.mock("@tauri-apps/api/event", () => ({
  listen: (event: string, callback: (payload: any) => void) => {
    eventListeners[event] = callback;
    return Promise.resolve(() => {
      delete eventListeners[event];
    });
  },
}));

const mockTrack: Track = {
  id: "t_media_1",
  title: "Blinding Lights",
  artist: "The Weeknd",
  thumbnail: "https://example.com/art.jpg",
  duration: 200,
  source: "youtube",
  signature: "sig_media_1",
};

describe("useMediaControls Hook", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    eventListeners = {};
    mockInvoke.mockResolvedValue(undefined);
    (window as any).__TAURI_INTERNALS__ = {};
  });

  it("publishes metadata to OS when track is set", () => {
    renderHook(() =>
      useMediaControls({
        currentTrack: mockTrack,
        isPlaying: true,
        currentTime: 45,
        duration: 200,
        onTogglePlay: vi.fn(),
        onNext: vi.fn(),
        onPrev: vi.fn(),
        onSeek: vi.fn(),
      })
    );

    expect(mockInvoke).toHaveBeenCalledWith(
      "update_media_metadata",
      expect.objectContaining({
        title: "Blinding Lights",
        artist: "The Weeknd",
        durationSecs: 200,
        isPlaying: true,
        positionSecs: 45,
      })
    );
  });

  it("handles media key events from OS", async () => {
    const onTogglePlay = vi.fn();
    const onNext = vi.fn();
    const onPrev = vi.fn();
    const onSeek = vi.fn();

    renderHook(() =>
      useMediaControls({
        currentTrack: mockTrack,
        isPlaying: false,
        currentTime: 10,
        duration: 200,
        onTogglePlay,
        onNext,
        onPrev,
        onSeek,
      })
    );

    await waitFor(() => {
      expect(eventListeners["media-key"]).toBeDefined();
      expect(eventListeners["media-key-seek"]).toBeDefined();
    });

    // Simulate OS play key
    eventListeners["media-key"]({ payload: "play" });
    expect(onTogglePlay).toHaveBeenCalled();

    // Simulate OS next key
    eventListeners["media-key"]({ payload: "next" });
    expect(onNext).toHaveBeenCalled();

    // Simulate OS prev key
    eventListeners["media-key"]({ payload: "prev" });
    expect(onPrev).toHaveBeenCalled();

    // Simulate OS seek event
    eventListeners["media-key-seek"]({ payload: 75.5 });
    expect(onSeek).toHaveBeenCalledWith(75.5);
  });
});
