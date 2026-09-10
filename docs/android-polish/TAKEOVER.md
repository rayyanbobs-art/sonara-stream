# Sonara Stream — Master Takeover Audit & Stability Analysis

**Document Version:** 2.0.0  
**Audit Date:** 2026-09-11  
**Target Branches:** `main` (base), `android-polish` (active feature branch)  
**Latest Baseline Commit:** `3a6a9e4`  

---

## 1. Access & Environment Verification

| Resource | Capability Status | Notes |
|---|---|---|
| **Repository Access** | **VERIFIED** | Full read, edit, git branch, and command execution on `d:\software\myst\sonara-stream`. |
| **Mobbin Live Access** | **RESTRICTED / FALLBACK** | `Johnson-f/OpenMobbin` is not a valid public GitHub repo (HTTP 404). Direct Mobbin search requires authenticated paid seat. **Fallback verified:** 5 authentic Mobbin & Spotify design screenshots provided directly by the user in `.user_uploaded/` (`media_1788992400857` through `media_1788992684380`) and official Figma Spotify Redesign Community files. |
| **Desktop Environment** | **VERIFIED** | Local Windows machine with Bun 1.4.2, Rust 1.84+ (cargo 1.84.0), Node 22+. `bun run test` (TypeScript), `bun run build` (Vite), and `cargo test` (Rust unit tests) pass 100%. |
| **Android Environment** | **UNVERIFIED LOCALLY / CI VERIFIED** | Android SDK/NDK (`adb`, `gradlew`) are not in local Windows PATH. Full Android APK compilation and packaging is managed via `.github/workflows/android.yml`. All Kotlin and manifest changes are audited statically and patched via `scripts/configure-android.py`. |

---

## 2. In-Depth Android Crash Root-Cause Analysis

The user reported: *"the app also crashes alot like alot when the android build of it like really alot"*.  
Our technical audit revealed **5 distinct crash vectors** across the Android lifecycle:

### Vector 1: Foreground Service Start on Cold Launch (Android 12/13/14+ Fatal Crash)
- **File**: `src-tauri/android-patches/MainActivity.kt:35-40`
- **Root Cause**: `MainActivity.onCreate()` immediately invokes `ContextCompat.startForegroundService(this, serviceIntent)` before any audio has started playing.  
  - On **Android 12+ (API 31+)**, calling `startForegroundService` when the activity is transitioning or when media is not playing triggers `ForegroundServiceStartNotAllowedException`.
  - On **Android 14 (API 34)**, `FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK` requires an active playback session.
  - If `startForeground()` is delayed or caught without notification attachment, the OS terminates the app with `android.app.RemoteServiceException$ForegroundServiceDidNotStartInTimeException`.
- **Resolution Plan**: Remove immediate service startup in `MainActivity.onCreate()`. Only start the service when the user actually initiates playback (`onPlay` / `updateTrack`).

### Vector 2: Unhandled WebView Chromium Render Process Crash (`onRenderProcessGone`)
- **File**: `src-tauri/android-patches/MainActivity.kt` (Missing `WebViewClient.onRenderProcessGone`)
- **Root Cause**: By default on Android, if the Chromium rendering process dies (e.g. from GPU memory exhaustion or out-of-memory), Android kills the entire host app with `SIGKILL`.
- **Resolution Plan**: Register a custom `WebViewClient` in `MainActivity.kt` with `onRenderProcessGone(view, detail): Boolean` returning `true` to cleanly recover rather than crash.

### Vector 3: Background Timer Reflection Hack on WebView
- **File**: `src-tauri/android-patches/MainActivity.kt:116-122` & `169-178`
- **Root Cause**: A repeating 1500ms Handler invokes private reflection `View.onWindowVisibilityChanged(View.VISIBLE)` while the app is in the background. In modern Android WebViews (`libmonochrome.so`), forcing a hidden/detached surface to report `VISIBLE` causes a fatal C++ assertion failure / `SIGSEGV` in the Chromium compositor thread.
- **Resolution Plan**: Eliminate the reflection loop completely. Background playback is preserved by `settings.mediaPlaybackRequiresUserGesture = false`, `wakeLock`, and `wifiLock`.

### Vector 4: GPU Memory Exhaustion from Heavy Full-Screen CSS Blur (`blur-[60px]`)
- **File**: `src/features/player/components/OverlayPlayer.tsx:109-115`
- **Root Cause**: An `<img>` with `blur-[60px] scale-110 saturate-150` covering the entire screen forces Android's mobile GPU (Adreno / Mali) to allocate massive convolution textures on every render tick, causing `RenderProcessGone` and OOM.
- **Resolution Plan**: Replace the expensive 60px full-screen blur with a lightweight CSS ambient radial gradient or a downscaled 32x32px texture canvas with a dark overlay (`#09090b/85`).

