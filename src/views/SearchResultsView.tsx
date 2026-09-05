import React, { useState, useMemo } from "react";
import { Play, Pause, ChevronDown, ChevronUp, Layers } from "lucide-react";
import { Track } from "../types";
import { getOptimizedThumbnail } from "../utils/thumbnail";

interface SearchResultsViewProps {
  query: string;
  rawResults: Track[];
  currentTrack: Track | null;
  isPlaying: boolean;
  onPlayTrack: (track: Track) => void;
  onPrefetchTrack?: (track: Track) => void;
  loading: boolean;
}

const formatDuration = (secs: number): string => {
  if (!secs) return "0:00";
  const m = Math.floor(secs / 60);
  const s = Math.floor(secs % 60);
  return `${m}:${s.toString().padStart(2, "0")}`;
};

interface GroupedSong {
  signature: string;
  primaryTrack: Track;
  allVersions: Track[];
}

export const SearchResultsView: React.FC<SearchResultsViewProps> = React.memo(({
  query,
  rawResults,
  currentTrack,
  isPlaying,
  onPlayTrack,
  onPrefetchTrack,
  loading,
}) => {
  const [expandedSigs, setExpandedSigs] = useState<Set<string>>(new Set());

  const groupedSongs = useMemo(() => {
    const map = new Map<string, GroupedSong>();
    for (const track of rawResults) {
      const sig = track.signature || `${track.artist} - ${track.title}`.toLowerCase();
      if (!map.has(sig)) {
        map.set(sig, {
          signature: sig,
          primaryTrack: track,
          allVersions: [track],
        });
      } else {
        map.get(sig)!.allVersions.push(track);
      }
    }
    return Array.from(map.values());
  }, [rawResults]);

  const toggleExpand = (sig: string, e: React.MouseEvent) => {
    e.stopPropagation();
    setExpandedSigs((prev) => {
      const next = new Set(prev);
      if (next.has(sig)) {
        next.delete(sig);
      } else {
        next.add(sig);
      }
      return next;
    });
  };

  if (loading) {
    return (
      <div className="view-container">
        <div className="section-header">
          <h3>Searching for &ldquo;{query}&rdquo;...</h3>
        </div>
        <div className="sonara-empty-panel">
          <div className="player-buffer-spinner" style={{ width: "32px", height: "32px" }} />
          <p className="empty-panel-desc">Querying audio streams...</p>
        </div>
      </div>
    );
  }

  if (rawResults.length === 0 && query.trim()) {
    return (
      <div className="view-container">
        <div className="section-header">
          <h3>No results found for &ldquo;{query}&rdquo;</h3>
        </div>
        <div className="sonara-empty-panel">
          <p className="empty-panel-desc">
            No matching audio streams found. Try searching for a different song or artist.
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="view-container">
      {/* Section Header with True Deduplicated Count */}
      <div className="section-header">
        <h3>Results for &ldquo;{query}&rdquo;</h3>
        <span className="section-sub">
          {groupedSongs.length} distinct song{groupedSongs.length === 1 ? "" : "s"}
          {rawResults.length > groupedSongs.length &&
            ` (${rawResults.length} total uploads collapsed)`}
        </span>
      </div>

      {/* Grid of Deduplicated Song Cards */}
      <div className="tracks-grid">
        {groupedSongs.map(({ signature, primaryTrack, allVersions }) => {
          const isCurrent = currentTrack?.id === primaryTrack.id;
          const hasAlternateVersions = allVersions.length > 1;
          const isExpanded = expandedSigs.has(signature);

          return (
            <div
              key={primaryTrack.id}
              className={`track-card ${isCurrent ? "current" : ""}`}
              onClick={() => onPlayTrack(primaryTrack)}
              onMouseEnter={() => onPrefetchTrack?.(primaryTrack)}
            >
              <div className="card-thumb-wrap">
                <img
                  src={getOptimizedThumbnail(primaryTrack.thumbnail, "card")}
                  alt={primaryTrack.title}
                  className="card-thumb"
                  loading="lazy"
                  decoding="async"
                />
                <button
                  type="button"
                  className={`card-play-overlay ${isCurrent ? "visible" : ""}`}
                  aria-label={`Play ${primaryTrack.title}`}
                >
                  {isCurrent && isPlaying ? (
                    <Pause size={20} />
                  ) : (
                    <Play size={20} fill="currentColor" />
                  )}
                </button>
                <span className={`card-source-tag ${primaryTrack.source}`}>
                  {primaryTrack.source === "spotify" ? "Spotify" : "YouTube"}
                </span>
              </div>

              <div className="card-info">
                <h4 className="card-title" title={primaryTrack.title}>
                  {primaryTrack.title}
                </h4>
                <p className="card-artist">{primaryTrack.artist}</p>

                <div className="card-meta-row">
                  <span className="card-duration">
                    {formatDuration(primaryTrack.duration)}
                  </span>
                  {isCurrent && isPlaying && (
                    <div className="now-playing-bars">
                      <span /><span /><span />
                    </div>
                  )}
                </div>

                {/* "N more versions" Affordance */}
                {hasAlternateVersions && (
                  <div style={{ marginTop: "6px" }}>
                    <button
                      type="button"
                      className="empty-action-btn"
                      style={{
                        padding: "3px 8px",
                        fontSize: "11px",
                        display: "inline-flex",
                        alignItems: "center",
                        gap: "4px",
                        margin: 0,
                      }}
                      onClick={(e) => toggleExpand(signature, e)}
                    >
                      <Layers size={12} />
                      <span>
                        +{allVersions.length - 1} more version
                        {allVersions.length - 1 > 1 ? "s" : ""}
                      </span>
                      {isExpanded ? <ChevronUp size={12} /> : <ChevronDown size={12} />}
                    </button>

                    {isExpanded && (
                      <div
                        style={{
                          marginTop: "8px",
                          display: "flex",
                          flexDirection: "column",
                          gap: "4px",
                          background: "rgba(0,0,0,0.3)",
                          borderRadius: "8px",
                          padding: "6px",
                        }}
                        onClick={(e) => e.stopPropagation()}
                      >
                        {allVersions.slice(1).map((ver) => {
                          const isVerCurrent = currentTrack?.id === ver.id;
                          return (
                            <div
                              key={ver.id}
                              style={{
                                display: "flex",
                                alignItems: "center",
                                justifyContent: "space-between",
                                padding: "4px 6px",
                                borderRadius: "4px",
                                cursor: "pointer",
                                fontSize: "11px",
                                color: isVerCurrent ? "var(--accent)" : "var(--text-muted)",
                                background: isVerCurrent ? "rgba(255,255,255,0.05)" : "transparent",
                              }}
                              onClick={() => onPlayTrack(ver)}
                            >
                              <span
                                style={{
                                  overflow: "hidden",
                                  textOverflow: "ellipsis",
                                  whiteSpace: "nowrap",
                                  maxWidth: "110px",
                                }}
                                title={ver.title}
                              >
                                {ver.title}
                              </span>
                              <span>{formatDuration(ver.duration)}</span>
                            </div>
                          );
                        })}
                      </div>
                    )}
                  </div>
                )}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
});
