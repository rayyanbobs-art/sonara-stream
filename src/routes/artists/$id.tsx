import { createFileRoute } from "@tanstack/react-router";
import { useVirtualizer } from "@tanstack/react-virtual";
import { useRef } from "react";
import { convertFileSrc } from "@tauri-apps/api/core";
import { Button } from "@/components/ui/button";
import { Play, Shuffle, User } from "lucide-react";
import { getFormattedDuration } from "@/lib/helpers";
import useAppStore from "@/store/app-store";
import useGetSongsByArtistQuery from "@/features/artists/api/useGetSongsByArtistQuery";
import SongsTable from "@/features/songs/components/SongsTable";
import UpdateArtistImageButton from "@/features/artists/components/UpdateArtistImageButton";

export const Route = createFileRoute("/artists/$id")({
  component: RouteComponent,
});

function RouteComponent() {
  const { id } = Route.useParams();
  const { data } = useGetSongsByArtistQuery(parseInt(id));

  const playSong = useAppStore((state) => state.playSong);
  const isShuffle = useAppStore((state) => state.isShuffle);
  const setIsShuffle = useAppStore((state) => state.setIsShuffle);

  const songs = data?.songs;

  const totalDuration =
    songs?.reduce((total, song) => total + song.duration, 0) ?? 0;

  const parentRef = useRef<HTMLDivElement>(null);
  const rowVirtualizer = useVirtualizer({
    count: songs?.length ?? 0,
    getScrollElement: () => parentRef.current,
    estimateSize: () => 48,
    overscan: 8,
    getItemKey: (index) => songs?.[index].id ?? index,
  });

  const handleSongClick = (song: Song) => {
    if (songs) {
      playSong(song, songs);
    }
  };

  const handlePlayAll = () => {
    if (songs) {
      playSong(songs[0], songs);
    }
  };

  const handleShuffle = () => {
    setIsShuffle(!isShuffle);
  };

  if (songs) {
    const virtualRows = rowVirtualizer.getVirtualItems();
    const visibleSongs = virtualRows.map((row) => songs[row.index]);

    return (
      <main
        ref={parentRef}
        className="p-3 sm:p-6 pt-18 pb-36 md:pb-25 w-full h-screen space-y-6 overflow-y-auto custom-scrollbar"
      >
        <div className="flex flex-col sm:flex-row items-center sm:items-start gap-4 sm:gap-6 text-center sm:text-left border-b border-muted-foreground/20 pb-6 mb-4">
          <div className="relative group shrink-0">
            <div className="size-40 sm:size-50 rounded-full overflow-hidden bg-linear-to-br from-primary/30 to-primary/10 flex items-center justify-center shadow-xl border border-white/10">
              {data.artist.image_path ? (
                <img
                  src={convertFileSrc(data.artist.image_path)}
                  alt={data.artist.name}
                  className="w-full h-full object-cover"
                />
              ) : (
                <div className="text-primary/50">
                  <User size={80} />
                </div>
              )}
            </div>
            <UpdateArtistImageButton artistId={data.artist.id} />
          </div>
          <div className="flex flex-col gap-y-1.5 min-w-0">
            <h1 className="text-2xl sm:text-3xl font-bold font-heading tracking-tight">
              {data.artist.name}
            </h1>
            <p className="text-xs text-muted-foreground">
              {songs.length} {songs.length === 1 ? "Song" : "Songs"} •{" "}
              {getFormattedDuration(totalDuration)}
            </p>

            <div className="flex items-center justify-center sm:justify-start gap-3 mt-3">
              <Button onClick={handlePlayAll} className="gap-2 text-xs h-9 sm:h-10 px-4 rounded-xl shadow-md shadow-primary/20">
                <Play size={16} fill="currentColor" />
                Play All
              </Button>
              <Button
                onClick={handleShuffle}
                variant={isShuffle ? "default" : "outline"}
                className="gap-2 text-xs h-9 sm:h-10 px-4 rounded-xl border border-muted-foreground/30"
              >
                <Shuffle size={16} />
                Shuffle
              </Button>
            </div>
          </div>
        </div>

        <div
          style={{
            height: rowVirtualizer.getTotalSize(),
            position: "relative",
          }}
        >
          <div
            style={{
              position: "absolute",
              width: "100%",
              transform: `translateY(${virtualRows[0]?.start ?? 0}px)`,
            }}
          >
            <SongsTable
              songs={visibleSongs}
              handleSongClick={handleSongClick}
            />
          </div>
        </div>
      </main>
    );
  }
}
