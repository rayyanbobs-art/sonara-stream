# Changelog

All notable changes to **Sonara Stream** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
