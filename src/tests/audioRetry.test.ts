import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, act } from "@testing-library/react";
import { useAudioPlayer } from "../hooks/useAudioPlayer";
import { Track } from "../types";

const mockInvoke = vi.fn();
vi.mock("@tauri-apps/api/core", () => ({
  invoke: (...args: any[]) => mockInvoke(...args),
}));

describe("useAudioPlayer error retry behavior", () => {
  const sampleTrack: Track = {
    id: "retry-track-1",
    title: "Test Track",
    artist: "Test Artist",
    duration: 180,
    thumbnail: "https://example.com/thumb.jpg",
    source: "youtube",
    signature: "test artist test track",
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("retries with bypassCache: true exactly once on audio error", async () => {
    const errorCallback = vi.fn();

    // Mock HTMLMediaElement play & pause in jsdom
    window.HTMLMediaElement.prototype.play = vi.fn().mockResolvedValue(undefined);
    window.HTMLMediaElement.prototype.pause = vi.fn();

    mockInvoke.mockResolvedValueOnce("https://stream.youtube.com/initial-url");

    const { result } = renderHook(() =>
      useAudioPlayer({
        onError: errorCallback,
      })
    );

    // Initial play
    await act(async () => {
      await result.current.playTrack(sampleTrack);
    });

    expect(mockInvoke).toHaveBeenCalledWith("get_stream_url", { id: sampleTrack.id });

    // Mock successful retry on error
    mockInvoke.mockResolvedValueOnce("https://stream.youtube.com/fresh-bypassed-url");

    // Simulate audio element error event
    await act(async () => {
      if (result.current.audioRef.current && result.current.audioRef.current.onerror) {
        await (result.current.audioRef.current.onerror as any)(new Event("error"));
      }
    });

    // Expect invoke called with bypassCache: true
    expect(mockInvoke).toHaveBeenCalledWith("get_stream_url", {
      id: sampleTrack.id,
      bypassCache: true,
    });
    expect(result.current.audioRef.current?.src).toBe("https://stream.youtube.com/fresh-bypassed-url");
  });

  it("surfaces friendly copy if retry also fails", async () => {
    let latestError: string | null = null;
    const errorCallback = vi.fn((msg) => {
      latestError = msg;
    });

    window.HTMLMediaElement.prototype.play = vi.fn().mockResolvedValue(undefined);
    window.HTMLMediaElement.prototype.pause = vi.fn();

    mockInvoke.mockResolvedValueOnce("https://stream.youtube.com/initial-url");

    const { result } = renderHook(() =>
      useAudioPlayer({
        onError: errorCallback,
      })
    );

    await act(async () => {
      await result.current.playTrack(sampleTrack);
    });

    // Mock retry failure
    mockInvoke.mockRejectedValueOnce(new Error("Stream expired"));

    await act(async () => {
      if (result.current.audioRef.current && result.current.audioRef.current.onerror) {
        await (result.current.audioRef.current.onerror as any)(new Event("error"));
      }
    });

    expect(latestError).toBe(
      "Stream playback failed. The audio stream could not be loaded. Please choose another track."
    );
  });

  it("fires audio.onerror twice on the same track, calling bypassCache invoke exactly once and surfacing failure copy", async () => {
    let latestError: string | null = null;
    const errorCallback = vi.fn((msg) => {
      latestError = msg;
    });

    window.HTMLMediaElement.prototype.play = vi.fn().mockResolvedValue(undefined);
    window.HTMLMediaElement.prototype.pause = vi.fn();

    // Initial play resolves
    mockInvoke.mockResolvedValueOnce("https://stream.youtube.com/initial-url");

    const { result } = renderHook(() =>
      useAudioPlayer({
        onError: errorCallback,
      })
    );

    await act(async () => {
      await result.current.playTrack(sampleTrack);
    });

    expect(mockInvoke).toHaveBeenCalledTimes(1);
    expect(mockInvoke).toHaveBeenCalledWith("get_stream_url", { id: sampleTrack.id });

    // Mock first retry resolution
    mockInvoke.mockResolvedValueOnce("https://stream.youtube.com/bypassed-url-1");

    // First audio error event
    await act(async () => {
      if (result.current.audioRef.current?.onerror) {
        await (result.current.audioRef.current.onerror as any)(new Event("error"));
      }
    });

    // Exactly one bypassCache retry invoke has occurred
    const bypassCallsFirst = mockInvoke.mock.calls.filter(
      (call) => call[0] === "get_stream_url" && call[1]?.bypassCache === true
    );
    expect(bypassCallsFirst.length).toBe(1);

    // Second audio error event on the same track
    await act(async () => {
      if (result.current.audioRef.current?.onerror) {
        await (result.current.audioRef.current.onerror as any)(new Event("error"));
      }
    });

    // Assert invoke("get_stream_url", { id, bypassCache: true }) is called exactly once
    const bypassCallsSecond = mockInvoke.mock.calls.filter(
      (call) => call[0] === "get_stream_url" && call[1]?.bypassCache === true
    );
    expect(bypassCallsSecond.length).toBe(1);

    // Assert failure copy is surfaced after second error
    expect(latestError).toBe(
      "Stream playback failed. The audio stream could not be loaded. Please choose another track."
    );
  });

  it("resets retryCount on playTrack so playing track B after track A error also retries with bypassCache", async () => {
    const errorCallback = vi.fn();
    window.HTMLMediaElement.prototype.play = vi.fn().mockResolvedValue(undefined);
    window.HTMLMediaElement.prototype.pause = vi.fn();

    const trackA: Track = {
      id: "track-A",
      title: "Track A",
      artist: "Artist A",
      duration: 180,
      thumbnail: "https://example.com/a.jpg",
      source: "youtube",
      signature: "artist a track a",
    };

    const trackB: Track = {
      id: "track-B",
      title: "Track B",
      artist: "Artist B",
      duration: 200,
      thumbnail: "https://example.com/b.jpg",
      source: "youtube",
      signature: "artist b track b",
    };

    const { result } = renderHook(() =>
      useAudioPlayer({
        onError: errorCallback,
      })
    );

    // 1. Play Track A
    mockInvoke.mockResolvedValueOnce("https://stream.youtube.com/track-a");
    await act(async () => {
      await result.current.playTrack(trackA);
    });

    // 2. Track A onerror -> retries with bypassCache: true
    mockInvoke.mockResolvedValueOnce("https://stream.youtube.com/track-a-retry");
    await act(async () => {
      if (result.current.audioRef.current?.onerror) {
        await (result.current.audioRef.current.onerror as any)(new Event("error"));
      }
    });

    expect(mockInvoke).toHaveBeenCalledWith("get_stream_url", {
      id: trackA.id,
      bypassCache: true,
    });

    // 3. Play Track B
    mockInvoke.mockResolvedValueOnce("https://stream.youtube.com/track-b");
    await act(async () => {
      await result.current.playTrack(trackB);
    });

    // 4. Track B onerror -> must retry with bypassCache: true because retryCount was reset
    mockInvoke.mockResolvedValueOnce("https://stream.youtube.com/track-b-retry");
    await act(async () => {
      if (result.current.audioRef.current?.onerror) {
        await (result.current.audioRef.current.onerror as any)(new Event("error"));
      }
    });

    expect(mockInvoke).toHaveBeenCalledWith("get_stream_url", {
      id: trackB.id,
      bypassCache: true,
    });
  });
});


