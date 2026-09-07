import { invoke } from "@tauri-apps/api/core";
import { check, Update } from "@tauri-apps/plugin-updater";

export interface UnifiedAppUpdate {
  available: boolean;
  version: string;
  currentVersion: string;
  downloadUrl?: string;
  body?: string;
  isAndroid?: boolean;
  rawDesktopUpdate?: Update;
}

export function isAndroidPlatform(): boolean {
  if (typeof navigator === "undefined") return false;
  return /android/i.test(navigator.userAgent);
}

export function openExternalUrl(url: string): void {
  if (typeof window !== "undefined" && typeof (window as any).AndroidNative?.openUrl === "function") {
    (window as any).AndroidNative.openUrl(url);
    return;
  }
  if (typeof window !== "undefined") {
    window.open(url, "_blank");
  }
}

export async function checkUnifiedAppUpdate(): Promise<UnifiedAppUpdate> {
  const isAndroid = isAndroidPlatform();

  if (isAndroid) {
    const res = await invoke<{
      available: boolean;
      latest_version: string;
      current_version: string;
      download_url: string;
      release_notes: string;
    }>("check_app_update");

    return {
      available: res.available,
      version: res.latest_version,
      currentVersion: res.current_version,
      downloadUrl: res.download_url,
      body: res.release_notes,
      isAndroid: true,
    };
  }

  // Desktop flow: use Tauri plugin-updater directly
  const update = await check();
  if (update) {
    return {
      available: update.available,
      version: update.version,
      currentVersion: update.currentVersion,
      body: update.body,
      isAndroid: false,
      rawDesktopUpdate: update,
    };
  }

  return {
    available: false,
    version: "",
    currentVersion: "",
    isAndroid: false,
  };
}
