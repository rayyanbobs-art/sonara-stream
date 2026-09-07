import React, { useState, useEffect, useMemo, useRef } from "react";
import { invoke } from "@tauri-apps/api/core";
import {
  X,
  Play,
  Pause,
  SkipBack,
  SkipForward,
  Shuffle,
  Repeat,
  Repeat1,
  Heart,
  Music,
  ListMusic,
  FileText,
  Sparkles,
  Volume2,
  VolumeX,
} from "lucide-react";
import { Track, RepeatMode } from "../types";
import { getDominantColor } from "../utils/dominantColor";
import { getOptimizedThumbnail } from "../utils/thumbnail";
import { LyricsView } from "./LyricsView";

interface NowPlayingModalProps {
  isOpen: boolean;
  onClose: () => void;
  currentTrack: Track | null;
  isPlaying: boolean;
  isBuffering: boolean;
  currentTime: number;
  duration: number;
  volume: number;
  isShuffle: boolean;
  repeatMode?: RepeatMode;
  isFavorite: boolean;
  onTogglePlay: () => void;
  onSeek: (time: number) => void;
  onVolumeChange: (vol: number) => void;
  onNext: () => void;
  onPrev: () => void;
  onToggleShuffle: () => void;
  onToggleRepeat?: () => void;
  onToggleFavorite: () => void;
  upNextTracks: Track[];
  userQueue: Track[];
  onPlayTrack: (track: Track) => void;
  onRemoveFromUserQueue?: (index: number) => void;
  onClearUserQueue?: () => void;
  lyricsSlot?: React.ReactNode;
}

const formatTime = (secs: number): string => {
  if (!secs || isNaN(secs)) return "0:00";
  const m = Math.floor(secs / 60);
  const s = Math.floor(secs % 60);
  return `${m}:${s.toString().padStart(2, "0")}`;
};

