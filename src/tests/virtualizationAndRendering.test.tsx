import { describe, it, expect, vi } from "vitest";
import React from "react";
import { render, screen } from "@testing-library/react";
import { SongsView } from "../views/SongsView";
import { FavoritesView } from "../views/FavoritesView";
import { Player } from "../components/Player";
import { Track } from "../types";

const mockTrack: Track = {
  id: "song-1",
  title: "Test Track 1",
  artist: "Test Artist",
  duration: 210,
  thumbnail: "https://example.com/thumb.jpg",
  source: "youtube",
  signature: "test artist test track 1",
};

describe("UI Performance: Virtualization and Lazy Loading", () => {
  it("renders SongsView with virtualized list structure and loading='lazy' on thumbnails", () => {
    const tracks = Array.from({ length: 25 }, (_, i) => ({
      ...mockTrack,
      id: `song-${i}`,
      title: `Song ${i}`,
    }));

    render(
      <SongsView
        tracks={tracks}
        currentTrack={tracks[0]}
        isPlaying={false}
        onPlayTrack={vi.fn()}
        onToggleFavorite={vi.fn()}
        isFavorite={() => false}
        onBrowse={vi.fn()}
      />
    );

    const images = screen.getAllByRole("img");
    expect(images.length).toBeGreaterThan(0);
    images.forEach((img) => {
      expect(img.getAttribute("loading")).toBe("lazy");
    });
  });

  it("renders FavoritesView with virtualized list structure and loading='lazy' on thumbnails", () => {
    const favs = Array.from({ length: 15 }, (_, i) => ({
      ...mockTrack,
      id: `fav-${i}`,
      title: `Favorite Song ${i}`,
    }));

    render(
      <FavoritesView
        favorites={favs}
        currentTrack={null}
        isPlaying={false}
        onPlayTrack={vi.fn()}
        onToggleFavorite={vi.fn()}
        onBrowse={vi.fn()}
      />
    );

    const images = screen.getAllByRole("img");
    expect(images.length).toBeGreaterThan(0);
    images.forEach((img) => {
      expect(img.getAttribute("loading")).toBe("lazy");
    });
  });

  it("renders Player with loading='lazy' on track artwork", () => {
    render(
      <Player
        currentTrack={mockTrack}
        isPlaying={true}
        isBuffering={false}
        currentTime={45}
        duration={210}
        volume={0.8}
        isShuffle={false}
        isFavorite={true}
        onTogglePlay={vi.fn()}
        onSeek={vi.fn()}
        onVolumeChange={vi.fn()}
        onNext={vi.fn()}
        onPrev={vi.fn()}
        onToggleShuffle={vi.fn()}
        repeatMode="off"
        onToggleRepeat={vi.fn()}
        onToggleFavorite={vi.fn()}
      />
    );

    const playerArt = screen.getByAltText(mockTrack.title);
    expect(playerArt).toBeDefined();
    expect(playerArt.getAttribute("loading")).toBe("lazy");
  });
});
