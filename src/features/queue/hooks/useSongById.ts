import { useQueryClient } from "@tanstack/react-query";
import useAppStore from "@/store/app-store";

const useSongById = (id: number) => {
  const queryClient = useQueryClient();
  const onlineSong = useAppStore((state) => state.onlineSongsMap[id]);
  if (onlineSong) return onlineSong;

  return queryClient.getQueryData<Song[]>(["songs"])?.find((s) => s.id === id);
};

export default useSongById;
