declare global {
  interface Window {
    AndroidPlayback?: {
      loadTrack: (
        source: string,
        isLocal: boolean,
        songId: number,
        title: string,
        artist: string,
        albumArtUri: string | null,
        durationSecs: number
      ) => void;
      play: () => void;
      pause: () => void;
      seekTo: (positionSecs: number) => void;
      setVolume: (volume: number) => void;
      stop: () => void;
    };
  }
}

export const isAndroidPlatform = (): boolean => {
  if (typeof window === "undefined") return false;
  return (
    typeof window.AndroidPlayback !== "undefined" ||
    typeof (window as unknown as { AndroidMedia?: unknown }).AndroidMedia !== "undefined" ||
    /android/i.test(navigator.userAgent)
  );
};

export const androidLoadTrack = (
  source: string,
  isLocal: boolean,
  songId: number,
  title: string,
  artist: string,
  albumArtUri: string | null,
  durationSecs: number
): boolean => {
  if (typeof window !== "undefined" && window.AndroidPlayback?.loadTrack) {
    try {
      window.AndroidPlayback.loadTrack(
        source,
        isLocal,
        songId,
        title,
        artist,
        albumArtUri,
        Math.round(durationSecs)
      );
      return true;
    } catch (err) {
      console.error("[AndroidPlayback] loadTrack error:", err);
    }
  }
  return false;
};

export const androidPlay = (): void => {
  if (typeof window !== "undefined" && window.AndroidPlayback?.play) {
    try {
      window.AndroidPlayback.play();
    } catch (err) {
      console.error("[AndroidPlayback] play error:", err);
    }
  }
};

export const androidPause = (): void => {
  if (typeof window !== "undefined" && window.AndroidPlayback?.pause) {
    try {
      window.AndroidPlayback.pause();
    } catch (err) {
      console.error("[AndroidPlayback] pause error:", err);
    }
  }
};

export const androidSeek = (positionSecs: number): void => {
  if (typeof window !== "undefined" && window.AndroidPlayback?.seekTo) {
    try {
      window.AndroidPlayback.seekTo(positionSecs);
    } catch (err) {
      console.error("[AndroidPlayback] seekTo error:", err);
    }
  }
};

export const androidSetVolume = (volume: number): void => {
  if (typeof window !== "undefined" && window.AndroidPlayback?.setVolume) {
    try {
      window.AndroidPlayback.setVolume(Math.max(0, Math.min(1, volume)));
    } catch (err) {
      console.error("[AndroidPlayback] setVolume error:", err);
    }
  }
};

export const androidStop = (): void => {
  if (typeof window !== "undefined" && window.AndroidPlayback?.stop) {
    try {
      window.AndroidPlayback.stop();
    } catch (err) {
      console.error("[AndroidPlayback] stop error:", err);
    }
  }
};
