export function hashStringToId(str: string): number {
  let hash = 0;
  for (let i = 0; i < str.length; i++) {
    const char = str.charCodeAt(i);
    hash = (hash << 5) - hash + char;
    hash |= 0; // Convert to 32bit integer
  }
  // Ensure it is always negative so it never collides with positive SQLite auto-increment IDs
  return -Math.abs(hash === 0 ? 1 : hash);
}

export function onlineTrackToSong(track: OnlineTrack): Song {
  return {
    id: hashStringToId(track.id),
    title: track.title,
    artist_id: -1,
    artist_name: track.artist,
    album_id: -1,
    album_name: track.source === "spotify" ? "Spotify" : "YouTube Music",
    album_cover_path: track.thumbnail,
    album_artist_name: track.artist,
    duration: track.duration,
    track_number: 1,
    path: `online://${track.source}/${track.id}`,
    is_favorite: false,
    favorite_added_at: null,
    last_played_at: Date.now(),
    play_count: 0,
    created_at: Date.now(),
    is_online: true,
    online_id: track.id,
  };
}

export function isOnlineSong(song: Song | null | undefined): boolean {
  if (!song) return false;
  return (
    song.is_online === true ||
    song.path.startsWith("online://") ||
    song.path.startsWith("http://") ||
    song.path.startsWith("https://")
  );
}

export function getOnlineVideoId(song: Song | null | undefined): string | null {
  if (!song) return null;
  if (song.online_id) return song.online_id;
  if (song.path.startsWith("online://")) {
    const parts = song.path.replace("online://", "").split("/");
    return parts[parts.length - 1] || null;
  }
  return null;
}
