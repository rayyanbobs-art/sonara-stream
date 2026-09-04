import React from "react";
import { X, Radio, Music, Play, Sparkles } from "lucide-react";
import { Track } from "../types";

interface UpNextDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  currentTrack: Track | null;
  upNextTracks: Track[];
  onPlayTrack: (track: Track) => void;
  onPrefetchTrack?: (track: Track) => void;
  isPlaying: boolean;
}

const formatDuration = (secs: number) => {
  if (!secs || isNaN(secs)) return "3:30";
  const m = Math.floor(secs / 60);
  const s = Math.floor(secs % 60);
  return `${m}:${s.toString().padStart(2, "0")}`;
};

export const UpNextDrawer: React.FC<UpNextDrawerProps> = ({
  isOpen,
  onClose,
  currentTrack,
  upNextTracks,
  onPlayTrack,
  onPrefetchTrack,
  isPlaying,
}) => {
  if (!isOpen) return null;

  return (
    <div className="upnext-drawer-backdrop" onClick={onClose}>
      <div className="upnext-drawer-panel" onClick={(e) => e.stopPropagation()}>
        {/* Header */}
        <div className="upnext-drawer-header">
          <div className="upnext-header-title-block">
            <div className="upnext-badge">
              <Radio size={12} className="pulse-anim" />
              <span>UP NEXT</span>
            </div>
            <h3 className="upnext-mix-title">
              {currentTrack ? `Playing from ${currentTrack.title} Mix` : "Genre Radio Mix"}
            </h3>
            <p className="upnext-mix-subtitle">
              Songs matching this genre & vibe
            </p>
          </div>
          <button type="button" className="upnext-close-btn" onClick={onClose} title="Close">
            <X size={18} />
          </button>
        </div>

        {/* Tracks List */}
        <div className="upnext-tracks-scroll">
          {upNextTracks.length === 0 ? (
            <div className="upnext-loading-state">
              <Sparkles size={24} className="pulse-anim" style={{ color: "var(--accent)" }} />
              <p>Curating songs similar to this genre...</p>
            </div>
          ) : (
            <div className="upnext-tracks-list">
              {upNextTracks.map((track, idx) => {
                const isCurrent = currentTrack?.id === track.id || currentTrack?.title.toLowerCase() === track.title.toLowerCase();
                return (
                  <div
                    key={`${track.id}-${idx}`}
                    className={`upnext-track-row ${isCurrent ? "current" : ""}`}
                    onClick={() => onPlayTrack(track)}
                    onMouseEnter={() => onPrefetchTrack?.(track)}
                  >
                    <div className="upnext-row-left">
                      <span className="upnext-row-index">
                        {isCurrent && isPlaying ? (
                          <div className="now-playing-bars mini">
                            <span />
                            <span />
                            <span />
                          </div>
                        ) : (
                          idx + 1
                        )}
                      </span>
                      <img
                        src={track.thumbnail || "https://i.ytimg.com/vi/placeholder/hqdefault.jpg"}
                        alt={track.title}
                        className="upnext-row-thumb"
                      />
                      <div className="upnext-row-meta">
                        <span className="upnext-track-title" title={track.title}>
                          {track.title}
                        </span>
                        <span className="upnext-track-artist">{track.artist}</span>
                      </div>
                    </div>

                    <div className="upnext-row-right">
                      <span className="upnext-track-duration">
                        {formatDuration(track.duration)}
                      </span>
                      <button type="button" className="upnext-play-btn" title="Play Now">
                        <Play size={13} fill="currentColor" />
                      </button>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
