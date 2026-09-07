import useAppStore from "@/store/app-store";
//import useGetSongByIdQuery from "@/features/songs/api/useGetSongByIdQuery";
import useGetAllSongsQuery from "@/features/songs/api/useGetAllSongsQuery";

// const useCurrentSong = () => {
//   const currentQueueItem = useAppStore((state) => state.currentQueueItem);
//   const { data } = useGetSongByIdQuery({
//     songId: currentQueueItem?.songId ?? -1,
//   });
//   return data;
// };
// export default useCurrentSong;

const useCurrentSong = () => {
  const currentQueueItem = useAppStore((state) => state.currentQueueItem);
  const currentSongFromStore = useAppStore((state) => state.currentSong);
  const onlineSongsMap = useAppStore((state) => state.onlineSongsMap);
  const { data } = useGetAllSongsQuery();

  if (!currentQueueItem) return undefined;

  // If the queue item has the song inlined
  if (currentQueueItem.song) return currentQueueItem.song;

  // If it's an online song stored in the map
  if (onlineSongsMap[currentQueueItem.songId]) {
    return onlineSongsMap[currentQueueItem.songId];
  }

  // If currentSong in store matches this ID
  if (currentSongFromStore && currentSongFromStore.id === currentQueueItem.songId) {
    return currentSongFromStore;
  }

  // Offline SQLite database lookup
  const currentSong = data?.find(
    (song) => song.id === currentQueueItem?.songId,
  );
  return currentSong;
};

export default useCurrentSong;
