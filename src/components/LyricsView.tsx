import React, { useState, useEffect, useMemo, useRef, useCallback } from "react";
import { invoke } from "@tauri-apps/api/core";
import { FileText, Music, Sparkles, AlertCircle, Type } from "lucide-react";
import { Track } from "../types";

export type LyricsFontSize = "small" | "medium" | "large";

interface LyricsLine {
  time: number; // seconds
  text: string;
}

interface LyricsRecord {
  id?: number;
  plain_lyrics?: string | null;
  synced_lyrics?: string | null;
  instrumental: boolean;
  cached_at_unix: number;
}

interface LyricsViewProps {
  currentTrack: Track | null;
  currentTime: number;
  onSeek: (time: number) => void;
}

const parseLrc = (lrcString: string): LyricsLine[] => {
  const lines: LyricsLine[] = [];
  const rawLines = lrcString.split(/\r?\n/);
  const regex = /\[(\d{2}):(\d{2}(?:\.\d{1,3})?)\](.*)/;

  for (const raw of rawLines) {
    const match = raw.match(regex);
    if (match) {
      const minutes = parseInt(match[1], 10);
      const seconds = parseFloat(match[2]);
      const text = match[3].trim();
      const time = minutes * 60 + seconds;
      lines.push({ time, text });
    }
  }

  return lines.sort((a, b) => a.time - b.time);
};

export const LyricsView: React.FC<LyricsViewProps> = React.memo(({
  currentTrack,
  currentTime,
  onSeek,
}) => {
  const [lyricsData, setLyricsData] = useState<LyricsRecord | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fontSize, setFontSize] = useState<LyricsFontSize>(() => {
    return (localStorage.getItem("sonara_lyrics_fontsize") as LyricsFontSize) || "medium";
  });

  const scrollContainerRef = useRef<HTMLDivElement>(null);
  const activeLineRef = useRef<HTMLDivElement>(null);
  const userScrolledRef = useRef<boolean>(false);
  const scrollTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const handleFontSizeChange = (size: LyricsFontSize) => {
    setFontSize(size);
    localStorage.setItem("sonara_lyrics_fontsize", size);
  };

  // Fetch lyrics when currentTrack changes
  useEffect(() => {
    if (!currentTrack) {
      setLyricsData(null);
      return;
    }

    let isMounted = true;
    setLoading(true);
    setError(null);
    userScrolledRef.current = false;

    invoke<LyricsRecord | null>("get_lyrics", {
      title: currentTrack.title,
      artist: currentTrack.artist,
      duration: currentTrack.duration,
    })
      .then((data) => {
        if (isMounted) {
          setLyricsData(data);
        }
      })
      .catch((err) => {
        if (isMounted) {
          console.warn("Lyrics fetch error:", err);
          setError("Failed to load lyrics");
        }
      })
      .finally(() => {
        if (isMounted) {
          setLoading(false);
        }
      });

    return () => {
      isMounted = false;
    };
  }, [currentTrack]);

  // Parse synced lyrics if available
  const parsedLines = useMemo(() => {
    if (!lyricsData?.synced_lyrics) return [];
    return parseLrc(lyricsData.synced_lyrics);
  }, [lyricsData?.synced_lyrics]);

  // Find active line index for karaoke highlight
  const activeLineIndex = useMemo(() => {
    if (parsedLines.length === 0) return -1;
    let idx = -1;
    for (let i = 0; i < parsedLines.length; i++) {
      if (currentTime >= parsedLines[i].time - 0.2) {
        idx = i;
      } else {
        break;
      }
    }
    return idx;
  }, [parsedLines, currentTime]);

  // Handle user manual scroll pause
  const handleUserScroll = useCallback(() => {
    userScrolledRef.current = true;
    if (scrollTimeoutRef.current) {
      clearTimeout(scrollTimeoutRef.current);
    }
    scrollTimeoutRef.current = setTimeout(() => {
      userScrolledRef.current = false;
    }, 4000);
  }, []);

  // Auto-scroll to active line
  useEffect(() => {
    if (userScrolledRef.current) return;
    if (activeLineRef.current && scrollContainerRef.current) {
      if (typeof activeLineRef.current.scrollIntoView === "function") {
        activeLineRef.current.scrollIntoView({
          behavior: "smooth",
          block: "center",
        });
      }
    }
  }, [activeLineIndex]);

  const fontSizeClass = `lyrics-font-${fontSize}`;

  return (
    <div className="lyrics-view-wrapper">
      {/* Top Controls: Font Size Switcher */}
      <div className="lyrics-toolbar">
        <div className="lyrics-font-selector">
          <Type size={14} className="font-icon" />
          <button
            type="button"
            className={`font-size-btn ${fontSize === "small" ? "active" : ""}`}
            onClick={() => handleFontSizeChange("small")}
            title="Small font size"
          >
            S
          </button>
          <button
            type="button"
            className={`font-size-btn ${fontSize === "medium" ? "active" : ""}`}
            onClick={() => handleFontSizeChange("medium")}
            title="Medium font size"
          >
            M
          </button>
          <button
            type="button"
            className={`font-size-btn ${fontSize === "large" ? "active" : ""}`}
            onClick={() => handleFontSizeChange("large")}
            title="Large font size"
          >
            L
          </button>
        </div>
      </div>

      {/* Lyrics Body */}
      <div
        className={`lyrics-scroll-body ${fontSizeClass}`}
        ref={scrollContainerRef}
        onWheel={handleUserScroll}
        onTouchMove={handleUserScroll}
      >
        {loading ? (
          <div className="lyrics-state-message">
            <div className="player-buffer-spinner" />
            <span>Searching LRCLIB for lyrics...</span>
          </div>
        ) : lyricsData?.instrumental ? (
          <div className="lyrics-state-message">
            <Music size={32} className="state-icon" />
            <h3>Instrumental Track</h3>
            <p>This song is marked as an instrumental with no lyrics.</p>
          </div>
        ) : parsedLines.length > 0 ? (
          /* Synced Karaoke Lines */
          <div className="lyrics-synced-container">
            {parsedLines.map((line, index) => {
              const isActive = index === activeLineIndex;
              const isPast = index < activeLineIndex;

              return (
                <div
                  key={`line-${index}-${line.time}`}
                  ref={isActive ? activeLineRef : undefined}
                  className={`lyrics-line ${isActive ? "active" : ""} ${isPast ? "past" : ""}`}
                  onClick={() => onSeek(line.time)}
                  role="button"
                  tabIndex={0}
                  onKeyDown={(e) => {
                    if (e.key === "Enter" || e.key === " ") {
                      e.preventDefault();
                      onSeek(line.time);
                    }
                  }}
                  title={`Jump to ${Math.floor(line.time / 60)}:${Math.floor(line.time % 60)
                    .toString()
                    .padStart(2, "0")}`}
                >
                  {line.text || "♪"}
                </div>
              );
            })}
          </div>
        ) : lyricsData?.plain_lyrics ? (
          /* Plain Text Fallback */
          <div className="lyrics-plain-container">
            {lyricsData.plain_lyrics.split(/\r?\n/).map((line, idx) => (
              <p key={`plain-${idx}`} className="lyrics-plain-line">
                {line || "\u00A0"}
              </p>
            ))}
          </div>
        ) : (
          /* Honest Empty State */
          <div className="lyrics-state-message">
            <FileText size={36} className="state-icon" />
            <h3>No Lyrics Found</h3>
            <p>We couldn't find synced or plain lyrics for this track on LRCLIB.</p>
          </div>
        )}
      </div>
    </div>
  );
});
