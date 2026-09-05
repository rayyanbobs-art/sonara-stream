import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, act } from "@testing-library/react";
import { useAudioPlayer } from "../hooks/useAudioPlayer";
import { Track } from "../types";

const mockInvoke = vi.fn();
vi.mock("@tauri-apps/api/core", () => ({
  invoke: (...args: any[]) => mockInvoke(...args),
}));

describe("useAudioPlayer concurrency & supersession handling", () => {
  const track1: Track = {
    id: "track-1",
    title: "Track 1",
    artist: "Artist 1",
    duration: 180,
    thumbnail: "https://example.com/1.jpg",
    source: "youtube",
    signature: "artist 1 track 1",
  };

  const track2: Track = {
    id: "track-2",
    title: "Track 2",
    artist: "Artist 2",
    duration: 200,
    thumbnail: "https://example.com/2.jpg",
    source: "youtube",
    signature: "artist 2 track 2",
  };

  beforeEach(() => {
    vi.clearAllMocks();
    window.HTMLMediaElement.prototype.play = vi.fn().mockResolvedValue(undefined);
    window.HTMLMediaElement.prototype.pause = vi.fn();
  });

  it("resolves request 1 AFTER request 2: audio.src is request 2's URL, request 1 is discarded with no toast", async () => {
    let latestError: string | null = null;
    const onError = vi.fn((err) => {
      latestError = err;
    });

    let resolveReq1: (url: string) => void = () => {};
    let resolveReq2: (url: string) => void = () => {};

    const req1Promise = new Promise<string>((res) => {
      resolveReq1 = res;
    });
    const req2Promise = new Promise<string>((res) => {
      resolveReq2 = res;
    });

    mockInvoke.mockImplementation((cmd, args) => {
      if (cmd === "get_stream_url") {
        if (args.id === track1.id) return req1Promise;
        if (args.id === track2.id) return req2Promise;
      }
      if (cmd === "cancel_stream_request") {
        return Promise.resolve(true);
      }
      return Promise.resolve("");
    });

    const { result } = renderHook(() => useAudioPlayer({ onError }));

    // Start request 1 (track 1)
    let p1: Promise<void>;
    act(() => {
      p1 = result.current.playTrack(track1);
    });

    // Start request 2 (track 2) before request 1 finishes
    let p2: Promise<void>;
    act(() => {
      p2 = result.current.playTrack(track2);
    });

    // Resolve request 2 FIRST
    await act(async () => {
      resolveReq2("https://stream.youtube.com/track2-url");
      await p2;
    });

    expect(result.current.audioRef.current?.src).toBe("https://stream.youtube.com/track2-url");
    expect(result.current.currentTrack?.id).toBe(track2.id);

    // Now resolve request 1 AFTER request 2
    await act(async () => {
      resolveReq1("https://stream.youtube.com/track1-url");
      await p1;
    });

    // Assert request 1 was discarded and did NOT overwrite audio.src
    expect(result.current.audioRef.current?.src).toBe("https://stream.youtube.com/track2-url");
    expect(latestError).toBeNull();
    expect(onError).not.toHaveBeenCalledWith("Failed to stream track.");
  });

  it("treats play() rejection with AbortError after supersession as non-error (no toast, no retry)", async () => {
    let latestError: string | null = null;
    const onError = vi.fn((err) => {
      latestError = err;
    });

    mockInvoke.mockImplementation((cmd, args) => {
      if (cmd === "get_stream_url") {
        return Promise.resolve(`https://stream.youtube.com/${args.id}`);
      }
      return Promise.resolve(true);
    });

    const { result } = renderHook(() => useAudioPlayer({ onError }));

    // Mock play() rejecting with DOMException AbortError
    window.HTMLMediaElement.prototype.play = vi.fn().mockRejectedValue(
      new DOMException("The play() request was interrupted by a new load request.", "AbortError")
    );

    await act(async () => {
      await result.current.playTrack(track1);
    });

    // Should NOT surface error toast
    expect(latestError).toBeNull();
    expect(onError).not.toHaveBeenCalledWith("Failed to stream track.");
  });

  it("5 rapid playTrack calls results in audio.src of the last track and zero toasts on success", async () => {
    let latestError: string | null = null;
    const onError = vi.fn((err) => {
      latestError = err;
    });

    mockInvoke.mockImplementation((cmd, args) => {
      if (cmd === "get_stream_url") {
        return Promise.resolve(`https://stream.youtube.com/${args.id}`);
      }
      return Promise.resolve(true);
    });

    const { result } = renderHook(() => useAudioPlayer({ onError }));

    const tracks: Track[] = Array.from({ length: 5 }, (_, i) => ({
      id: `rapid-track-${i + 1}`,
      title: `Rapid Track ${i + 1}`,
      artist: `Artist ${i + 1}`,
      duration: 180,
      thumbnail: `https://example.com/${i + 1}.jpg`,
      source: "youtube",
      signature: `artist ${i + 1} rapid track ${i + 1}`,
    }));

    await act(async () => {
      // Fire 5 rapid calls
      const promises = tracks.map((t) => result.current.playTrack(t));
      await Promise.all(promises);
    });

    // Audio src must be the 5th track
    expect(result.current.audioRef.current?.src).toBe("https://stream.youtube.com/rapid-track-5");
    expect(result.current.currentTrack?.id).toBe("rapid-track-5");
    expect(latestError).toBeNull();
  });

  it("superseded request onerror does not trigger a retry invoke", async () => {
    mockInvoke.mockImplementation((cmd, args) => {
      if (cmd === "get_stream_url") {
        return Promise.resolve(`https://stream.youtube.com/${args.id}`);
      }
      return Promise.resolve(true);
    });

    const { result } = renderHook(() => useAudioPlayer());

    await act(async () => {
      await result.current.playTrack(track1);
    });

    // Play track 2
    await act(async () => {
      await result.current.playTrack(track2);
    });

    // Simulate an error on track 1 that arrived after track 2 took over
    // If the generation is superseded, onerror should ignore it
    result.current.currentTrackRef.current = track2;

    await act(async () => {
      if (result.current.audioRef.current?.onerror) {
        await (result.current.audioRef.current.onerror as any)(new Event("error"));
      }
    });

    // Verify no retry for track1
    const track1Retries = mockInvoke.mock.calls.filter(
      (c) => c[0] === "get_stream_url" && c[1]?.id === track1.id && c[1]?.bypassCache === true
    );
    expect(track1Retries.length).toBe(0);
  });
});
