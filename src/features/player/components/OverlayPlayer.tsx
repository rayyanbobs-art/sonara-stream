import { useState } from "react";
import { convertFileSrc } from "@tauri-apps/api/core";
import { platform } from "@tauri-apps/plugin-os";
import { Button } from "@/components/ui/button";
import {
  ChevronDown,
  Download,
  Heart,
  Loader2,
  Music,
  Pause,
  Play,
  Repeat,
  Repeat1,
  Shuffle,
  SkipBack,
  SkipForward,
  Volume2,
  VolumeOff,
  Mic2,
  Disc3,
  Check,
} from "lucide-react";
import { Slider } from "@/components/ui/slider";
import { getFormattedDuration } from "@/lib/helpers";
import useAppStore from "@/store/app-store";
import PlaybackQueue from "@/features/queue/components/PlaybackQueue";
import MarqueeText from "@/components/custom/MarqueText";
import ActionsDropdown from "@/features/songs/components/ActionsDropdown";
import AddToPlaylistDialog from "@/features/playlists/components/AddToPlaylistDialog";
import LyricsSection from "@/features/lyrics/components/LyricsSection";
import { getOptimizedThumbnail } from "@/utils/thumbnail";
import useDownloadTrack from "@/features/online/hooks/useDownloadTrack";
import { isOnlineSong } from "@/lib/onlineTrack";

type OverlayPlayerProps = {
  isExpanded: boolean;
  collapse: () => void;
  song: Song;
  position: number;
  duration: number;
  isPlaying: boolean;
  onPlay: () => void;
  onPause: () => void;
  onNext: () => void;
  onPrevious: () => void;
  onSeek: (value: number) => void;
  toggleFavorite: () => void;
};

