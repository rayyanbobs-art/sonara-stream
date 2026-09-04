import React from "react";
import { Play, Pause, Music, Clock } from "lucide-react";
import { Track } from "../types";

interface TrackListProps {
  tracks: Track[];
  currentTrack: Track | null;
  isPlaying: boolean;
  onSelectTrack: (track: Track) => void;
  onQuickSearch?: (query: string) => void;
}

const formatDuration = (seconds: number): string => {
  if (!seconds || isNaN(seconds)) return "0:00";
  const mins = Math.floor(seconds / 60);
  const secs = Math.floor(seconds % 60);
  return `${mins}:${secs.toString().padStart(2, "0")}`;
};

export const TrackList: React.FC<TrackListProps> = ({
  tracks,
  currentTrack,
  isPlaying,
  onSelectTrack,
  onQuickSearch,
}) => {
  if (tracks.length === 0) {
    return (
      <div className="empty-state">
        <div className="empty-icon-wrap">
          <Music size={28} />
        </div>
        <h3>Discover Music</h3>
        <p>Pick a vibe or search any artist above:</p>
        <div className="quick-tags">
          {["Top Hits", "Lofi Beats", "Synthwave", "Rock Classics", "Pop Hits"].map((tag) => (
            <button
              key={tag}
              type="button"
              className="quick-tag-btn"
              onClick={() => onQuickSearch && onQuickSearch(tag)}
            >
              {tag}
            </button>
          ))}
        </div>
      </div>
    );
  }

  return (
    <div className="track-list">
      {tracks.map((track) => {
        const isCurrent = currentTrack?.id === track.id;

        return (
          <div
            key={track.id}
            className={`track-item ${isCurrent ? "active" : ""}`}
            onClick={() => onSelectTrack(track)}
          >
            <div className="track-thumb-wrap">
              <img src={track.thumbnail} alt={track.title} className="track-thumb" />
              <div className={`thumb-overlay ${isCurrent ? "visible" : ""}`}>
                {isCurrent && isPlaying ? <Pause size={18} /> : <Play size={18} fill="currentColor" />}
              </div>
            </div>

            <div className="track-info">
              <h4 className="track-title" title={track.title}>
                {track.title}
              </h4>
              <p className="track-artist">{track.artist}</p>
            </div>

            <div className="track-meta">
              <span className={`source-badge ${track.source}`}>
                {track.source === "spotify" ? "Spotify" : "YouTube"}
              </span>
              <div className="track-duration">
                <Clock size={12} />
                <span>{formatDuration(track.duration)}</span>
              </div>
            </div>
          </div>
        );
      })}
    </div>
  );
};
