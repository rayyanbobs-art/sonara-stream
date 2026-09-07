import React, { useState, useMemo } from "react";
import { Users, Music, Clock, ChevronRight, Search, Play } from "lucide-react";
import { Track } from "../types";
import { getOptimizedThumbnail } from "../utils/thumbnail";

interface ArtistsViewProps {
  tracks: Track[];
  onSelectArtist: (artist: string) => void;
  onPlayTrack: (track: Track) => void;
  onBrowse: () => void;
}

interface ArtistGroup {
  name: string;
  tracks: Track[];
  trackCount: number;
  thumbnail: string;
  lastPlayedText?: string;
}

export const ArtistsView: React.FC<ArtistsViewProps> = React.memo(({
  tracks,
  onSelectArtist,
  onPlayTrack,
  onBrowse,
}) => {
  const [searchQuery, setSearchQuery] = useState("");

  const artists = useMemo(() => {
    const map = new Map<string, { name: string; tracks: Track[]; thumbnail: string }>();

    for (const track of tracks) {
      const trimmed = track.artist.trim();
      if (!trimmed || trimmed === "Unknown Artist") continue;
      const key = trimmed.toLowerCase();

      if (!map.has(key)) {
        map.set(key, {
          name: trimmed,
          tracks: [track],
          thumbnail: track.thumbnail,
        });
      } else {
        const entry = map.get(key)!;
        entry.tracks.push(track);
      }
    }

    const groups: ArtistGroup[] = Array.from(map.values()).map((g) => ({
      name: g.name,
      tracks: g.tracks,
      trackCount: g.tracks.length,
      thumbnail: g.thumbnail,
    }));

    // Sort by track count descending
    return groups.sort((a, b) => b.trackCount - a.trackCount);
  }, [tracks]);

  const filteredArtists = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    if (!q) return artists;
    return artists.filter((a) => a.name.toLowerCase().includes(q));
  }, [artists, searchQuery]);

  if (artists.length === 0) {
    return (
      <div className="sonara-empty-panel">
        <div className="empty-music-icon-wrap">
          <Users size={26} />
        </div>
        <h3 className="empty-panel-title">No Artists Found</h3>
        <p className="empty-panel-desc">
          Stream or search music to populate artists in your library.
        </p>
        <button type="button" className="empty-action-btn" onClick={onBrowse}>
          Browse Music
        </button>
      </div>
    );
  }

  return (
    <div className="view-container">
      {/* Header & Search */}
      <div className="artists-view-header">
        <div>
          <h2>Artists ({artists.length})</h2>
          <p className="artists-subtitle">Artists discovered across your library</p>
        </div>

        <div className="artists-search-bar">
          <Search size={16} className="search-icon" />
          <input
            type="text"
            placeholder="Search artists..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="artists-search-input"
          />
        </div>
      </div>

      {/* Grid of Artists */}
      <div className="artists-grid">
        {filteredArtists.map((artist) => (
          <div
            key={artist.name}
            className="artist-card"
            onClick={() => onSelectArtist(artist.name)}
            role="button"
            tabIndex={0}
            onKeyDown={(e) => {
              if (e.key === "Enter" || e.key === " ") {
                e.preventDefault();
                onSelectArtist(artist.name);
              }
            }}
          >
            <div className="artist-avatar-wrapper">
              <img
                src={getOptimizedThumbnail(artist.thumbnail, "card")}
                alt={artist.name}
                className="artist-avatar"
                loading="lazy"
                decoding="async"
              />
              <button
                type="button"
                className="artist-quick-play"
                onClick={(e) => {
                  e.stopPropagation();
                  if (artist.tracks.length > 0) {
                    onPlayTrack(artist.tracks[0]);
                  }
                }}
                title={`Play top song by ${artist.name}`}
                aria-label={`Play top song by ${artist.name}`}
              >
                <Play size={18} fill="currentColor" />
              </button>
            </div>

            <div className="artist-info">
              <h4 className="artist-name" title={artist.name}>
                {artist.name}
              </h4>
              <span className="artist-count">
                {artist.trackCount} {artist.trackCount === 1 ? "track" : "tracks"}
              </span>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
});
