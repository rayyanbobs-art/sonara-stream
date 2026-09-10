import { convertFileSrc } from "@tauri-apps/api/core";
import { Music, Play, Pause } from "lucide-react";
import useAppStore from "@/store/app-store";

type QuickAccessCardProps = {
  song: Song;
  songs: Song[];
  onClick?: () => void;
};

export const QuickAccessCard = ({ song, songs }: QuickAccessCardProps) => {
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
      className={`group relative flex items-center gap-3 rounded-lg overflow-hidden cursor-pointer transition-all duration-200 border ${
        isCurrent
          ? "bg-primary/15 border-primary/40 shadow-sm shadow-primary/10"
          : "bg-white/5 hover:bg-white/10 border-white/5 hover:border-white/10"
      }`}
    >
      {/* Artwork */}
      <div className="relative size-12 sm:size-14 shrink-0 bg-white/5 flex items-center justify-center overflow-hidden">
        {coverSrc ? (
          <img
            src={coverSrc}
            alt={song.title}
            className="w-full h-full object-cover"
            loading="lazy"
          />
        ) : (
          <Music className="size-5 text-primary/60" />
        )}
      </div>

      {/* Song Info */}
      <div className="flex-1 min-w-0 pr-2">
        <p
          className={`text-xs sm:text-sm font-semibold truncate ${
            isCurrent ? "text-primary" : "text-foreground"
          }`}
        >
          {song.title}
        </p>
        <p className="text-[11px] text-muted-foreground truncate">
          {song.artist_name || "Unknown Artist"}
        </p>
      </div>

      {/* Play/Pause Button (Appears on hover on desktop, always accessible) */}
      <div className="pr-3 shrink-0 opacity-0 group-hover:opacity-100 sm:transition-opacity">
        <button
          onClick={handlePlayClick}
          aria-label={isCurrent && isPlaying ? "Pause" : "Play"}
          className="size-8 sm:size-9 rounded-full bg-primary text-primary-foreground flex items-center justify-center shadow-lg shadow-primary/30 transition-transform active:scale-95 hover:scale-105"
        >
          {isCurrent && isPlaying ? (
            <Pause className="size-4 fill-current" />
          ) : (
            <Play className="size-4 fill-current ml-0.5" />
          )}
        </button>
      </div>
    </div>
  );
};

export default QuickAccessCard;
