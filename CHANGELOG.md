# Changelog

All notable changes to **Sonara Stream** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [0.2.0] - 2026-09-05

### 🔄 Multi-Tier Self-Updating Architecture
- **Runtime yt-dlp Self-Updater**:
  - Automatic periodic background check against GitHub Releases (`api.github.com/repos/yt-dlp/yt-dlp/releases/latest`).
  - Strict SHA-256 verification against upstream `SHA2-256SUMS` before activation.
  - Active child-process lease tracking via `LiveChildGuard` ensuring atomic binary updates without file lock collisions.
  - Automatic rollback on 3 consecutive extraction failures to previous stable release or bundled sidecar, with faulty version blacklisting.
  - Engine status card, auto-check toggle, and manual "Check for Engine Updates" button in Settings.
- **Desktop Application Updater**:
  - Tauri updater integration via `@tauri-apps/plugin-updater` and `@tauri-apps/plugin-process`.
  - Non-blocking update notification banner with real-time download progress and size display.
  - Playback-safe relaunch protection preventing disruptive restarts while music is playing.
  - Desktop update controls and startup check toggle in Settings.
- **Automated CI/CD & Dependabot**:
  - Configured `.github/dependabot.yml` for automated weekly npm and cargo updates with grouped minor/patch PRs.
  - Configured `.github/workflows/ci.yml` verifying pull requests and main pushes with automated test suites and clippy linting.
  - Configured `.github/workflows/release.yml` for automated signed Tauri desktop releases.

### 🛡️ Security Hardening & Correctness
- **Strict TLS Verification**: Removed `--no-check-certificates` flags across extraction pipelines.
- **Mutex Poisoning Resilience**: Replaced `.unwrap()` calls on Mutex locks with safe error handling and propagation.
- **Self-Hosted Typography**: Fully bundled Plus Jakarta Sans font files in `src/assets/fonts/` with `@font-face` definitions, eliminating external font network requests and removing `fonts.googleapis.com` / `fonts.gstatic.com` from CSP.
- **Audio Error Recovery**: 1-time automated stream retry with cache bypass on playback errors to recover from expired URLs.
- **Minimal Capability Scope**: Scoped permissions strictly to `updater:default` and `process:default`, completely removing unused `@tauri-apps/plugin-shell`.

### ⚡ Architecture & Performance
- **Rust Track Signatures**: Normalized title signature generation in Rust backend for instant deduplication.
- **Shared HTTP Connection Pool**: Consolidated network calls across Spotify oEmbed, genre mixes, search suggestions, and update checks onto a shared `reqwest::Client`.
- **Modular Frontend Hooks**: Extracted `useAudioPlayer`, `useQueue`, and `useFavorites` hooks, reducing `App.tsx` by over 60%.

### 🧪 Comprehensive Test Suites
- Automated unit tests for manifest persistence, checksum parsing, live process tracking, and failure rollback in Rust.
- Vitest unit tests verifying audio error retry policies and track signature matching.

---

## [0.1.0] - 2026-09-05

### 🚀 Performance & Latency
- **Near-Zero Playback Latency**: Slashed initial song playback delay from 6–7 seconds down to **< 500ms** (or instant).
- **Hover-Triggered Pre-Fetching (`onMouseEnter`)**: Cursor movement over song cards/rows triggers background audio resolution, priming the stream in memory before the click.
- **In-Flight Task Deduplication**: Implemented a thread-safe broadcast manager (`IN_FLIGHT`) in Rust to prevent redundant concurrent processes when hovering and clicking.
- **Progressive Stream Acceleration**: Prioritized format `18/ba/b` and bypassed slow webpage HTML scraping (`player_skip=webpage,configs`) to eliminate HTTP 429 rate-limiting retries.
- **Lookahead Gapless Buffer**: Preloads upcoming queue tracks into a secondary in-memory audio engine for instant zero-wait song transitions.

### ✨ New Features
- **YouTube Music-Style Genre Mix Autoplay**: Playing any song automatically identifies its exact musical genre and era, curating a personalized continuous radio mix of iconic similar tracks.
- **"Up Next" Slide-Out Queue Drawer**:
  - Added an **Up Next** button (`ListMusic` icon) to the floating bottom player.
  - Opens a YouTube Music-styled drawer displaying upcoming genre tracks with artwork, title, artist, duration, and an animated mini equalizer on the currently playing track.
  - One-click track jumping directly from the queue.
- **Real-Time Autocomplete Search**:
  - Integrated public YouTube suggestion API in Rust (<25ms response).
  - Search dropdown supports keyboard navigation (`↑`/`↓` arrows to highlight, `Enter` to search, `Escape` to close).
- **Artist Variety Cap**: Enforced a limit of maximum 2 songs per artist in genre mixes to ensure high playlist diversity (e.g., Radiohead → Nirvana → The Smiths → Oasis).

### 🐛 Bug Fixes
- **Eliminated 2-Song Ping-Pong Loop**:
  - Implemented **Queue Preservation** (`preserveQueue`): Prevents `handleNext()` from wiping out the upcoming queue when advancing to the next song.
  - Implemented **Session-Wide Played History** (`recentlyPlayedSignatures`): Tracks played songs and normalized title signatures to strictly disqualify previously heard songs from repeating.
  - Auto-replenishes the queue when low (< 4 songs) by appending fresh unplayed tracks.
- **Fixed Flashing Command Prompt Window**: Applied Windows `CREATE_NO_WINDOW (0x08000000)` flag to all background sidecar processes.
- **Fixed Light / Dark Theme & Accent Color Sync**: Ensured theme changes persist in `localStorage` and update DOM root variables dynamically.
- **Fixed Windows MediaSession Integration**: Synchronized track metadata, artwork, and hardware media keys (`Play`, `Pause`, `Next`, `Previous`) to the Windows system volume overlay.

### 📦 Distribution
- **Windows Setup Installer**: NSIS installer (`Sonara-Stream-0.1.0-Setup.exe`) with Start Menu and Desktop shortcuts.
- **Portable Edition**: Self-contained ZIP archive (`Sonara-Stream-0.1.0-Portable.zip`) with zero dependencies and no registry modification.
