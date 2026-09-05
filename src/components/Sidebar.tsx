import React from "react";
import {
  Home,
  Music,
  User,
  Disc,
  Heart,
  Plus,
  Settings,
  Radio,
} from "lucide-react";
import { NavTab } from "../types";

interface SidebarProps {
  activeTab: NavTab;
  onTabChange: (tab: NavTab) => void;
  onSelectMix?: (mix: string) => void;
  playlistCount?: number;
}

const libraryItems = [
  { id: "home" as NavTab, label: "Home", icon: Home },
  { id: "songs" as NavTab, label: "Songs", icon: Music },
  { id: "favorites" as NavTab, label: "Favorites", icon: Heart },
];

export const Sidebar: React.FC<SidebarProps> = React.memo(({
  activeTab,
  onTabChange,
  onSelectMix,
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

      {/* Curated Mixes Group */}
      <div className="sidebar-group">
        <div className="sidebar-group-header">
          <span className="sidebar-group-title">Curated Mixes</span>
        </div>
        <nav className="sidebar-menu playlists">
          <button
            type="button"
            className="sidebar-playlist-item"
            onClick={() => onSelectMix ? onSelectMix("Trending Hits") : onTabChange("home")}
          >
            🔥 Top Global Hits
          </button>
          <button
            type="button"
            className="sidebar-playlist-item"
            onClick={() => onSelectMix ? onSelectMix("Lofi Beats") : onTabChange("home")}
          >
            🎧 Lofi Study Beats
          </button>
          <button
            type="button"
            className="sidebar-playlist-item"
            onClick={() => onTabChange("favorites")}
          >
            💖 Liked Collection
          </button>
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