const OverlayPlayer = ({
  isExpanded,
  collapse,
  song,
  position,
  duration,
  isPlaying,
  onPlay,
  onPause,
  onNext,
  onPrevious,
  onSeek,
  toggleFavorite,
}: OverlayPlayerProps) => {
  const [mobileTab, setMobileTab] = useState<"track" | "lyrics">("track");
  const downloadMutation = useDownloadTrack();
  const isOnline = isOnlineSong(song);

  const isShuffle = useAppStore((state) => state.isShuffle);
  const setIsShuffle = useAppStore((state) => state.setIsShuffle);

  const repeatMode = useAppStore((state) => state.repeatMode);
  const toggleRepeatMode = useAppStore((state) => state.toggleRepeatMode);

  const muted = useAppStore((state) => state.muted);
  const setMuted = useAppStore((state) => state.setMuted);

  const volume = useAppStore((state) => state.volume);
  const setVolume = useAppStore((state) => state.setVolume);

  const handleVolumeChange = (value: number) => {
    setVolume(value);
    setMuted(value === 0);
  };

  const currentPlatform = platform();
  const isMacOS = currentPlatform === "macos";

  const rawCover = song.album_cover_path
    ? song.album_cover_path.startsWith("http://") ||
      song.album_cover_path.startsWith("https://")
      ? song.album_cover_path
      : convertFileSrc(song.album_cover_path)
    : "";

  const coverSrc = rawCover.startsWith("http")
    ? getOptimizedThumbnail(rawCover, "full")
    : rawCover;

  return (
    <section className="fixed inset-0 z-50 pointer-events-none">
      <div
        className={`absolute inset-0 transition-opacity duration-300 ease-out ${
          isExpanded ? "opacity-100" : "opacity-0"
        }`}
        onClick={collapse}
      >
        {/* Safe ambient color glow (mobile) without GPU-crashing full-screen blur */}
        <div
          aria-hidden="true"
          className="md:hidden absolute inset-0 opacity-40 bg-[radial-gradient(ellipse_at_top,_var(--tw-gradient-stops))] from-primary/35 via-background/80 to-background pointer-events-none"
        />
        {/* Solid overlay */}
        <div className="absolute inset-0 bg-background/90 md:bg-background/95 backdrop-blur-sm md:backdrop-blur-2xl" />
      </div>
      <div
        className={`absolute inset-0 w-full h-full flex flex-col ${
          isExpanded ? "translate-y-0" : "translate-y-full"
        } transition-transform duration-300 ease-out pointer-events-auto overflow-hidden safe-top safe-bottom will-change-transform`}
      >
        {/* Top Header Bar (Figma Spotify Style) */}
        <div
          data-tauri-drag-region={isMacOS}
          className="w-full h-14 shrink-0 flex items-center justify-between px-4 pt-1"
        >
          <Button variant="ghost" size="icon" onClick={collapse} className="rounded-full" aria-label="Collapse Player">
            <ChevronDown className="size-5" />
          </Button>

          {/* Mobile Context Title */}
          <div className="flex md:hidden flex-col items-center justify-center text-center">
            <span className="text-[10px] uppercase font-bold tracking-widest text-muted-foreground/70">
              Playing from library
            </span>
            <span className="text-xs font-bold font-heading text-foreground truncate max-w-[180px]">
              {song.album_name || "Sonara Stream"}
            </span>
          </div>

          <div className="flex items-center gap-x-1.5">
            {isOnline && (
              <Button
                variant="ghost"
                size="icon"
                className="hidden md:flex rounded-full"
                disabled={downloadMutation.isPending || downloadMutation.isSuccess}
                onClick={() => downloadMutation.mutate({ song })}
              >
                {downloadMutation.isPending ? (
                  <Loader2 className="size-4 animate-spin" />
                ) : downloadMutation.isSuccess ? (
                  <Check className="size-4 text-emerald-400" />
                ) : (
                  <Download className="size-4" />
                )}
              </Button>
            )}
            <ActionsDropdown song={song}>
              <AddToPlaylistDialog song={song} />
            </ActionsDropdown>
            <div className="hidden md:block">
              <PlaybackQueue />
            </div>
          </div>
        </div>

        {/* Content Container: Dual-column on desktop, Tab-switched on mobile */}
        <div className="flex-1 w-full max-w-6xl mx-auto overflow-hidden px-4 pb-6">
          {/* Desktop Grid Layout (visible on md screens and above) */}
          <div className="hidden md:grid md:grid-cols-2 h-full gap-8 items-center">
            {/* Left: Player & Controls */}
            <div className="w-full h-full flex flex-col justify-center items-center gap-y-6">
              <div className="size-72 xl:size-80 rounded-2xl bg-linear-to-br from-primary/30 to-primary/10 flex items-center justify-center overflow-hidden shadow-2xl border border-border/40">
                {coverSrc ? (
                  <img
                    src={coverSrc}
                    alt={song.title}
                    className="w-full h-full object-cover object-center"
                  />
                ) : (
                  <Music className="size-24 text-primary/70" />
                )}
              </div>

              <div className="space-y-1.5 text-center w-full max-w-sm">
                <MarqueeText
                  text={song.title}
                  className="text-xl font-bold leading-tight font-heading"
                />
                <MarqueeText
                  text={`${song.artist_name || "Unknown Artist"} • ${song.album_name || "Unknown Album"}`}
                  className="text-sm text-muted-foreground font-medium"
                />
              </div>

              <div className="w-full max-w-md space-y-4">
                {/* Seek Bar */}
                <div className="w-full space-y-1.5">
                  <Slider
                    defaultValue={[0]}
                    max={duration || 100}
                    value={[position]}
                    onValueChange={(value) => onSeek(value[0])}
                    className="w-full"
                  />
                  <div className="flex items-center justify-between text-xs font-medium text-muted-foreground">
                    <span>{getFormattedDuration(position)}</span>
                    <span>{getFormattedDuration(duration)}</span>
                  </div>
                </div>

                {/* Primary Controls */}
                <div className="flex items-center justify-between gap-x-4">
                  <Button
                    variant={isShuffle ? "default" : "ghost"}
                    size="icon"
                    className="rounded-full"
                    onClick={() => setIsShuffle(!isShuffle)}
                  >
                    <Shuffle className="size-4" />
                  </Button>
                  <Button
                    variant="ghost"
                    size="icon"
                    className="rounded-full"
                    onClick={onPrevious}
                  >
                    <SkipBack className="size-5" />
                  </Button>
                  <Button
                    size="icon-lg"
                    className="rounded-full size-14 shadow-lg shadow-primary/25"
                    onClick={isPlaying ? onPause : onPlay}
                  >
                    {isPlaying ? <Pause className="size-6" /> : <Play className="size-6 ml-0.5" />}
                  </Button>
                  <Button
                    variant="ghost"
                    size="icon"
                    className="rounded-full"
                    onClick={onNext}
                  >
                    <SkipForward className="size-5" />
                  </Button>
                  <Button
                    variant={repeatMode !== "off" ? "default" : "ghost"}
                    size="icon"
                    className="rounded-full"
                    onClick={toggleRepeatMode}
                  >
                    {repeatMode === "off" && <Repeat className="size-4" />}
                    {repeatMode === "one" && <Repeat1 className="size-4" />}
                    {repeatMode === "all" && <Repeat className="size-4" />}
                  </Button>
                </div>

                {/* Secondary Controls (Volume & Favorite) */}
                <div className="flex items-center justify-between gap-3 pt-2">
                  <Button
                    variant="ghost"
                    size="icon"
                    className="rounded-full"
                    onClick={() => setMuted(!muted)}
                  >
                    {muted ? <VolumeOff className="size-4" /> : <Volume2 className="size-4" />}
                  </Button>
                  <Slider
                    defaultValue={[0]}
                    max={100}
                    value={[volume]}
                    onValueChange={(value) => handleVolumeChange(value[0])}
                    className={`w-full flex-1 ${muted ? "opacity-50" : ""}`}
                  />
                  <Button
                    variant="ghost"
                    size="icon"
                    className="rounded-full"
                    onClick={toggleFavorite}
                  >
                    {song.is_favorite ? (
                      <Heart className="size-4 text-primary fill-current" />
                    ) : (
                      <Heart className="size-4" />
                    )}
                  </Button>
                </div>
              </div>
            </div>

            {/* Right: Synced Lyrics */}
            <div className="w-full h-full overflow-hidden flex flex-col justify-center">
              <LyricsSection song={song} position={position} />
            </div>
          </div>

          {/* Mobile Layout (visible on screens < md) */}
          <div className="flex md:hidden flex-col h-full overflow-hidden">
            {mobileTab === "track" ? (
              <div className="flex-1 flex flex-col justify-between py-2 max-w-sm mx-auto w-full px-4 overflow-y-auto">
                {/* Artwork */}
                <div className="flex-1 min-h-0 flex items-center justify-center py-2">
                  <div className="w-full max-w-[82vw] aspect-square rounded-2xl bg-linear-to-br from-primary/30 to-primary/10 flex items-center justify-center overflow-hidden shadow-2xl border border-white/10">
                    {coverSrc ? (
                      <img
                        src={coverSrc}
                        alt={song.title}
                        className="w-full h-full object-cover object-center"
                      />
                    ) : (
                      <Music className="size-20 text-primary/70" />
                    )}
                  </div>
                </div>

                {/* Track Details & Favorite */}
                <div className="flex items-center justify-between py-2">
                  <div className="min-w-0 flex-1 pr-3 space-y-0.5">
                    <MarqueeText
                      text={song.title}
                      className="text-xl font-bold leading-tight font-heading truncate"
                    />
                    <MarqueeText
                      text={song.artist_name || "Unknown Artist"}
                      className="text-sm text-muted-foreground font-medium truncate"
                    />
                  </div>
                  <Button
                    variant="ghost"
                    size="icon"
                    className="rounded-full size-11 shrink-0 text-muted-foreground hover:text-foreground active:scale-90 transition-transform"
                    onClick={toggleFavorite}
                    aria-label="Toggle Favorite"
                  >
                    {song.is_favorite ? (
                      <Heart className="size-6 text-primary fill-current" />
                    ) : (
                      <Heart className="size-6" />
                    )}
                  </Button>
                </div>

                {/* Timeline Scrubber */}
                <div className="space-y-1.5 pt-1 pb-2">
                  <Slider
                    defaultValue={[0]}
                    max={duration || 100}
                    value={[position]}
                    onValueChange={(value) => onSeek(value[0])}
                    className="w-full"
                  />
                  <div className="flex items-center justify-between text-xs font-mono font-medium text-muted-foreground px-0.5">
                    <span>{getFormattedDuration(position)}</span>
                    <span>{getFormattedDuration(duration)}</span>
                  </div>
                </div>

                {/* Transport Controls (5 buttons, 64px central play) */}
                <div className="flex items-center justify-between px-1 py-1">
                  <Button
                    variant={isShuffle ? "default" : "ghost"}
                    size="icon"
                    className={`rounded-full size-10 ${isShuffle ? "bg-primary/20 text-primary hover:bg-primary/30" : "text-muted-foreground hover:text-foreground"}`}
                    onClick={() => setIsShuffle(!isShuffle)}
                    aria-label="Shuffle"
                  >
                    <Shuffle className="size-[18px]" />
                  </Button>
                  <Button
                    variant="ghost"
                    size="icon"
                    className="rounded-full size-12 text-foreground active:scale-90 transition-transform"
                    onClick={onPrevious}
                    aria-label="Previous Track"
                  >
                    <SkipBack className="size-6" />
                  </Button>
                  <Button
                    size="icon-lg"
                    className="rounded-full size-16 bg-primary text-primary-foreground hover:bg-primary/90 shadow-xl shadow-primary/25 active:scale-95 transition-transform"
                    onClick={isPlaying ? onPause : onPlay}
                    aria-label={isPlaying ? "Pause" : "Play"}
                  >
                    {isPlaying ? (
                      <Pause className="size-7 fill-current" />
                    ) : (
                      <Play className="size-7 fill-current ml-0.5" />
                    )}
                  </Button>
                  <Button
                    variant="ghost"
                    size="icon"
                    className="rounded-full size-12 text-foreground active:scale-90 transition-transform"
                    onClick={onNext}
                    aria-label="Next Track"
                  >
                    <SkipForward className="size-6" />
                  </Button>
                  <Button
                    variant={repeatMode !== "off" ? "default" : "ghost"}
                    size="icon"
                    className={`rounded-full size-10 ${repeatMode !== "off" ? "bg-primary/20 text-primary hover:bg-primary/30" : "text-muted-foreground hover:text-foreground"}`}
                    onClick={toggleRepeatMode}
                    aria-label="Repeat Mode"
                  >
                    {repeatMode === "off" && <Repeat className="size-[18px]" />}
                    {repeatMode === "one" && <Repeat1 className="size-[18px]" />}
                    {repeatMode === "all" && <Repeat className="size-[18px]" />}
                  </Button>
                </div>

                {/* Bottom Utility Row: Download / Lyrics Switcher / Queue */}
                <div className="flex items-center justify-between pt-3 pb-2 px-2 border-t border-white/5">
                  <div className="w-10 flex items-center justify-start">
                    {isOnline ? (
                      <Button
                        variant="ghost"
                        size="icon"
                        className="rounded-full size-10 text-muted-foreground hover:text-foreground"
                        disabled={downloadMutation.isPending || downloadMutation.isSuccess}
                        onClick={() => downloadMutation.mutate({ song })}
                        aria-label="Download Song"
                      >
                        {downloadMutation.isPending ? (
                          <Loader2 className="size-4.5 animate-spin text-primary" />
                        ) : downloadMutation.isSuccess ? (
                          <Check className="size-4.5 text-emerald-500" />
                        ) : (
                          <Download className="size-4.5" />
                        )}
                      </Button>
                    ) : (
                      <div className="size-10" />
                    )}
                  </div>

                  <button
                    onClick={() => setMobileTab("lyrics")}
                    className="flex items-center gap-1.5 px-4 py-1.5 rounded-full bg-white/5 hover:bg-white/10 active:scale-95 transition-all text-xs font-medium text-muted-foreground hover:text-foreground border border-white/10"
                    aria-label="Open Lyrics"
                  >
                    <Mic2 className="size-3.5 text-primary" />
                    <span>Lyrics</span>
                  </button>

                  <div className="w-10 flex items-center justify-end">
                    <PlaybackQueue />
                  </div>
                </div>
              </div>
            ) : (
              /* Mobile Lyrics View */
              <div className="flex-1 flex flex-col justify-between py-2 max-w-sm mx-auto w-full px-4 overflow-hidden">
                <div className="flex-1 overflow-hidden py-2">
                  <LyricsSection song={song} position={position} />
                </div>

                <div className="flex items-center justify-between px-2 pt-2 border-t border-white/5 shrink-0">
                  <div className="flex items-center gap-2 min-w-0 flex-1">
                    <Button
                      size="icon"
                      className="rounded-full size-10 bg-primary text-primary-foreground shadow-md shadow-primary/25 shrink-0"
                      onClick={isPlaying ? onPause : onPlay}
                      aria-label={isPlaying ? "Pause" : "Play"}
                    >
                      {isPlaying ? (
                        <Pause className="size-5 fill-current" />
                      ) : (
                        <Play className="size-5 fill-current ml-0.5" />
                      )}
                    </Button>
                    <div className="min-w-0 pr-2">
                      <p className="text-xs font-semibold truncate text-foreground">{song.title}</p>
                      <p className="text-[10px] text-muted-foreground truncate">{song.artist_name || "Unknown"}</p>
                    </div>
                  </div>

                  <div className="flex items-center gap-2 shrink-0">
                    <button
                      onClick={() => setMobileTab("track")}
                      className="flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-primary text-primary-foreground text-xs font-semibold shadow-xs active:scale-95 transition-transform"
                      aria-label="Back to Track View"
                    >
                      <Disc3 className="size-3.5" />
                      <span>Track</span>
                    </button>
                    <PlaybackQueue />
                  </div>
                </div>
              </div>
            )}
          </div>
        </div>
      </div>
    </section>
  );
};

export default OverlayPlayer;
