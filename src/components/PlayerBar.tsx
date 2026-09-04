import React, { useState } from "react";
import { Play, Pause, SkipBack, SkipForward, Volume2, VolumeX, Radio } from "lucide-react";
import { Track } from "../types";

interface PlayerBarProps {
  currentTrack: Track | null;
  isPlaying: boolean;
  isBuffering: boolean;
  currentTime: number;
  duration: number;
  volume: number;
  onTogglePlay: () => void;
  onSeek: (time: number) => void;
  onVolumeChange: (volume: number) => void;
  onNext: () => void;
  onPrev: () => void;
}

const formatTime = (seconds: number): string => {
  if (!seconds || isNaN(seconds)) return "0:00";
  const mins = Math.floor(seconds / 60);
  const secs = Math.floor(seconds % 60);
  return `${mins}:${secs.toString().padStart(2, "0")}`;
};

export const PlayerBar: React.FC<PlayerBarProps> = ({
  currentTrack,
  isPlaying,
  isBuffering,
  currentTime,
  duration,
  volume,
  onTogglePlay,
  onSeek,
  onVolumeChange,
  onNext,
  onPrev,
}) => {
  const [prevVolume, setPrevVolume] = useState(volume || 0.8);

  const toggleMute = () => {
    if (volume > 0) {
      setPrevVolume(volume);
      onVolumeChange(0);
    } else {
      onVolumeChange(prevVolume || 0.8);
    }
  };

  const progressPercent = duration > 0 ? (currentTime / duration) * 100 : 0;

  return (
    <footer className="player-bar">
      <div className="seek-container">
        <input
          type="range"
          min={0}
          max={duration || 100}
          step={0.5}
          value={currentTime}
          onChange={(e) => onSeek(parseFloat(e.target.value))}
          className="seek-slider"
          style={{
            background: `linear-gradient(to right, #1ed760 ${progressPercent}%, #282a36 ${progressPercent}%)`,
          }}
        />
        <div className="time-display">
          <span>{formatTime(currentTime)}</span>
          <span>{formatTime(duration)}</span>
        </div>
      </div>

      <div className="player-main">
        <div className="player-track-info">
          {currentTrack ? (
            <>
              <img src={currentTrack.thumbnail} alt={currentTrack.title} className="player-thumb" />
              <div className="player-text">
                <div className="player-title" title={currentTrack.title}>
                  {currentTrack.title}
                </div>
                <div className="player-artist">{currentTrack.artist}</div>
              </div>
            </>
          ) : (
            <div className="no-track-text">No track selected</div>
          )}
        </div>

        <div className="player-controls">
          <button type="button" className="ctrl-btn" onClick={onPrev} title="Previous">
            <SkipBack size={18} />
          </button>

          <button
            type="button"
            className="play-btn"
            onClick={onTogglePlay}
            disabled={!currentTrack}
            title={isPlaying ? "Pause" : "Play"}
          >
            {isBuffering ? (
              <div className="mini-buffer-dot" />
            ) : isPlaying ? (
              <Pause size={20} />
            ) : (
              <Play size={20} fill="currentColor" />
            )}
          </button>

          <button type="button" className="ctrl-btn" onClick={onNext} title="Next">
            <SkipForward size={18} />
          </button>
        </div>

        <div className="player-extra">
          {isBuffering && (
            <div className="streaming-badge buffering">
              <Radio size={12} className="pulse-icon" />
              <span>Buffering</span>
            </div>
          )}
          {!isBuffering && isPlaying && (
            <div className="streaming-badge live">
              <span className="live-indicator" />
              <span>Streaming</span>
            </div>
          )}

          <div className="volume-control">
            <button type="button" className="vol-btn" onClick={toggleMute}>
              {volume === 0 ? <VolumeX size={17} /> : <Volume2 size={17} />}
            </button>
            <input
              type="range"
              min={0}
              max={1}
              step={0.01}
              value={volume}
              onChange={(e) => onVolumeChange(parseFloat(e.target.value))}
              className="vol-slider"
              style={{
                background: `linear-gradient(to right, #1ed760 ${volume * 100}%, #282a36 ${volume * 100}%)`,
              }}
            />
          </div>
        </div>
      </div>
    </footer>
  );
};
