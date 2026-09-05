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

export type AccentColor = "gold" | "green" | "blue" | "purple" | "red";

export type NavTab = "home" | "search" | "songs" | "favorites" | "settings";

export type RepeatMode = "off" | "all" | "one";
