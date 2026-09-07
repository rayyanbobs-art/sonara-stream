import React from "react";
import {
  Home,
  Music,
  Users,
  Heart,
  Plus,
  Settings,
  Radio,
  ListMusic,
} from "lucide-react";
import { NavTab, Playlist } from "../types";

interface SidebarProps {
  activeTab: NavTab;
  onTabChange: (tab: NavTab) => void;
  playlists?: Playlist[];
  activePlaylistId?: string | null;
  onSelectPlaylist?: (id: string) => void;
  onCreatePlaylist?: () => void;
}

const libraryItems = [
  { id: "home" as NavTab, label: "Home", icon: Home },
  { id: "songs" as NavTab, label: "Songs", icon: Music },
  { id: "artists" as NavTab, label: "Artists", icon: Users },
];

export const Sidebar: React.FC<SidebarProps> = React.memo(({
  activeTab,
  onTabChange,
  playlists = [],
  activePlaylistId,
  onSelectPlaylist,
  onCreatePlaylist,
}) => {
  return (
    <aside className="sonara-sidebar">
      {/* Brand / Logo */}
      <div className="sidebar-brand">
        <div className="brand-logo-icon">
          <Radio size={18} />
        </div>
        <span className="brand-name">Sonara</span>
        <span className="brand-badge">Stream</span>
      </div>

      {/* Library Group */}
      <div className="sidebar-group">
        <div className="sidebar-group-title">Your Library</div>
        <nav className="sidebar-menu">
          {libraryItems.map((item) => {
            const Icon = item.icon;
            const isActive = activeTab === item.id;
            return (
              <button
                key={item.id}
                type="button"
                className={`sidebar-menu-btn ${isActive ? "active" : ""}`}
                onClick={() => onTabChange(item.id)}
              >
                <Icon size={18} />
                <span>{item.label}</span>
              </button>
            );
          })}
        </nav>
      </div>

      {/* Real User Playlists Group */}
      <div className="sidebar-group">
        <div className="sidebar-group-header">
          <span className="sidebar-group-title">Playlists</span>
          {onCreatePlaylist && (
            <button
              type="button"
              className="sidebar-add-playlist-btn"
              onClick={onCreatePlaylist}
              title="Create Playlist"
              aria-label="Create Playlist"
            >
              <Plus size={14} />
            </button>
          )}
        </div>
        <nav className="sidebar-menu playlists">
          {/* Liked Songs auto-playlist backed by favorites */}
          <button
            type="button"
            className={`sidebar-playlist-item ${activeTab === "favorites" ? "active" : ""}`}
            onClick={() => onTabChange("favorites")}
          >
            <Heart size={14} fill={activeTab === "favorites" ? "currentColor" : "none"} className="liked-songs-icon" />
            <span>Liked Songs</span>
          </button>

          {/* User playlists */}
          {playlists.map((playlist) => {
            const isActive = activeTab === "playlist" && activePlaylistId === playlist.id;
            return (
              <button
                key={playlist.id}
                type="button"
                className={`sidebar-playlist-item ${isActive ? "active" : ""}`}
                onClick={() => onSelectPlaylist?.(playlist.id)}
              >
                <ListMusic size={14} />
                <span className="sidebar-playlist-title" title={playlist.name}>
                  {playlist.name}
                </span>
              </button>
            );
          })}
        </nav>
      </div>

      {/* Footer Settings */}
      <div className="sidebar-footer">
        <button
          type="button"
          className={`sidebar-menu-btn ${activeTab === "settings" ? "active" : ""}`}
          onClick={() => onTabChange("settings")}
        >
          <Settings size={18} />
          <span>Settings</span>
        </button>
      </div>
    </aside>
  );
});
