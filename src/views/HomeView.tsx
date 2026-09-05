import React from "react";
import { Play, Pause, Headphones } from "lucide-react";
import { Track } from "../types";

interface HomeViewProps {
  tracks: Track[];
  title?: string;
  lastPlayedTrack?: Track | null;
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
  title = "Made from your listening",
  lastPlayedTrack,
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

  const featured = currentTrack || lastPlayedTrack;

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

      {/* Continue Listening Banner (Replaces generic banner when a track has been played) */}
      {featured && (
        <div
          className="home-hero-card"
          style={{ cursor: "pointer" }}
          onClick={() => onPlayTrack(featured)}
        >
          <div className="hero-content">
            <div className="hero-tag">
              <Headphones size={14} />
              <span>Continue Listening</span>
            </div>
            <h2>{featured.title}</h2>
            <p>{featured.artist}</p>
          </div>
          <button
            type="button"
            className="empty-action-btn"
            style={{
              display: "inline-flex",
              alignItems: "center",
              gap: "8px",
              padding: "10px 20px",
              fontSize: "13px",
            }}
            onClick={(e) => {
              e.stopPropagation();
              onPlayTrack(featured);
            }}
          >
            {isPlaying && currentTrack?.id === featured.id ? (
              <>
                <Pause size={16} />
                <span>Pause</span>
              </>
            ) : (
              <>
                <Play size={16} fill="currentColor" />
                <span>Play Now</span>
              </>
            )}
          </button>
        </div>
      )}

      {/* Section Title */}
      <div className="section-header">
        <h3>{title}</h3>
        <span className="section-sub">{tracks.length} tracks</span>
      </div>

      {loading && tracks.length === 0 ? (
        <div className="sonara-empty-panel">
          <div className="player-buffer-spinner" style={{ width: "32px", height: "32px" }} />
          <p className="empty-panel-desc">Curating your recommendations...</p>
        </div>
      ) : (
        /* Grid of Cards */
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
      )}
    </div>
  );
};
