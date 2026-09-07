import React from "react";
import { Home, Music, Users, ListMusic, Settings } from "lucide-react";
import { NavTab } from "../types";

interface BottomNavBarProps {
  activeTab: NavTab;
  onTabChange: (tab: NavTab) => void;
}

interface NavItem {
  id: NavTab;
  label: string;
  icon: React.FC<{ size?: number; className?: string }>;
}

const navItems: NavItem[] = [
  { id: "home", label: "Home", icon: Home },
  { id: "songs", label: "Songs", icon: Music },
  { id: "artists", label: "Artists", icon: Users },
  { id: "playlist", label: "Playlists", icon: ListMusic },
  { id: "settings", label: "Settings", icon: Settings },
];

export const BottomNavBar: React.FC<BottomNavBarProps> = React.memo(({
  activeTab,
  onTabChange,
}) => {
  return (
    <nav className="sonara-bottom-nav" aria-label="Mobile Navigation">
      {navItems.map((item) => {
        const Icon = item.icon;
        const isActive = activeTab === item.id;
        return (
          <button
            key={item.id}
            type="button"
            className={`bottom-nav-btn ${isActive ? "active" : ""}`}
            onClick={() => onTabChange(item.id)}
            aria-selected={isActive}
            role="tab"
          >
            <div className="bottom-nav-icon-wrap">
              <Icon size={20} />
            </div>
            <span className="bottom-nav-label">{item.label}</span>
          </button>
        );
      })}
    </nav>
  );
});
