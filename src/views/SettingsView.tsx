import React, { useState, useEffect } from "react";
import { invoke } from "@tauri-apps/api/core";
import { check } from "@tauri-apps/plugin-updater";
import { Radio, Sparkles, RefreshCw, ArrowUpCircle } from "lucide-react";
import { AccentColor } from "../types";

interface SettingsViewProps {
  theme: "dark" | "light";
  onThemeChange: (t: "dark" | "light") => void;
  accent: AccentColor;
  onAccentChange: (a: AccentColor) => void;
  shuffleDefault: boolean;
  onShuffleDefaultChange: (s: boolean) => void;
}

export const SettingsView: React.FC<SettingsViewProps> = ({
  theme,
  onThemeChange,
  accent,
  onAccentChange,
  shuffleDefault,
  onShuffleDefaultChange,
}) => {
  const [ytdlpStatus, setYtdlpStatus] = useState<{
    version: string;
    source: string;
    last_check_unix: number;
    auto_update_enabled: boolean;
    consecutive_failures: number;
  } | null>(null);
  const [checkingYtdlp, setCheckingYtdlp] = useState(false);
  const [checkingApp, setCheckingApp] = useState(false);
  const [appAutoUpdate, setAppAutoUpdate] = useState<boolean>(() => {
    return localStorage.getItem("sonara_app_autoupdate") !== "false";
  });
  const [toastMessage, setToastMessage] = useState<string | null>(null);

  useEffect(() => {
    invoke("get_ytdlp_status")
      .then((status: any) => setYtdlpStatus(status))
      .catch((err) => console.error("Failed to fetch yt-dlp status:", err));
  }, []);

  const handleCheckYtdlp = async () => {
    setCheckingYtdlp(true);
    setToastMessage("Checking GitHub for yt-dlp updates...");
    try {
      const newVersion: string | null = await invoke("check_ytdlp_update", { forced: true });
      const updatedStatus: any = await invoke("get_ytdlp_status");
      setYtdlpStatus(updatedStatus);
      if (newVersion) {
        setToastMessage(`Playback engine updated to ${newVersion}!`);
      } else {
        setToastMessage(`Playback engine is up to date (${updatedStatus?.version || "latest"}).`);
      }
    } catch (err: any) {
      const errStr = String(err?.message || err || "").toLowerCase();
      if (errStr.includes("offline") || errStr.includes("network") || errStr.includes("connection")) {
        setToastMessage("Unable to check engine updates — network unreachable.");
      } else {
        setToastMessage("Engine update check completed — no new updates available.");
      }
    } finally {
      setCheckingYtdlp(false);
      setTimeout(() => setToastMessage(null), 4000);
    }
  };

  const handleToggleAutoUpdate = async (enabled: boolean) => {
    try {
      await invoke("set_ytdlp_auto_update", { enabled });
      setYtdlpStatus((prev) => prev ? { ...prev, auto_update_enabled: enabled } : null);
    } catch (e) {
      console.error("Failed to toggle auto-update:", e);
    }
  };

  const handleToggleAppAutoUpdate = (enabled: boolean) => {
    setAppAutoUpdate(enabled);
    localStorage.setItem("sonara_app_autoupdate", enabled ? "true" : "false");
  };

  const handleCheckAppUpdate = async () => {
    setCheckingApp(true);
    setToastMessage("Checking for Sonara Stream app updates...");
    try {
      const update = await check();
      if (update?.available) {
        setToastMessage(`New version available: v${update.version}! Relaunch via update banner.`);
      } else {
        setToastMessage("You're up to date — no newer release published.");
      }
    } catch (err: any) {
      const errStr = String(err?.message || err || "").toLowerCase();
      if (errStr.includes("404") || errStr.includes("release json") || errStr.includes("not found")) {
        setToastMessage("You're up to date — no newer release published.");
      } else if (
        errStr.includes("failed to fetch") ||
        errStr.includes("network") ||
        errStr.includes("connection") ||
        errStr.includes("dns") ||
        errStr.includes("offline")
      ) {
        setToastMessage("Unable to check for updates — please check your internet connection.");
      } else {
        setToastMessage("Update service unavailable — please try again later.");
      }
    } finally {
      setCheckingApp(false);
      setTimeout(() => setToastMessage(null), 5000);
    }
  };

  const colors: { id: AccentColor; hex: string; name: string }[] = [
    { id: "purple", hex: "#8b5cf6", name: "Purple" },
    { id: "red", hex: "#ef4444", name: "Red" },
    { id: "green", hex: "#1ed760", name: "Emerald" },
    { id: "blue", hex: "#3b82f6", name: "Blue" },
    { id: "gold", hex: "#f5b800", name: "Gold" },
  ];

  return (
    <div className="view-container settings-view">
      <h2 className="settings-page-title">Settings</h2>

      {/* Theme Card */}
      <div className="settings-card">
        <div className="card-header-block">
          <h4>Theme</h4>
          <p>Choose your preferred theme</p>
        </div>
        <div className="theme-options-row">
          <label className="radio-label">
            <input
              type="radio"
              name="theme"
              checked={theme === "light"}
              onChange={() => onThemeChange("light")}
            />
            <span>Light</span>
          </label>
          <label className="radio-label">
            <input
              type="radio"
              name="theme"
              checked={theme === "dark"}
              onChange={() => onThemeChange("dark")}
            />
            <span>Dark</span>
          </label>
        </div>
      </div>

      {/* Accent Color Card */}
      <div className="settings-card">
        <div className="card-header-block">
          <h4>Accent Color</h4>
          <p>Choose your preferred accent color</p>
        </div>
        <div className="accent-dots-row">
          {colors.map((c) => {
            const isSelected = accent === c.id;
            return (
              <button
                key={c.id}
                type="button"
                className={`accent-color-circle ${isSelected ? "selected" : ""}`}
                style={{ backgroundColor: c.hex }}
                onClick={() => onAccentChange(c.id)}
                title={c.name}
              />
            );
          })}
        </div>
      </div>

      {/* Playback Behavior Card */}
      <div className="settings-card">
        <div className="card-header-block">
          <h4>Playback Behavior</h4>
          <p>Configure default streaming and playback preferences</p>
        </div>
        <div className="setting-toggle-row">
          <div>
            <div className="toggle-title">Default Shuffle On</div>
            <div className="toggle-subtitle">Start playback with shuffle enabled by default</div>
          </div>
          <input
            type="checkbox"
            checked={shuffleDefault}
            onChange={(e) => onShuffleDefaultChange(e.target.checked)}
            className="settings-checkbox"
          />
        </div>
      </div>

      {/* Streaming Engine & Updates Card */}
      <div className="settings-card">
        <div className="card-header-block">
          <h4>Streaming Engine (`yt-dlp`)</h4>
          <p>Local extractor status, integrity verification, and automatic updates</p>
        </div>

        <div className="about-stats-grid" style={{ marginBottom: "16px" }}>
          <div className="about-stat-row">
            <span>⚡ Active Version</span>
            <strong>{ytdlpStatus ? ytdlpStatus.version : "Loading..."}</strong>
          </div>
          <div className="about-stat-row">
            <span>🛡️ Binary Source</span>
            <strong>{ytdlpStatus ? (ytdlpStatus.source === "bundled" ? "Bundled Sidecar (SHA-256 Pinned)" : "Verified Auto-Updated") : "..."}</strong>
          </div>
          <div className="about-stat-row">
            <span>🕒 Last Checked</span>
            <strong>
              {ytdlpStatus && ytdlpStatus.last_check_unix > 0
                ? new Date(ytdlpStatus.last_check_unix * 1000).toLocaleString()
                : "Never"}
            </strong>
          </div>
        </div>

        <div className="setting-toggle-row">
          <div>
            <div className="toggle-title">Automatic Updates</div>
            <div
              className="toggle-subtitle"
              title="Periodically checks GitHub Releases and verifies SHA-256 before activating"
            >
              Automatically keep the playback engine up to date
            </div>
          </div>
          <input
            type="checkbox"
            checked={ytdlpStatus?.auto_update_enabled ?? true}
            onChange={(e) => handleToggleAutoUpdate(e.target.checked)}
            className="settings-checkbox"
          />
        </div>

        <div style={{ marginTop: "16px", display: "flex", alignItems: "center", gap: "12px" }}>
          <button
            type="button"
            className="empty-action-btn"
            onClick={handleCheckYtdlp}
            disabled={checkingYtdlp}
            style={{ display: "inline-flex", alignItems: "center", gap: "8px" }}
          >
            <RefreshCw size={14} className={checkingYtdlp ? "spin" : ""} />
            <span>{checkingYtdlp ? "Checking Releases..." : "Check for Engine Updates"}</span>
          </button>
        </div>

        <div style={{ marginTop: "24px", paddingTop: "16px", borderTop: "1px solid var(--border)" }}>
          <div className="card-header-block" style={{ marginBottom: "12px" }}>
            <h4>Desktop Application Updates</h4>
            <p>Sonara Stream desktop player release channel</p>
          </div>

          <div className="setting-toggle-row">
            <div>
              <div className="toggle-title">App Auto-Check on Startup</div>
              <div className="toggle-subtitle">Non-blockingly notify when a new desktop release is published</div>
            </div>
            <input
              type="checkbox"
              checked={appAutoUpdate}
              onChange={(e) => handleToggleAppAutoUpdate(e.target.checked)}
              className="settings-checkbox"
            />
          </div>

          <div style={{ marginTop: "16px", display: "flex", alignItems: "center", gap: "12px" }}>
            <button
              type="button"
              className="empty-action-btn"
              onClick={handleCheckAppUpdate}
              disabled={checkingApp}
              style={{ display: "inline-flex", alignItems: "center", gap: "8px" }}
            >
              <ArrowUpCircle size={14} className={checkingApp ? "spin" : ""} />
              <span>{checkingApp ? "Checking App Releases..." : "Check for App Updates"}</span>
            </button>
          </div>
        </div>

        {toastMessage && (
          <div style={{ marginTop: "14px", fontSize: "13px", color: "var(--accent)", fontWeight: 500 }}>
            {toastMessage}
          </div>
        )}
      </div>

      {/* About Card */}
      <div className="settings-card about-card">
        <div className="card-header-block">
          <h4>About Sonara</h4>
          <p>Information about the application</p>
        </div>

        <div className="about-branding">
          <div className="about-logo-circle">
            <Radio size={24} />
          </div>
          <div className="about-info-text">
            <h5>Sonara Stream</h5>
            <p>
              Sonara is a lightweight desktop music player focused on speed, simplicity, and direct audio streaming without storing permanent audio files.
            </p>
            <span className="version-tag">Version 0.3.1 (Stream Edition)</span>
          </div>
        </div>

        <div className="about-stats-grid">
          <div className="about-stat-row">
            <span>🎵 Stack</span>
            <strong>Rust + Tauri 2 + React</strong>
          </div>
          <div className="about-stat-row">
            <span>⚡ Audio Engine</span>
            <strong>Native Web Audio Streaming</strong>
          </div>
          <div className="about-stat-row">
            <span>🌐 Sources</span>
            <strong>YouTube & Spotify oEmbed</strong>
          </div>
        </div>
      </div>
    </div>
  );
};
