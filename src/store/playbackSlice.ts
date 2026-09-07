import { StateCreator } from "zustand";
import { type AppStoreState } from "./app-store";
import { shuffleQueue } from "@/lib/helpers";
import { onlineTrackToSong } from "@/lib/onlineTrack";

export interface PlaybackState {
  currentQueueItem: QueueItem | null;
  currentSong: Song | null;
  onlineSongsMap: Record<number, Song>;
  // UI queue not affected by shuffle mode
  queue: QueueItem[];
  // actual playback queue (no shuffle: same as queue, shuffle: shuffled version of queue)
  playbackQueue: QueueItem[];

  // playback state
  isPlaying: boolean;
  isShuffle: boolean;
  muted: boolean;
  repeatMode: "off" | "one" | "all";

  // Actions
  // main entry point for playing a song, sets the current queue item and queue
  playSong: (song: Song, songs: Song[]) => void;
  playOnlineTrack: (track: OnlineTrack, trackList?: OnlineTrack[]) => void;

  // select current song
  setCurrentQueueItem: (item: QueueItem | null) => void;
  setCurrentSong: (song: Song | null) => void;

  // add to queue
  addToQueue: (song: Song) => void;

  // remove from queue
  removeFromQueue: (id: string) => void;

  setIsPlaying: (isPlaying: boolean) => void;
  setIsShuffle: (isShuffle: boolean) => void;
  setMuted: (muted: boolean) => void;
  toggleRepeatMode: () => void;

  next: () => void;
  previous: () => void;

  volume: number;
  setVolume: (v: number) => void;

  isPlaybackInitialized: boolean;

  updateSongFavorite: (songId: number, isFavorite: boolean, updatedSong?: Song) => void;

  initPlaybackFromSettings: () => void;
}

const createPlaybackSlice: StateCreator<
  AppStoreState,
  [],
  [],
  PlaybackState
