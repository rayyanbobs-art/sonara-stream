import React, { useState, useRef, useMemo, useEffect } from "react";
import { invoke } from "@tauri-apps/api/core";
import { Play, Pause, Music, Heart, Search, X, ArrowUpDown, ArrowUp, ArrowDown } from "lucide-react";
import { useVirtualizer } from "@tanstack/react-virtual";
import { Track } from "../types";
import { getOptimizedThumbnail } from "../utils/thumbnail";

interface SongsViewProps {
  tracks: Track[];
  currentTrack: Track | null;
  isPlaying: boolean;
  onPlayTrack: (track: Track) => void;
  onPrefetchTrack?: (track: Track) => void;
  onToggleFavorite: (track: Track) => void;
  isFavorite: (id: string) => boolean;
  onBrowse: () => void;
  onOpenContextMenu?: (track: Track, e: React.MouseEvent) => void;
  filterArtist?: string | null;
  onClearArtistFilter?: () => void;
}

type SortField = "recent" | "title" | "artist" | "plays";
type SortDirection = "asc" | "desc";

const formatDuration = (secs: number): string => {
  if (!secs) return "0:00";
  const m = Math.floor(secs / 60);
  const s = Math.floor(secs % 60);
  return `${m}:${s.toString().padStart(2, "0")}`;
};

