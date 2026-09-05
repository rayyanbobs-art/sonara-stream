import React, { useState, useEffect, useRef } from "react";
import { ChevronLeft, Search, Music2, Loader2, Sparkles } from "lucide-react";
import { invoke } from "@tauri-apps/api/core";

const YoutubeIcon: React.FC<{ size?: number; className?: string }> = ({ size = 14, className }) => (
  <svg
    width={size}
    height={size}
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    strokeWidth="2"
    strokeLinecap="round"
    strokeLinejoin="round"
    className={className}
  >
    <path d="M2.5 17a24.12 24.12 0 0 1 0-10 2 2 0 0 1 1.4-1.4 49.56 49.56 0 0 1 16.2 0A2 2 0 0 1 21.5 7a24.12 24.12 0 0 1 0 10 2 2 0 0 1-1.4 1.4 49.55 49.55 0 0 1-16.2 0A2 2 0 0 1 2.5 17" />
    <polygon points="10 15 15 12 10 9" fill="currentColor" />
  </svg>
);

interface HeaderProps {
  query: string;
  onQueryChange: (q: string) => void;
  onSearch: (customQuery?: string) => void;
  source: "youtube" | "spotify";
  onSourceChange: (s: "youtube" | "spotify") => void;
  loading: boolean;
  onBack: () => void;
}

import { perf } from "../utils/perf";

export const Header: React.FC<HeaderProps> = React.memo(({
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
  const keystrokeTimeRef = useRef<number>(0);

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
        if (keystrokeTimeRef.current > 0) {
          perf.recordKeystroke(Math.round(performance.now() - keystrokeTimeRef.current));
        }
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
        <button
          type="button"
          className="header-nav-btn"
          onClick={onBack}
          title="Go Back"
          aria-label="Go Back"
        >
          <ChevronLeft size={18} />
        </button>

        <div className="header-search-container" ref={containerRef}>
          <form onSubmit={handleSubmit} className="header-search-form">
            <Search size={16} className="header-search-icon" />
            <input
              type="text"
              value={query}
              onChange={(e) => {
                keystrokeTimeRef.current = performance.now();
                onQueryChange(e.target.value);
              }}
              onFocus={() => {
                if (suggestions.length > 0) setShowSuggestions(true);
              }}
              onKeyDown={handleKeyDown}
              placeholder={
                source === "spotify"
                  ? "Search Spotify track or paste link..."
                  : "Search songs, artists, albums..."
              }
              aria-label="Search"
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
            aria-pressed={source === "youtube"}
            aria-label="Source: YouTube"
          >
            <YoutubeIcon size={14} />
            <span>YouTube</span>
          </button>
          <button
            type="button"
            className={`source-chip sp ${source === "spotify" ? "active" : ""}`}
            onClick={() => onSourceChange("spotify")}
            aria-pressed={source === "spotify"}
            aria-label="Source: Spotify"
          >
            <Music2 size={14} />
            <span>Spotify</span>
          </button>
        </div>
      </div>
    </header>
  );
});
