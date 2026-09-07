import React, { useState, useEffect, useCallback } from "react";
import { invoke } from "@tauri-apps/api/core";
import { check } from "@tauri-apps/plugin-updater";
import {
  Radio,
  RefreshCw,
  ArrowUpCircle,
  Play,
  Library,
  Sliders,
  Palette,
  Info,
  Trash2,
  AlertTriangle,
  ExternalLink,
  ShieldCheck,
  Sun,
  Moon,
  Sparkles,
} from "lucide-react";
import { AccentColor } from "../types";

export interface SettingsViewProps {
  theme: "dark" | "light";
  onThemeChange: (t: "dark" | "light") => void;
  accent: AccentColor;
  onAccentChange: (a: AccentColor) => void;
  shuffleDefault: boolean;
  onShuffleDefaultChange: (s: boolean) => void;
  perfOverlay?: boolean;
  onPerfOverlayChange?: (enabled: boolean) => void;
}

const ACCENT_COLOR_CONFIG: Record<
  AccentColor,
  { hex: string; glow: string; name: string }
> = {
  purple: { hex: "#8b5cf6", glow: "rgba(139, 92, 246, 0.25)", name: "Purple" },
  red: { hex: "#ef4444", glow: "rgba(239, 68, 68, 0.25)", name: "Red" },
  green: { hex: "#1ed760", glow: "rgba(30, 215, 96, 0.25)", name: "Emerald" },
  blue: { hex: "#3b82f6", glow: "rgba(59, 130, 246, 0.25)", name: "Blue" },
  gold: { hex: "#f5b800", glow: "rgba(245, 184, 0, 0.25)", name: "Gold" },
};

