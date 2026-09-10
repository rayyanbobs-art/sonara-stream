# Android Polish — Takeover Audit

**Date:** 2026-09-10  
**Branch:** `android-polish` (off `main` at `d9ea07a`)  
**Base version:** v0.6.5

---

## 1. Repository State

- **Branch:** `main` — clean, no uncommitted changes
- **Latest commit:** `d9ea07a fix: eliminate Android crash when playing downloaded songs (v0.6.5)`
- **New branch:** `android-polish` created for all UI polish work

---

## 2. Architecture Overview

| Layer | Technology |
|-------|-----------|
| Framework | Tauri 2 (Rust + WebView) |
| Frontend | React 18 + TypeScript |
| Bundler | Vite |
| CSS | Tailwind CSS v4 + shadcn/ui |
| State | Zustand (persisted settings slice) |
| Data fetching | TanStack React Query |
| Routing | TanStack React Router (file-based) |
| Database | SQLite via rusqlite (bundled) |
| Desktop | Windows (SMTC via souvlaki) |
| Android | WebView-based Tauri + Kotlin patches |
| Package manager | Bun 1.4.2 |

---

## 3. Component Inventory

### Navigation
| Component | Desktop | Mobile |
|-----------|---------|--------|
| `AppSideBar.tsx` | Floating sidebar with Library + Playlists | Hidden (CSS `md:` breakpoint) |
| `BottomNavBar.tsx` | Hidden | Fixed bottom nav: Home, Online, Songs, Favorites, Settings |
| `AppHeader.tsx` | Fixed header with back button, SearchDialog, ImportButton | Same (responsive padding) |

### Player
| Component | Purpose |
|-----------|---------|
| `AppFooter.tsx` | Renders `AudioPlayer` when song is active, or empty state placeholder (desktop only) |
| `AudioPlayer.tsx` | Mobile mini-player (`<md`) + Desktop full-width footer (`>=md`). Contains `<audio>` element, blob URL playback for local files, stream URL for online. |
| `OverlayPlayer.tsx` | Full-screen expanded Now Playing. Desktop: dual-column (player + lyrics). Mobile: tab-switched (Track / Lyrics). |

### Routes (Pages)
| Route | Page |
|-------|------|
| `/` | Home — Continue Listening, Browse Library stats, Most Played, Recently Added |
| `/stream` | Online Music & Streams — YouTube search + Spotify link paste |
| `/songs` | All songs table |
| `/artists` | Artists grid |
| `/artists/$id` | Artist detail |
| `/albums` | Albums grid |
| `/albums/$id` | Album detail |
| `/playlists/$id` | Playlist detail |
| `/favorites` | Favorited songs |
| `/settings` | Settings: Theme, Accent Color, Playback, Library, About |

### Features
| Feature | Components | Android Status |
|---------|------------|---------------|
| **Player** | AudioPlayer, OverlayPlayer | ✅ Working (blob URLs for local, stream for online) |
| **Online/Stream** | OnlineSearchSection, OnlineTrackCard | ✅ Working |
| **Download** | useDownloadTrack hook, download button in OverlayPlayer | ✅ Working (v0.6.4 overhaul) |
| **Favorites** | useToggleFavoriteMutation, Heart button | ✅ Working (v0.6.3 fix) |
| **Lyrics** | LyricsSection, SongLyrics, RenderLyricsView | ✅ Working |
| **Queue** | PlaybackQueue, QueueItem | ✅ Working |
| **Search** | SearchDialog (modal), SearchResultItem | ✅ Working |
| **Import** | ImportButton, useImportFilesMutation | ⚠️ Desktop-focused (folder picker) |
| **Settings** | Theme, AccentColor, Playback, LibraryManagement | ✅ Working |

---

## 4. Platform-Specific Code Map

### Android-Only (Kotlin)
- `src-tauri/android-patches/MainActivity.kt` — WebView setup, JS bridges (`AndroidNative`, `AndroidMedia`), immersive mode, keep-alive handler
- `src-tauri/android-patches/MediaPlaybackService.kt` — Foreground notification service, MediaSessionCompat, artwork loading, media controls

### Frontend Android Awareness
- `useMediaSession.ts` — Lines 162-200: Android native bridge listeners. Lines 228-260: AndroidMedia.updateMetadata/updatePlaybackState calls
- `AudioPlayer.tsx` — Line 295: Binary IPC for local files (Android WebView workaround)
- `updater.ts` — Line 16: Skips auto-update on Android/iOS

### CSS Responsive Breakpoints
- **Mobile breakpoint:** `md:` (768px) — consistently used across all components
- `use-mobile.ts` hook: `MOBILE_BREAKPOINT = 768`
- All mobile vs desktop splits use Tailwind `md:hidden` / `hidden md:block` pattern

---

## 5. Current Android UX Assessment

### What Exists (Functional)
1. **Bottom nav bar** — 5 tabs (Home, Online, Songs, Favorites, Settings)
2. **Mini-player** — Artwork thumbnail, title/artist marquee, prev/play/next controls
3. **Expanded Now Playing** — Tab switcher (Track/Lyrics), artwork, seek bar, controls, volume slider, favorite button
4. **Online streaming** — Search + play from YouTube
5. **Download** — Download button in expanded player for online tracks
6. **Theme system** — 5 accent colors, light/dark/system

### What Needs Polish (Design Gaps)
1. **No artwork-dominant Now Playing** — Current mobile artwork is `w-64 h-64 max-w-[75vw] max-h-[38vh]`, constrained and small. References show artwork filling ~60% of screen height with blurred background.
2. **No blurred background** — OverlayPlayer uses `bg-background/95 backdrop-blur-2xl` (UI blur, not artwork-based blur)
3. **Mini-player has no progress indicator** — Missing thin progress bar at top/bottom
4. **No swipe-to-dismiss** on expanded player — Only chevron button
5. **Bottom nav has no active indicator dot/line** — Just color change
6. **Home screen is data-sparse on mobile** — Grid cards don't use album art prominently
7. **Volume controls shown on mobile** — Volume slider shown in mobile expanded player (should be hidden on Android where hardware buttons control volume)
8. **Settings page is desktop-first** — Card layout doesn't feel native on mobile
9. **No safe-area handling for notch/punch-hole** — `safe-top`/`safe-bottom` classes exist but aren't applied everywhere

---

## 6. Shared vs Platform-Specific Boundary

### Shared (MUST NOT BREAK)
- All Zustand state logic (playbackSlice, settingSlice, app-store)
- All API hooks (TanStack Query + Tauri invoke calls)
- All Rust commands
- Route definitions and data fetching
- Theme system CSS variables

### Android-Safe to Modify
- CSS classes with mobile-first or `md:` responsive variants
- Mobile-specific JSX blocks (gated by `md:hidden` or `hidden md:block`)
- OverlayPlayer mobile layout (`mobileTab` sections)
- BottomNavBar styling
- AppHeader mobile variant
- New Android-only CSS (safe-area, touch feedback, etc.)

---

## 7. Critical Constraints for Modification

1. **No framework changes** — Stay with Tauri 2 + React + Tailwind
2. **No rewriting shared logic** — Playback, queue, favorites must remain unchanged
3. **CSS-first approach** — Most polish is visual; avoid new components where CSS modifications suffice
4. **Test on both platforms** — Every change must work on Windows desktop AND Android
5. **Blob URL playback** — Do not touch the `read_audio_file` → Blob pattern, it's critical for Android
6. **MediaPlaybackService.kt** — Only modify if notification UX needs updating
7. **Preserve accent colors** — All 5 color themes must continue to work
