import React from "react";
import { Track } from "../types";
import { QueuePanel } from "./QueuePanel";

export interface UpNextDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  currentTrack: Track | null;
  upNextTracks: Track[];
  onPlayTrack: (track: Track) => void;
  onPrefetchTrack?: (track: Track) => void;
  isPlaying: boolean;
  userQueue?: Track[];
  onRemoveFromUserQueue?: (index: number) => void;
  onClearUserQueue?: () => void;
  onReorderUserQueue?: (fromIndex: number, toIndex: number) => void;
  onSaveQueueAsPlaylist?: (tracks: Track[]) => void;
}

export const UpNextDrawer: React.FC<UpNextDrawerProps> = React.memo((props) => {
  return (
    <QueuePanel
      isOpen={props.isOpen}
      onClose={props.onClose}
      currentTrack={props.currentTrack}
      isPlaying={props.isPlaying}
      userQueue={props.userQueue || []}
      upNextTracks={props.upNextTracks}
      onPlayTrack={props.onPlayTrack}
      onPrefetchTrack={props.onPrefetchTrack}
      onRemoveFromUserQueue={props.onRemoveFromUserQueue || (() => {})}
      onClearUserQueue={props.onClearUserQueue || (() => {})}
      onReorderUserQueue={props.onReorderUserQueue || (() => {})}
      onSaveQueueAsPlaylist={props.onSaveQueueAsPlaylist}
    />
  );
});
