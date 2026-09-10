# Sonara Stream — Master Polish & Stability Handoff

**Branch:** `android-polish`  
**Last Updated:** 2026-09-11  

---

## 1. Architecture & Protected Boundaries
- **Framework:** Tauri 2 (Rust + Android WebView + React 19 + Vite + Tailwind CSS v4 + Zustand).
- **Protected Boundaries:**
  - `src/store/playbackSlice.ts`, `app-store.ts`: Authoritative queue and playback state logic.
  - `src-tauri/src/commands/`: All Rust IPC commands (`read_audio_file`, `get_stream_url`, download engine).
  - Desktop UI: Windows SMTC controls, floating sidebar (`AppSideBar.tsx`), full multi-column tables.

---

## 2. Approved Design Decisions & References
- **Figma Reference:** [Spotify Redesign Community](https://www.figma.com/design/qy2NqKYdjy86GbeWXVdhfK/Spotify-Redesign--Community-?node-id=3-2&t=EXbeoFLUyTVXAMvx-1) (dev mode: `https://www.figma.com/design/qy2NqKYdjy86GbeWXVdhfK/Spotify-Redesign--Community-?node-id=3-2&m=dev&t=EXbeoFLUyTVXAMvx-1`).
- **User Reference Screenshots:** Uploaded in `.user_uploaded/` (`media_1788992400857` - `media_1788992684380`).
- **Brand Identity:** Deep OLED Black `#09090b` + Sonara Golden Yellow accent (`#f3bc16`) with high-contrast readable typography.

---

## 3. Investigated Known Problems (Android Crashes)
1. **Cold-Launch Foreground Service Crash:** `MainActivity.kt` called `startForegroundService()` on launch before playback started (`ForegroundServiceStartNotAllowedException` / `RemoteServiceException` on Android 12-14).
2. **Chromium Compositor Reflection Crash:** `keepAliveRunnable` invoking `View.onWindowVisibilityChanged(VISIBLE)` via reflection in background causing `SIGSEGV` in `libmonochrome.so`.
3. **Missing `onRenderProcessGone`:** Any WebView render process termination results in immediate OS kill of host app.
4. **Heavy Full-Screen 60px CSS Blur:** Causes GPU texture allocation exhaustion on mobile Adreno/Mali chips.

---

## 5. Verification Commands
- `bun run test` (TypeScript typecheck): Pass
- `bun run build` (Vite production build): Pass
- `cargo test` (Rust backend unit tests): Pass (29 passed, 0 failed)
- Android CI build: `.github/workflows/android.yml`

---

## 4. Completed Slices
- **Slice 1: Android Crash Elimination & Native Hardening (COMPLETED 2026-09-11)**
  - Patched `MainActivity.kt`: Deferred `MediaPlaybackService` activation until active playback begins, eliminating Android 12-14 foreground service crash; eliminated reflection keep-alive loop.
  - Patched `MediaPlaybackService.kt`: Reduced bitmap scale to 192px max; cleanly detached foreground service when paused.
  - Patched `OverlayPlayer.tsx`: Replaced 60px full-screen blur with lightweight ambient radial gradient.
  - Critic Review & Verification passed (Score: 9.25/10).

- **Slice 2: Home Screen Figma Restyle (COMPLETED 2026-09-11)**
  - Added top category filter chips (`All`, `Music`, `Favorites`, `Stream`).
  - Added `QuickAccessCard.tsx`: 2-column (mobile) and 4-column (desktop) compact cards with square album art, bold title, and instant play trigger.
  - Added `ArtworkTrackCard.tsx`: Vertical cards with prominent square artwork, floating circular play buttons on desktop hover, and touch carousels on mobile.
  - Critic Review & Verification passed (Score: 9.47/10).

---

## 5. Verification Commands
- `bun run test` (TypeScript typecheck): Pass (0 errors)
- `bun run build` (Vite production build): Pass (3.39s)
- `cargo test` (Rust backend unit tests): Pass (29 passed, 0 failed)
- Android CI build: `.github/workflows/android.yml`

---

- **Slice 3: Search / Browse Screen Figma Overhaul (COMPLETED 2026-09-11)**
  - Added `BrowseCategoryCard.tsx`: 12 vibrant genre/category cards matching Figma's "Browse all" grid.
  - Modernized search input bar into a high-contrast pill with placeholder *"What do you want to listen to?"*.
  - Updated `BottomNavBar.tsx` to display Search tab with `Search` icon.
  - Critic Review & Verification passed (Score: 9.53/10).

---

## 5. Verification Commands
- `bun run test` (TypeScript typecheck): Pass (0 errors)
- `bun run build` (Vite production build): Pass (3.48s)
- `cargo test` (Rust backend unit tests): Pass (29 passed, 0 failed)
- Android CI build: `.github/workflows/android.yml`

---

- **Slice 4: Now Playing & Mini-Player Refinement (COMPLETED 2026-09-11)**
  - Floating mini-player elevated above mobile nav bar with 12px rounded artwork, marquee text, favorite toggle, and integrated 2px progress bar.
  - Full-screen expanded player aligned with Figma Spotify Redesign: context title ("PLAYING FROM LIBRARY"), left-aligned song/artist with right-aligned favorite heart.
  - Precision timeline scrubber with tabular monospace timestamps.
  - Thumb-reachable 5-button transport controls with 64px central circular Play/Pause button in Sonara golden yellow (`#f3bc16`).
  - Bottom utility row: Online track download button, lyrics toggle pill, and playback queue sheet trigger.
  - Full desktop playback controls and dual-column view preserved without regressions.
  - Critic Review & Verification passed (Score: 9.63/10).

---

## 5. Verification Baseline
- `bun run test` (TypeScript typecheck): Pass (0 errors)
- `bun run build` (Vite production build): Pass (3.50s)
- `cargo test --lib` (Rust backend unit tests): Pass (29 passed, 0 failed, 3 ignored)
- Android CI build: `.github/workflows/android.yml`

---

## 6. The Exact Next Task
**Slice 5: Library, Playlists & Navigation Polish (Mobile Tab 3 & 4)**:
- Polish `src/routes/songs/index.tsx` and `src/routes/favorites/index.tsx` for mobile touch interaction (48dp list item height, swipe actions, compact album art).
- Refine playlist header and track list views to match modern streaming aesthetics.
- Verify safe-area insets and scroll clearance above mini-player.

