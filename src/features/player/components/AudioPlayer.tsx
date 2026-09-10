import {
  ChevronUp,
  Heart,
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
} from "lucide-react";
import { Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Slider } from "@/components/ui/slider";
import { convertFileSrc, invoke } from "@tauri-apps/api/core";
import { useEffect, useRef, useState } from "react";
import { getFormattedDuration } from "@/lib/helpers";
import useAppStore from "@/store/app-store";
import useToggleFavoriteMutation from "@/features/songs/api/useToggleFavoriteMutation";
import PlaybackQueue from "@/features/queue/components/PlaybackQueue";
import OverlayPlayer from "@/features/player/components/OverlayPlayer";
import useMediaSession from "@/hooks/useMediaSession";
import MarqueeText from "@/components/custom/MarqueText";
import { isOnlineSong, getOnlineVideoId } from "@/lib/onlineTrack";
import { getOptimizedThumbnail } from "@/utils/thumbnail";

type AudioPlayerProps = {
  currentSong: Song;
};

const AudioPlayer = ({ currentSong }: AudioPlayerProps) => {
  const [isExpanded, setIsExpanded] = useState(false);

  const playerRef = useRef<HTMLAudioElement | null>(null);
  const activeBlobUrlRef = useRef<string | null>(null);

  const hasCountedPlayRef = useRef(false);
  const lastSongIdRef = useRef<number | null>(null);

  const next = useAppStore((state) => state.next);
  const previous = useAppStore((state) => state.previous);

  const isPlaying = useAppStore((state) => state.isPlaying);
  const setIsPlaying = useAppStore((state) => state.setIsPlaying);

  const isShuffle = useAppStore((state) => state.isShuffle);
  const setIsShuffle = useAppStore((state) => state.setIsShuffle);

  const muted = useAppStore((state) => state.muted);
  const setMuted = useAppStore((state) => state.setMuted);
  const volume = useAppStore((state) => state.volume);

  const repeatMode = useAppStore((state) => state.repeatMode);
  const toggleRepeatMode = useAppStore((state) => state.toggleRepeatMode);

  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState(0);

  const { mutate } = useToggleFavoriteMutation();

  const [isResolvingStream, setIsResolvingStream] = useState(false);
  const activeRequestIdRef = useRef<string | null>(null);

  const collapse = () => {
    setIsExpanded(false);
  };

  const handleFavoriteToggle = () => {
    if (currentSong) {
      mutate({ song: currentSong, isFavorite: !currentSong.is_favorite });
    }
  };

  const playAudio = () => {
    if (playerRef.current) {
      playerRef.current.play();
    }
  };

  const pauseAudio = () => {
    if (playerRef.current) {
      playerRef.current.pause();
    }
  };

  const handleNext = () => {
    next();
  };

  const handlePrevious = () => {
    previous();
  };

  const handleEnded = () => {
    if (repeatMode === "one") {
      if (playerRef.current) {
        playerRef.current.currentTime = 0;
        playerRef.current.play();
      }
    } else {
      next();
    }
  };

  const handleAudioError = () => {
    if (isOnlineSong(currentSong)) {
      const videoId = getOnlineVideoId(currentSong);
      if (!videoId) return;
      console.warn("Audio element error on online track, retrying with bypassCache...");
      invoke<string>("get_stream_url", { id: videoId, bypassCache: true })
        .then((streamUrl) => {
          if (playerRef.current && activeRequestIdRef.current === videoId) {
            playerRef.current.src = streamUrl;
            playerRef.current.play().catch(() => {});
          }
        })
        .catch((err) => console.error("Stream retry failed:", err));
    }
  };

  const handleTimeUpdate = () => {
    if (playerRef.current) {
      setCurrentTime(playerRef.current.currentTime);
    }
    if (lastSongIdRef.current !== currentSong.id) {
      lastSongIdRef.current = currentSong.id;
      return;
    }

    if (currentTime < 1) return;

    if (!hasCountedPlayRef.current && duration > 0) {
      const playThreshold = Math.min(30, duration * 0.5);

      if (currentTime >= playThreshold) {
        hasCountedPlayRef.current = true;

        if (currentSong.id > 0) {
          invoke("record_song_play", { songId: currentSong.id })
            .then(() => console.log("Recorded song play:", currentSong.id))
            .catch((err) => console.error(err));
        } else {
          invoke("record_play_event", {
            title: currentSong.title,
            artist: currentSong.artist_name,
            durationSecs: Math.round(duration),
            trackId: currentSong.online_id || String(currentSong.id),
          }).catch(() => {});
        }
      }
    }
  };

  const handleSeek = (value: number) => {
    if (playerRef.current) {
      playerRef.current.currentTime = value;
      setCurrentTime(value);
    }
  };

  const handleOnLoadedMetadata = () => {
    if (playerRef.current) {
      setDuration(playerRef.current.duration);
      setCurrentTime(playerRef.current.currentTime);
    }
  };

  const handleMuteToggle = () => {
    setMuted(!muted);
  };

  useEffect(() => {
    const player = playerRef.current;

    if (!player) return;

    player.volume = volume / 100;
    player.muted = muted;
  }, [muted, volume, currentSong.id]);

  useMediaSession({
    song: currentSong!,
    position: currentTime,
    duration,
    isPlaying,
    onPlay: playAudio,
    onPause: pauseAudio,
    onNext: handleNext,
    onPrevious: handlePrevious,
    onSeek: handleSeek,
  });

  useEffect(() => {
    if (isExpanded) {
      document.body.style.overflow = "hidden";
    } else {
      document.body.style.overflow = "auto";
    }

    return () => {
      document.body.style.overflow = "auto";
    };
  }, [isExpanded]);

  useEffect(() => {
    hasCountedPlayRef.current = false;
  }, [currentSong.id]);

  // Clean up any existing blob url on unmount
  useEffect(() => {
    return () => {
      if (activeBlobUrlRef.current) {
        URL.revokeObjectURL(activeBlobUrlRef.current);
        activeBlobUrlRef.current = null;
      }
    };
  }, []);

  // Song switching
  useEffect(() => {
    const player = playerRef.current;
    if (!player) return;

    if (isOnlineSong(currentSong)) {
      if (activeBlobUrlRef.current) {
        URL.revokeObjectURL(activeBlobUrlRef.current);
        activeBlobUrlRef.current = null;
      }
      const videoId = getOnlineVideoId(currentSong);
      if (!videoId) return;

      activeRequestIdRef.current = videoId;
      setIsResolvingStream(true);

      invoke<string>("get_stream_url", { id: videoId })
        .then((streamUrl) => {
          if (activeRequestIdRef.current !== videoId) return;

          player.src = streamUrl;
          const playPromise = player.play();
          if (playPromise !== undefined) {
            playPromise.catch((err) => {
              if (err.name !== "AbortError") {
                console.error("Online audio play failed:", err);
              }
            });
          }
        })
        .catch((err) => {
          console.error("Failed to resolve stream URL:", err);
        })
        .finally(() => {
          if (activeRequestIdRef.current === videoId) {
            setIsResolvingStream(false);
          }
        });

      return () => {
        invoke("cancel_stream_request", { videoId }).catch(() => {});
      };
    } else {
      activeRequestIdRef.current = null;
      setIsResolvingStream(false);

      let isCancelled = false;
      const loadAndPlayLocal = async () => {
        try {
          if (activeBlobUrlRef.current) {
            URL.revokeObjectURL(activeBlobUrlRef.current);
            activeBlobUrlRef.current = null;
          }

          // Stream audio via local HTTP 206 server (zero-copy range streaming, safe for Android & desktop)
          const localUrl = await invoke<string>("get_local_audio_url", {
            path: currentSong.path,
          });
          if (isCancelled || !playerRef.current) return;

          player.src = localUrl;
          const playPromise = player.play();
          if (playPromise !== undefined) {
            playPromise.catch((err) => {
              if (err.name !== "AbortError") {
                console.error("Audio play failed:", err);
              }
            });
          }
        } catch (err) {
          console.warn("get_local_audio_url fallback to convertFileSrc:", err);
          if (isCancelled || !playerRef.current) return;
          if (activeBlobUrlRef.current) {
            URL.revokeObjectURL(activeBlobUrlRef.current);
            activeBlobUrlRef.current = null;
          }
          player.src = convertFileSrc(currentSong.path);
          const playPromise = player.play();
          if (playPromise !== undefined) {
            playPromise.catch((e) => {
              if (e.name !== "AbortError") {
                console.error("Audio play failed:", e);
              }
            });
          }
        }
      };

      loadAndPlayLocal();

      return () => {
        isCancelled = true;
      };
    }
  }, [currentSong.id, currentSong.path, playerRef]);

  const isCoverUrl =
    currentSong.album_cover_path?.startsWith("http://") ||
    currentSong.album_cover_path?.startsWith("https://");
  const rawCoverSrc = currentSong.album_cover_path
    ? isCoverUrl
      ? currentSong.album_cover_path
      : convertFileSrc(currentSong.album_cover_path)
    : null;
  const coverSrc =
    rawCoverSrc && isCoverUrl
      ? getOptimizedThumbnail(rawCoverSrc, "card")
      : rawCoverSrc;

  return (
    <>
      <footer className="fixed bottom-[calc(3.75rem+env(safe-area-inset-bottom,0px))] md:bottom-2 left-2 right-2 rounded-2xl md:rounded-3xl p-2 md:p-4 shadow-2xl border border-white/10 bg-card/95 md:bg-muted/50 dark:bg-sidebar/95 md:dark:bg-sidebar/50 backdrop-blur-2xl z-30 transition-all">
        <audio
          ref={playerRef}
          onEnded={handleEnded}
          onTimeUpdate={handleTimeUpdate}
          onLoadedMetadata={handleOnLoadedMetadata}
          onError={handleAudioError}
          onPlay={() => setIsPlaying(true)}
          onPause={() => setIsPlaying(false)}
          muted={muted}
        />

        {/* Mobile Mini Player (< md) */}
        <section
          className="flex md:hidden flex-col"
          onTouchStart={(e) => {
            const touch = e.touches[0];
            (e.currentTarget as HTMLElement).dataset.touchStartY = String(touch.clientY);
          }}
          onTouchEnd={(e) => {
            const startY = Number((e.currentTarget as HTMLElement).dataset.touchStartY || 0);
            const endY = e.changedTouches[0].clientY;
            if (startY - endY > 40) setIsExpanded(true);
          }}
        >
          <div className="flex items-center justify-between gap-3 px-1 py-0.5">
            <div
              onClick={() => setIsExpanded(true)}
              className="flex items-center gap-3 min-w-0 flex-1 cursor-pointer select-none"
            >
              <div className="size-12 rounded-xl bg-linear-to-br from-primary/30 to-primary/10 shrink-0 flex items-center justify-center overflow-hidden shadow-sm border border-white/10">
                {coverSrc ? (
                  <img
                    src={coverSrc}
                    alt={currentSong.title}
                    className="w-full h-full object-cover"
                  />
                ) : (
                  <Music className="size-5 text-primary" />
                )}
              </div>
              <div className="min-w-0 space-y-0.5 flex-1 pr-1">
                <MarqueeText
                  text={currentSong.title}
                  className="text-sm font-semibold font-heading truncate text-foreground"
                />
                <p className="text-xs text-muted-foreground truncate font-medium">
                  {currentSong.artist_name || "Unknown Artist"}
                </p>
              </div>
            </div>

            <div className="flex items-center gap-1.5 shrink-0">
              <Button
                variant="ghost"
                size="icon"
                className="rounded-full size-9 text-muted-foreground hover:text-foreground active:scale-90 transition-transform"
                onClick={handleFavoriteToggle}
                aria-label="Toggle Favorite"
              >
                {currentSong.is_favorite ? (
                  <Heart className="size-4.5 text-primary fill-current" />
                ) : (
                  <Heart className="size-4.5" />
                )}
              </Button>
              {isResolvingStream ? (
                <Button variant="ghost" size="icon" className="rounded-full size-10" disabled>
                  <Loader2 className="size-5 animate-spin text-primary" />
                </Button>
              ) : isPlaying ? (
                <Button
                  variant="default"
                  size="icon"
                  className="rounded-full size-10 shadow-md shadow-primary/20 active:scale-95 transition-transform"
                  onClick={pauseAudio}
                  aria-label="Pause"
                >
                  <Pause className="size-5 fill-current" />
                </Button>
              ) : (
                <Button
                  variant="default"
                  size="icon"
                  className="rounded-full size-10 shadow-md shadow-primary/20 active:scale-95 transition-transform"
                  onClick={playAudio}
                  aria-label="Play"
                >
                  <Play className="size-5 fill-current ml-0.5" />
                </Button>
              )}
              <Button
                variant="ghost"
                size="icon"
                className="rounded-full size-9 text-muted-foreground hover:text-foreground active:scale-90 transition-transform"
                onClick={handleNext}
                aria-label="Next Song"
              >
                <SkipForward className="size-4.5" />
              </Button>
            </div>
          </div>
          {/* Integrated 2px progress bar */}
          <div className="w-full h-[2px] bg-white/10 rounded-full mt-1.5 overflow-hidden">
            <div
              className="h-full bg-primary rounded-full transition-[width] duration-300 ease-linear"
              style={{ width: duration > 0 ? `${(currentTime / duration) * 100}%` : "0%" }}
            />
          </div>
        </section>

        {/* Desktop Full Player (>= md) */}
        <section className="hidden md:grid w-full h-full grid-cols-10 items-center">
          <div className="col-span-2 flex items-center justify-start gap-x-2 2xl:gap-x-4 min-w-0">
            <div className="size-12 rounded-md bg-linear-to-br from-primary/50 to-primary/30 shrink-0 flex items-center justify-center overflow-hidden">
              {coverSrc ? (
                <img
                  src={coverSrc}
                  alt={currentSong.title}
                  className="w-full h-full object-cover"
                />
              ) : (
                <Music className="size-5 text-primary" />
              )}
            </div>
            <div className="min-w-0 space-y-px">
              <MarqueeText
                text={currentSong.title}
                className="text-sm font-medium font-heading"
              />
              <MarqueeText
                text={`${currentSong.artist_name} - ${currentSong.album_name}`}
                className="text-xs text-muted-foreground"
              />
            </div>
          </div>
          <div className="col-span-1 flex items-center justify-center gap-x-2 2xl:gap-x-4">
            <Button variant="ghost" size="icon" onClick={handleFavoriteToggle}>
              {currentSong.is_favorite ? (
                <Heart className="text-primary" fill="currentColor" />
              ) : (
                <Heart />
              )}
            </Button>
            <Button variant="ghost" size="icon" onClick={handleMuteToggle}>
              {muted ? <VolumeOff /> : <Volume2 />}
            </Button>
          </div>
          <div className="col-span-4 w-full grid grid-cols-10 items-center justify-center">
            <span className="text-sm font-heading font-medium text-muted-foreground text-center">
              {getFormattedDuration(currentTime)}
            </span>
            <Slider
              defaultValue={[0]}
              max={duration}
              value={[currentTime]}
              onValueChange={(value) => {
                handleSeek(value[0]);
              }}
              className="col-span-8 w-full"
            />
            <span className="text-sm font-heading font-medium text-muted-foreground text-center">
              {getFormattedDuration(duration)}
            </span>
          </div>
          <div className="col-span-2 flex items-center justify-center gap-x-2 2xl:gap-x-4">
            <Button
              variant={isShuffle ? "default" : "ghost"}
              size="icon"
              onClick={() => setIsShuffle(!isShuffle)}
            >
              <Shuffle />
            </Button>
            <Button variant="ghost" size="icon" onClick={handlePrevious}>
              <SkipBack />
            </Button>
            {isResolvingStream ? (
              <Button variant="ghost" size="icon" disabled>
                <Loader2 className="size-4 animate-spin text-primary" />
              </Button>
            ) : isPlaying ? (
              <Button variant="ghost" size="icon" onClick={pauseAudio}>
                <Pause />
              </Button>
            ) : (
              <Button variant="ghost" size="icon" onClick={playAudio}>
                <Play />
              </Button>
            )}
            <Button variant="ghost" size="icon" onClick={handleNext}>
              <SkipForward />
            </Button>
            <Button
              variant={repeatMode !== "off" ? "default" : "ghost"}
              size="icon"
              onClick={toggleRepeatMode}
            >
              {repeatMode === "off" && <Repeat />}
              {repeatMode === "one" && <Repeat1 />}
              {repeatMode === "all" && <Repeat />}
            </Button>
          </div>
          <div className="col-span-1 flex items-center justify-end gap-x-2 2xl:gap-x-4">
            <Button
              variant="ghost"
              size="icon"
              onClick={() => setIsExpanded(true)}
            >
              <ChevronUp />
            </Button>
            <PlaybackQueue />
          </div>
        </section>
      </footer>

      <OverlayPlayer
        isExpanded={isExpanded}
        collapse={collapse}
        song={currentSong}
        position={currentTime}
        duration={duration}
        isPlaying={isPlaying}
        onPlay={playAudio}
        onPause={pauseAudio}
        onNext={handleNext}
        onPrevious={handlePrevious}
        onSeek={handleSeek}
        toggleFavorite={handleFavoriteToggle}
      />
    </>
  );
};
export default AudioPlayer;
