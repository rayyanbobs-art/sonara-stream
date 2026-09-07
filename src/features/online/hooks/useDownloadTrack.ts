import { useMutation, useQueryClient } from "@tanstack/react-query";
import { invoke } from "@tauri-apps/api/core";
import { toast } from "sonner";
import { getOnlineVideoId } from "@/lib/onlineTrack";

type DownloadTrackInput = {
  song: Song;
};

const useDownloadTrack = () => {
  const queryClient = useQueryClient();

  return useMutation<Song, Error, DownloadTrackInput>({
    mutationFn: async ({ song }) => {
      const videoId = getOnlineVideoId(song);
      if (!videoId) throw new Error("Cannot determine video ID for download");

      return invoke<Song>("download_online_track", {
        input: {
          id: videoId,
          title: song.title,
          artist: song.artist_name,
          album: song.album_name || undefined,
          thumbnail: song.album_cover_path || undefined,
          duration: Math.round(song.duration || 0),
        },
      });
    },
    onSuccess: () => {
      toast.success("Song downloaded successfully");
      queryClient.invalidateQueries({ queryKey: ["songs"], exact: false });
    },
    onError: (err) => {
      toast.error("Download failed");
      console.error("Download error:", err);
    },
  });
};

export default useDownloadTrack;
