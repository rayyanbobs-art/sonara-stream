import { describe, it, expect } from "vitest";
import { Track } from "../types";

describe("Search Results Signature Deduplication & UI Grouping", () => {
  const duplicateTracks: Track[] = [
    {
      id: "yt-1",
      title: "Woh Lamhe Woh Baatein (Official Video)",
      artist: "Atif Aslam",
      duration: 320,
      thumbnail: "https://example.com/1.jpg",
      source: "youtube",
      signature: "atif aslam woh lamhe baatein",
    },
    {
      id: "yt-2",
      title: "Woh Lamhe Woh Baatein (Lyrical)",
      artist: "Atif Aslam",
      duration: 322,
      thumbnail: "https://example.com/2.jpg",
      source: "youtube",
      signature: "atif aslam woh lamhe baatein",
    },
    {
      id: "yt-3",
      title: "Woh Lamhe Woh Baatein (Remix)",
      artist: "DJ Suketu",
      duration: 280,
      thumbnail: "https://example.com/3.jpg",
      source: "youtube",
      signature: "dj suketu woh lamhe baatein",
    },
  ];

  it("groups tracks by signature and accurately counts distinct songs vs collapsed uploads", () => {
    interface GroupedSong {
      signature: string;
      primaryTrack: Track;
      allVersions: Track[];
    }

    const map = new Map<string, GroupedSong>();
    for (const track of duplicateTracks) {
      const sig = track.signature || `${track.artist} - ${track.title}`.toLowerCase();
      if (!map.has(sig)) {
        map.set(sig, {
          signature: sig,
          primaryTrack: track,
          allVersions: [track],
        });
      } else {
        map.get(sig)!.allVersions.push(track);
      }
    }

    const grouped = Array.from(map.values());

    expect(grouped.length).toBe(2);
    expect(duplicateTracks.length).toBe(3);

    const atifGroup = grouped.find((g) => g.signature === "atif aslam woh lamhe baatein");
    expect(atifGroup).toBeDefined();
    expect(atifGroup?.allVersions.length).toBe(2);
    expect(atifGroup?.primaryTrack.id).toBe("yt-1");

    const alternateVersionsCount = atifGroup!.allVersions.length - 1;
    expect(alternateVersionsCount).toBe(1);
    expect(atifGroup!.allVersions[1].id).toBe("yt-2");
  });

  it("handles tracks with unique signatures without alternate versions", () => {
    const singleTrack: Track[] = [
      {
        id: "yt-unique",
        title: "Unique Acoustic Song",
        artist: "Solo Artist",
        duration: 210,
        thumbnail: "https://example.com/u.jpg",
        source: "youtube",
        signature: "solo artist unique acoustic song",
      },
    ];

    const map = new Map<string, { signature: string; primaryTrack: Track; allVersions: Track[] }>();
    for (const track of singleTrack) {
      const sig = track.signature;
      if (!map.has(sig)) {
        map.set(sig, { signature: sig, primaryTrack: track, allVersions: [track] });
      }
    }

    const grouped = Array.from(map.values());
    expect(grouped.length).toBe(1);
    expect(grouped[0].allVersions.length).toBe(1);
    expect(grouped[0].allVersions.length > 1).toBe(false);
  });
});
