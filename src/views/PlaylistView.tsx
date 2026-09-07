import React, { useState, useRef } from "react";
import {
  Play,
  Pause,
  Shuffle,
  Trash2,
  Edit2,
  Check,
  X,
  Music,
  GripVertical,
  Clock,
  Heart,
} from "lucide-react";
import { Playlist, Track } from "../types";
import { getOptimizedThumbnail } from "../utils/thumbnail";

interface PlaylistViewProps {
  playlist: Playlist;
  currentTrack: Track | null;
  isPlaying: boolean;
  onPlayTrack: (track: Track) => void;
  onPlayPlaylist: (tracks: Track[], shuffle?: boolean) => void;
  onRenamePlaylist: (id: string, newName: string) => Promise<any>;
  onDeletePlaylist: (id: string) => Promise<any>;
  onRemoveTrack: (playlistId: string, trackId: string) => void;
  onReorderTracks: (playlistId: string, fromIndex: number, toIndex: number) => void;
  onToggleFavorite: (track: Track) => void;
  isFavorite: (id: string) => boolean;
  onOpenContextMenu?: (track: Track, e: React.MouseEvent) => void;
}

const formatDuration = (secs: number): string => {
  if (!secs || isNaN(secs)) return "0:00";
  const m = Math.floor(secs / 60);
  const s = Math.floor(secs % 60);
  return `${m}:${s.toString().padStart(2, "0")}`;
};

const formatTotalDuration = (tracks: Track[]): string => {
  const totalSecs = tracks.reduce((acc, t) => acc + (t.duration || 0), 0);
  const mins = Math.floor(totalSecs / 60);
  const hrs = Math.floor(mins / 60);
  const remMins = mins % 60;
  if (hrs > 0) {
    return `${hrs} hr ${remMins} min`;
  }
  return `${mins} min`;
};

