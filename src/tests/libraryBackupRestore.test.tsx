import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import React from "react";
import { SettingsView } from "../views/SettingsView";

const mockInvoke = vi.fn();
const mockCheck = vi.fn();

vi.mock("@tauri-apps/api/core", () => ({
  invoke: (...args: any[]) => mockInvoke(...args),
}));

vi.mock("@tauri-apps/plugin-updater", () => ({
  check: () => mockCheck(),
}));

describe("SettingsView Library Export & Import", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    mockInvoke.mockImplementation((cmd: string) => {
      if (cmd === "get_ytdlp_status") {
        return Promise.resolve({
          version: "2026.01.01",
          source: "bundled",
          last_check_unix: 0,
          auto_update_enabled: true,
          consecutive_failures: 0,
        });
      }
      if (cmd === "get_playlists") {
        return Promise.resolve([
          {
            id: "pl_1",
            name: "Summer Hits",
            tracks: [
              {
                id: "t1",
                title: "Song 1",
                artist: "Artist A",
                duration: 200,
                thumbnail: "",
                source: "youtube",
                signature: "sig_1",
              },
            ],
            created_at: 100,
            updated_at: 100,
          },
        ]);
      }
      if (cmd === "get_listening_history") {
        return Promise.resolve([
          {
            signature: "sig_1",
            artist: "Artist A",
            played_at_unix: 500,
            completed: true,
          },
        ]);
      }
      if (cmd === "merge_imported_playlists") {
        return Promise.resolve([]);
      }
      if (cmd === "merge_imported_history") {
        return Promise.resolve(1);
      }
      return Promise.resolve();
    });
  });

  it("exports library with playlists, liked songs, and history", async () => {
    localStorage.setItem(
      "sonara_favorites",
      JSON.stringify([
        {
          id: "fav_1",
          title: "Liked Song",
          artist: "Artist B",
          duration: 180,
          thumbnail: "",
          source: "youtube",
          signature: "sig_fav_1",
        },
      ])
    );

    // Mock URL.createObjectURL
    const mockCreateObjectURL = vi.fn(() => "blob:http://localhost/backup");
    const mockRevokeObjectURL = vi.fn();
    window.URL.createObjectURL = mockCreateObjectURL;
    window.URL.revokeObjectURL = mockRevokeObjectURL;

    render(
      <SettingsView
        theme="dark"
        onThemeChange={() => {}}
        accent="gold"
        onAccentChange={() => {}}
        shuffleDefault={false}
        onShuffleDefaultChange={() => {}}
      />
    );

    const exportBtn = screen.getByText("Export Library");
    fireEvent.click(exportBtn);

    await waitFor(() => {
      expect(mockInvoke).toHaveBeenCalledWith("get_playlists");
      expect(mockInvoke).toHaveBeenCalledWith("get_listening_history");
      expect(mockCreateObjectURL).toHaveBeenCalled();
    });
  });

  it("validates schema and rejects invalid files", async () => {
    render(
      <SettingsView
        theme="dark"
        onThemeChange={() => {}}
        accent="gold"
        onAccentChange={() => {}}
        shuffleDefault={false}
        onShuffleDefaultChange={() => {}}
      />
    );

    const fileInput = screen.getByTestId("library-file-input") as HTMLInputElement;

    const invalidContent = JSON.stringify({ invalid: true });
    const file = new File([invalidContent], "backup.json", { type: "application/json" });

    fireEvent.change(fileInput, { target: { files: [file] } });

    await waitFor(() => {
      expect(
        screen.getByText(/Missing or invalid schema version in backup file/i)
      ).toBeDefined();
    });
  });

  it("shows preview with exact count format and merges on confirm", async () => {
    localStorage.setItem(
      "sonara_favorites",
      JSON.stringify([
        {
          id: "fav_existing",
          title: "Old Favorite",
          artist: "Artist X",
          duration: 150,
          thumbnail: "",
          source: "youtube",
          signature: "sig_existing",
        },
      ])
    );

    render(
      <SettingsView
        theme="dark"
        onThemeChange={() => {}}
        accent="gold"
        onAccentChange={() => {}}
        shuffleDefault={false}
        onShuffleDefaultChange={() => {}}
      />
    );

    const fileInput = screen.getByTestId("library-file-input") as HTMLInputElement;

    const validBackup = {
      version: 1,
      exported_at: 1700000000,
      playlists: [
        {
          id: "imp_pl_1",
          name: "Imported Rock",
          tracks: [],
          created_at: 100,
          updated_at: 100,
        },
        {
          id: "imp_pl_2",
          name: "Imported Jazz",
          tracks: [],
          created_at: 200,
          updated_at: 200,
        },
        {
          id: "imp_pl_3",
          name: "Imported Pop",
          tracks: [],
          created_at: 300,
          updated_at: 300,
        },
      ],
      liked_songs: [
        {
          id: "fav_existing", // duplicate signature/id -> should dedupe
          title: "Old Favorite Duplicate",
          artist: "Artist X",
          duration: 150,
          thumbnail: "",
          source: "youtube",
          signature: "sig_existing",
        },
        {
          id: "fav_new",
          title: "New Favorite",
          artist: "Artist Y",
          duration: 220,
          thumbnail: "",
          source: "youtube",
          signature: "sig_new",
        },
      ],
      history: [
        {
          signature: "sig_h1",
          artist: "Artist Z",
          played_at_unix: 1234,
          completed: true,
        },
      ],
    };

    const file = new File([JSON.stringify(validBackup)], "backup.json", { type: "application/json" });
    fireEvent.change(fileInput, { target: { files: [file] } });

    // Expect preview with exact count format
    await waitFor(() => {
      expect(
        screen.getByText("3 playlists, 2 liked songs, 1 history entries")
      ).toBeDefined();
    });

    // Click confirm merge button
    const confirmBtn = screen.getByText("Merge into Library");
    fireEvent.click(confirmBtn);

    await waitFor(() => {
      expect(mockInvoke).toHaveBeenCalledWith("merge_imported_playlists", {
        imported: validBackup.playlists,
      });
      expect(mockInvoke).toHaveBeenCalledWith("merge_imported_history", {
        imported: validBackup.history,
      });

      // Check deduplication in localStorage
      const savedFavorites = JSON.parse(localStorage.getItem("sonara_favorites") || "[]");
      expect(savedFavorites.length).toBe(2); // old + new, not 3
      expect(savedFavorites.some((f: any) => f.id === "fav_new")).toBe(true);
    });
  });
});