> = (set, get) => ({
  currentQueueItem: null,
  currentSong: null,
  onlineSongsMap: {},
  queue: [],
  playbackQueue: [],
  isPlaying: false,
  isShuffle: false,
  muted: false,
  repeatMode: "all",

  setCurrentQueueItem: (item) => {
    set((state) => {
      const song = item?.song || state.onlineSongsMap[item?.songId ?? 0] || null;
      return {
        currentQueueItem: item,
        currentSong: song ?? state.currentSong,
      };
    });
  },

  setCurrentSong: (song) => set({ currentSong: song }),

  playSong: (song, songs) => {
    set((state) => {
      const newOnlineMap = { ...state.onlineSongsMap };
      songs.forEach((s) => {
        if (s.is_online || s.path.startsWith("online://")) {
          newOnlineMap[s.id] = s;
        }
      });
      if (song.is_online || song.path.startsWith("online://")) {
        newOnlineMap[song.id] = song;
      }

      // build queue items with unique ids and inlined song
      const queue = songs.map((s) => ({
        id: crypto.randomUUID(),
        songId: s.id,
        song: s,
      }));

      // build playback queue based on shuffle mode
      const playbackQueue = state.isShuffle ? shuffleQueue(queue) : queue;

      // find the queue item for the selected song
      const currentItem = queue.find((item) => item.songId === song.id);

      return {
        onlineSongsMap: newOnlineMap,
        queue,
        playbackQueue,
        currentQueueItem: currentItem || null,
        currentSong: song,
        isPlaying: true,
      };
    });
  },

  playOnlineTrack: (track, trackList = []) => {
    const list = trackList.length > 0 ? trackList : [track];
    const songs = list.map(onlineTrackToSong);
    const targetSong = onlineTrackToSong(track);
    get().playSong(targetSong, songs);
  },

  // add new song to both UI queue and playback queue
  addToQueue: (song) =>
    set((state) => {
      const newItem: QueueItem = { id: crypto.randomUUID(), songId: song.id, song };
      const newOnlineMap =
        song.is_online || song.path.startsWith("online://")
          ? { ...state.onlineSongsMap, [song.id]: song }
          : state.onlineSongsMap;

      return {
        onlineSongsMap: newOnlineMap,
        queue: [...state.queue, newItem],
        playbackQueue: [...state.playbackQueue, newItem],
      };
    }),
  // remove song from both UI queue and playback queue
  // if the removed song is the current song, set currentQueueItem to null
  removeFromQueue: (queueItemId) =>
    set((state) => {
      const queue = state.queue.filter((item) => item.id !== queueItemId);

      const playbackQueue = state.playbackQueue.filter(
        (item) => item.id !== queueItemId,
      );

      const currentQueueItem =
        state.currentQueueItem?.id === queueItemId
          ? null
          : state.currentQueueItem;

      return {
        queue,
        playbackQueue,
        currentQueueItem,
        currentSong: currentQueueItem ? state.currentSong : null,
        isPlaying: currentQueueItem ? state.isPlaying : false,
      };
    }),

  setIsPlaying: (isPlaying) => set({ isPlaying }),
  setIsShuffle: (isShuffle) =>
    set((state) => {
      const newPlaybackQueue = isShuffle
        ? shuffleQueue(state.queue)
        : state.queue;
      return { isShuffle, playbackQueue: newPlaybackQueue };
    }),
  setMuted: (muted) => set({ muted }),
  toggleRepeatMode: () =>
    set((state) => {
      const repeatModes: Array<"off" | "one" | "all"> = ["off", "one", "all"];
      const currentIndex = repeatModes.indexOf(state.repeatMode);
      const nextIndex = (currentIndex + 1) % repeatModes.length;
      return { repeatMode: repeatModes[nextIndex] };
    }),

  next: () =>
    set((state) => {
      if (state.playbackQueue.length === 0) {
        return { currentQueueItem: null, currentSong: null, isPlaying: false };
      }

      const currentIndex = state.playbackQueue.findIndex(
        (item) => item.id === state.currentQueueItem?.id,
      );
      const nextIndex = (currentIndex + 1) % state.playbackQueue.length;

      // reach the end of the queue and repeat mode is off, stop playback
      if (state.repeatMode === "off" && nextIndex === 0) {
        return { currentQueueItem: null, currentSong: null, isPlaying: false };
      }

      const nextItem = state.playbackQueue[nextIndex];
      const nextSong =
        nextItem?.song || state.onlineSongsMap[nextItem?.songId ?? 0] || null;

      // otherwise, move to the next song
      return {
        currentQueueItem: nextItem,
        currentSong: nextSong ?? state.currentSong,
      };
    }),
  previous: () =>
    set((state) => {
      if (state.playbackQueue.length === 0) {
        return { currentQueueItem: null, currentSong: null, isPlaying: false };
      }

      const currentIndex = state.playbackQueue.findIndex(
        (item) => item.id === state.currentQueueItem?.id,
      );
      const previousIndex =
        (currentIndex - 1 + state.playbackQueue.length) %
        state.playbackQueue.length;
      const prevItem = state.playbackQueue[previousIndex];
      const prevSong =
        prevItem?.song || state.onlineSongsMap[prevItem?.songId ?? 0] || null;

      return {
        currentQueueItem: prevItem,
        currentSong: prevSong ?? state.currentSong,
      };
    }),

  volume: 50,
  setVolume: (v) => set({ volume: v }),

  isPlaybackInitialized: false,

  updateSongFavorite: (songId, isFavorite, updatedSong) => {
    set((state) => {
      let currentSong = state.currentSong;
      if (
        currentSong &&
        (currentSong.id === songId ||
          (updatedSong && currentSong.path === updatedSong.path))
      ) {
        currentSong = updatedSong || {
          ...currentSong,
          is_favorite: isFavorite,
          favorite_added_at: isFavorite ? Date.now() : null,
        };
      }

      const onlineSongsMap = { ...state.onlineSongsMap };
      if (onlineSongsMap[songId]) {
        onlineSongsMap[songId] = {
          ...onlineSongsMap[songId],
          is_favorite: isFavorite,
          favorite_added_at: isFavorite ? Date.now() : null,
        };
      }
      if (updatedSong && updatedSong.id !== songId) {
        delete onlineSongsMap[songId];
        onlineSongsMap[updatedSong.id] = updatedSong;
      }

      const updateItem = (item: QueueItem) => {
        if (
          item.songId === songId ||
          (updatedSong && item.song?.path === updatedSong.path)
        ) {
          const song =
            updatedSong ||
            (item.song ? { ...item.song, is_favorite: isFavorite } : undefined);
          return {
            ...item,
            songId: updatedSong ? updatedSong.id : item.songId,
            song,
          };
        }
        return item;
      };

      const currentQueueItem = state.currentQueueItem
        ? updateItem(state.currentQueueItem)
        : null;
      const queue = state.queue.map(updateItem);
      const playbackQueue = state.playbackQueue.map(updateItem);

      return {
        currentSong,
        currentQueueItem,
        onlineSongsMap,
        queue,
        playbackQueue,
      };
    });
  },

  initPlaybackFromSettings: () => {
    if (get().isPlaybackInitialized) return;

    // Grab the freshly loaded persisted value
    const isShuffle = get().isShuffleConfig;
    const repeatMode = get().repeatModeConfig;

    // Update the playback state with the persisted settings
    set({
      isShuffle: isShuffle,
      repeatMode: repeatMode,
      isPlaybackInitialized: true,
    });
  },
});

export default createPlaybackSlice;
