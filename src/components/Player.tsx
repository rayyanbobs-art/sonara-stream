import React, { useState } from "react";
import {
  Play,
  Pause,
  SkipBack,
  SkipForward,
  Shuffle,
  Repeat,
  Repeat1,
  Volume2,
  VolumeX,
  Heart,
  Radio,
  ListMusic,
  Maximize2,
} from "lucide-react";
import { Track, RepeatMode } from "../types";
import { getOptimizedThumbnail } from "../utils/thumbnail";

interface PlayerProps {
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
  onToggleQueue?: () => void;
  isQueueOpen?: boolean;
  onToggleFullscreen?: () => void;
}

const formatTime = (secs: number): string => {
  if (!secs || isNaN(secs)) return "0:00";
  const m = Math.floor(secs / 60);
  const s = Math.floor(secs % 60);
  return `${m}:${s.toString().padStart(2, "0")}`;
};

export const Player: React.FC<PlayerProps> = ({
  currentTrack,
  isPlaying,
  isBuffering,
  currentTime,
  duration,
  volume,
  isShuffle,
  isFavorite,
  onTogglePlay,
  onSeek,
  onVolumeChange,
  onNext,
  onPrev,
  repeatMode = "off",
  onToggleRepeat,
  onToggleShuffle,
  onToggleFavorite,
  onToggleQueue,
  isQueueOpen,
  onToggleFullscreen,
}) => {
  const [prevVol, setPrevVol] = useState(volume || 0.85);

  const toggleMute = () => {
    if (volume > 0) {
      setPrevVol(volume);
      onVolumeChange(0);
    } else {
      onVolumeChange(prevVol || 0.85);
    }
  };

  const progressPercent = duration > 0 ? (currentTime / duration) * 100 : 0;

  return (
    <footer className="sonara-player">
      <div className="player-content-grid">
        {/* Left: Track Details */}
        <div className="player-left">
          {currentTrack ? (
            <div className="player-track-meta">
              <img
                src={getOptimizedThumbnail(currentTrack.thumbnail, "list")}
                alt={currentTrack.title}
                className="player-track-art"
                loading="lazy"
                decoding="async"
                onClick={onToggleFullscreen}
                style={{ cursor: onToggleFullscreen ? "pointer" : "default" }}
                title={onToggleFullscreen ? "Expand fullscreen view" : undefined}
              />
              <div className="player-track-labels">
                <div className="player-track-name" title={currentTrack.title}>
                  {currentTrack.title}
                </div>
                <div className="player-track-sub">{currentTrack.artist}</div>
              </div>
              <button
                type="button"
                className={`player-fav-btn ${isFavorite ? "fav" : ""}`}
                onClick={onToggleFavorite}
                title={isFavorite ? "Remove from Favorites" : "Add to Favorites"}
                aria-label={isFavorite ? "Remove from Favorites" : "Add to Favorites"}
                aria-pressed={isFavorite}
              >
                <Heart size={16} fill={isFavorite ? "currentColor" : "none"} />
              </button>
            </div>
          ) : (
            <div className="player-empty-meta">
              <div className="player-art-placeholder" />
              <div>
                <p className="player-empty-title">No song selected</p>
              </div>
            </div>
          )}
        </div>

        {/* Center: Playback Controls & Progress Bar */}
        <div className="player-center">
          <div className="player-button-row">
            <button
              type="button"
              className={`ctrl-icon-btn ${isShuffle ? "active" : ""}`}
              onClick={onToggleShuffle}
              title="Shuffle"
              aria-label="Toggle shuffle"
              aria-pressed={isShuffle}
            >
              <Shuffle size={15} />
            </button>

            <button
              type="button"
              className="ctrl-icon-btn"
              onClick={onPrev}
              disabled={!currentTrack}
              title="Previous"
              aria-label="Previous track"
            >
              <SkipBack size={18} />
            </button>

            <button
              type="button"
              className="player-play-btn"
              onClick={onTogglePlay}
              disabled={!currentTrack}
              title={isPlaying ? "Pause" : "Play"}
              aria-label={isPlaying ? "Pause" : "Play"}
            >
              {isBuffering ? (
                <div className="player-buffer-spinner" />
              ) : isPlaying ? (
                <Pause size={18} />
              ) : (
                <Play size={18} fill="currentColor" />
              )}
            </button>

            <button
              type="button"
              className="ctrl-icon-btn"
              onClick={onNext}
              disabled={!currentTrack}
              title="Next"
              aria-label="Next track"
            >
              <SkipForward size={18} />
            </button>

            <button
              type="button"
              className={`ctrl-icon-btn ${repeatMode && repeatMode !== "off" ? "active" : ""}`}
              onClick={onToggleRepeat}
              title={`Repeat: ${repeatMode === "one" ? "One" : repeatMode === "all" ? "All" : "Off"}`}
              aria-label={`Repeat mode: ${repeatMode === "one" ? "One" : repeatMode === "all" ? "All" : "Off"}`}
              aria-pressed={repeatMode && repeatMode !== "off"}
            >
              {repeatMode === "one" ? <Repeat1 size={15} /> : <Repeat size={15} />}
            </button>
          </div>

          <div className="player-scrubber-row">
            <span className="player-time">{formatTime(currentTime)}</span>
            <div className="slider-track-wrap">
              <input
                type="range"
                min={0}
                max={duration || 100}
                step={0.5}
                value={currentTime}
                onChange={(e) => onSeek(parseFloat(e.target.value))}
                className="player-progress-slider"
                aria-label="Seek track position"
                style={{
                  background: `linear-gradient(to right, var(--accent) ${progressPercent}%, rgba(255,255,255,0.15) ${progressPercent}%)`,
                }}
              />
            </div>
            <span className="player-time">{formatTime(duration)}</span>
          </div>
        </div>

        {/* Right: Streaming Status, Up Next Queue & Volume */}
        <div className="player-right">
          {currentTrack && (
            <div className={`player-stream-chip ${isBuffering ? "buffering" : isPlaying ? "live" : "idle"}`}>
              <Radio size={12} className={isBuffering ? "pulse-anim" : ""} />
              <span>{isBuffering ? "Buffering" : isPlaying ? "Streaming" : "Ready"}</span>
            </div>
          )}

          {currentTrack && onToggleQueue && (
            <button
              type="button"
              className={`ctrl-icon-btn ${isQueueOpen ? "active" : ""}`}
              onClick={onToggleQueue}
              title="Up Next (Genre Radio Mix)"
              aria-label="Up Next queue"
              aria-pressed={isQueueOpen}
            >
              <ListMusic size={18} />
            </button>
          )}

          {currentTrack && onToggleFullscreen && (
            <button
              type="button"
              className="ctrl-icon-btn"
              onClick={onToggleFullscreen}
              title="Fullscreen Now Playing"
              aria-label="Fullscreen Now Playing"
            >
              <Maximize2 size={16} />
            </button>
          )}

          <div className="player-vol-wrapper">
            <button
              type="button"
              className="vol-toggle-btn"
              onClick={toggleMute}
              aria-label={volume === 0 ? "Unmute volume" : "Mute volume"}
            >
              {volume === 0 ? <VolumeX size={17} /> : <Volume2 size={17} />}
            </button>
            <input
              type="range"
              min={0}
              max={1}
              step={0.01}
              value={volume}
              onChange={(e) => onVolumeChange(parseFloat(e.target.value))}
              className="player-vol-slider"
              aria-label="Volume level"
              style={{
                background: `linear-gradient(to right, var(--accent) ${volume * 100}%, rgba(255,255,255,0.15) ${volume * 100}%)`,
              }}
            />
          </div>
        </div>
      </div>
    </footer>
  );
};
