import React from "react";
import { Play, Pause, Flame, Radio } from "lucide-react";
import { Track } from "../types";

interface HomeViewProps {
  tracks: Track[];
  currentTrack: Track | null;
  isPlaying: boolean;
  onPlayTrack: (track: Track) => void;
  onPrefetchTrack?: (track: Track) => void;
  onVibeClick: (genre: string) => void;
  loading: boolean;
}

const formatDuration = (secs: number): string => {
  if (!secs) return "0:00";
  const m = Math.floor(secs / 60);
  const s = Math.floor(secs % 60);
  return `${m}:${s.toString().padStart(2, "0")}`;
};

export const HomeView: React.FC<HomeViewProps> = ({
  tracks,
  currentTrack,
  isPlaying,
  onPlayTrack,
  onPrefetchTrack,
  onVibeClick,
  loading,
}) => {
  const vibes = [
    "Trending Hits",
    "Lofi Beats",
    "Pop Songs 2026",
    "Rock Classics",
    "Electronic & Dance",
    "Synthwave",
  ];

  return (
    <div className="view-container">
      {/* Vibe Chips Bar */}
      <div className="vibe-chips-row">
        {vibes.map((vibe) => (
          <button
            key={vibe}
            type="button"
            className="vibe-chip"
            onClick={() => onVibeClick(vibe)}
          >
            {vibe}
          </button>
        ))}
      </div>

      {/* Hero Banner */}
      <div className="home-hero-card">
        <div className="hero-content">
          <div className="hero-tag">
            <Flame size={14} />
            <span>Featured Streams</span>
          </div>
          <h2>Instant Live Stream</h2>
          <p>
            Zero downloads. Zero local storage used. Click any song to buffer and play in pure memory.
          </p>
        </div>
      </div>

      {/* Section Title */}
      <div className="section-header">
        <h3>Recommended For You</h3>
        <span className="section-sub">{tracks.length} tracks ready</span>
      </div>

      {/* Grid of Cards */}
      <div className="tracks-grid">
        {tracks.map((track) => {
          const isCurrent = currentTrack?.id === track.id;

          return (
            <div
              key={track.id}
              className={`track-card ${isCurrent ? "current" : ""}`}
              onClick={() => onPlayTrack(track)}
              onMouseEnter={() => onPrefetchTrack?.(track)}
            >
              <div className="card-thumb-wrap">
                <img src={track.thumbnail} alt={track.title} className="card-thumb" />
                <button
                  type="button"
                  className={`card-play-overlay ${isCurrent ? "visible" : ""}`}
                >
                  {isCurrent && isPlaying ? (
                    <Pause size={20} />
                  ) : (
                    <Play size={20} fill="currentColor" />
                  )}
                </button>
                <span className={`card-source-tag ${track.source}`}>
                  {track.source === "spotify" ? "Spotify" : "YouTube"}
                </span>
              </div>

              <div className="card-info">
                <h4 className="card-title" title={track.title}>
                  {track.title}
                </h4>
                <p className="card-artist">{track.artist}</p>
                <div className="card-meta-row">
                  <span className="card-duration">{formatDuration(track.duration)}</span>
                  {isCurrent && isPlaying && (
                    <div className="now-playing-bars">
                      <span /><span /><span />
                    </div>
                  )}
                </div>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};