export const SettingsView: React.FC<SettingsViewProps> = React.memo(
  ({
    theme,
    onThemeChange,
    accent,
    onAccentChange,
    shuffleDefault,
    onShuffleDefaultChange,
    perfOverlay,
    onPerfOverlayChange,
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

    const [streamQuality, setStreamQuality] = useState<string>(() => {
      return localStorage.getItem("sonara_stream_quality") || "high";
    });

    const [prefetchEnabled, setPrefetchEnabled] = useState<boolean>(() => {
      return localStorage.getItem("sonara_prefetch_enabled") !== "false";
    });

    const [lyricsFontSize, setLyricsFontSize] = useState<string>(() => {
      return localStorage.getItem("sonara_lyrics_fontsize") || "medium";
    });

    const [clearingCache, setClearingCache] = useState(false);
    const [resettingHistory, setResettingHistory] = useState(false);
    const [showScaryResetWarning, setShowScaryResetWarning] = useState(false);
    const [toastMessage, setToastMessage] = useState<string | null>(null);

    useEffect(() => {
      invoke("get_ytdlp_status")
        .then((status: any) => setYtdlpStatus(status))
        .catch((err) => console.error("Failed to fetch yt-dlp status:", err));
    }, []);

    const showToast = useCallback((msg: string, durationMs = 4000) => {
      setToastMessage(msg);
      setTimeout(() => setToastMessage(null), durationMs);
    }, []);

    const handleStreamQualityChange = (q: string) => {
      setStreamQuality(q);
      localStorage.setItem("sonara_stream_quality", q);
      showToast(`Stream quality set to ${q === "high" ? "High (Best Audio)" : "Standard (Faster Buffer)"}`);
    };

    const handlePrefetchChange = (enabled: boolean) => {
      setPrefetchEnabled(enabled);
      localStorage.setItem("sonara_prefetch_enabled", enabled ? "true" : "false");
    };

    const handleLyricsFontSizeChange = (size: string) => {
      setLyricsFontSize(size);
      localStorage.setItem("sonara_lyrics_fontsize", size);
      showToast(`Lyrics font size set to ${size}`);
    };

    const handleClearSearchCache = async () => {
      setClearingCache(true);
      try {
        await invoke("clear_search_cache");
        showToast("Search and stream caches cleared successfully!");
      } catch (err: any) {
        showToast(`Failed to clear cache: ${err?.message || err}`);
      } finally {
        setClearingCache(false);
      }
    };

    const handleResetHistory = async () => {
      setResettingHistory(true);
      try {
        await invoke("reset_listening_history");
        setShowScaryResetWarning(false);
        showToast("Listening history and playback logs permanently reset.");
      } catch (err: any) {
        showToast(`Failed to reset history: ${err?.message || err}`);
      } finally {
        setResettingHistory(false);
      }
    };

    const handleCheckYtdlp = async () => {
      setCheckingYtdlp(true);
      setToastMessage("Checking GitHub for yt-dlp updates...");
      try {
        const newVersion: string | null = await invoke("check_ytdlp_update", { forced: true });
        const updatedStatus: any = await invoke("get_ytdlp_status");
        setYtdlpStatus(updatedStatus);
        if (newVersion) {
          showToast(`Playback engine updated to ${newVersion}!`);
        } else {
          showToast(`Playback engine is up to date (${updatedStatus?.version || "latest"}).`);
        }
      } catch (err: any) {
        const errStr = String(err?.message || err || "").toLowerCase();
        if (errStr.includes("offline") || errStr.includes("network") || errStr.includes("connection")) {
          showToast("Unable to check engine updates — network unreachable.");
        } else {
          showToast("Engine update check completed — no new updates available.");
        }
      } finally {
        setCheckingYtdlp(false);
      }
    };

    const handleToggleAutoUpdate = async (enabled: boolean) => {
      try {
        await invoke("set_ytdlp_auto_update", { enabled });
        setYtdlpStatus((prev) => (prev ? { ...prev, auto_update_enabled: enabled } : null));
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
          showToast(`New version available: v${update.version}! Relaunch via update banner.`);
        } else {
          showToast("You're up to date — no newer release published.");
        }
      } catch (err: any) {
        const errStr = String(err?.message || err || "").toLowerCase();
        if (errStr.includes("404") || errStr.includes("release json") || errStr.includes("not found")) {
          showToast("You're up to date — no newer release published.");
        } else if (
          errStr.includes("failed to fetch") ||
          errStr.includes("network") ||
          errStr.includes("connection") ||
          errStr.includes("dns") ||
          errStr.includes("offline")
        ) {
          showToast("Unable to check for updates — please check your internet connection.");
        } else {
          showToast("Update service unavailable — please try again later.");
        }
      } finally {
        setCheckingApp(false);
      }
    };

    const handleSelectAccent = (colorId: AccentColor) => {
      onAccentChange(colorId);
      const conf = ACCENT_COLOR_CONFIG[colorId];
      if (conf && typeof document !== "undefined") {
        document.documentElement.style.setProperty("--accent", conf.hex);
        document.documentElement.style.setProperty("--accent-glow", conf.glow);
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
      <div className="view-container settings-view" style={{ maxWidth: "860px", paddingBottom: "100px" }}>
        <h2 className="settings-page-title">Settings</h2>

        {toastMessage && (
          <div
            style={{
              padding: "10px 16px",
              borderRadius: "8px",
              background: "rgba(255, 255, 255, 0.08)",
              border: "1px solid var(--accent)",
              color: "var(--accent)",
              fontSize: "13px",
              fontWeight: 500,
              marginBottom: "18px",
              display: "flex",
              alignItems: "center",
              gap: "8px",
            }}
          >
            <Sparkles size={14} />
            <span>{toastMessage}</span>
          </div>
        )}

        {/* SECTION 1: PLAYBACK */}
        <div className="settings-card">
          <div className="card-header-block" style={{ display: "flex", alignItems: "center", gap: "10px" }}>
            <Play size={18} color="var(--accent)" />
            <div>
              <h4 style={{ margin: 0 }}>Playback</h4>
              <p style={{ margin: "2px 0 0" }}>Streaming preferences, prefetching, and queue behavior</p>
            </div>
          </div>

          <div style={{ display: "flex", flexDirection: "column", gap: "16px", marginTop: "16px" }}>
            {/* Default Shuffle */}
            <div className="setting-toggle-row">
              <div>
                <div className="toggle-title">Default Shuffle On</div>
                <div className="toggle-subtitle">Always start playback queues with shuffle enabled</div>
              </div>
              <button
                type="button"
                role="switch"
                aria-checked={shuffleDefault}
                aria-label="Default Shuffle On"
                className={`sonara-switch ${shuffleDefault ? "active" : ""}`}
                onClick={() => onShuffleDefaultChange(!shuffleDefault)}
              >
                <span className="sonara-switch-knob" />
              </button>
            </div>

            {/* Stream Quality */}
            <div className="setting-toggle-row">
              <div>
                <div className="toggle-title">Stream Quality Preference</div>
                <div className="toggle-subtitle">Select preferred audio bitrates and buffer profile</div>
              </div>
              <div className="segmented-control" role="radiogroup">
                <button
                  type="button"
                  role="radio"
                  aria-checked={streamQuality === "high"}
                  className={`segmented-btn ${streamQuality === "high" ? "active" : ""}`}
                  onClick={() => handleStreamQualityChange("high")}
                >
                  High (Best Audio)
                </button>
                <button
                  type="button"
                  role="radio"
                  aria-checked={streamQuality === "standard"}
                  className={`segmented-btn ${streamQuality === "standard" ? "active" : ""}`}
                  onClick={() => handleStreamQualityChange("standard")}
                >
                  Standard (Faster Buffer)
                </button>
              </div>
            </div>

            {/* Smart Prefetching */}
            <div className="setting-toggle-row">
              <div>
                <div className="toggle-title">Smart Background Prefetching</div>
                <div className="toggle-subtitle">Pre-resolve next track stream URLs to guarantee sub-300ms transitions</div>
              </div>
              <button
                type="button"
                role="switch"
                aria-checked={prefetchEnabled}
                aria-label="Smart Background Prefetching"
                className={`sonara-switch ${prefetchEnabled ? "active" : ""}`}
                onClick={() => handlePrefetchChange(!prefetchEnabled)}
              >
                <span className="sonara-switch-knob" />
              </button>
            </div>
          </div>
        </div>

        {/* SECTION 2: LIBRARY */}
        <div className="settings-card">
          <div className="card-header-block" style={{ display: "flex", alignItems: "center", gap: "10px" }}>
            <Library size={18} color="var(--accent)" />
            <div>
              <h4 style={{ margin: 0 }}>Library & Data</h4>
              <p style={{ margin: "2px 0 0" }}>Lyrics typography, query caches, and local play history</p>
            </div>
          </div>

          <div style={{ display: "flex", flexDirection: "column", gap: "18px", marginTop: "16px" }}>
            {/* Lyrics Font Size */}
            <div className="setting-toggle-row">
              <div>
                <div className="toggle-title">Lyrics Font Size</div>
                <div className="toggle-subtitle">Adjust synced karaoke lyrics typography in Now Playing</div>
              </div>
              <div className="segmented-control" role="radiogroup">
                <button
                  type="button"
                  role="radio"
                  aria-checked={lyricsFontSize === "small"}
                  className={`segmented-btn ${lyricsFontSize === "small" ? "active" : ""}`}
                  onClick={() => handleLyricsFontSizeChange("small")}
                >
                  Small
                </button>
                <button
                  type="button"
                  role="radio"
                  aria-checked={lyricsFontSize === "medium"}
                  className={`segmented-btn ${lyricsFontSize === "medium" ? "active" : ""}`}
                  onClick={() => handleLyricsFontSizeChange("medium")}
                >
                  Medium
                </button>
                <button
                  type="button"
                  role="radio"
                  aria-checked={lyricsFontSize === "large"}
                  className={`segmented-btn ${lyricsFontSize === "large" ? "active" : ""}`}
                  onClick={() => handleLyricsFontSizeChange("large")}
                >
                  Large
                </button>
              </div>
            </div>

            {/* Clear Search Cache */}
            <div className="setting-toggle-row">
              <div>
                <div className="toggle-title">Clear Search & Stream Cache</div>
                <div className="toggle-subtitle">Purge cached search results and temporary audio stream URLs</div>
              </div>
              <button
                type="button"
                className="settings-action-btn"
                onClick={handleClearSearchCache}
                disabled={clearingCache}
              >
                <Trash2 size={14} />
                <span>{clearingCache ? "Clearing..." : "Clear Search Cache"}</span>
              </button>
            </div>

            {/* Reset Listening History */}
            <div className="setting-toggle-row" style={{ alignItems: "flex-start" }}>
              <div>
                <div className="toggle-title">Reset Listening History</div>
                <div className="toggle-subtitle">
                  Erase local play counts, completion stats, and personalized recommendation signals
                </div>
              </div>
              {!showScaryResetWarning ? (
                <button
                  type="button"
                  className="settings-danger-btn"
                  onClick={() => setShowScaryResetWarning(true)}
                >
                  <AlertTriangle size={14} />
                  <span>Reset Listening History...</span>
                </button>
              ) : null}
            </div>

            {/* Scary Confirmation Box */}
            {showScaryResetWarning && (
              <div className="scary-warning-box">
                <div className="scary-warning-header">
                  <AlertTriangle size={18} />
                  <span>⚠️ Permanent Action: Reset Listening History?</span>
                </div>
                <div className="scary-warning-desc">
                  This will permanently delete your entire local play log, reset play count columns in your Songs view,
                  and clear your personalized "Made from your listening" recommendation model. This action cannot be undone.
                </div>
                <div className="scary-actions">
                  <button
                    type="button"
                    className="settings-danger-btn"
                    onClick={handleResetHistory}
                    disabled={resettingHistory}
                  >
                    {resettingHistory ? "Resetting..." : "Yes, Permanently Delete History"}
                  </button>
                  <button
                    type="button"
                    className="settings-action-btn"
                    onClick={() => setShowScaryResetWarning(false)}
                    disabled={resettingHistory}
                  >
                    Cancel
                  </button>
                </div>
              </div>
            )}
          </div>
        </div>

        {/* SECTION 3: UPDATES */}
        <div className="settings-card">
          <div className="card-header-block" style={{ display: "flex", alignItems: "center", gap: "10px" }}>
            <RefreshCw size={18} color="var(--accent)" />
            <div>
              <h4 style={{ margin: 0 }}>Updates & Engine</h4>
              <p style={{ margin: "2px 0 0" }}>Local streaming extractor binary and application release channels</p>
            </div>
          </div>

          <div className="about-stats-grid" style={{ marginTop: "16px", marginBottom: "16px" }}>
            <div className="about-stat-row">
              <span>⚡ Extractor Version</span>
              <strong>{ytdlpStatus ? ytdlpStatus.version : "Loading..."}</strong>
            </div>
            <div className="about-stat-row">
              <span>🛡️ Binary Source</span>
              <strong>
                {ytdlpStatus
                  ? ytdlpStatus.source === "bundled"
                    ? "Bundled Sidecar (SHA-256 Pinned)"
                    : "Verified Auto-Updated"
                  : "..."}
              </strong>
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

          <div style={{ display: "flex", flexDirection: "column", gap: "18px" }}>
            <div className="setting-toggle-row">
              <div>
                <div className="toggle-title">Automatic Engine Updates</div>
                <div className="toggle-subtitle">Periodically check GitHub Releases and verify SHA-256 before activating</div>
              </div>
              <button
                type="button"
                role="switch"
                aria-checked={ytdlpStatus?.auto_update_enabled ?? true}
                aria-label="Automatic Engine Updates"
                className={`sonara-switch ${ytdlpStatus?.auto_update_enabled ?? true ? "active" : ""}`}
                onClick={() => handleToggleAutoUpdate(!(ytdlpStatus?.auto_update_enabled ?? true))}
              >
                <span className="sonara-switch-knob" />
              </button>
            </div>

            <div>
              <button
                type="button"
                className="settings-action-btn"
                onClick={handleCheckYtdlp}
                disabled={checkingYtdlp}
              >
                <RefreshCw size={14} className={checkingYtdlp ? "spin" : ""} />
                <span>{checkingYtdlp ? "Checking Releases..." : "Check for Engine Updates"}</span>
              </button>
            </div>

            <div style={{ paddingTop: "14px", borderTop: "1px solid var(--border)" }}>
              <div className="setting-toggle-row">
                <div>
                  <div className="toggle-title">App Auto-Check on Startup</div>
                  <div className="toggle-subtitle">Non-blockingly notify when a new desktop release is published</div>
                </div>
                <button
                  type="button"
                  role="switch"
                  aria-checked={appAutoUpdate}
                  aria-label="App Auto-Check on Startup"
                  className={`sonara-switch ${appAutoUpdate ? "active" : ""}`}
                  onClick={() => handleToggleAppAutoUpdate(!appAutoUpdate)}
                >
                  <span className="sonara-switch-knob" />
                </button>
              </div>

              <div style={{ marginTop: "14px" }}>
                <button
                  type="button"
                  className="settings-action-btn"
                  onClick={handleCheckAppUpdate}
                  disabled={checkingApp}
                >
                  <ArrowUpCircle size={14} className={checkingApp ? "spin" : ""} />
                  <span>{checkingApp ? "Checking App Releases..." : "Check for App Updates"}</span>
                </button>
              </div>
            </div>
          </div>
        </div>

        {/* SECTION 4: APPEARANCE */}
        <div className="settings-card">
          <div className="card-header-block" style={{ display: "flex", alignItems: "center", gap: "10px" }}>
            <Palette size={18} color="var(--accent)" />
            <div>
              <h4 style={{ margin: 0 }}>Appearance</h4>
              <p style={{ margin: "2px 0 0" }}>Theme mode and visual accent customization</p>
            </div>
          </div>

          <div style={{ display: "flex", flexDirection: "column", gap: "18px", marginTop: "16px" }}>
            {/* Theme Selector */}
            <div className="setting-toggle-row">
              <div>
                <div className="toggle-title">Interface Theme</div>
                <div className="toggle-subtitle">Toggle between Dark and Light mode</div>
              </div>
              <div className="segmented-control" role="radiogroup">
                <button
                  type="button"
                  role="radio"
                  aria-checked={theme === "dark"}
                  className={`segmented-btn ${theme === "dark" ? "active" : ""}`}
                  onClick={() => onThemeChange("dark")}
                >
                  <Moon size={14} />
                  <span>Dark</span>
                </button>
                <button
                  type="button"
                  role="radio"
                  aria-checked={theme === "light"}
                  className={`segmented-btn ${theme === "light" ? "active" : ""}`}
                  onClick={() => onThemeChange("light")}
                >
                  <Sun size={14} />
                  <span>Light</span>
                </button>
              </div>
            </div>

            {/* Accent Color Picker */}
            <div className="setting-toggle-row">
              <div>
                <div className="toggle-title">Accent Color</div>
                <div className="toggle-subtitle">Choose a highlight color persisted across sessions</div>
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
                      onClick={() => handleSelectAccent(c.id)}
                      title={c.name}
                      aria-label={`Select ${c.name} accent`}
                    />
                  );
                })}
              </div>
            </div>
          </div>
        </div>

        {/* SECTION 5: ABOUT & NETWORK DISCLOSURE */}
        <div className="settings-card about-card">
          <div className="card-header-block" style={{ display: "flex", alignItems: "center", gap: "10px" }}>
            <Info size={18} color="var(--accent)" />
            <div>
              <h4 style={{ margin: 0 }}>About Sonara</h4>
              <p style={{ margin: "2px 0 0" }}>Project details, license, and external network disclosures</p>
            </div>
          </div>

          <div className="about-branding" style={{ marginTop: "16px" }}>
            <div className="about-logo-circle">
              <Radio size={24} />
            </div>
            <div className="about-info-text">
              <h5>Sonara Stream</h5>
              <p>
                A high-speed, lightweight desktop music player engineered with Rust, Tauri 2, and React. Focused on zero
                telemetry, local privacy, and direct audio streaming without permanent disk storage.
              </p>
              <span className="version-tag">v0.4.1 (Stream Edition)</span>
            </div>
          </div>

          <div className="about-stats-grid" style={{ marginTop: "16px" }}>
            <div className="about-stat-row">
              <span>🎵 License</span>
              <strong>MIT License</strong>
            </div>
            <div className="about-stat-row">
              <span>📦 Source Code</span>
              <a
                href="https://github.com/saeed/sonara"
                target="_blank"
                rel="noreferrer"
                style={{ color: "var(--accent)", display: "inline-flex", alignItems: "center", gap: "4px" }}
              >
                <span>GitHub Repository</span>
                <ExternalLink size={12} />
              </a>
            </div>
            <div className="about-stat-row">
              <span>🛡️ Telemetry & Tracking</span>
              <strong style={{ color: "#1ed760", display: "inline-flex", alignItems: "center", gap: "4px" }}>
                <ShieldCheck size={14} />
                Zero Telemetry
              </strong>
            </div>
          </div>

          {/* Transparent External Network Disclosure Table */}
          <div style={{ marginTop: "20px", paddingTop: "14px", borderTop: "1px solid var(--border)" }}>
            <h5 style={{ fontSize: "13px", color: "#fff", margin: "0 0 6px 0", fontWeight: 600 }}>
              External Network Requests Disclosure
            </h5>
            <p style={{ fontSize: "11px", color: "var(--text-muted)", margin: "0 0 10px 0", lineHeight: 1.4 }}>
              Sonara Stream only contacts external services when strictly necessary to fulfill your direct search,
              playback, and update actions. No user profile or listening data is ever transmitted.
            </p>

            <table className="disclosure-table">
              <thead>
                <tr>
                  <th>Destination Service</th>
                  <th>Purpose</th>
                  <th>Frequency / Trigger</th>
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td><strong>YouTube / Googlevideo</strong></td>
                  <td>Search metadata & direct HTTPS audio streaming via local yt-dlp sidecar</td>
                  <td>Only on user search & track playback</td>
                </tr>
                <tr>
                  <td><strong>LRCLIB (lrclib.net)</strong></td>
                  <td>Synchronized karaoke and plain-text lyrics retrieval</td>
                  <td>On track playback (7-day local disk cache)</td>
                </tr>
                <tr>
                  <td><strong>GitHub Releases</strong></td>
                  <td>Sidecar extractor updates and application release checks</td>
                  <td>Manual check or background on launch</td>
                </tr>
                <tr>
                  <td><strong>Spotify oEmbed</strong></td>
                  <td>Open metadata resolution when pasting Spotify links</td>
                  <td>Only when a Spotify URL is entered</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        {/* DEVELOPER & DIAGNOSTICS */}
        {onPerfOverlayChange && (
          <div className="settings-card">
            <div className="card-header-block" style={{ display: "flex", alignItems: "center", gap: "10px" }}>
              <Sliders size={18} color="var(--accent)" />
              <div>
                <h4 style={{ margin: 0 }}>Developer & Diagnostics</h4>
                <p style={{ margin: "2px 0 0" }}>Live performance budget monitoring (B1–B11)</p>
              </div>
            </div>

            <div style={{ marginTop: "16px" }}>
              <div className="setting-toggle-row">
                <div>
                  <div className="toggle-title">Performance Budgets Overlay (B1–B11)</div>
                  <div className="toggle-subtitle">Display a live HUD showing cold start, keystroke latency, playback start times, and FPS</div>
                </div>
                <button
                  type="button"
                  role="switch"
                  aria-checked={perfOverlay ?? false}
                  aria-label="Performance Budgets Overlay"
                  className={`sonara-switch ${perfOverlay ? "active" : ""}`}
                  onClick={() => onPerfOverlayChange(!perfOverlay)}
                >
                  <span className="sonara-switch-knob" />
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    );
  }
);
