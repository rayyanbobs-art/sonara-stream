import { useMutation, useQueryClient } from "@tanstack/react-query";
import { invoke } from "@tauri-apps/api/core";
import { toast } from "sonner";
import useAppStore from "@/store/app-store";
import { isOnlineSong, getOnlineVideoId } from "@/lib/onlineTrack";

type ToggleFavoriteInput = {
  songId?: number;
  isFavorite: boolean;
  song?: Song;
};

const useToggleFavoriteMutation = () => {
  const queryClient = useQueryClient();
  const updateSongFavorite = useAppStore((state) => state.updateSongFavorite);

  const mutation = useMutation<string, Error, ToggleFavoriteInput>({
    mutationFn: async ({ songId, isFavorite, song }) => {
      // Optimistic UI state update
      const targetId = song ? song.id : songId ?? 0;
      updateSongFavorite(targetId, isFavorite);

      if (song && (isOnlineSong(song) || song.id <= 0)) {
        const videoId = getOnlineVideoId(song) || String(song.id);
        const updated = await invoke<Song>("toggle_online_favorite", {
          input: {
            id: videoId,
            title: song.title,
            artist: song.artist_name,
            duration: Math.round(song.duration || 0),
            thumbnail: song.album_cover_path || undefined,
            is_favorite: isFavorite,
          },
        });
        updateSongFavorite(song.id, isFavorite, updated);
        return isFavorite ? "Song marked as favorite" : "Song removed from favorites";
      } else {
        const id = songId ?? (song ? song.id : 0);
        const res = await invoke<string>("set_favorite_song", {
          songId: id,
          isFavorite,
        });
        return res;
      }
    },
    onSuccess: (message) => {
      toast.success(message);
      queryClient.invalidateQueries({ queryKey: ["songs"], exact: false });
    },
    onError: (err, variables) => {
      // Rollback optimistic update
      const targetId = variables.song ? variables.song.id : variables.songId ?? 0;
      updateSongFavorite(targetId, !variables.isFavorite);
      toast.error("Failed to update favorite status");
      console.error(err);
    },
  });

  return mutation;
};

export default useToggleFavoriteMutation;
