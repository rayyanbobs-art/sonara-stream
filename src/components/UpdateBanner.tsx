import React, { useState } from "react";
import { relaunch } from "@tauri-apps/plugin-process";
import { Download, RefreshCw, X, AlertCircle } from "lucide-react";
import { UnifiedAppUpdate, openExternalUrl } from "../utils/appUpdater";

interface UpdateBannerProps {
  update: UnifiedAppUpdate;
  isPlaying: boolean;
  onDismiss: () => void;
}

export const UpdateBanner: React.FC<UpdateBannerProps> = ({
  update,
  isPlaying,
  onDismiss,
}) => {
  const [downloading, setDownloading] = useState(false);
  const [progress, setProgress] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  const handleUpdate = async () => {
    // Android path: open direct APK download link via AndroidNative or system browser
    if (update.isAndroid || !update.rawDesktopUpdate) {
      const url =
        update.downloadUrl ||
        "https://github.com/rayyanbobs-art/sonara-stream/releases/latest/download/SonaraStream-Android.apk";
      openExternalUrl(url);
      onDismiss();
      return;
    }

    if (isPlaying) {
      const proceed = window.confirm(
        "Audio is currently playing. Updating and restarting will interrupt playback. Continue?"
      );
      if (!proceed) return;
    }

    setDownloading(true);
    setError(null);

    try {
      let downloadedBytes = 0;
      let totalBytes = 0;

      await update.rawDesktopUpdate.downloadAndInstall((event) => {
        if (event.event === "Started") {
          totalBytes = event.data.contentLength || 0;
        } else if (event.event === "Progress") {
          downloadedBytes += event.data.chunkLength;
          if (totalBytes > 0) {
            setProgress(Math.round((downloadedBytes / totalBytes) * 100));
          }
        } else if (event.event === "Finished") {
          setProgress(100);
        }
      });

      await relaunch();
    } catch (err: any) {
      console.error("App update failed:", err);
      setError(err?.message || "Failed to download update.");
      setDownloading(false);
    }
  };

  return (
    <aside
      className="update-banner"
      role="status"
      aria-live="polite"
      style={{
        position: "fixed",
        bottom: "96px",
        right: "24px",
        zIndex: 100,
        background: "var(--bg-card)",
        border: "1px solid var(--accent)",
        borderRadius: "12px",
        padding: "14px 18px",
        maxWidth: "400px",
        boxShadow: "0 8px 30px rgba(0, 0, 0, 0.5)",
        backdropFilter: "blur(12px)",
      }}
    >
      <div style={{ display: "flex", alignItems: "flex-start", justifyContent: "space-between", gap: "12px" }}>
        <div>
          <div style={{ display: "flex", alignItems: "center", gap: "8px", fontWeight: 600, color: "var(--text-main)", fontSize: "14px" }}>
            <Download size={16} color="var(--accent)" />
            <span>Update Available: v{update.version}</span>
          </div>
          {update.body && (
            <p
              style={{
                fontSize: "12px",
                color: "var(--text-muted)",
                margin: "6px 0 10px 0",
                maxHeight: "60px",
                overflow: "hidden",
                textOverflow: "ellipsis",
              }}
            >
              {update.body}
            </p>
          )}
          {error && (
            <p style={{ fontSize: "12px", color: "var(--accent-danger, #ef4444)", margin: "4px 0" }}>
              <AlertCircle size={12} style={{ display: "inline", marginRight: "4px" }} />
              {error}
            </p>
          )}
        </div>
        <button
          type="button"
          onClick={onDismiss}
          aria-label="Dismiss update notification"
          style={{ background: "none", border: "none", color: "var(--text-muted)", cursor: "pointer", padding: "2px" }}
        >
          <X size={16} />
        </button>
      </div>

      <div style={{ display: "flex", alignItems: "center", gap: "10px", marginTop: "8px" }}>
        <button
          type="button"
          className="search-btn"
          onClick={handleUpdate}
          disabled={downloading}
          style={{
            fontSize: "12px",
            padding: "6px 14px",
            display: "inline-flex",
            alignItems: "center",
            gap: "6px",
            cursor: downloading ? "not-allowed" : "pointer",
          }}
        >
          {downloading ? (
            <>
              <RefreshCw size={13} className="spin" />
              <span>{progress !== null ? `Downloading (${progress}%)...` : "Downloading..."}</span>
            </>
          ) : (
            <>
              <Download size={13} />
              <span>{update.isAndroid ? "Download APK" : "Update & Restart"}</span>
            </>
          )}
        </button>
        <button
          type="button"
          onClick={onDismiss}
          style={{
            background: "none",
            border: "1px solid var(--border)",
            borderRadius: "6px",
            color: "var(--text-muted)",
            fontSize: "12px",
            padding: "5px 12px",
            cursor: "pointer",
          }}
        >
          Later
        </button>
      </div>
    </aside>
  );
};
