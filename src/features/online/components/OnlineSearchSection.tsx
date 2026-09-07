import { useState, useRef, useEffect, useCallback } from "react";
import { invoke } from "@tauri-apps/api/core";
import { Search, Loader2, Radio, Sparkles, X } from "lucide-react";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import OnlineTrackCard from "./OnlineTrackCard";
import useAppStore from "@/store/app-store";
import useCurrentSong from "@/hooks/useCurrentSong";

const QUICK_CHIPS = [
  "Lofi Beats",
  "Acoustic Pop",
  "Rock Classics",
  "Synthwave",
  "Bollywood Hits",
  "Chillhop",
  "Jazz & Blues",
];

export const OnlineSearchSection = () => {
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<OnlineTrack[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Suggestions state
  const [suggestions, setSuggestions] = useState<string[]>([]);
  const [showSuggestions, setShowSuggestions] = useState(false);
  const [activeSuggestionIdx, setActiveSuggestionIdx] = useState(-1);

  const inputRef = useRef<HTMLInputElement>(null);
  const suggestionsBoxRef = useRef<HTMLDivElement>(null);
  const prefetchSetRef = useRef<Set<string>>(new Set());

  const playOnlineTrack = useAppStore((state) => state.playOnlineTrack);
  const isPlaying = useAppStore((state) => state.isPlaying);
  const currentSong = useCurrentSong();

  // Prefetch audio stream on hover
  const handlePrefetch = useCallback((track: OnlineTrack) => {
    if (prefetchSetRef.current.has(track.id)) return;
    prefetchSetRef.current.add(track.id);
    invoke("get_stream_url", { id: track.id }).catch(() => {});
  }, []);

  // Real-time suggestions fetching
  useEffect(() => {
    const trimmed = query.trim();
    if (!trimmed || trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
      setSuggestions([]);
      setShowSuggestions(false);
      return;
    }

    const timer = setTimeout(() => {
      invoke<string[]>("get_search_suggestions", { query: trimmed })
        .then((items) => {
          setSuggestions(items || []);
          setShowSuggestions((items || []).length > 0);
          setActiveSuggestionIdx(-1);
        })
        .catch(() => {
          setSuggestions([]);
          setShowSuggestions(false);
        });
    }, 120);

    return () => clearTimeout(timer);
  }, [query]);

  // Execute Search
  const performSearch = async (searchQuery: string) => {
    const trimmed = searchQuery.trim();
    if (!trimmed) return;

    setShowSuggestions(false);
    setLoading(true);
    setError(null);

    // Spotify URL detection
    if (trimmed.includes("open.spotify.com/track/")) {
      try {
        const resolvedTrack = await invoke<OnlineTrack>("resolve_spotify_track", {
          url: trimmed,
        });
        if (resolvedTrack) {
          setResults([resolvedTrack]);
          playOnlineTrack(resolvedTrack, [resolvedTrack]);
        }
      } catch (err) {
        setError("Failed to resolve Spotify track. Please verify the URL.");
      } finally {
        setLoading(false);
      }
      return;
    }

    // YouTube Search
    try {
      const items = await invoke<OnlineTrack[]>("search_youtube", { query: trimmed });
      setResults(items || []);
      if (!items || items.length === 0) {
        setError("No online tracks found. Try different search terms.");
      }
    } catch (err) {
      setError(typeof err === "string" ? err : "Failed to search online music.");
    } finally {
      setLoading(false);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === "ArrowDown") {
      e.preventDefault();
      if (suggestions.length > 0) {
        setActiveSuggestionIdx((prev) => (prev + 1) % suggestions.length);
      }
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      if (suggestions.length > 0) {
        setActiveSuggestionIdx((prev) => (prev - 1 + suggestions.length) % suggestions.length);
      }
    } else if (e.key === "Enter") {
      e.preventDefault();
      if (activeSuggestionIdx >= 0 && activeSuggestionIdx < suggestions.length) {
        const selected = suggestions[activeSuggestionIdx];
        setQuery(selected);
        performSearch(selected);
      } else {
        performSearch(query);
      }
    } else if (e.key === "Escape") {
      setShowSuggestions(false);
    }
  };

  return (
    <div className="space-y-6 w-full max-w-4xl mx-auto">
      {/* Search Input Bar */}
      <div className="relative">
        <div className="flex items-center gap-2">
          <div className="relative flex-1">
            <Search className="absolute left-3.5 top-1/2 -translate-y-1/2 size-4 text-muted-foreground" />
            <Input
              ref={inputRef}
              placeholder="Search YouTube music or paste Spotify link..."
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              onKeyDown={handleKeyDown}
              onFocus={() => {
                if (suggestions.length > 0) setShowSuggestions(true);
              }}
              className="pl-10 pr-10 h-11 text-sm rounded-xl border-muted-foreground/30 bg-muted/40 focus-visible:ring-primary"
            />
            {query && (
              <button
                onClick={() => {
                  setQuery("");
                  setSuggestions([]);
                  setShowSuggestions(false);
                  inputRef.current?.focus();
                }}
                className="absolute right-3.5 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
              >
                <X className="size-4" />
              </button>
            )}
          </div>
          <Button
            onClick={() => performSearch(query)}
            disabled={loading || !query.trim()}
            className="h-11 px-5 rounded-xl font-heading font-medium"
          >
            {loading ? <Loader2 className="size-4 animate-spin" /> : "Search"}
          </Button>
        </div>

        {/* Autocomplete Suggestions Dropdown */}
        {showSuggestions && suggestions.length > 0 && (
          <div
            ref={suggestionsBoxRef}
            className="absolute top-full left-0 right-16 mt-1.5 py-1.5 rounded-xl border border-muted-foreground/30 bg-card/95 backdrop-blur-md shadow-xl z-50 max-h-60 overflow-y-auto"
          >
            {suggestions.map((suggestion, idx) => (
              <div
                key={suggestion}
                onMouseDown={() => {
                  setQuery(suggestion);
                  performSearch(suggestion);
                }}
                className={`px-4 py-2 text-sm cursor-pointer flex items-center gap-2.5 transition-colors ${
                  idx === activeSuggestionIdx
                    ? "bg-primary text-primary-foreground font-medium"
                    : "hover:bg-muted text-foreground"
                }`}
              >
                <Search className="size-3.5 opacity-60 shrink-0" />
                <span className="truncate">{suggestion}</span>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Quick Vibe Chips */}
      <div className="flex flex-wrap items-center gap-2">
        <span className="text-xs font-heading font-semibold text-muted-foreground flex items-center gap-1.5 mr-1">
          <Sparkles className="size-3.5 text-primary" />
          Explore:
        </span>
        {QUICK_CHIPS.map((chip) => (
          <button
            key={chip}
            onClick={() => {
              setQuery(chip);
              performSearch(chip);
            }}
            className="px-3 py-1 text-xs rounded-full border border-muted-foreground/30 bg-muted/30 hover:bg-primary/20 hover:border-primary/40 hover:text-primary transition-all cursor-pointer font-heading"
          >
            {chip}
          </button>
        ))}
      </div>

      {/* Error Message */}
      {error && (
        <div className="p-3.5 rounded-xl border border-destructive/40 bg-destructive/10 text-destructive text-sm flex items-center gap-2">
          <span>{error}</span>
        </div>
      )}

      {/* Loading State */}
      {loading && (
        <div className="flex flex-col items-center justify-center py-16 space-y-3">
          <Loader2 className="size-8 animate-spin text-primary" />
          <p className="text-sm font-medium text-muted-foreground">
            Searching YouTube & Spotify streams...
          </p>
        </div>
      )}

      {/* Results List */}
      {!loading && results.length > 0 && (
        <div className="space-y-2">
          <div className="flex items-center justify-between pb-1">
            <h3 className="text-xs font-semibold uppercase tracking-wider text-muted-foreground font-heading">
              Online Results ({results.length})
            </h3>
            <span className="text-xs text-muted-foreground">Click to play instantly</span>
          </div>
          <div className="space-y-1.5">
            {results.map((track) => {
              const isCurrent = currentSong?.online_id === track.id || currentSong?.path.includes(track.id);
              return (
                <OnlineTrackCard
                  key={track.id}
                  track={track}
                  isCurrent={isCurrent}
                  isPlaying={isPlaying && isCurrent}
                  onPlay={() => playOnlineTrack(track, results)}
                  onPrefetch={() => handlePrefetch(track)}
                />
              );
            })}
          </div>
        </div>
      )}

      {/* Empty State before any search */}
      {!loading && results.length === 0 && !error && (
        <div className="text-center py-16 space-y-3">
          <div className="size-16 rounded-full bg-primary/10 flex items-center justify-center mx-auto">
            <Radio className="size-8 text-primary" />
          </div>
          <div className="space-y-1">
            <h4 className="font-heading font-semibold text-base">Instant Online Music</h4>
            <p className="text-xs text-muted-foreground max-w-sm mx-auto">
              Search any song on YouTube or paste a Spotify track link to listen to online music alongside your local files.
            </p>
          </div>
        </div>
      )}
    </div>
  );
};

export default OnlineSearchSection;
