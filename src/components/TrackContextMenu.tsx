import React, { useState, useEffect, useRef } from "react";
import {
  Plus,
  ListPlus,
  Play,
  ListEnd,
  Heart,
  FolderPlus,
  ChevronRight,
  Check,
} from "lucide-react";
import { Track, Playlist } from "../types";

interface TrackContextMenuProps {
  track: Track;
  playlists: Playlist[];
  isOpen: boolean;
  position: { x: number; y: number };
  onClose: () => void;
  onPlayNext?: (track: Track) => void;
  onAddToQueue?: (track: Track) => void;
  onAddToPlaylist: (playlistId: string, track: Track) => void;
  onCreateAndAddToPlaylist: (name: string, track: Track) => void;
  isFavorite?: boolean;
  onToggleFavorite?: (track: Track) => void;
}

export const TrackContextMenu: React.FC<TrackContextMenuProps> = ({
  track,
  playlists,
  isOpen,
  position,
  onClose,
  onPlayNext,
  onAddToQueue,
  onAddToPlaylist,
  onCreateAndAddToPlaylist,
  isFavorite,
  onToggleFavorite,
}) => {
  const [showPlaylistSubmenu, setShowPlaylistSubmenu] = useState(false);
  const [showNewPlaylistDialog, setShowNewPlaylistDialog] = useState(false);
  const [newPlaylistName, setNewPlaylistName] = useState("");
  const menuRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        onClose();
      }
    };
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        onClose();
      }
    };

    if (isOpen) {
      document.addEventListener("mousedown", handleClickOutside);
      document.addEventListener("keydown", handleKeyDown);
    }
    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [isOpen, onClose]);

  useEffect(() => {
    if (showNewPlaylistDialog) {
      setTimeout(() => inputRef.current?.focus(), 50);
    }
  }, [showNewPlaylistDialog]);

  if (!isOpen) return null;

  // Keep menu within viewport bounds
  const menuX = Math.min(position.x, window.innerWidth - 220);
  const menuY = Math.min(position.y, window.innerHeight - 280);

  const handleCreateSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (newPlaylistName.trim()) {
      onCreateAndAddToPlaylist(newPlaylistName.trim(), track);
      setNewPlaylistName("");
      setShowNewPlaylistDialog(false);
      onClose();
    }
  };

  return (
    <div
      ref={menuRef}
      className="track-context-menu"
      style={{ top: `${menuY}px`, left: `${menuX}px` }}
      onClick={(e) => e.stopPropagation()}
    >
      {showNewPlaylistDialog ? (
        <form className="context-new-playlist-form" onSubmit={handleCreateSubmit}>
          <div className="context-form-title">New Playlist</div>
          <input
            ref={inputRef}
            type="text"
            className="context-form-input"
            placeholder="Playlist name..."
            value={newPlaylistName}
            onChange={(e) => setNewPlaylistName(e.target.value)}
          />
          <div className="context-form-actions">
            <button
              type="button"
              className="context-form-btn cancel"
              onClick={() => setShowNewPlaylistDialog(false)}
            >
              Cancel
            </button>
            <button type="submit" className="context-form-btn submit">
              Create
            </button>
          </div>
        </form>
      ) : (
        <div className="context-menu-list">
          {onPlayNext && (
            <button
              type="button"
              className="context-menu-item"
              onClick={() => {
                onPlayNext(track);
                onClose();
              }}
            >
              <Play size={14} />
              <span>Play Next</span>
            </button>
          )}

          {onAddToQueue && (
            <button
              type="button"
              className="context-menu-item"
              onClick={() => {
                onAddToQueue(track);
                onClose();
              }}
            >
              <ListEnd size={14} />
              <span>Add to Queue</span>
            </button>
          )}

          {onToggleFavorite && (
            <button
              type="button"
              className="context-menu-item"
              onClick={() => {
                onToggleFavorite(track);
                onClose();
              }}
            >
              <Heart size={14} fill={isFavorite ? "currentColor" : "none"} />
              <span>{isFavorite ? "Remove from Favorites" : "Add to Favorites"}</span>
            </button>
          )}

          <div className="context-menu-divider" />

          {/* Add to playlist submenu trigger */}
          <div
            className="context-menu-item with-submenu"
            onMouseEnter={() => setShowPlaylistSubmenu(true)}
            onMouseLeave={() => setShowPlaylistSubmenu(false)}
            onClick={() => setShowPlaylistSubmenu((prev) => !prev)}
          >
            <div className="menu-item-left">
              <ListPlus size={14} />
              <span>Add to Playlist</span>
            </div>
            <ChevronRight size={14} className="submenu-arrow" />

            {/* Submenu */}
            {showPlaylistSubmenu && (
              <div className="context-submenu">
                <button
                  type="button"
                  className="context-submenu-item new-playlist"
                  onClick={(e) => {
                    e.stopPropagation();
                    setShowNewPlaylistDialog(true);
                  }}
                >
                  <Plus size={14} />
                  <span>New playlist</span>
                </button>

                {playlists.length > 0 && <div className="context-menu-divider" />}

                <div className="context-submenu-scroll">
                  {playlists.map((playlist) => {
                    const hasTrack = playlist.tracks.some((t) => t.id === track.id);
                    return (
                      <button
                        key={playlist.id}
                        type="button"
                        className="context-submenu-item"
                        onClick={(e) => {
                          e.stopPropagation();
                          onAddToPlaylist(playlist.id, track);
                          onClose();
                        }}
                      >
                        <span className="playlist-name-text" title={playlist.name}>
                          {playlist.name}
                        </span>
                        {hasTrack && <Check size={13} className="playlist-has-track" />}
                      </button>
                    );
                  })}
                </div>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
};
