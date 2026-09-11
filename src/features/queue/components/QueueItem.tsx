import { Music, Play, Trash2 } from "lucide-react";
import { getFormattedDuration } from "@/lib/helpers";
import useSongById from "@/features/queue/hooks/useSongById";
import useAppStore from "@/store/app-store";

type QueueItemProps = {
  queueItem: QueueItem;
  isCurrentPlaying?: boolean;
};

const QueueItem = ({ queueItem, isCurrentPlaying }: QueueItemProps) => {
  const song = useSongById(queueItem.songId);
  const setCurrentQueueItem = useAppStore((state) => state.setCurrentQueueItem);
  const removeFromQueue = useAppStore((state) => state.removeFromQueue);

  const handleSelectQueueItem = () => {
    setCurrentQueueItem(queueItem);
  };

  return (
    <div
      className={`group flex items-center gap-3 px-3 py-2 rounded-xl transition-colors
  ${isCurrentPlaying ? "bg-primary/20" : "bg-card/50 hover:bg-muted"}`}
    >
      <div className="relative w-12 h-12 rounded-md bg-linear-to-br from-primary/30 to-primary/10 shrink-0 flex items-center justify-center overflow-hidden">
        <Music className="size-5 text-primary/50" />
        <button
          className="absolute inset-0 flex items-center justify-center bg-black/0 transition-colors group-hover:bg-black/50"
          onClick={handleSelectQueueItem}
        >
          {!isCurrentPlaying && (
            <Play className="size-4 text-white opacity-0 transition-opacity group-hover:opacity-100 fill-white" />
          )}
        </button>
      </div>
      <div className="flex-1 min-w-0 space-y-1">
        <h3
          className={`font-medium text-sm truncate transition-colors
  ${
    isCurrentPlaying
      ? "text-primary"
      : "text-foreground group-hover:text-primary"
  }`}
        >
          {song?.title}
        </h3>
        <p className="text-xs text-muted-foreground truncate">
          {song?.artist_name} - {song?.album_name}
        </p>
      </div>
      <div className="flex items-center gap-2 shrink-0">
        <span className="text-xs text-muted-foreground">
          {song && getFormattedDuration(song.duration)}
        </span>
        <button
          type="button"
          onClick={(e) => {
            e.stopPropagation();
            removeFromQueue(queueItem.id);
          }}
          aria-label="Remove from queue"
          className="size-7 rounded-full flex items-center justify-center text-muted-foreground opacity-60 sm:opacity-0 group-hover:opacity-100 hover:text-red-400 hover:bg-white/10 transition-all focus:opacity-100"
        >
          <Trash2 className="size-3.5" />
        </button>
      </div>
    </div>
  );
};
export default QueueItem;
