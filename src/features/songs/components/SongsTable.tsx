import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Button } from "@/components/ui/button";
import { Heart, Music2, Play } from "lucide-react";
import { convertFileSrc } from "@tauri-apps/api/core";
import { getFormattedDuration } from "@/lib/helpers";
import { getOptimizedThumbnail } from "@/utils/thumbnail";
import useToggleFavoriteMutation from "@/features/songs/api/useToggleFavoriteMutation";
import ActionsDropdown from "@/features/songs/components/ActionsDropdown";
import useCurrentSong from "@/hooks/useCurrentSong";
import AddToPlaylistDialog from "@/features/playlists/components/AddToPlaylistDialog";

type SongsTableProps = {
  songs: Song[];
  handleSongClick: (song: Song) => void;
  renderActions?: (song: Song) => React.ReactNode;
};

const SongsTable = ({
  songs,
  handleSongClick,
  renderActions,
}: SongsTableProps) => {
  const currentSong = useCurrentSong();
  const { mutate } = useToggleFavoriteMutation();

  const toggleFavorite = (song: Song) => {
    mutate({ songId: song.id, isFavorite: !song.is_favorite, song });
  };

  return (
    <div>
      {/* Desktop Table View (>= md) */}
      <div className="hidden md:block">
        <Table className="table-fixed">
          <TableHeader>
            <TableRow className="hover:bg-transparent">
              <TableHead className="w-[5%] xl:w-[3%] text-center">#</TableHead>
              <TableHead className="w-[30%] xl:w-[32%]">Title</TableHead>
              <TableHead className="w-[15%]">Artist</TableHead>
              <TableHead className="w-[20%]">Album</TableHead>
              <TableHead className="w-[10%] text-center">Duration</TableHead>
              <TableHead className="w-[10%] text-center"> </TableHead>
              <TableHead className="w-[10%] text-center">Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody className="text-xs">
            {songs.map((song, index) => {
              const isActive = currentSong?.id === song.id;

              return (
                <TableRow
                  key={song.id}
                  className={`group ${isActive ? "text-primary" : "hover:bg-primary/10"}`}
                >
                  <TableCell
                    className="w-10 text-center cursor-pointer"
                    onClick={() => handleSongClick(song)}
                  >
                    <div className="flex items-center justify-center">
                      {isActive ? (
                        <Music2 size={14} />
                      ) : (
                        <>
                          <span className="group-hover:hidden">{index + 1}</span>
                          <Play size={14} className="hidden group-hover:block" />
                        </>
                      )}
                    </div>
                  </TableCell>
                  <TableCell
                    onClick={() => handleSongClick(song)}
                    className="truncate cursor-pointer"
                  >
                    {song.title}
                  </TableCell>
                  <TableCell className="truncate">{song.artist_name}</TableCell>
                  <TableCell className="truncate">{song.album_name}</TableCell>
                  <TableCell className="text-center">
                    {getFormattedDuration(song.duration)}
                  </TableCell>
                  <TableCell className="text-center">
                    <Button
                      variant="ghost"
                      size="icon"
                      className="size-4 hover:text-primary"
                      onClick={(e) => {
                        e.stopPropagation();
                        toggleFavorite(song);
                      }}
                    >
                      {song.is_favorite ? (
                        <Heart fill="currentColor" size={14} />
                      ) : (
                        <Heart size={14} />
                      )}
                    </Button>
                  </TableCell>
                  <TableCell className="flex justify-center items-center">
                    <ActionsDropdown song={song}>
                      {renderActions ? (
                        renderActions(song)
                      ) : (
                        <AddToPlaylistDialog song={song} />
                      )}
                    </ActionsDropdown>
                  </TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </div>

      {/* Mobile Track List View (< md) */}
      <div className="flex md:hidden flex-col divide-y divide-white/5">
        {songs.map((song) => {
          const isActive = currentSong?.id === song.id;
          const isCoverUrl =
            song.album_cover_path?.startsWith("http://") ||
            song.album_cover_path?.startsWith("https://");
          const rawCoverSrc = song.album_cover_path
            ? isCoverUrl
              ? song.album_cover_path
              : convertFileSrc(song.album_cover_path)
            : null;
          const coverSrc =
            rawCoverSrc && isCoverUrl
              ? getOptimizedThumbnail(rawCoverSrc, "card")
              : rawCoverSrc;

          return (
            <div
              key={song.id}
              onClick={() => handleSongClick(song)}
              className={`flex items-center justify-between gap-3 px-2 py-2 rounded-xl cursor-pointer select-none active:bg-white/10 transition-colors ${
                isActive ? "bg-primary/10 text-primary" : "hover:bg-muted/40"
              }`}
            >
              <div className="flex items-center gap-3 min-w-0 flex-1">
                {/* 40px square artwork */}
                <div className="size-10 rounded-lg bg-linear-to-br from-primary/30 to-primary/10 shrink-0 flex items-center justify-center overflow-hidden border border-white/10 relative shadow-xs">
                  {coverSrc ? (
                    <img
                      src={coverSrc}
                      alt={song.title}
                      className="w-full h-full object-cover"
                    />
                  ) : (
                    <Music2
                      className={`size-4 ${
                        isActive ? "text-primary" : "text-muted-foreground/60"
                      }`}
                    />
                  )}
                  {isActive && (
                    <div className="absolute inset-0 bg-black/40 backdrop-blur-[1px] flex items-center justify-center">
                      <Music2 className="size-4 text-primary animate-pulse" />
                    </div>
                  )}
                </div>

                {/* Track Details */}
                <div className="min-w-0 flex-1 space-y-0.5">
                  <p
                    className={`text-sm font-semibold truncate leading-tight font-heading ${
                      isActive ? "text-primary font-bold" : "text-foreground"
                    }`}
                  >
                    {song.title}
                  </p>
                  <p className="text-xs text-muted-foreground truncate font-medium">
                    {song.artist_name || "Unknown Artist"}
                    {song.album_name ? ` • ${song.album_name}` : ""}
                  </p>
                </div>
              </div>

              {/* Action Buttons */}
              <div
                className="flex items-center gap-1 shrink-0"
                onClick={(e) => e.stopPropagation()}
              >
                <span className="text-[11px] font-mono text-muted-foreground mr-1 hidden sm:inline">
                  {getFormattedDuration(song.duration)}
                </span>
                <Button
                  variant="ghost"
                  size="icon"
                  className="size-9 rounded-full text-muted-foreground hover:text-foreground active:scale-90 transition-transform"
                  onClick={() => toggleFavorite(song)}
                  aria-label="Toggle Favorite"
                >
                  {song.is_favorite ? (
                    <Heart className="size-4 text-primary fill-current" />
                  ) : (
                    <Heart className="size-4" />
                  )}
                </Button>

                <ActionsDropdown
                  song={song}
                  className="size-9 rounded-full text-muted-foreground hover:text-foreground active:scale-90 transition-transform"
                >
                  {renderActions ? (
                    renderActions(song)
                  ) : (
                    <AddToPlaylistDialog song={song} />
                  )}
                </ActionsDropdown>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};
export default SongsTable;
