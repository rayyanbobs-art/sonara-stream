import React from "react";
import { ChevronLeft, Search, Youtube, Music2, Loader2 } from "lucide-react";

interface HeaderProps {
  query: string;
  onQueryChange: (q: string) => void;
  onSearch: () => void;
  source: "youtube" | "spotify";
  onSourceChange: (s: "youtube" | "spotify") => void;
  loading: boolean;
  onBack: () => void;
}

export const Header: React.FC<HeaderProps> = ({
  query,
  onQueryChange,
  onSearch,
  source,
  onSourceChange,
  loading,
  onBack,
}) => {
  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (query.trim()) {
      onSearch();
    }
  };

  return (
    <header className="sonara-header">
      <div className="header-left">
        <button type="button" className="header-nav-btn" onClick={onBack} title="Go Back">
          <ChevronLeft size={18} />
        </button>

        <form onSubmit={handleSubmit} className="header-search-form">
          <Search size={16} className="header-search-icon" />
          <input
            type="text"
            value={query}
            onChange={(e) => onQueryChange(e.target.value)}
            placeholder={
              source === "spotify"
                ? "Search Spotify track or paste link..."
                : "Search songs, artists, albums..."
            }
            className="header-search-input"
          />
          {loading && <Loader2 size={16} className="spinner header-spinner" />}
        </form>
      </div>

      <div className="header-right">
        <div className="header-source-toggle">
          <button
            type="button"
            className={`source-chip yt ${source === "youtube" ? "active" : ""}`}
            onClick={() => onSourceChange("youtube")}
          >
            <Youtube size={14} />
            <span>YouTube</span>
          </button>
          <button
            type="button"
            className={`source-chip sp ${source === "spotify" ? "active" : ""}`}
            onClick={() => onSourceChange("spotify")}
          >
            <Music2 size={14} />
            <span>Spotify</span>
          </button>
        </div>
      </div>
    </header>
  );
};
