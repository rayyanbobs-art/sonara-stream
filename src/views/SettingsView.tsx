import React from "react";
import { Radio, Sparkles } from "lucide-react";
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
              Sonara is a lightweight desktop music player focused on speed, simplicity, and instant in-memory streaming with zero local file downloads.
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
            <strong>Native WebView2 Stream Buffer (0 Disk Writes)</strong>
          </div>
          <div className="about-stat-row">
            <span>🌐 Sources</span>
            <strong>YouTube & Spotify (Bot Bypass Enabled)</strong>
          </div>
        </div>
      </div>
    </div>
  );
};
