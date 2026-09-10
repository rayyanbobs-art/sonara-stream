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
        {/* Top Header Bar */}
        <div
          data-tauri-drag-region={isMacOS}
          className="w-full h-14 shrink-0 flex items-center justify-between px-4 pt-1"
        >
          <Button variant="ghost" size="icon" onClick={collapse} className="rounded-full">
            <ChevronDown className="size-5" />
          </Button>

          {/* Mobile Tab Switcher */}
          <div className="flex md:hidden items-center p-1 bg-muted/60 rounded-full border border-border/40 text-xs font-medium">
            <button
              onClick={() => setMobileTab("track")}
              className={`flex items-center gap-1.5 px-3 py-1 rounded-full transition-all ${
                mobileTab === "track"
                  ? "bg-primary text-primary-foreground shadow-xs font-semibold"
                  : "text-muted-foreground hover:text-foreground"
              }`}
            >
              <Disc3 className="size-3.5" />
              <span>Track</span>
            </button>
            <button
              onClick={() => setMobileTab("lyrics")}
              className={`flex items-center gap-1.5 px-3 py-1 rounded-full transition-all ${
                mobileTab === "lyrics"
                  ? "bg-primary text-primary-foreground shadow-xs font-semibold"
                  : "text-muted-foreground hover:text-foreground"
              }`}
            >
              <Mic2 className="size-3.5" />
              <span>Lyrics</span>
            </button>
          </div>

          <div className="flex items-center gap-x-2">
            {isOnline && (
              <Button
                variant="ghost"
                size="icon"
                className="rounded-full"
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
            <PlaybackQueue />
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
          <div className="flex md:hidden flex-col h-full overflow-y-auto">
            {mobileTab === "track" ? (
              <div className="flex-1 flex flex-col justify-between py-2 max-w-sm mx-auto w-full">
                {/* Artwork */}
                <div className="flex-1 min-h-0 flex items-center justify-center px-4 py-2">
                  <div className="w-full max-w-[85vw] max-h-[50vh] aspect-square rounded-3xl bg-linear-to-br from-primary/30 to-primary/10 flex items-center justify-center overflow-hidden shadow-2xl border border-white/10">
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
                </div>

                {/* Track Details */}
                <div className="space-y-1 text-center py-2">
                  <MarqueeText
                    text={song.title}
                    className="text-lg font-bold leading-tight font-heading"
                  />
                  <MarqueeText
                    text={`${song.artist_name || "Unknown Artist"} • ${song.album_name || "Unknown Album"}`}
                    className="text-xs text-muted-foreground font-medium"
                  />
                </div>

                {/* Sliders & Controls */}
                <div className="space-y-3 pt-2">
                  <div className="space-y-1">
                    <Slider
                      defaultValue={[0]}
                      max={duration || 100}
                      value={[position]}
                      onValueChange={(value) => onSeek(value[0])}
                      className="w-full"
                    />
                    <div className="flex items-center justify-between text-[11px] font-medium text-muted-foreground">
                      <span>{getFormattedDuration(position)}</span>
                      <span>{getFormattedDuration(duration)}</span>
                    </div>
                  </div>

                  <div className="flex items-center justify-between px-2">
                    <Button
                      variant={isShuffle ? "default" : "ghost"}
                      size="icon"
                      className="rounded-full size-10"
                      onClick={() => setIsShuffle(!isShuffle)}
                    >
                      <Shuffle className="size-[18px]" />
                    </Button>
                    <Button
                      variant="ghost"
                      size="icon"
                      className="rounded-full size-12"
                      onClick={onPrevious}
                    >
                      <SkipBack className="size-6" />
                    </Button>
                    <Button
                      size="icon-lg"
                      className="rounded-full size-16 shadow-lg shadow-primary/25"
                      onClick={isPlaying ? onPause : onPlay}
                    >
                      {isPlaying ? <Pause className="size-7" /> : <Play className="size-7 ml-0.5" />}
                    </Button>
                    <Button
                      variant="ghost"
                      size="icon"
                      className="rounded-full size-12"
                      onClick={onNext}
                    >
                      <SkipForward className="size-6" />
                    </Button>
                    <Button
                      variant={repeatMode !== "off" ? "default" : "ghost"}
                      size="icon"
                      className="rounded-full size-10"
                      onClick={toggleRepeatMode}
                    >
                      {repeatMode === "off" && <Repeat className="size-[18px]" />}
                      {repeatMode === "one" && <Repeat1 className="size-[18px]" />}
                      {repeatMode === "all" && <Repeat className="size-[18px]" />}
                    </Button>
                  </div>

                  <div className="flex items-center justify-center pt-2">
                    <Button
                      variant="ghost"
                      size="icon"
                      className="rounded-full size-10"
                      onClick={toggleFavorite}
                    >
                      {song.is_favorite ? (
                        <Heart className="size-5 text-primary fill-current" />
                      ) : (
                        <Heart className="size-5" />
                      )}
                    </Button>
                  </div>
                </div>
              </div>
            ) : (
              <div className="flex-1 h-full overflow-hidden">
                <LyricsSection song={song} position={position} />
              </div>
            )}
          </div>
        </div>
      </div>
    </section>
  );
};

export default OverlayPlayer;
