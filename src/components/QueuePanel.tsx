import React, { useState, useRef, useEffect, useCallback } from "react";
import {
  X,
  Radio,
  GripVertical,
  Trash2,
  ListPlus,
  Play,
  Music,
  ListX,
} from "lucide-react";
import { Track } from "../types";
import { getOptimizedThumbnail } from "../utils/thumbnail";

interface QueuePanelProps {
  isOpen: boolean;
  onClose: () => void;
  currentTrack: Track | null;
  isPlaying: boolean;
  userQueue: Track[];
  upNextTracks: Track[];
  sourceName?: string;
  onPlayTrack: (track: Track) => void;
  onPrefetchTrack?: (track: Track) => void;
  onRemoveFromUserQueue: (index: number) => void;
  onClearUserQueue: () => void;
  onReorderUserQueue: (fromIndex: number, toIndex: number) => void;
  onSaveQueueAsPlaylist?: (tracks: Track[]) => void;
}

const formatDuration = (secs: number) => {
  if (!secs || isNaN(secs)) return "3:30";
  const m = Math.floor(secs / 60);
  const s = Math.floor(secs % 60);
  return `${m}:${s.toString().padStart(2, "0")}`;
};

export const QueuePanel: React.FC<QueuePanelProps> = React.memo(({
  isOpen,
  onClose,
  currentTrack,
  isPlaying,
  userQueue,
  upNextTracks,
  sourceName,
  onPlayTrack,
  onPrefetchTrack,
  onRemoveFromUserQueue,
  onClearUserQueue,
  onReorderUserQueue,
  onSaveQueueAsPlaylist,
}) => {
  const [draggedIndex, setDraggedIndex] = useState<number | null>(null);
  const [dragOverIndex, setDragOverIndex] = useState<number | null>(null);
  const [focusedIndex, setFocusedIndex] = useState<number>(-1);

  const containerRef = useRef<HTMLDivElement>(null);
  const totalItems = userQueue.length + upNextTracks.length;

  // Auto-focus container when opening
  useEffect(() => {
    if (isOpen) {
      setFocusedIndex(-1);
      setTimeout(() => {
        containerRef.current?.focus();
      }, 50);
    }
  }, [isOpen]);

  // Keyboard navigation
  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (e.key === "Escape") {
        e.preventDefault();
        onClose();
        return;
      }

      if (e.key === "ArrowDown") {
        e.preventDefault();
        setFocusedIndex((prev) => (totalItems > 0 ? (prev + 1) % totalItems : -1));
        return;
      }

      if (e.key === "ArrowUp") {
        e.preventDefault();
        setFocusedIndex((prev) =>
          totalItems > 0 ? (prev <= 0 ? totalItems - 1 : prev - 1) : -1
        );
        return;
      }

      if (e.key === "Enter" && focusedIndex >= 0) {
        e.preventDefault();
        if (focusedIndex < userQueue.length) {
          onPlayTrack(userQueue[focusedIndex]);
        } else {
          const upNextIdx = focusedIndex - userQueue.length;
          if (upNextTracks[upNextIdx]) {
            onPlayTrack(upNextTracks[upNextIdx]);
          }
        }
        return;
      }

      if ((e.key === "Delete" || e.key === "Backspace") && focusedIndex >= 0) {
        if (focusedIndex < userQueue.length) {
          e.preventDefault();
          onRemoveFromUserQueue(focusedIndex);
          if (focusedIndex >= userQueue.length - 1) {
            setFocusedIndex(Math.max(-1, userQueue.length - 2));
          }
        }
      }
    },
    [totalItems, focusedIndex, userQueue, upNextTracks, onClose, onPlayTrack, onRemoveFromUserQueue]
  );

  // Drag and drop handlers
  const handleDragStart = (e: React.DragEvent, index: number) => {
    setDraggedIndex(index);
    e.dataTransfer.effectAllowed = "move";
    if (e.dataTransfer.setData) {
      e.dataTransfer.setData("text/plain", index.toString());
    }
  };

  const handleDragOver = (e: React.DragEvent, index: number) => {
    e.preventDefault();
    e.dataTransfer.dropEffect = "move";
    if (dragOverIndex !== index) {
      setDragOverIndex(index);
    }
  };

  const handleDrop = (e: React.DragEvent, targetIndex: number) => {
    e.preventDefault();
    if (draggedIndex !== null && draggedIndex !== targetIndex) {
      onReorderUserQueue(draggedIndex, targetIndex);
    }
    setDraggedIndex(null);
    setDragOverIndex(null);
  };

  const handleDragEnd = () => {
    setDraggedIndex(null);
    setDragOverIndex(null);
  };

  const handleSaveQueue = () => {
    const allTracks: Track[] = [];
    if (currentTrack) allTracks.push(currentTrack);
    allTracks.push(...userQueue);
    allTracks.push(...upNextTracks);
    onSaveQueueAsPlaylist?.(allTracks);
  };

  if (!isOpen) return null;

  return (
    <div className="upnext-drawer-backdrop" onClick={onClose}>
      <div
        ref={containerRef}
        className="upnext-drawer-panel queue-panel"
        tabIndex={0}
        onClick={(e) => e.stopPropagation()}
        onKeyDown={handleKeyDown}
        aria-label="Queue Panel"
      >
        {/* Header */}
        <div className="upnext-drawer-header queue-header">
          <div className="queue-header-left">
            <div className="upnext-badge">
              <Radio size={12} className="pulse-anim" />
              <span>QUEUE</span>
            </div>
            <h3 className="upnext-mix-title">Play Queue</h3>
          </div>

          <div className="queue-header-actions">
            <button
              type="button"
              className="queue-save-btn"
              onClick={handleSaveQueue}
              title="Save queue as playlist"
              aria-label="Save queue as playlist"
            >
              <ListPlus size={14} />
              <span>Save as playlist</span>
            </button>
            <button
              type="button"
              className="upnext-close-btn"
              onClick={onClose}
              title="Close queue"
              aria-label="Close queue"
            >
              <X size={18} />
            </button>
          </div>
        </div>

        {/* Scrollable Queue Content */}
        <div className="upnext-tracks-scroll queue-scroll">
          {/* Section 1: Now Playing */}
          <div className="queue-section">
            <div className="queue-section-header">
              <span className="queue-section-title">Now Playing</span>
            </div>
            {currentTrack ? (
              <div className="queue-now-playing-card">
                <img
                  src={getOptimizedThumbnail(currentTrack.thumbnail, "list")}
                  alt={currentTrack.title}
                  className="queue-now-playing-art"
                />
                <div className="queue-now-playing-meta">
                  <span className="queue-track-title" title={currentTrack.title}>
                    {currentTrack.title}
                  </span>
                  <span className="queue-track-artist">{currentTrack.artist}</span>
                </div>
                <div className="queue-now-playing-status">
                  {isPlaying ? (
                    <div className="now-playing-bars mini">
                      <span />
                      <span />
                      <span />
                    </div>
                  ) : (
                    <span className="queue-paused-badge">Paused</span>
                  )}
                  <span className="queue-track-duration">
                    {formatDuration(currentTrack.duration)}
                  </span>
                </div>
              </div>
            ) : (
              <div className="queue-empty-subtext">No song selected</div>
            )}
          </div>

          {/* Section 2: Next in Queue (User-added) */}
          <div className="queue-section">
            <div className="queue-section-header between">
              <div className="queue-section-title-group">
                <span className="queue-section-title">Next in queue</span>
                {userQueue.length > 0 && (
                  <span className="queue-count-badge">{userQueue.length}</span>
                )}
              </div>
              {userQueue.length > 0 && (
                <button
                  type="button"
                  className="queue-clear-btn"
                  onClick={onClearUserQueue}
                  title="Clear user queue"
                >
                  <ListX size={13} />
                  <span>Clear all</span>
                </button>
              )}
            </div>

            {userQueue.length === 0 ? (
              <div className="queue-empty-box">
                <p>No user-queued songs</p>
                <span>Add songs using the &ldquo;Add to queue&rdquo; option in any track menu.</span>
              </div>
            ) : (
              <div className="queue-list user-queue-list">
                {userQueue.map((track, idx) => {
                  const isFocused = focusedIndex === idx;
                  const isDragging = draggedIndex === idx;
                  const isOver = dragOverIndex === idx;

                  return (
                    <div
                      key={`user-${track.id}-${idx}`}
                      className={`queue-track-row draggable ${isFocused ? "focused" : ""} ${
                        isDragging ? "dragging" : ""
                      } ${isOver ? "drag-over" : ""}`}
                      draggable
                      onDragStart={(e) => handleDragStart(e, idx)}
                      onDragOver={(e) => handleDragOver(e, idx)}
                      onDrop={(e) => handleDrop(e, idx)}
                      onDragEnd={handleDragEnd}
                      onClick={() => onPlayTrack(track)}
                      onMouseEnter={() => onPrefetchTrack?.(track)}
                    >
                      <div className="queue-row-left">
                        <span
                          className="queue-drag-handle"
                          title="Drag to reorder"
                          onClick={(e) => e.stopPropagation()}
                        >
                          <GripVertical size={14} />
                        </span>
                        <img
                          src={getOptimizedThumbnail(track.thumbnail, "list")}
                          alt={track.title}
                          className="upnext-row-thumb"
                          loading="lazy"
                        />
                        <div className="upnext-row-meta">
                          <span className="upnext-track-title" title={track.title}>
                            {track.title}
                          </span>
                          <span className="upnext-track-artist">{track.artist}</span>
                        </div>
                      </div>

                      <div className="queue-row-right">
                        <span className="queue-track-duration">
                          {formatDuration(track.duration)}
                        </span>
                        <button
                          type="button"
                          className="queue-remove-item-btn"
                          onClick={(e) => {
                            e.stopPropagation();
                            onRemoveFromUserQueue(idx);
                          }}
                          title="Remove from queue"
                          aria-label={`Remove ${track.title} from queue`}
                        >
                          <Trash2 size={13} />
                        </button>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>

          {/* Section 3: Next from <source> (Autoplay/Radio) */}
          <div className="queue-section">
            <div className="queue-section-header">
              <div className="queue-section-title-group">
                <span className="queue-section-title">
                  Next from {sourceName || (currentTrack ? `${currentTrack.title} Radio` : "Radio Mix")}
                </span>
                <span className="queue-source-tag">Autoplay</span>
              </div>
            </div>

            {upNextTracks.length === 0 ? (
              <div className="queue-empty-box">
                <p>Generating radio mix...</p>
              </div>
            ) : (
              <div className="queue-list upnext-list">
                {upNextTracks.map((track, idx) => {
                  const globalIdx = userQueue.length + idx;
                  const isFocused = focusedIndex === globalIdx;

                  return (
                    <div
                      key={`radio-${track.id}-${idx}`}
                      className={`queue-track-row ${isFocused ? "focused" : ""}`}
                      onClick={() => onPlayTrack(track)}
                      onMouseEnter={() => onPrefetchTrack?.(track)}
                    >
                      <div className="queue-row-left">
                        <span className="upnext-row-index">{idx + 1}</span>
                        <img
                          src={getOptimizedThumbnail(track.thumbnail, "list")}
                          alt={track.title}
                          className="upnext-row-thumb"
                          loading="lazy"
                        />
                        <div className="upnext-row-meta">
                          <span className="upnext-track-title" title={track.title}>
                            {track.title}
                          </span>
                          <span className="upnext-track-artist">{track.artist}</span>
                        </div>
                      </div>

                      <div className="queue-row-right">
                        <span className="queue-track-duration">
                          {formatDuration(track.duration)}
                        </span>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
});
