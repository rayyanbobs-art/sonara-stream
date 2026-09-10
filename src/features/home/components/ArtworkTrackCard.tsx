import { convertFileSrc } from "@tauri-apps/api/core";
import { Music, Play, Pause } from "lucide-react";
import useAppStore from "@/store/app-store";

type ArtworkTrackCardProps = {
  song: Song;
  songs: Song[];
  isStacked?: boolean;
  badge?: string | number;
  tag?: string;
  tagColor?: string;
};

export const ArtworkTrackCard = ({
  song,
  songs,
  isStacked,
  badge,
  tag,
  tagColor = "bg-primary",
}: ArtworkTrackCardProps) => {
  const currentSong = useAppStore((state) => state.currentSong);
  const isPlaying = useAppStore((state) => state.isPlaying);
  const playSong = useAppStore((state) => state.playSong);
  const setIsPlaying = useAppStore((state) => state.setIsPlaying);

  const isCurrent = currentSong?.id === song.id;

  const handlePlayClick = (e: React.MouseEvent) => {
    e.stopPropagation();
    if (isCurrent) {
      setIsPlaying(!isPlaying);
    } else {
      playSong(song, songs);
    }
  };

  const isCoverUrl =
    song.album_cover_path?.startsWith("http://") ||
    song.album_cover_path?.startsWith("https://");
  const coverSrc = song.album_cover_path
    ? isCoverUrl
      ? song.album_cover_path
      : convertFileSrc(song.album_cover_path)
    : null;

  return (
    <div
      onClick={() => playSong(song, songs)}
      className={`group relative p-2.5 sm:p-3 rounded-2xl cursor-pointer transition-all duration-300 border flex flex-col ${
        isCurrent
          ? "bg-primary/10 border-primary/40 shadow-md shadow-primary/10"
          : "bg-card/40 hover:bg-card/80 border-white/5 hover:border-white/15"
      }`}
    >
      {/* Stacked Deck / Vinyl Sleeves Effect (Figma Spotify Redesign Pattern) */}
      {isStacked && (
        <div className="relative w-full px-2 sm:px-3 mb-[-6px] z-0 flex flex-col items-center">
          <div className="w-[88%] h-1 rounded-t-md bg-white/10 border-t border-white/15" />
          <div className="w-[94%] h-1 rounded-t-md bg-white/15 border-t border-white/20" />
        </div>
      )}

      {/* Artwork Container */}
      <div className="relative w-full aspect-square rounded-xl overflow-hidden bg-white/5 shadow-md flex items-center justify-center z-10">
        {coverSrc ? (
          <img
            src={coverSrc}
            alt={song.title}
            className="w-full h-full object-cover transition-transform duration-500 group-hover:scale-105"
            loading="lazy"
          />
        ) : (
          <Music className="size-10 text-primary/50" />
        )}

        {/* Tag (e.g. Rock Mix, Chill Mix) with vertical accent line */}
        {tag && (
          <div className="absolute bottom-2 left-2 px-2 py-0.5 rounded bg-black/70 backdrop-blur-xs flex items-center gap-1.5 border border-white/10">
            <div className={`w-1 h-3 rounded-full ${tagColor}`} />
            <span className="text-[10px] font-bold text-white tracking-wide uppercase">
              {tag}
            </span>
          </div>
        )}

        {/* Floating Circular Play Button (Figma / Spotify Style) */}
        <div
          className={`absolute bottom-2 right-2 sm:bottom-2.5 sm:right-2.5 transition-all duration-300 ${
            isCurrent
              ? "opacity-100 translate-y-0"
              : "opacity-0 translate-y-2 group-hover:opacity-100 group-hover:translate-y-0"
          }`}
        >
          <button
            onClick={handlePlayClick}
            aria-label={isCurrent && isPlaying ? "Pause" : "Play"}
            className="size-10 sm:size-11 rounded-full bg-primary text-primary-foreground flex items-center justify-center shadow-xl shadow-primary/40 transition-transform active:scale-90 hover:scale-105"
          >
            {isCurrent && isPlaying ? (
              <Pause className="size-5 fill-current" />
            ) : (
              <Play className="size-5 fill-current ml-0.5" />
            )}
          </button>
        </div>
      </div>

      {/* Metadata */}
      <div className="pt-2.5 pb-0.5 space-y-0.5 z-10">
        <div className="flex items-center justify-between gap-1.5">
          <h4
            className={`text-xs sm:text-sm font-bold truncate flex-1 ${
              isCurrent ? "text-primary" : "text-foreground"
            }`}
          >
            {song.title}
          </h4>
          {badge !== undefined && (
            <span className="text-[11px] font-bold text-primary/80 shrink-0 font-mono">
              {badge}
            </span>
          )}
        </div>
        <p className="text-[11px] sm:text-xs text-muted-foreground truncate">
          {song.artist_name || "Unknown Artist"}
        </p>
      </div>
    </div>
  );
};

export default ArtworkTrackCard;
