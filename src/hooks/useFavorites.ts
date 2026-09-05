import { useState, useEffect } from "react";
import { Track } from "../types";

export function useFavorites() {
  const [favorites, setFavorites] = useState<Track[]>(() => {
    try {
      const saved = localStorage.getItem("sonara_favorites");
      return saved ? JSON.parse(saved) : [];
    } catch {
      return [];
    }
  });

  useEffect(() => {
    localStorage.setItem("sonara_favorites", JSON.stringify(favorites));
  }, [favorites]);

  const isFavorite = (id: string) => favorites.some((f) => f.id === id);

  const toggleFavorite = (track: Track) => {
    if (isFavorite(track.id)) {
      setFavorites((prev) => prev.filter((f) => f.id !== track.id));
    } else {
      setFavorites((prev) => [...prev, track]);
    }
  };

  return {
    favorites,
    isFavorite,
    toggleFavorite,
  };
}
