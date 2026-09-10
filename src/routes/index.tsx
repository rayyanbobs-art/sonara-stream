import { createFileRoute, Link } from "@tanstack/react-router";
import {
  Album,
  BarChart3,
  Flame,
  Heart,
  Music,
  Play,
  Sparkle,
  User,
} from "lucide-react";
import EmptySongAlert from "@/components/custom/EmptySongAlert";
import SongCard from "@/features/home/components/SongCard";
import StatsCard from "@/features/home/components/StatsCard";
import useGetHomeDataQuery from "@/features/home/api/useGetHomeDataQuery";
import useAppStore from "@/store/app-store";
import Loading from "@/components/custom/Loading";

export const Route = createFileRoute("/")({
  component: Index,
});

function Index() {
  const { data, isLoading } = useGetHomeDataQuery();
  const playSong = useAppStore((state) => state.playSong);

  const handlePlaySong = (song: Song, songs: Song[]) => {
    playSong(song, songs);
  };

  if (!data || isLoading) {
    return <Loading />;
  }

  if (data.recently_added_songs.length > 0) {
    return (
      <main className="p-2 pt-18 pb-25 w-full h-screen space-y-6 overflow-y-auto custom-scrollbar">
        {data.recently_played_songs.length > 0 && (
          <section className="space-y-4">
            <div className="flex items-center justify-between">
              <h2 className="text-lg font-semibold flex items-center gap-2">
                <Play size={18} className="text-primary" />
                Continue Listening
              </h2>
              <Link
                to={"/songs"}
                className="text-xs text-muted-foreground underline hover:text-primary"
              >
                See all
              </Link>
            </div>
            {/* Desktop: grid, Mobile: horizontal scroll */}
            <div className="hidden sm:grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
              {data.recently_played_songs.slice(0, 4).map((song) => (
                <SongCard
                  key={song.id}
                  song={song}
                  handleClick={() =>
                    handlePlaySong(song, data.recently_played_songs)
                  }
                />
              ))}
            </div>
            <div className="flex sm:hidden gap-3 overflow-x-auto pb-1 -mx-2 px-2 snap-x snap-mandatory scrollbar-none">
              {data.recently_played_songs.slice(0, 6).map((song) => (
                <div key={song.id} className="min-w-[75vw] snap-start">
                  <SongCard
                    song={song}
                    handleClick={() =>
                      handlePlaySong(song, data.recently_played_songs)
                    }
                  />
                </div>
              ))}
            </div>
          </section>
        )}

        <section className="space-y-4">
          <div className="flex items-center justify-between">
            <h2 className="text-lg font-semibold flex items-center gap-2">
              <BarChart3 size={18} className="text-primary" />
              Browse Library
            </h2>
          </div>
          {/* Desktop: grid */}
          <div className="hidden sm:grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <Link to={"/songs"} className="w-full">
              <StatsCard
                icon={<Music size={20} />}
                label="Songs"
                value={data.stats.total_songs}
              />
            </Link>
            <Link to={"/artists"} className="w-full">
              <StatsCard
                icon={<User size={20} />}
                label="Artists"
                value={data.stats.total_artists}
              />
            </Link>
            <Link to={"/albums"} className="w-full">
              <StatsCard
                icon={<Album size={20} />}
                label="Albums"
                value={data.stats.total_albums}
              />
            </Link>
            <Link to={"/favorites"} className="w-full">
              <StatsCard
                icon={<Heart size={20} />}
                label="Favorites"
                value={data.stats.total_favorites}
              />
            </Link>
          </div>
          {/* Mobile: compact horizontal row */}
          <div className="flex sm:hidden gap-2 overflow-x-auto pb-1 -mx-2 px-2 scrollbar-none">
            <Link to={"/songs"} className="shrink-0">
              <div className="flex items-center gap-2 px-4 py-2.5 rounded-full bg-card border border-border">
                <Music size={16} className="text-primary" />
                <span className="text-sm font-medium">{data.stats.total_songs} Songs</span>
              </div>
            </Link>
            <Link to={"/artists"} className="shrink-0">
              <div className="flex items-center gap-2 px-4 py-2.5 rounded-full bg-card border border-border">
                <User size={16} className="text-primary" />
                <span className="text-sm font-medium">{data.stats.total_artists} Artists</span>
              </div>
            </Link>
            <Link to={"/albums"} className="shrink-0">
              <div className="flex items-center gap-2 px-4 py-2.5 rounded-full bg-card border border-border">
                <Album size={16} className="text-primary" />
                <span className="text-sm font-medium">{data.stats.total_albums} Albums</span>
              </div>
            </Link>
            <Link to={"/favorites"} className="shrink-0">
              <div className="flex items-center gap-2 px-4 py-2.5 rounded-full bg-card border border-border">
                <Heart size={16} className="text-primary" />
                <span className="text-sm font-medium">{data.stats.total_favorites} Favs</span>
              </div>
            </Link>
          </div>
        </section>

        {data.most_played_songs.length > 0 && (
          <section className="space-y-4">
            <div className="flex items-center justify-between">
              <h2 className="text-lg font-semibold flex items-center gap-2">
                <Flame size={18} className="text-primary" />
                Most Played
              </h2>
              <Link
                to={"/songs"}
                className="text-xs text-muted-foreground underline hover:text-primary"
              >
                View All
              </Link>
            </div>
            <div className="hidden sm:grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
              {data.most_played_songs.map((song) => (
                <SongCard
                  key={song.id}
                  song={song}
                  handleClick={() =>
                    handlePlaySong(song, data.most_played_songs)
                  }
                />
              ))}
            </div>
            <div className="flex sm:hidden gap-3 overflow-x-auto pb-1 -mx-2 px-2 snap-x snap-mandatory scrollbar-none">
              {data.most_played_songs.map((song) => (
                <div key={song.id} className="min-w-[75vw] snap-start">
                  <SongCard
                    song={song}
                    handleClick={() =>
                      handlePlaySong(song, data.most_played_songs)
                    }
                  />
                </div>
              ))}
            </div>
          </section>
        )}

        <section className="space-y-4">
          <div className="flex items-center justify-between">
            <h2 className="text-lg font-semibold flex items-center gap-2">
              <Sparkle size={18} className="text-primary" />
              Recently Added
            </h2>
            <Link
              to={"/songs"}
              className="text-xs text-muted-foreground underline hover:text-primary"
            >
              View All
            </Link>
          </div>
          <div className="hidden sm:grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            {data.recently_added_songs.map((song) => (
              <SongCard
                key={song.id}
                song={song}
                handleClick={() =>
                  handlePlaySong(song, data.recently_added_songs)
                }
              />
            ))}
          </div>
          <div className="flex sm:hidden gap-3 overflow-x-auto pb-1 -mx-2 px-2 snap-x snap-mandatory scrollbar-none">
            {data.recently_added_songs.map((song) => (
              <div key={song.id} className="min-w-[75vw] snap-start">
                <SongCard
                  song={song}
                  handleClick={() =>
                    handlePlaySong(song, data.recently_added_songs)
                  }
                />
              </div>
            ))}
          </div>
        </section>
      </main>
    );
  }

  return <EmptySongAlert />;
}
