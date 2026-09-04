import React from "react";
import { Music, Play, Pause, Heart } from "lucide-react";
import { Track } from "../types";

interface FavoritesViewProps {
  favorites: Track[];
  currentTrack: Track | null;
  isPlaying: boolean;
  onPlayTrack: (track: Track) => void;
  onPrefetchTrack?: (track: Track) => void;
  onToggleFavorite: (track: Track) => void;
  onBrowse: () => void;
}

const formatDuration = (secs: number): string => {
  if (!secs) return "0:00";
  const m = Math.floor(secs / 60);
  const s = Math.floor(secs % 60);
  return `${m}:${s.toString().padStart(2, "0")}`;
};

export const FavoritesView: React.FC<FavoritesViewProps> = ({
  favorites,
  currentTrack,
  isPlaying,
  onPlayTrack,
  onPrefetchTrack,
  onToggleFavorite,
  onBrowse,
}) => {
  if (favorites.length === 0) {
    return (
      <div className="sonara-empty-panel">
        <div className="empty-music-icon-wrap">
          <Music size={26} />
        </div>
        <h3 className="empty-panel-title">No Favorite Songs</h3>
        <p className="empty-panel-desc">
          You have no favorite songs yet. Start adding some to your favorites!
        </p>
        <button type="button" className="empty-action-btn" onClick={onBrowse}>
          Browse Songs
        </button>
      </div>
    );
  }

  return (
    <div className="view-container">
      <div className="section-header">
        <h3>Your Favorites ({favorites.length})</h3>
      </div>

      <div className="sonara-table-wrap">
        <table className="sonara-table">
          <thead>
            <tr>
              <th style={{ width: "40px" }}>#</th>
              <th>Title</th>
              <th>Artist</th>
              <th style={{ width: "80px" }}>Duration</th>
              <th style={{ width: "60px" }}>Favorite</th>
            </tr>
          </thead>
          <tbody>
            {favorites.map((track, index) => {
              const isCurrent = currentTrack?.id === track.id;

              return (
                <tr
                  key={track.id}
                  className={`table-row ${isCurrent ? "current" : ""}`}
                  onClick={() => onPlayTrack(track)}
                  onMouseEnter={() => onPrefetchTrack?.(track)}
                >
                  <td className="row-index">
                    <span className="index-num">{index + 1}</span>
                    <button type="button" className="row-hover-play">
                      {isCurrent && isPlaying ? <Pause size={14} /> : <Play size={14} fill="currentColor" />}
                    </button>
                  </td>
                  <td className="row-title-cell">
                    <img src={track.thumbnail} alt={track.title} className="table-thumb" />
                    <span className="table-track-title">{track.title}</span>
                  </td>
                  <td className="row-artist-cell">{track.artist}</td>
                  <td className="row-duration-cell">{formatDuration(track.duration)}</td>
                  <td className="row-fav-cell" onClick={(e) => e.stopPropagation()}>
                    <button
                      type="button"
                      className="table-fav-btn active"
                      onClick={() => onToggleFavorite(track)}
                    >
                      <Heart size={16} fill="currentColor" />
                    </button>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
};
