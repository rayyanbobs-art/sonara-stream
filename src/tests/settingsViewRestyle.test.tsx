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

describe("SettingsView Restyle & Section Functionality", () => {
  const onThemeChange = vi.fn();
  const onAccentChange = vi.fn();
  const onShuffleDefaultChange = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    mockInvoke.mockImplementation((cmd: string) => {
      if (cmd === "get_ytdlp_status") {
        return Promise.resolve({
          version: "2026.01.01",
          source: "bundled",
          last_check_unix: 1700000000,
          auto_update_enabled: true,
          consecutive_failures: 0,
        });
      }
      return Promise.resolve();
    });
  });

  it("renders all 5 core sections and disclosures", async () => {
    render(
      <SettingsView
        theme="dark"
        onThemeChange={onThemeChange}
        accent="gold"
        onAccentChange={onAccentChange}
        shuffleDefault={false}
        onShuffleDefaultChange={onShuffleDefaultChange}
      />
    );

    expect(screen.getByText("Playback")).toBeDefined();
    expect(screen.getByText("Library & Data")).toBeDefined();
    expect(screen.getByText("Updates & Engine")).toBeDefined();
    expect(screen.getByText("Appearance")).toBeDefined();
    expect(screen.getByText("About Sonara")).toBeDefined();
    expect(screen.getByText("External Network Requests Disclosure")).toBeDefined();
    expect(screen.getByText("LRCLIB (lrclib.net)")).toBeDefined();
  });

  it("toggles default shuffle via custom switch", () => {
    render(
      <SettingsView
        theme="dark"
        onThemeChange={onThemeChange}
        accent="gold"
        onAccentChange={onAccentChange}
        shuffleDefault={false}
        onShuffleDefaultChange={onShuffleDefaultChange}
      />
    );

    const shuffleSwitch = screen.getByRole("switch", { name: /default shuffle on/i });
    fireEvent.click(shuffleSwitch);
    expect(onShuffleDefaultChange).toHaveBeenCalledWith(true);
  });

  it("switches theme via segmented buttons", () => {
    render(
      <SettingsView
        theme="dark"
        onThemeChange={onThemeChange}
        accent="gold"
        onAccentChange={onAccentChange}
        shuffleDefault={false}
        onShuffleDefaultChange={onShuffleDefaultChange}
      />
    );

    const lightBtn = screen.getByRole("radio", { name: /light/i });
    fireEvent.click(lightBtn);
    expect(onThemeChange).toHaveBeenCalledWith("light");
  });

  it("handles clear search cache invocation", async () => {
    mockInvoke.mockResolvedValueOnce({});

    render(
      <SettingsView
        theme="dark"
        onThemeChange={onThemeChange}
        accent="gold"
        onAccentChange={onAccentChange}
        shuffleDefault={false}
        onShuffleDefaultChange={onShuffleDefaultChange}
      />
    );

    const clearBtn = screen.getByText("Clear Search Cache");
    fireEvent.click(clearBtn);

    await waitFor(() => {
      expect(mockInvoke).toHaveBeenCalledWith("clear_search_cache");
    });
  });

  it("shows scary warning on reset listening history and confirms deletion", async () => {
    mockInvoke.mockResolvedValueOnce({});

    render(
      <SettingsView
        theme="dark"
        onThemeChange={onThemeChange}
        accent="gold"
        onAccentChange={onAccentChange}
        shuffleDefault={false}
        onShuffleDefaultChange={onShuffleDefaultChange}
      />
    );

    const triggerBtn = screen.getByText("Reset Listening History...");
    fireEvent.click(triggerBtn);

    expect(screen.getByText(/⚠️ Permanent Action: Reset Listening History\?/i)).toBeDefined();

    const confirmBtn = screen.getByText("Yes, Permanently Delete History");
    fireEvent.click(confirmBtn);

    await waitFor(() => {
      expect(mockInvoke).toHaveBeenCalledWith("reset_listening_history");
    });
  });

  it("changes lyrics font size and updates localStorage", () => {
    render(
      <SettingsView
        theme="dark"
        onThemeChange={onThemeChange}
        accent="gold"
        onAccentChange={onAccentChange}
        shuffleDefault={false}
        onShuffleDefaultChange={onShuffleDefaultChange}
      />
    );

    const largeBtn = screen.getByRole("radio", { name: /large/i });
    fireEvent.click(largeBtn);

    expect(localStorage.getItem("sonara_lyrics_fontsize")).toBe("large");
  });
});