export const NowPlayingModal: React.FC<NowPlayingModalProps> = React.memo(({
  isOpen,
  onClose,
  currentTrack,
  isPlaying,
  isBuffering,
  currentTime,
  duration,
  volume,
  isShuffle,
  repeatMode = "off",
  isFavorite,
  onTogglePlay,
  onSeek,
  onVolumeChange,
  onNext,
  onPrev,
  onToggleShuffle,
  onToggleRepeat,
  onToggleFavorite,
  upNextTracks,
  userQueue,
  onPlayTrack,
  onRemoveFromUserQueue,
  lyricsSlot,
}) => {
  const [activeTab, setActiveTab] = useState<"queue" | "lyrics" | "related">("queue");
  const [dominantColor, setDominantColor] = useState<string>("rgba(35, 30, 45, 0.95)");
  const [relatedTracks, setRelatedTracks] = useState<Track[]>([]);
  const [loadingRelated, setLoadingRelated] = useState(false);
  const [isSeeking, setIsSeeking] = useState(false);
  const [seekValue, setSeekValue] = useState(0);

  // Close on Escape key
  useEffect(() => {
    if (!isOpen) return;

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        onClose();
      }
    };

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [isOpen, onClose]);

  // Compute dominant color whenever current track changes
  useEffect(() => {
    if (!currentTrack || !isOpen) return;

    let isMounted = true;
    getDominantColor(currentTrack.id, currentTrack.thumbnail).then((color) => {
      if (isMounted) {
        setDominantColor(color);
      }
    });

    return () => {
      isMounted = false;
    };
  }, [currentTrack, isOpen]);

  // Load related tracks when entering related tab
  useEffect(() => {
    if (!isOpen || !currentTrack || activeTab !== "related") return;

    let isMounted = true;
    setLoadingRelated(true);

    invoke<Track[]>("build_radio", { seedTrack: currentTrack, limit: 15 })
      .then((tracks) => {
        if (isMounted && Array.isArray(tracks)) {
          setRelatedTracks(tracks);
        }
      })
      .catch((err) => {
        console.warn("Failed to load related tracks:", err);
      })
      .finally(() => {
        if (isMounted) {
          setLoadingRelated(false);
        }
      });

    return () => {
      isMounted = false;
    };
  }, [isOpen, currentTrack, activeTab]);

  if (!isOpen || !currentTrack) return null;

  const displayTime = isSeeking ? seekValue : currentTime;
  const progressPercent = duration > 0 ? (displayTime / duration) * 100 : 0;

  return (
    <div
      className="now-playing-fullscreen"
      role="dialog"
      aria-modal="true"
      aria-label={`Now Playing: ${currentTrack.title}`}
      style={{
        background: `radial-gradient(ellipse 90% 70% at 25% 35%, ${dominantColor} 0%, rgba(10, 11, 15, 0.98) 75%)`,
      }}
    >
      {/* Top Bar with Close Button */}
      <div className="np-top-bar">
        <div className="np-top-tag">
          <Music size={14} />
          <span>NOW PLAYING</span>
        </div>
        <button
          type="button"
          className="np-close-btn"
          onClick={onClose}
          aria-label="Close fullscreen player (Esc)"
          title="Close (Esc)"
        >
          <X size={22} />
        </button>
      </div>

      {/* Main Split Layout */}
      <div className="np-content-grid">
        {/* Left / Top: Track presentation and core playback */}
        <div className="np-left-section">
          <div className="np-art-wrapper">
            <img
              src={getOptimizedThumbnail(currentTrack.thumbnail, "full")}
              alt={currentTrack.title}
              className="np-large-art"
            />
          </div>

          <div className="np-info-row">
            <div className="np-meta">
              <h2 className="np-track-title" title={currentTrack.title}>
                {currentTrack.title}
              </h2>
              <p className="np-track-artist">{currentTrack.artist}</p>
            </div>
            <button
              type="button"
              className={`np-fav-btn ${isFavorite ? "active" : ""}`}
              onClick={onToggleFavorite}
              aria-label={isFavorite ? "Remove from Favorites" : "Add to Favorites"}
            >
              <Heart size={22} fill={isFavorite ? "currentColor" : "none"} />
            </button>
          </div>

          {/* Seek Bar */}
          <div className="np-progress-section">
            <div className="np-seek-wrapper">
              <input
                type="range"
                min={0}
                max={duration || 100}
                step={0.1}
                value={displayTime}
                onChange={(e) => {
                  setIsSeeking(true);
                  setSeekValue(parseFloat(e.target.value));
                }}
                onMouseUp={() => {
                  setIsSeeking(false);
                  onSeek(seekValue);
                }}
                onTouchEnd={() => {
                  setIsSeeking(false);
                  onSeek(seekValue);
                }}
                className="np-progress-slider"
                aria-label="Seek track position"
                style={{
                  background: `linear-gradient(to right, var(--accent, #f5b800) ${progressPercent}%, rgba(255,255,255,0.15) ${progressPercent}%)`,
                }}
              />
            </div>
            <div className="np-time-row">
              <span>{formatTime(displayTime)}</span>
              <span>{formatTime(duration)}</span>
            </div>
          </div>

          {/* Primary Controls */}
          <div className="np-controls-row">
            <button
              type="button"
              className={`np-ctrl-btn ${isShuffle ? "active" : ""}`}
              onClick={onToggleShuffle}
              title="Shuffle"
              aria-label="Toggle shuffle"
            >
              <Shuffle size={18} />
            </button>

            <button
              type="button"
              className="np-ctrl-btn"
              onClick={onPrev}
              title="Previous"
              aria-label="Previous track"
            >
              <SkipBack size={24} />
            </button>

            <button
              type="button"
              className="np-play-btn"
              onClick={onTogglePlay}
              title={isPlaying ? "Pause" : "Play"}
              aria-label={isPlaying ? "Pause" : "Play"}
            >
              {isBuffering ? (
                <div className="player-buffer-spinner" />
              ) : isPlaying ? (
                <Pause size={28} />
              ) : (
                <Play size={28} fill="currentColor" />
              )}
            </button>

            <button
              type="button"
              className="np-ctrl-btn"
              onClick={onNext}
              title="Next"
              aria-label="Next track"
            >
              <SkipForward size={24} />
            </button>

            {onToggleRepeat && (
              <button
                type="button"
                className={`np-ctrl-btn ${repeatMode !== "off" ? "active" : ""}`}
                onClick={onToggleRepeat}
                title={`Repeat: ${repeatMode}`}
                aria-label={`Repeat: ${repeatMode}`}
              >
                {repeatMode === "one" ? <Repeat1 size={18} /> : <Repeat size={18} />}
              </button>
            )}
          </div>
        </div>

        {/* Right / Bottom: Tabs for Up Next, Lyrics, Related */}
        <div className="np-right-section">
          <div className="np-tabs-header">
            <button
              type="button"
              className={`np-tab-btn ${activeTab === "queue" ? "active" : ""}`}
              onClick={() => setActiveTab("queue")}
            >
              <ListMusic size={16} />
              <span>Up Next</span>
            </button>
            <button
              type="button"
              className={`np-tab-btn ${activeTab === "lyrics" ? "active" : ""}`}
              onClick={() => setActiveTab("lyrics")}
            >
              <FileText size={16} />
              <span>Lyrics</span>
            </button>
            <button
              type="button"
              className={`np-tab-btn ${activeTab === "related" ? "active" : ""}`}
              onClick={() => setActiveTab("related")}
            >
              <Sparkles size={16} />
              <span>Related</span>
            </button>
          </div>

          <div className="np-tab-body">
            {activeTab === "queue" && (
              <div className="np-queue-list">
                {userQueue.length > 0 && (
                  <div className="np-section-group">
                    <span className="np-section-title">In Queue</span>
                    {userQueue.map((track, idx) => (
                      <div
                        key={`uq-${track.id}-${idx}`}
                        className="np-track-item"
                        onClick={() => onPlayTrack(track)}
                      >
                        <img
                          src={getOptimizedThumbnail(track.thumbnail, "list")}
                          alt={track.title}
                          className="np-item-thumb"
                        />
                        <div className="np-item-text">
                          <span className="np-item-title">{track.title}</span>
                          <span className="np-item-artist">{track.artist}</span>
                        </div>
                        {onRemoveFromUserQueue && (
                          <button
                            type="button"
                            className="np-item-remove"
                            onClick={(e) => {
                              e.stopPropagation();
                              onRemoveFromUserQueue(idx);
                            }}
                            title="Remove from queue"
                          >
                            <X size={14} />
                          </button>
                        )}
                      </div>
                    ))}
                  </div>
                )}

                <div className="np-section-group">
                  <span className="np-section-title">Next Up</span>
                  {upNextTracks.length === 0 ? (
                    <div className="np-empty-state">No upcoming songs</div>
                  ) : (
                    upNextTracks.map((track, idx) => (
                      <div
                        key={`un-${track.id}-${idx}`}
                        className="np-track-item"
                        onClick={() => onPlayTrack(track)}
                      >
                        <img
                          src={getOptimizedThumbnail(track.thumbnail, "list")}
                          alt={track.title}
                          className="np-item-thumb"
                        />
                        <div className="np-item-text">
                          <span className="np-item-title">{track.title}</span>
                          <span className="np-item-artist">{track.artist}</span>
                        </div>
                      </div>
                    ))
                  )}
                </div>
              </div>
            )}

            {activeTab === "lyrics" && (
              <div className="np-lyrics-container">
                {lyricsSlot ? (
                  lyricsSlot
                ) : (
                  <LyricsView
                    currentTrack={currentTrack}
                    currentTime={currentTime}
                    onSeek={onSeek}
                  />
                )}
              </div>
            )}

            {activeTab === "related" && (
              <div className="np-related-list">
                {loadingRelated ? (
                  <div className="np-loading-state">
                    <div className="player-buffer-spinner" />
                    <span>Finding songs you might like...</span>
                  </div>
                ) : relatedTracks.length === 0 ? (
                  <div className="np-empty-state">No recommendations found</div>
                ) : (
                  relatedTracks.map((track) => (
                    <div
                      key={`rel-${track.id}`}
                      className="np-track-item"
                      onClick={() => onPlayTrack(track)}
                    >
                      <img
                        src={getOptimizedThumbnail(track.thumbnail, "list")}
                        alt={track.title}
                        className="np-item-thumb"
                      />
                      <div className="np-item-text">
                        <span className="np-item-title">{track.title}</span>
                        <span className="np-item-artist">{track.artist}</span>
                      </div>
                    </div>
                  ))
                )}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
});
