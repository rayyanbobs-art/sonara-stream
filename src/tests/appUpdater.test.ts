import { describe, it, expect, vi, beforeEach } from "vitest";
import { checkUnifiedAppUpdate, openExternalUrl, isAndroidPlatform } from "../utils/appUpdater";

const mockInvoke = vi.fn();
const mockCheck = vi.fn();

vi.mock("@tauri-apps/api/core", () => ({
  invoke: (...args: any[]) => mockInvoke(...args),
}));

vi.mock("@tauri-apps/plugin-updater", () => ({
  check: () => mockCheck(),
}));

describe("appUpdater", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("identifies non-android by default in test environment", () => {
    expect(isAndroidPlatform()).toBe(false);
  });

  it("checks desktop updater when not on Android", async () => {
    mockCheck.mockResolvedValueOnce({
      available: true,
      version: "0.5.1",
      currentVersion: "0.5.0",
      body: "New features added",
    });

    const res = await checkUnifiedAppUpdate();
    expect(res.available).toBe(true);
    expect(res.version).toBe("0.5.1");
    expect(res.isAndroid).toBe(false);
  });

  it("invokes Android update command when userAgent contains Android", async () => {
    const originalUserAgent = navigator.userAgent;
    Object.defineProperty(navigator, "userAgent", {
      value: "Mozilla/5.0 (Linux; U; Android 9; LG G Pad 5 10.1 FHD)",
      configurable: true,
    });

    expect(isAndroidPlatform()).toBe(true);

    mockInvoke.mockResolvedValueOnce({
      available: true,
      latest_version: "0.5.1",
      current_version: "0.5.0",
      download_url: "https://example.com/SonaraStream-Android.apk",
      release_notes: "Android bugfixes",
    });

    const res = await checkUnifiedAppUpdate();
    expect(res.available).toBe(true);
    expect(res.version).toBe("0.5.1");
    expect(res.downloadUrl).toBe("https://example.com/SonaraStream-Android.apk");
    expect(res.isAndroid).toBe(true);

    Object.defineProperty(navigator, "userAgent", {
      value: originalUserAgent,
      configurable: true,
    });
  });

  it("calls AndroidNative.openUrl if available on window", () => {
    const mockOpenUrl = vi.fn();
    (window as any).AndroidNative = { openUrl: mockOpenUrl };

    openExternalUrl("https://example.com/test.apk");
    expect(mockOpenUrl).toHaveBeenCalledWith("https://example.com/test.apk");

    delete (window as any).AndroidNative;
  });
});
