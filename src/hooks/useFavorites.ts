import { useState, useEffect, useCallback, useMemo } from "react";
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
    const timer = setTimeout(() => {
      try {
        localStorage.setItem("sonara_favorites", JSON.stringify(favorites));
      } catch (e) {
        console.warn("Failed to persist favorites:", e);
      }
    }, 300);
    return () => clearTimeout(timer);
  }, [favorites]);

  useEffect(() => {
    const handleSync = () => {
      try {
        const saved = localStorage.getItem("sonara_favorites");
        if (saved) {
          setFavorites(JSON.parse(saved));
        }
      } catch {}
    };
    window.addEventListener("sonara_favorites_updated", handleSync);
    window.addEventListener("storage", handleSync);
    return () => {
      window.removeEventListener("sonara_favorites_updated", handleSync);
      window.removeEventListener("storage", handleSync);
    };
  }, []);

  const favoritesSet = useMemo(() => new Set(favorites.map((f) => f.id)), [favorites]);

  const isFavorite = useCallback(
    (id: string) => favoritesSet.has(id),
    [favoritesSet]
  );

  const toggleFavorite = useCallback((track: Track) => {
    setFavorites((prev) => {
      const exists = prev.some((f) => f.id === track.id);
      return exists ? prev.filter((f) => f.id !== track.id) : [...prev, track];
    });
  }, []);

  return {
    favorites,
    isFavorite,
    toggleFavorite,
  };
}
