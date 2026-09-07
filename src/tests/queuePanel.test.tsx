import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { QueuePanel } from "../components/QueuePanel";
import { Track } from "../types";

const mockTrack1: Track = {
  id: "track-1",
  title: "Song Alpha",
  artist: "Artist A",
  duration: 180,
  thumbnail: "https://example.com/thumb1.jpg",
  source: "youtube",
  signature: "artist a song alpha",
};

const mockTrack2: Track = {
  id: "track-2",
  title: "Song Beta",
  artist: "Artist B",
  duration: 210,
  thumbnail: "https://example.com/thumb2.jpg",
  source: "youtube",
  signature: "artist b song beta",
};

const mockTrack3: Track = {
  id: "track-3",
  title: "Song Gamma",
  artist: "Artist C",
  duration: 240,
  thumbnail: "https://example.com/thumb3.jpg",
  source: "youtube",
  signature: "artist c song gamma",
};

describe("QueuePanel Component", () => {
  it("renders Now Playing, Next in queue, and Next from source sections correctly", () => {
    render(
      <QueuePanel
        isOpen={true}
        onClose={vi.fn()}
        currentTrack={mockTrack1}
        isPlaying={true}
        userQueue={[mockTrack2]}
        upNextTracks={[mockTrack3]}
        onPlayTrack={vi.fn()}
        onRemoveFromUserQueue={vi.fn()}
        onClearUserQueue={vi.fn()}
        onReorderUserQueue={vi.fn()}
      />
    );

    // Section 1: Now playing
    expect(screen.getByText("Now Playing")).toBeDefined();
    expect(screen.getByText("Song Alpha")).toBeDefined();

    // Section 2: User queue
    expect(screen.getByText("Next in queue")).toBeDefined();
    expect(screen.getByText("Song Beta")).toBeDefined();
    expect(screen.getByText("Clear all")).toBeDefined();

    // Section 3: Next from source
    expect(screen.getByText(/Next from/i)).toBeDefined();
    expect(screen.getByText("Song Gamma")).toBeDefined();
  });

  it("calls onClearUserQueue when 'Clear all' button is clicked", () => {
    const handleClear = vi.fn();
    render(
      <QueuePanel
        isOpen={true}
        onClose={vi.fn()}
        currentTrack={mockTrack1}
        isPlaying={false}
        userQueue={[mockTrack2]}
        upNextTracks={[]}
        onPlayTrack={vi.fn()}
        onRemoveFromUserQueue={vi.fn()}
        onClearUserQueue={handleClear}
        onReorderUserQueue={vi.fn()}
      />
    );

    const clearBtn = screen.getByText("Clear all");
    fireEvent.click(clearBtn);
    expect(handleClear).toHaveBeenCalledTimes(1);
  });

  it("calls onRemoveFromUserQueue when item remove button is clicked", () => {
    const handleRemove = vi.fn();
    render(
      <QueuePanel
        isOpen={true}
        onClose={vi.fn()}
        currentTrack={mockTrack1}
        isPlaying={false}
        userQueue={[mockTrack2]}
        upNextTracks={[]}
        onPlayTrack={vi.fn()}
        onRemoveFromUserQueue={handleRemove}
        onClearUserQueue={vi.fn()}
        onReorderUserQueue={vi.fn()}
      />
    );

    const removeBtn = screen.getByLabelText("Remove Song Beta from queue");
    fireEvent.click(removeBtn);
    expect(handleRemove).toHaveBeenCalledWith(0);
  });

  it("calls onPlayTrack when an item in queue is clicked", () => {
    const handlePlay = vi.fn();
    render(
      <QueuePanel
        isOpen={true}
        onClose={vi.fn()}
        currentTrack={mockTrack1}
        isPlaying={false}
        userQueue={[mockTrack2]}
        upNextTracks={[]}
        onPlayTrack={handlePlay}
        onRemoveFromUserQueue={vi.fn()}
        onClearUserQueue={vi.fn()}
        onReorderUserQueue={vi.fn()}
      />
    );

    const trackTitle = screen.getByText("Song Beta");
    fireEvent.click(trackTitle);
    expect(handlePlay).toHaveBeenCalledWith(mockTrack2);
  });

  it("supports keyboard navigation (Escape to close, arrows to select, delete to remove)", () => {
    const handleClose = vi.fn();
    const handleRemove = vi.fn();
    const { container } = render(
      <QueuePanel
        isOpen={true}
        onClose={handleClose}
        currentTrack={mockTrack1}
        isPlaying={false}
        userQueue={[mockTrack2]}
        upNextTracks={[]}
        onPlayTrack={vi.fn()}
        onRemoveFromUserQueue={handleRemove}
        onClearUserQueue={vi.fn()}
        onReorderUserQueue={vi.fn()}
      />
    );

    const panel = container.querySelector(".queue-panel") as HTMLElement;
    expect(panel).toBeDefined();

    // Escape closes
    fireEvent.keyDown(panel, { key: "Escape" });
    expect(handleClose).toHaveBeenCalledTimes(1);

    // Arrow down focuses item 0
    fireEvent.keyDown(panel, { key: "ArrowDown" });
    // Delete removes focused item 0
    fireEvent.keyDown(panel, { key: "Delete" });
    expect(handleRemove).toHaveBeenCalledWith(0);
  });
});