export const PlaylistView: React.FC<PlaylistViewProps> = React.memo(({
  playlist,
  currentTrack,
  isPlaying,
  onPlayTrack,
  onPlayPlaylist,
  onRenamePlaylist,
  onDeletePlaylist,
  onRemoveTrack,
  onReorderTracks,
  onToggleFavorite,
  isFavorite,
  onOpenContextMenu,
}) => {
  const [isEditing, setIsEditing] = useState(false);
  const [editName, setEditName] = useState(playlist.name);
  const [draggedIndex, setDraggedIndex] = useState<number | null>(null);
  const [dragOverIndex, setDragOverIndex] = useState<number | null>(null);

  const inputRef = useRef<HTMLInputElement>(null);

  const handleStartRename = () => {
    setEditName(playlist.name);
    setIsEditing(true);
    setTimeout(() => inputRef.current?.focus(), 50);
  };

  const handleSaveRename = async () => {
    if (editName.trim() && editName.trim() !== playlist.name) {
      await onRenamePlaylist(playlist.id, editName.trim());
    }
    setIsEditing(false);
  };

  const handleCancelRename = () => {
    setEditName(playlist.name);
    setIsEditing(false);
  };

  const handleDelete = () => {
    if (window.confirm(`Are you sure you want to delete the playlist "${playlist.name}"?`)) {
      onDeletePlaylist(playlist.id);
    }
  };

  // Drag & drop handlers
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
      onReorderTracks(playlist.id, draggedIndex, targetIndex);
    }
    setDraggedIndex(null);
    setDragOverIndex(null);
  };

  const coverArt = playlist.tracks.length > 0 ? playlist.tracks[0].thumbnail : null;

  return (
    <div className="view-container playlist-view-container">
      {/* Hero Header */}
      <div className="playlist-hero-header">
        <div className="playlist-hero-art-wrapper">
          {coverArt ? (
            <img
              src={getOptimizedThumbnail(coverArt, "card")}
              alt={playlist.name}
              className="playlist-hero-art"
            />
          ) : (
            <div className="playlist-hero-art-placeholder">
              <Music size={48} />
            </div>
          )}
        </div>

        <div className="playlist-hero-info">
          <span className="playlist-type-tag">PLAYLIST</span>

          {isEditing ? (
            <div className="playlist-rename-row">
              <input
                ref={inputRef}
                type="text"
                className="playlist-rename-input"
                value={editName}
                onChange={(e) => setEditName(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === "Enter") handleSaveRename();
                  if (e.key === "Escape") handleCancelRename();
                }}
              />
              <button
                type="button"
                className="playlist-rename-save-btn"
                onClick={handleSaveRename}
                title="Save"
              >
                <Check size={16} />
              </button>
              <button
                type="button"
                className="playlist-rename-cancel-btn"
                onClick={handleCancelRename}
                title="Cancel"
              >
                <X size={16} />
              </button>
            </div>
          ) : (
            <div className="playlist-title-row">
              <h1 className="playlist-hero-title">{playlist.name}</h1>
              <button
                type="button"
                className="playlist-edit-btn"
                onClick={handleStartRename}
                title="Rename playlist"
                aria-label="Rename playlist"
              >
                <Edit2 size={16} />
              </button>
            </div>
          )}

          <div className="playlist-hero-meta">
            <span>{playlist.tracks.length} {playlist.tracks.length === 1 ? "track" : "tracks"}</span>
            {playlist.tracks.length > 0 && (
              <>
                <span className="meta-dot">&bull;</span>
                <span>{formatTotalDuration(playlist.tracks)}</span>
              </>
            )}
          </div>

          {/* Action buttons */}
          <div className="playlist-action-bar">
            <button
              type="button"
              className="playlist-play-btn"
              onClick={() => onPlayPlaylist(playlist.tracks, false)}
              disabled={playlist.tracks.length === 0}
              title="Play playlist"
            >
              <Play size={18} fill="currentColor" />
              <span>Play</span>
            </button>

            <button
              type="button"
              className="playlist-shuffle-btn"
              onClick={() => onPlayPlaylist(playlist.tracks, true)}
              disabled={playlist.tracks.length === 0}
              title="Shuffle playlist"
            >
              <Shuffle size={16} />
              <span>Shuffle</span>
            </button>

            <button
              type="button"
              className="playlist-delete-btn"
              onClick={handleDelete}
              title="Delete playlist"
              aria-label="Delete playlist"
            >
              <Trash2 size={16} />
            </button>
          </div>
        </div>
      </div>

      {/* Tracks Table */}
      {playlist.tracks.length === 0 ? (
        <div className="sonara-empty-panel">
          <div className="empty-music-icon-wrap">
            <Music size={28} />
          </div>
          <h3 className="empty-panel-title">This playlist is empty</h3>
          <p className="empty-panel-desc">
            Search or browse for tracks and use &ldquo;Add to Playlist&rdquo; to build your collection.
          </p>
        </div>
      ) : (
        <div className="playlist-tracks-table-wrapper">
          <table className="sonara-table playlist-table">
            <thead>
              <tr>
                <th className="th-reorder" style={{ width: "32px" }}></th>
                <th className="th-num" style={{ width: "40px" }}>#</th>
                <th className="th-title">Title</th>
                <th className="th-artist">Artist</th>
                <th className="th-time" style={{ width: "90px" }}>
                  <Clock size={14} />
                </th>
                <th className="th-actions" style={{ width: "80px" }}></th>
              </tr>
            </thead>
            <tbody>
              {playlist.tracks.map((track, idx) => {
                const isCurrent = currentTrack?.id === track.id;
                const isDragging = draggedIndex === idx;
                const isOver = dragOverIndex === idx;

                return (
                  <tr
                    key={`${track.id}-${idx}`}
                    className={`playlist-track-row ${isCurrent ? "active-row" : ""} ${
                      isDragging ? "dragging" : ""
                    } ${isOver ? "drag-over" : ""}`}
                    draggable
                    onDragStart={(e) => handleDragStart(e, idx)}
                    onDragOver={(e) => handleDragOver(e, idx)}
                    onDrop={(e) => handleDrop(e, idx)}
                    onDragEnd={() => {
                      setDraggedIndex(null);
                      setDragOverIndex(null);
                    }}
                    onContextMenu={(e) => {
                      e.preventDefault();
                      onOpenContextMenu?.(track, e);
                    }}
                    onDoubleClick={() => onPlayTrack(track)}
                  >
                    <td className="td-reorder">
                      <span className="playlist-drag-handle" title="Drag to reorder">
                        <GripVertical size={14} />
                      </span>
                    </td>
                    <td className="td-num">
                      {isCurrent && isPlaying ? (
                        <div className="now-playing-bars mini">
                          <span />
                          <span />
                          <span />
                        </div>
                      ) : (
                        <span className="row-number">{idx + 1}</span>
                      )}
                    </td>
                    <td className="td-title" onClick={() => onPlayTrack(track)}>
                      <div className="td-track-main">
                        <img
                          src={getOptimizedThumbnail(track.thumbnail, "list")}
                          alt={track.title}
                          className="table-row-thumb"
                          loading="lazy"
                        />
                        <span className="table-title-text" title={track.title}>
                          {track.title}
                        </span>
                      </div>
                    </td>
                    <td className="td-artist">{track.artist}</td>
                    <td className="td-time">{formatDuration(track.duration)}</td>
                    <td className="td-actions">
                      <div className="row-action-btns">
                        <button
                          type="button"
                          className={`row-fav-btn ${isFavorite(track.id) ? "active" : ""}`}
                          onClick={(e) => {
                            e.stopPropagation();
                            onToggleFavorite(track);
                          }}
                          title={isFavorite(track.id) ? "Favorited" : "Favorite"}
                        >
                          <Heart size={14} fill={isFavorite(track.id) ? "currentColor" : "none"} />
                        </button>
                        <button
                          type="button"
                          className="row-delete-btn"
                          onClick={(e) => {
                            e.stopPropagation();
                            onRemoveTrack(playlist.id, track.id);
                          }}
                          title="Remove from playlist"
                        >
                          <Trash2 size={14} />
                        </button>
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
});
