export interface Track {
  id: string;
  title: string;
  artist: string;
  duration: number;
  thumbnail: string;
  source: "youtube" | "spotify";
  signature: string;
  addedAt?: number;
}

export interface Playlist {
  id: string;
  name: string;
  tracks: Track[];
  created_at: number;
  updated_at: number;
}

export type AccentColor = "gold" | "green" | "blue" | "purple" | "red";

export type NavTab = "home" | "search" | "songs" | "artists" | "favorites" | "settings" | "playlist";

export type RepeatMode = "off" | "all" | "one";

