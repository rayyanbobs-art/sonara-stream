export interface Track {
  id: string;
  title: string;
  artist: string;
  duration: number;
  thumbnail: string;
  source: "youtube" | "spotify";
  addedAt?: number;
}

export type AccentColor = "gold" | "green" | "blue" | "purple" | "red";

export type NavTab = "home" | "songs" | "artists" | "albums" | "favorites" | "settings";
