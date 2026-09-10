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

---

## 5. Verification Commands
- `bun run test` (TypeScript typecheck): Pass (0 errors)
- `bun run build` (Vite production build): Pass (23.26s)
- `cargo test` (Rust backend unit tests): Pass (29 passed, 0 failed)
- Android CI build: `.github/workflows/android.yml`

---

## 6. The Exact Next Task
**Slice 2: Home Screen Figma Restyle (Desktop & Android)**:
- Add top category filter chips (`All`, `Music`, `Favorites`, `Downloads`).
- Build quick-access grid (2-column on mobile, 4-column on desktop) with square album art, bold titles, and instant play trigger matching Figma Spotify Redesign.
- Modernize section headers and card layouts.
- Files to touch: `src/routes/index.tsx`, `src/features/home/components/SongCard.tsx`.
- Desktop impact: Enhances desktop with 4-column quick grid while giving mobile responsive 2-column grid. Zero breaking changes to playback.
