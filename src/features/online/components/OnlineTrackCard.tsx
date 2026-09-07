import { Play, Pause, Radio, Download, Loader2, Check } from "lucide-react";
import { getFormattedDuration } from "@/lib/helpers";
import { getOptimizedThumbnail } from "@/utils/thumbnail";
import useDownloadTrack from "@/features/online/hooks/useDownloadTrack";
import { onlineTrackToSong } from "@/lib/onlineTrack";

type OnlineTrackCardProps = {
  track: OnlineTrack;
  isPlaying?: boolean;
  isCurrent?: boolean;
  onPlay: () => void;
  onPrefetch?: () => void;
};

export const OnlineTrackCard = ({
  track,
  isPlaying = false,
  isCurrent = false,
  onPlay,
  onPrefetch,
}: OnlineTrackCardProps) => {
  const downloadMutation = useDownloadTrack();

  return (
    <div
      onMouseEnter={onPrefetch}
      onClick={onPlay}
      className={`group flex items-center justify-between p-2.5 rounded-xl transition-all cursor-pointer border ${
        isCurrent
          ? "bg-primary/15 border-primary/40 shadow-xs"
          : "bg-card/40 hover:bg-muted/80 border-transparent hover:border-muted-foreground/20"
      }`}
    >
      <div className="flex items-center gap-3.5 min-w-0 flex-1">
        {/* Artwork with play overlay */}
        <div className="relative size-12 rounded-lg overflow-hidden shrink-0 bg-muted/40 shadow-xs">
          {track.thumbnail ? (
            <img
              src={getOptimizedThumbnail(track.thumbnail, "card")}
              alt={track.title}
              className="w-full h-full object-cover"
              loading="lazy"
              decoding="async"
            />
          ) : (
            <div className="w-full h-full flex items-center justify-center bg-primary/10">
              <Radio className="size-5 text-primary/60" />
            </div>
          )}
          <div
            className={`absolute inset-0 flex items-center justify-center bg-black/40 transition-opacity ${
              isCurrent ? "opacity-100" : "opacity-0 group-hover:opacity-100"
            }`}
          >
            {isCurrent && isPlaying ? (
              <Pause className="size-5 text-white fill-white" />
            ) : (
              <Play className="size-5 text-white fill-white ml-0.5" />
            )}
          </div>
        </div>

        {/* Title and Artist */}
        <div className="min-w-0 space-y-0.5 flex-1 pr-2">
          <p
            className={`text-sm font-medium truncate ${
              isCurrent ? "text-primary font-semibold" : "text-foreground group-hover:text-primary transition-colors"
            }`}
          >
            {track.title}
          </p>
          <div className="flex items-center gap-2 text-xs text-muted-foreground truncate">
            <span
              className={`px-1.5 py-0.2 rounded text-[10px] font-semibold uppercase tracking-wider ${
                track.source === "spotify"
                  ? "bg-emerald-500/20 text-emerald-400 border border-emerald-500/30"
                  : "bg-red-500/20 text-red-400 border border-red-500/30"
              }`}
            >
              {track.source === "spotify" ? "Spotify" : "YouTube"}
            </span>
            <span className="truncate">{track.artist}</span>
          </div>
        </div>
      </div>

      {/* Duration & Download */}
      <div className="flex items-center gap-2 shrink-0 pl-2">
        <button
          onClick={(e) => {
            e.stopPropagation();
            downloadMutation.mutate({ song: onlineTrackToSong(track) });
          }}
          disabled={downloadMutation.isPending || downloadMutation.isSuccess}
          className="p-1.5 rounded-lg text-muted-foreground hover:text-foreground hover:bg-muted/60 transition-colors disabled:opacity-50"
        >
          {downloadMutation.isPending ? (
            <Loader2 className="size-3.5 animate-spin" />
          ) : downloadMutation.isSuccess ? (
            <Check className="size-3.5 text-emerald-400" />
          ) : (
            <Download className="size-3.5" />
          )}
        </button>
        <span className="text-xs font-heading font-medium text-muted-foreground">
          {getFormattedDuration(track.duration)}
        </span>
      </div>
    </div>
  );
};

export default OnlineTrackCard;