export const SongsView: React.FC<SongsViewProps> = React.memo(({
  tracks,
  currentTrack,
  isPlaying,
  onPlayTrack,
  onPrefetchTrack,
  onToggleFavorite,
  isFavorite,
  onBrowse,
  onOpenContextMenu,
  filterArtist,
  onClearArtistFilter,
}) => {
  const [searchQuery, setSearchQuery] = useState("");
  const [sortField, setSortField] = useState<SortField>("recent");
  const [sortDirection, setSortDirection] = useState<SortDirection>("desc");
  const [playCounts, setPlayCounts] = useState<Record<string, number>>({});
  const tableWrapRef = useRef<HTMLDivElement>(null);

  // Fetch listening history to build accurate play counts
  useEffect(() => {
    invoke<any[]>("get_listening_history")
      .then((history) => {
        if (Array.isArray(history)) {
          const counts: Record<string, number> = {};
          for (const entry of history) {
            const id = entry.track?.id || entry.signature;
            if (id) {
              counts[id] = (counts[id] || 0) + 1;
            }
          }
          setPlayCounts(counts);
        }
      })
      .catch(() => {});
  }, [tracks.length]);

  const toggleSort = (field: SortField) => {
    if (sortField === field) {
      setSortDirection((prev) => (prev === "asc" ? "desc" : "asc"));
    } else {
      setSortField(field);
      setSortDirection(field === "recent" || field === "plays" ? "desc" : "asc");
    }
  };

  // Filter and Sort tracks
  const processedTracks = useMemo(() => {
    let list = [...tracks];

    // 1. Filter by artist if navigation arrived from Artists view
    if (filterArtist) {
      list = list.filter(
        (t) => t.artist.toLowerCase() === filterArtist.toLowerCase()
      );
    }

    // 2. Search filter
    const q = searchQuery.trim().toLowerCase();
    if (q) {
      list = list.filter(
        (t) =>
          t.title.toLowerCase().includes(q) || t.artist.toLowerCase().includes(q)
      );
    }

    // 3. Sort
    list.sort((a, b) => {
      let cmp = 0;
      if (sortField === "title") {
        cmp = a.title.localeCompare(b.title);
      } else if (sortField === "artist") {
        cmp = a.artist.localeCompare(b.artist);
      } else if (sortField === "plays") {
        const countA = playCounts[a.id] || 0;
        const countB = playCounts[b.id] || 0;
        cmp = countA - countB;
      } else {
        // "recent" keeps arrival order or id
        return 0;
      }

      return sortDirection === "asc" ? cmp : -cmp;
    });

    return list;
  }, [tracks, filterArtist, searchQuery, sortField, sortDirection, playCounts]);

  const rowVirtualizer = useVirtualizer({
    count: processedTracks.length,
    getScrollElement: () =>
      tableWrapRef.current?.closest(".sonara-content-scroll") as HTMLElement | null,
    estimateSize: () => 56,
    overscan: 5,
    scrollMargin: tableWrapRef.current?.offsetTop ?? 0,
    initialRect: { width: 1000, height: 800 },
  });

  if (tracks.length === 0) {
    return (
      <div className="sonara-empty-panel">
        <div className="empty-music-icon-wrap">
          <Music size={26} />
        </div>
        <h3 className="empty-panel-title">No Songs Available</h3>
        <p className="empty-panel-desc">
          You haven't searched any songs yet. Get started by streaming some music.
        </p>
        <button type="button" className="empty-action-btn" onClick={onBrowse}>
          + Browse Songs
        </button>
      </div>
    );
  }

  const virtualRows = rowVirtualizer.getVirtualItems();
  const isVirtualized = virtualRows.length > 0;
  const margin = rowVirtualizer.options.scrollMargin ?? 0;
  const paddingTop = isVirtualized && virtualRows[0] ? Math.max(0, virtualRows[0].start - margin) : 0;
  const paddingBottom =
    isVirtualized && virtualRows.length > 0
      ? Math.max(0, rowVirtualizer.getTotalSize() - (virtualRows[virtualRows.length - 1].end - margin))
      : 0;

  const rowsToRender = isVirtualized
    ? virtualRows.map((vRow) => ({ track: processedTracks[vRow.index], index: vRow.index, key: vRow.key }))
    : processedTracks.map((track, index) => ({ track, index, key: track.id }));

  return (
    <div className="view-container">
      {/* Header with Search and Filter Pill */}
      <div className="songs-header-toolbar">
        <div className="songs-header-meta">
          <h3>
            All Songs ({processedTracks.length})
          </h3>
          {filterArtist && (
            <div className="songs-filter-pill">
              <span>Artist: {filterArtist}</span>
              {onClearArtistFilter && (
                <button
                  type="button"
                  onClick={onClearArtistFilter}
                  title="Clear artist filter"
                  aria-label="Clear artist filter"
                >
                  <X size={12} />
                </button>
              )}
            </div>
          )}
        </div>

        <div className="songs-search-input-wrap">
          <Search size={15} className="search-icon" />
          <input
            type="text"
            placeholder="Search songs or artists..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="songs-search-field"
          />
        </div>
      </div>

      <div className="sonara-table-wrap" ref={tableWrapRef}>
        <table className="sonara-table">
          <thead>
            <tr>
              <th style={{ width: "40px" }}>#</th>
              <th className="sortable-th" onClick={() => toggleSort("title")}>
                <div className="th-content">
                  <span>Title</span>
                  {sortField === "title" && (
                    sortDirection === "asc" ? <ArrowUp size={13} /> : <ArrowDown size={13} />
                  )}
                </div>
              </th>
              <th className="sortable-th" onClick={() => toggleSort("artist")}>
                <div className="th-content">
                  <span>Artist</span>
                  {sortField === "artist" && (
                    sortDirection === "asc" ? <ArrowUp size={13} /> : <ArrowDown size={13} />
                  )}
                </div>
              </th>
              <th className="sortable-th" style={{ width: "70px" }} onClick={() => toggleSort("plays")}>
                <div className="th-content">
                  <span>Plays</span>
                  {sortField === "plays" && (
                    sortDirection === "asc" ? <ArrowUp size={13} /> : <ArrowDown size={13} />
                  )}
                </div>
              </th>
              <th style={{ width: "80px" }}>Duration</th>
              <th style={{ width: "60px" }}>Favorite</th>
            </tr>
          </thead>
          <tbody>
            {paddingTop > 0 && (
              <tr>
                <td colSpan={6} style={{ height: `${paddingTop}px`, padding: 0, border: "none" }} />
              </tr>
            )}
            {rowsToRender.map(({ track, index, key }) => {
              if (!track) return null;
              const isCurrent = currentTrack?.id === track.id;
              const fav = isFavorite(track.id);
              const plays = playCounts[track.id] || 0;

              return (
                <tr
                  key={key}
                  className={`table-row ${isCurrent ? "current" : ""}`}
                  onClick={() => onPlayTrack(track)}
                  onMouseEnter={() => onPrefetchTrack?.(track)}
                  onContextMenu={(e) => onOpenContextMenu?.(track, e)}
                >
                  <td className="row-index">
                    <span className="index-num">{index + 1}</span>
                    <button type="button" className="row-hover-play">
                      {isCurrent && isPlaying ? <Pause size={14} /> : <Play size={14} fill="currentColor" />}
                    </button>
                  </td>
                  <td className="row-title-cell">
                    <img
                      src={getOptimizedThumbnail(track.thumbnail, "list")}
                      alt={track.title}
                      className="table-thumb"
                      loading="lazy"
                      decoding="async"
                    />
                    <span className="table-track-title">{track.title}</span>
                  </td>
                  <td className="row-artist-cell">{track.artist}</td>
                  <td className="row-plays-cell">{plays}</td>
                  <td className="row-duration-cell">{formatDuration(track.duration)}</td>
                  <td className="row-fav-cell" onClick={(e) => e.stopPropagation()}>
                    <button
                      type="button"
                      className={`table-fav-btn ${fav ? "active" : ""}`}
                      onClick={() => onToggleFavorite(track)}
                    >
                      <Heart size={16} fill={fav ? "currentColor" : "none"} />
                    </button>
                  </td>
                </tr>
              );
            })}
            {paddingBottom > 0 && (
              <tr>
                <td colSpan={6} style={{ height: `${paddingBottom}px`, padding: 0, border: "none" }} />
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
});
