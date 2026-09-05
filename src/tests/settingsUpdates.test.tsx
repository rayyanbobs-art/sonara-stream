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

describe("SettingsView Update Handling", () => {
  beforeEach(() => {
    vi.clearAllMocks();
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
      return Promise.resolve();
    });
  });

  it("handles 404 or missing release json with friendly copy", async () => {
    mockCheck.mockRejectedValueOnce(new Error("Failed with status 404: release json not found"));

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

    const checkAppBtn = screen.getByText("Check for App Updates");
    fireEvent.click(checkAppBtn);

    await waitFor(() => {
      expect(
        screen.getByText("You're up to date — no newer release published.")
      ).toBeDefined();
    });
  });

  it("handles offline or network failure with friendly copy", async () => {
    mockCheck.mockRejectedValueOnce(new Error("Failed to fetch: network is offline"));

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

    const checkAppBtn = screen.getByText("Check for App Updates");
    fireEvent.click(checkAppBtn);

    await waitFor(() => {
      expect(
        screen.getByText("Unable to check for updates — please check your internet connection.")
      ).toBeDefined();
    });
  });

  it("handles engine update offline gracefully", async () => {
    mockInvoke.mockImplementation((cmd: string) => {
      if (cmd === "check_ytdlp_update") {
        return Promise.reject(new Error("Network connection unreachable"));
      }
      if (cmd === "get_ytdlp_status") {
        return Promise.resolve({
          version: "2026.01.01",
          source: "bundled",
          last_check_unix: 0,
          auto_update_enabled: true,
          consecutive_failures: 0,
        });
      }
      return Promise.resolve();
    });

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

    const checkEngineBtn = screen.getByText("Check for Engine Updates");
    fireEvent.click(checkEngineBtn);

    await waitFor(() => {
      expect(
        screen.getByText("Unable to check engine updates — network unreachable.")
      ).toBeDefined();
    });
  });
});
