import { describe, it, expect } from "vitest";
import {
  hashStringToId,
  isOnlineSong,
  getOnlineVideoId,
  onlineTrackToSong,
} from "../lib/onlineTrack";

describe("onlineTrack utilities", () => {
  describe("hashStringToId", () => {
    it("deterministically returns a negative number for any string", () => {
      const id1 = hashStringToId("test-track-1");
      const id2 = hashStringToId("test-track-1");
      const id3 = hashStringToId("different-track");

      expect(id1).toBe(id2);
      expect(id1).toBeLessThan(0);
      expect(id3).toBeLessThan(0);
    });

    it("ensures hash 0 falls back to -1", () => {
      expect(hashStringToId("")).toBe(-1);
    });
  });

  describe("isOnlineSong", () => {
    it("returns false for null or undefined song", () => {
      expect(isOnlineSong(null)).toBe(false);
      expect(isOnlineSong(undefined)).toBe(false);
    });

    it("returns false for local file paths on disk", () => {
      const localSong1: Song = {
        id: 1,
        title: "Local Track",
        artist_id: 1,
        artist_name: "Artist",
        album_id: 1,
        album_name: "Album",
        album_cover_path: null,
        album_artist_name: "Artist",
        duration: 180,
        track_number: 1,
        path: "C:\\Users\\Music\\song.mp3",
        is_favorite: false,
        favorite_added_at: null,
        last_played_at: null,
        play_count: 0,
        created_at: Date.now(),
        is_online: false,
        online_id: undefined,
      };

      const localSong2: Song = {
        ...localSong1,
        path: "/data/data/com.sonara.stream/files/downloads/track.m4a",
      };

      expect(isOnlineSong(localSong1)).toBe(false);
      expect(isOnlineSong(localSong2)).toBe(false);
    });

    it("returns true for streaming URLs and online schemes", () => {
      const onlineSong1: Song = {
        id: -1234,
        title: "Online Track",
        artist_id: -1,
        artist_name: "Artist",
        album_id: -1,
        album_name: "YouTube Music",
        album_cover_path: null,
        album_artist_name: "Artist",
        duration: 200,
        track_number: 1,
        path: "online://youtube/dQw4w9WgXcQ",
        is_favorite: false,
        favorite_added_at: null,
        last_played_at: null,
        play_count: 0,
        created_at: Date.now(),
        is_online: true,
        online_id: "dQw4w9WgXcQ",
      };

      const onlineSong2: Song = {
        ...onlineSong1,
        path: "https://googlevideo.com/videoplayback?id=123",
      };

      expect(isOnlineSong(onlineSong1)).toBe(true);
      expect(isOnlineSong(onlineSong2)).toBe(true);
    });
  });

  describe("getOnlineVideoId", () => {
    it("returns online_id if present", () => {
      const song = {
        online_id: "abc123xyz",
        path: "online://youtube/abc123xyz",
      } as Song;
      expect(getOnlineVideoId(song)).toBe("abc123xyz");
    });

    it("extracts ID from online:// path if online_id is absent", () => {
      const song = {
        online_id: null,
        path: "online://youtube/extracted_id",
      } as unknown as Song;
      expect(getOnlineVideoId(song)).toBe("extracted_id");
    });

    it("returns null for non-online song or null song", () => {
      expect(getOnlineVideoId(null)).toBeNull();
      const song = {
        online_id: null,
        path: "D:\\music\\song.flac",
      } as unknown as Song;
      expect(getOnlineVideoId(song)).toBeNull();
    });
  });

  describe("onlineTrackToSong", () => {
    it("transforms an OnlineTrack into a standard Song struct", () => {
      const onlineTrack: OnlineTrack = {
        id: "yt_video_123",
        title: "Streamed Title",
        artist: "Online Artist",
        thumbnail: "https://i.ytimg.com/vi/123/hqdefault.jpg",
        duration: 245,
        source: "youtube",
        signature: "Streamed Title Online Artist",
      };

      const song = onlineTrackToSong(onlineTrack);
      expect(song.id).toBe(hashStringToId("yt_video_123"));
      expect(song.title).toBe("Streamed Title");
      expect(song.artist_name).toBe("Online Artist");
      expect(song.duration).toBe(245);
      expect(song.is_online).toBe(true);
      expect(song.path).toBe("online://youtube/yt_video_123");
    });
  });
});
