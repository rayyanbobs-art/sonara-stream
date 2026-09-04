import React, { useState } from "react";
import { Search, Youtube, Music2, Loader2, Link2 } from "lucide-react";

interface SearchBarProps {
  onSearch: (query: string, source: "youtube" | "spotify") => void;
  loading: boolean;
}

export const SearchBar: React.FC<SearchBarProps> = ({ onSearch, loading }) => {
  const [query, setQuery] = useState("");
  const [source, setSource] = useState<"youtube" | "spotify">("youtube");

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (query.trim()) {
      onSearch(query.trim(), source);
    }
  };

  const isLink = query.includes("spotify.com") || query.includes("youtube.com") || query.includes("youtu.be");

  return (
    <div className="search-container">
      <div className="source-toggle">
        <button
          type="button"
          className={`source-btn ${source === "youtube" ? "active yt" : ""}`}
          onClick={() => setSource("youtube")}
        >
          <Youtube size={15} />
          <span>YouTube</span>
        </button>
        <button
          type="button"
          className={`source-btn ${source === "spotify" ? "active sp" : ""}`}
          onClick={() => setSource("spotify")}
        >
          <Music2 size={15} />
          <span>Spotify</span>
        </button>
      </div>

      <form onSubmit={handleSubmit} className="search-form">
        <div className="input-wrapper">
          {isLink ? <Link2 className="search-icon link" size={17} /> : <Search className="search-icon" size={17} />}
          <input
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder={
              source === "spotify"
                ? "Search Spotify track or paste Spotify link..."
                : "Search YouTube or paste URL..."
            }
            autoFocus
          />
          {loading && <Loader2 className="spinner" size={17} />}
        </div>
        <button type="submit" className="search-submit" disabled={loading || !query.trim()}>
          Search
        </button>
      </form>
    </div>
  );
};
