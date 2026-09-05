import { describe, it, expect } from "vitest";
import { isSameSong } from "../utils/trackSignature";
import { Track } from "../types";

describe("isSameSong", () => {
  it("returns true when track IDs match", () => {
    const a: Track = {
      id: "abc-123",
      title: "Song A",
      artist: "Artist 1",
      duration: 180,
      thumbnail: "https://example.com/a.jpg",
      source: "youtube",
      signature: "song a artist",
    };
    const b: Track = {
      id: "abc-123",
      title: "Song A (Live)",
      artist: "Artist 1",
      duration: 190,
      thumbnail: "https://example.com/b.jpg",
      source: "youtube",
      signature: "live song a artist",
    };
    expect(isSameSong(a, b)).toBe(true);
  });

  it("returns true when canonical signatures match even with different IDs", () => {
    const a: Track = {
      id: "id-1",
      title: "Blinding Lights (Official Video)",
      artist: "The Weeknd",
      duration: 200,
      thumbnail: "https://example.com/1.jpg",
      source: "youtube",
      signature: "blinding lights the weeknd",
    };
    const b: Track = {
      id: "id-2",
      title: "Blinding Lights [Audio]",
      artist: "The Weeknd",
      duration: 200,
      thumbnail: "https://example.com/2.jpg",
      source: "youtube",
      signature: "blinding lights the weeknd",
    };
    expect(isSameSong(a, b)).toBe(true);
  });

  it("returns false when both ID and canonical signature differ", () => {
    const a: Track = {
      id: "id-1",
      title: "Song One",
      artist: "Artist A",
      duration: 180,
      thumbnail: "https://example.com/1.jpg",
      source: "youtube",
      signature: "artist a song one",
    };
    const b: Track = {
      id: "id-2",
      title: "Song Two",
      artist: "Artist B",
      duration: 210,
      thumbnail: "https://example.com/2.jpg",
      source: "youtube",
      signature: "artist b song two",
    };
    expect(isSameSong(a, b)).toBe(false);
  });

  it("handles null or undefined gracefully", () => {
    const track: Track = {
      id: "id-1",
      title: "Song One",
      artist: "Artist A",
      duration: 180,
      thumbnail: "https://example.com/1.jpg",
      source: "youtube",
      signature: "artist a song one",
    };
    expect(isSameSong(track, null)).toBe(false);
    expect(isSameSong(null, track)).toBe(false);
    expect(isSameSong(undefined, undefined)).toBe(false);
  });
});