### Vector 5: Local Audio Range Request NuPlayer Crash
- **File**: `src/features/player/components/AudioPlayer.tsx:295-340`
- **Root Cause**: When playing local files via `convertFileSrc`, Android's native `NuPlayer` requests HTTP 206 Range headers which Tauri's custom asset protocol does not fully support, causing native media decoder aborts.
- **Status**: Mitigated in v0.6.5 with `read_audio_file` binary IPC + in-memory `Blob` URL. Requires safety checks to ensure large audio files don't exceed heap memory.

---

## 3. Architecture & Implementation Matrix

| Component / Layer | Desktop Implementation | Android Implementation | Shared / Protected Boundary |
|---|---|---|---|
| **App Shell & Windows** | Tauri Window with native titlebar drag regions | Fullscreen immersive window, gesture navigation bars handled | `src/routes/__root.tsx` provides shared layout |
| **Primary Navigation** | Floating Sidebar (`AppSideBar.tsx`) with Library & Playlists | Fixed Bottom Bar (`BottomNavBar.tsx`) with active indicator pills | Navigation uses `@tanstack/react-router`. Gated by `md:hidden` / `hidden md:block`. |
| **Audio Engine** | `<audio>` tag + HTML5 MediaSession + SMTC (`souvlaki`) | `<audio>` tag + HTML5 MediaSession + `MediaPlaybackService.kt` bridge | `src/features/player/components/AudioPlayer.tsx` and `useMediaSession.ts` |
| **State Management** | Zustand (`app-store.ts`, `playbackSlice.ts`, `settingSlice.ts`) | Same authoritative Zustand store | **PROTECTED**: No modifications to core queue/playback state logic without approval |
| **Local Database** | SQLite (`rusqlite`) in app data directory | SQLite (`rusqlite`) in app data dir with in-memory fallback | Bundled migrations in `src-tauri/src/db/` |
| **Online Streaming** | YouTube search + yt-dlp sidecar binary + direct stream | Direct reqwest HTTP via InnerTube API (no sidecar) | Stream extraction in `src-tauri/src/youtube.rs` |
| **Theme & Accent** | 5 Color Palettes (Purple, Red, Green, Blue, Yellow `#f3bc16`) | Same 5 Palettes, Dark mode `#09090b` default | Theme variables in `App.css` |

---

## 4. Design Direction & Figma Reference Mapping

The user provided the Figma reference: [Spotify Redesign Community](https://www.figma.com/design/qy2NqKYdjy86GbeWXVdhfK/Spotify-Redesign--Community-?node-id=3-2&t=EXbeoFLUyTVXAMvx-1) and 5 Mobbin screenshots.

### Harmonized Design System (Sonara Identity + Figma Polish):
1. **Color Palette**:
   - Primary Surface: Deep OLED Black `#09090b`
   - Secondary Surface / Cards: Subtle Translucent Surface `oklch(0.2 0.015 240 / 60%)`
   - Primary Accent: Sonara Golden Yellow `#f3bc16` (or user's selected theme color)
   - Typography: `Space Grotesk` (headings) + `Inter` (body/metadata)
2. **Home Screen (Figma Pattern)**:
   - **Header Category Chips**: `All`, `Music`, `Favorites`, `Downloads`
   - **Quick-Access Grid (2x3 on mobile, 4x2 on desktop)**: 6 compact high-frequency items with square thumbnail, bold title, and instant play trigger
   - **Artwork-Led Carousels**: "Continue Listening", "Most Played", "Recently Added" with clean cards, rounded corners (`rounded-2xl`), bold titles, and subtle artist labels
3. **Now Playing Screen (Mobbin Ambient Pattern)**:
   - Dynamic ambient backdrop (safe gradient/mesh, no GPU-crashing full-screen blur)
   - Centered large hero album artwork with 24px border radius and deep shadow
   - Track title + artist with inline favorite heart button
   - Scrubber with precise seekbar and duration labels
   - Full thumb-reachable transport controls (48dp minimum touch targets, central 64dp play button)
4. **Persistent Mini-Player**:
   - Floating pill positioned directly above bottom navigation with safe-area spacing
   - High-contrast artwork, legible text marquee, responsive play/pause & skip controls
   - Integrated 2px progress bar along the bottom edge
5. **Search & Browse Screen (Figma Browse Grid)**:
   - Search bar pill: "What do you want to listen to?"
   - Colorful curated browse cards: *Pop, Rock, Hip-Hop, Acoustic, Downloads, Favorites, Playlists*

---

## 5. Protected Boundaries & Change Safety Rules

1. **NO Desktop Regressions**: Desktop continues to use full floating sidebar, multi-column tables, keyboard shortcuts, and SMTC controls.
2. **NO Playback Logic Rewrites**: Zustand playback state machine, queue index tracking, and favorite persistence remain authoritative.
3. **NO Lockfile or Framework Churn**: Maintain React 19, Tailwind CSS v4, Tauri 2, and Bun.
4. **Tested Incrementally**: Each slice must pass `bun run test`, `bun run build`, and `cargo test`.
