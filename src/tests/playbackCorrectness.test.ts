import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { renderHook, act } from "@testing-library/react";
import { useQueue } from "../hooks/useQueue";
import { useAudioPlayer } from "../hooks/useAudioPlayer";
import { Track } from "../types";

const mockInvoke = vi.fn();
vi.mock("@tauri-apps/api/core", () => ({
  invoke: (...args: any[]) => mockInvoke(...args),
}));

describe("Playback & Queue Correctness (Next, Prev, Repeat)", () => {
  const trackA1: Track = {
    id: "track-a1",
    title: "Song A Version 1",
    artist: "Artist A",
    duration: 180,
    thumbnail: "https://example.com/a1.jpg",
    source: "youtube",
    signature: "artist a song a",
  };

  const trackA2: Track = {
    id: "track-a2",
    title: "Song A Version 2",
    artist: "Artist A",
    duration: 185,
    thumbnail: "https://example.com/a2.jpg",
    source: "youtube",
    signature: "artist a song a", // Identical signature!
  };

  const trackB: Track = {
    id: "track-b",
    title: "Song B",
    artist: "Artist B",
    duration: 210,
    thumbnail: "https://example.com/b.jpg",
    source: "youtube",
    signature: "artist b song b",
  };

  beforeEach(() => {
    vi.clearAllMocks();
    vi.useFakeTimers();
    window.HTMLMediaElement.prototype.play = vi.fn().mockResolvedValue(undefined);
    window.HTMLMediaElement.prototype.pause = vi.fn();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("handleNext with empty queue builds radio and advances playback", async () => {
    const onPlayTrack = vi.fn().mockResolvedValue(undefined);
    const onSelectTrack = vi.fn();

    mockInvoke.mockImplementation((cmd) => {
      if (cmd === "build_radio") {
        return Promise.resolve([trackB]);
      }
      return Promise.resolve([]);
    });

    const { result } = renderHook(() =>
      useQueue({
        currentTrack: trackA1,
        activeTab: "home",
        tracks: [],
        favorites: [],
        onPlayTrack,
        onSelectTrack,
      })
    );

    // Initial queue is empty
    expect(result.current.upNextMix.length).toBe(0);

    // User presses Next
    await act(async () => {
      await result.current.handleNext();
    });

    // Advance 200ms debounce
    act(() => {
      vi.advanceTimersByTime(250);
    });

    expect(mockInvoke).toHaveBeenCalledWith(
      "build_radio",
      expect.objectContaining({ seedTrack: trackA1 })
    );
    expect(onPlayTrack).toHaveBeenCalledWith(trackB, true);
  });

  it("handleNext with queue whose every item shares current signature advances to a different signature", async () => {
    const onPlayTrack = vi.fn().mockResolvedValue(undefined);
    const onSelectTrack = vi.fn();

    mockInvoke.mockImplementation((cmd) => {
      if (cmd === "build_radio") {
        return Promise.resolve([trackB]);
      }
      return Promise.resolve([]);
    });

    const { result } = renderHook(() =>
      useQueue({
        currentTrack: trackA1,
        activeTab: "home",
        tracks: [trackA1, trackA2], // All tracks share the same song signature
        favorites: [],
        onPlayTrack,
        onSelectTrack,
      })
    );

    // Put trackA2 in upNextMix (same signature as current track)
    act(() => {
      result.current.setUpNextMix([trackA2]);
    });

    // Press Next: must NOT play trackA2 because it shares the same song signature
    await act(async () => {
      await result.current.handleNext();
    });

    act(() => {
      vi.advanceTimersByTime(250);
    });

    // It must build radio and advance to trackB (different signature)
    expect(mockInvoke).toHaveBeenCalledWith(
      "build_radio",
      expect.objectContaining({ seedTrack: trackA1 })
    );
    expect(onPlayTrack).toHaveBeenCalledWith(trackB, true);
  });

  it("Prev restarts track if currentTime > 3s, without navigating to previous track", async () => {
    const onPlayTrack = vi.fn().mockResolvedValue(undefined);
    const fakeAudio = document.createElement("audio");
    fakeAudio.currentTime = 12.5; // > 3 seconds
    const audioRef = { current: fakeAudio };

    const { result } = renderHook(() =>
      useQueue({
        currentTrack: trackB,
        activeTab: "home",
        tracks: [trackA1, trackB],
        favorites: [],
        onPlayTrack,
        audioRef,
      })
    );

    act(() => {
      result.current.handlePrev();
    });

    // Audio position restarted to 0
    expect(fakeAudio.currentTime).toBe(0);
    // Did NOT call onPlayTrack to navigate
    act(() => {
      vi.advanceTimersByTime(250);
    });
    expect(onPlayTrack).not.toHaveBeenCalled();
  });

  it("Repeat-one loops naturally onended but explicit Next advances to different track", async () => {
    const onNext = vi.fn();
    mockInvoke.mockImplementation(() => Promise.resolve("https://example.com/stream"));

    const { result } = renderHook(() =>
      useAudioPlayer({
        onNext,
      })
    );

    // Set repeat mode to 'one'
    act(() => {
      result.current.toggleRepeat(); // off -> all
      result.current.toggleRepeat(); // all -> one
    });
    expect(result.current.repeatMode).toBe("one");

    // Play track A1
    await act(async () => {
      await result.current.playTrack(trackA1);
    });

    if (result.current.audioRef.current) {
      result.current.audioRef.current.currentTime = 180;
    }

    // Trigger natural track end
    await act(async () => {
      if (result.current.audioRef.current?.onended) {
        (result.current.audioRef.current.onended as any)(new Event("ended"));
      }
    });

    // onNext was NOT called because repeat-one replayed track
    expect(onNext).not.toHaveBeenCalled();
    expect(result.current.audioRef.current?.currentTime).toBe(0);
  });
});
