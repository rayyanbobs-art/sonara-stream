import React, { useState, useEffect, useRef } from "react";
import { ChevronLeft, Search, Youtube, Music2, Loader2, Sparkles } from "lucide-react";
import { invoke } from "@tauri-apps/api/core";

interface HeaderProps {
  query: string;
  onQueryChange: (q: string) => void;
  onSearch: (customQuery?: string) => void;
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
  const [suggestions, setSuggestions] = useState<string[]>([]);
  const [showSuggestions, setShowSuggestions] = useState(false);
  const [selectedIndex, setSelectedIndex] = useState(-1);
  const containerRef = useRef<HTMLDivElement>(null);

  // Fetch search suggestions debounced
  useEffect(() => {
    if (!query.trim() || query.length < 2 || source === "spotify") {
      setSuggestions([]);
      setShowSuggestions(false);
      return;
    }

    const timer = setTimeout(async () => {
      try {
        const results: string[] = await invoke("get_search_suggestions", { query });
        setSuggestions(results);
        setShowSuggestions(results.length > 0);
        setSelectedIndex(-1);
      } catch {
        setSuggestions([]);
      }
    }, 120);

    return () => clearTimeout(timer);
  }, [query, source]);

  // Click outside listener
  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setShowSuggestions(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (selectedIndex >= 0 && suggestions[selectedIndex]) {
      const selected = suggestions[selectedIndex];
      onQueryChange(selected);
      setShowSuggestions(false);
      onSearch(selected);
      return;
    }

    if (query.trim()) {
      setShowSuggestions(false);
      onSearch();
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (!showSuggestions || suggestions.length === 0) return;

    if (e.key === "ArrowDown") {
      e.preventDefault();
      setSelectedIndex((prev) => (prev + 1) % suggestions.length);
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      setSelectedIndex((prev) => (prev - 1 + suggestions.length) % suggestions.length);
    } else if (e.key === "Escape") {
      setShowSuggestions(false);
    }
  };

  return (
    <header className="sonara-header">
      <div className="header-left">
        <button type="button" className="header-nav-btn" onClick={onBack} title="Go Back">
          <ChevronLeft size={18} />
        </button>

        <div className="header-search-container" ref={containerRef}>
          <form onSubmit={handleSubmit} className="header-search-form">
            <Search size={16} className="header-search-icon" />
            <input
              type="text"
              value={query}
              onChange={(e) => onQueryChange(e.target.value)}
              onFocus={() => {
                if (suggestions.length > 0) setShowSuggestions(true);
              }}
              onKeyDown={handleKeyDown}
              placeholder={
                source === "spotify"
                  ? "Search Spotify track or paste link..."
                  : "Search songs, artists, albums..."
              }
              className="header-search-input"
            />
            {loading && <Loader2 size={16} className="spinner header-spinner" />}
          </form>

          {/* Autosuggest Dropdown */}
          {showSuggestions && suggestions.length > 0 && (
            <div className="search-suggestions-dropdown">
              {suggestions.map((suggestion, idx) => (
                <button
                  key={suggestion}
                  type="button"
                  className={`suggestion-item ${idx === selectedIndex ? "active" : ""}`}
                  onMouseDown={(e) => {
                    e.preventDefault();
                    onQueryChange(suggestion);
                    setShowSuggestions(false);
                    onSearch(suggestion);
                  }}
                >
                  <Search size={13} className="suggestion-icon" />
                  <span className="suggestion-text">{suggestion}</span>
                  <Sparkles size={11} className="suggestion-accent-icon" />
                </button>
              ))}
            </div>
          )}
        </div>
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
