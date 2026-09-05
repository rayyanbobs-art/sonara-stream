# 🎵 Sonara Stream

> A sleek, ultra-fast desktop music player built with **Rust**, **Tauri 2**, and **React**. Stream music directly into memory with zero local disk writes and near-zero playback latency.

---

## ✨ Features

- **⚡ Zero-Disk In-Memory Streaming**: Instant audio streaming directly buffered in RAM — no files are downloaded or saved to your disk.
- **🚀 Sub-Second Playback Latency**: Hover-triggered predictive prefetching, progressive MP4 audio streaming, and thread-safe in-flight deduplication slash playback start times to **< 500ms**.
- **📻 YouTube Music-Style Genre Autoplay**: Automatically detects the genre and vibe of the currently playing song (e.g., Alternative, Rock, Pop, R&B) and curates a continuous **Radio Mix** of iconic similar tracks.
- **📑 "Up Next" Queue Drawer**: Slide-out panel displaying upcoming tracks in the current mix with album artwork, live animated visualizer equalizer, and one-click track jumping.
- **🔄 Anti-Loop Protection**: Intelligent session history tracking and queue preservation prevents repetitive 2-song loops and duplicate covers/remixes.
- **🔍 Real-Time Autocomplete Search**: Sub-25ms search suggestions with full keyboard navigation (`↑`/`↓` and `Enter`).
- **🎧 Spotify Link Resolution**: Paste Spotify track URLs (`open.spotify.com/track/...`) to automatically resolve title, artist, and album artwork via Spotify's public oEmbed API, then match and stream high-quality audio from YouTube (*note: audio is always streamed from YouTube, not from Spotify servers*).
- **🎨 Glassmorphic Sonara Aesthetic**: Floating rounded sidebar, floating search bar, custom accent colors (Gold, Emerald, Blue, Purple, Red), and Dark / Light theme support.
- **🎛️ Windows Media Session Integration**: Full support for keyboard hardware media keys (`Play/Pause`, `Next`, `Previous`) and Windows system volume overlay.
- **🪶 Featherweight Footprint**: Native compiled Rust binary (~5 MB) using only ~35 MB of RAM (unlike heavy Electron apps consuming 400 MB+).

---

## ⚠️ Known Limitations & Risks

- **yt-dlp Scraping Dependency & ToS**: Stream URLs and media extraction rely on an embedded `yt-dlp` executable querying public YouTube endpoints. Streaming or extracting content from YouTube may be subject to YouTube's Terms of Service and applicable copyright laws. Playback is susceptible to breakage whenever YouTube updates internal player protocols or anti-bot defenses until `yt-dlp` releases a corresponding extractor patch.
- **Windows-Only Scope**: The current build target, media keys integration, and sidecar resolution are tailored specifically for Windows (`x86_64-pc-windows-msvc`). Cross-platform support (macOS/Linux) is planned for future iterations.
- **Unsigned Installer / SmartScreen**: Pre-built Windows binaries are currently self-signed or unsigned open-source binaries. Windows SmartScreen may present an unrecognized app warning ("Windows protected your PC") on first run. Click *More info* -> *Run anyway*.
- **Local Storage Only for Favorites**: Liked tracks and app settings (theme, accent color) are persisted exclusively in browser `localStorage` on your machine. There is no cloud synchronization or account login.

---

## 📥 Downloads

Download the latest version from [GitHub Releases](https://github.com/rayyanbobs-art/sonara-stream/releases/latest):

| Edition | File | Description |
|---|---|---|
| **Windows Installer** | [`Sonara-Stream-0.1.0-Setup.exe`](https://github.com/rayyanbobs-art/sonara-stream/releases/download/v0.1.0/Sonara-Stream-0.1.0-Setup.exe) | Standard 1-click Windows installer with Start Menu & Desktop shortcuts. |
| **Portable Edition** | [`Sonara-Stream-0.1.0-Portable.zip`](https://github.com/rayyanbobs-art/sonara-stream/releases/download/v0.1.0/Sonara-Stream-0.1.0-Portable.zip) | Standalone portable folder. Unzip and run `sonara-stream.exe` anywhere (runs from USB). |

---

## 📋 Latest Updates

See the full [CHANGELOG.md](./CHANGELOG.md) for version details.

### Recent Highlights (v0.1.0):
- **Slashed Latency**: Song play latency reduced from 6–7s to **under 500ms**.
- **Hover Pre-fetching**: Moving mouse over any track card immediately primes the stream in background RAM.
- **Up Next Drawer**: YouTube Music-style sliding queue panel with real-time audio visualizer.
- **Autocomplete Autosuggest**: Instant search recommendations as you type.
- **Loop-Free Autoplay**: Session-wide tracking eliminates repetitive song loops.

---

## 🛠️ Development & Building

### Prerequisites
- [Node.js](https://nodejs.org/) (v18+) & [pnpm](https://pnpm.io/)
- [Rust Toolchain](https://rustup.rs/) (`stable-x86_64-pc-windows-msvc`)
- Visual Studio Build Tools (C++ workload)

### Sidecar Binary Setup (`yt-dlp`)
Tauri expects the sidecar executable to match the target triple (`yt-dlp-x86_64-pc-windows-msvc.exe`). Because sidecar binaries are excluded from Git to keep the repository lightweight, you must obtain and verify `yt-dlp.exe` before building or running `pnpm tauri dev`:

```powershell
# Fetch and verify pinned yt-dlp sidecar binary via PowerShell script
powershell -ExecutionPolicy Bypass -File scripts/fetch-ytdlp.ps1
```
*Note: During build, `src-tauri/build.rs` strictly fails the build if the binary is absent or if its SHA-256 hash does not match the pinned release, preventing execution of unverified binaries.*

### Updating yt-dlp
When upstream YouTube changes require updating the pinned `yt-dlp` sidecar release:

1. **Download the new release executable**:
   ```powershell
   Invoke-WebRequest -Uri "https://github.com/yt-dlp/yt-dlp/releases/download/<NEW_TAG>/yt-dlp.exe" -OutFile "src-tauri/binaries/yt-dlp-x86_64-pc-windows-msvc.exe"
   ```
2. **Compute its cryptographic SHA-256 hash**:
   ```powershell
   Get-FileHash -Path "src-tauri/binaries/yt-dlp-x86_64-pc-windows-msvc.exe" -Algorithm SHA256
   ```
3. **Update build and script configurations**:
   - Update `EXPECTED_YTDLP_SHA256` and the version comments in `src-tauri/build.rs`.
   - Update `$YTDLP_VERSION` and `$EXPECTED_SHA256` in `scripts/fetch-ytdlp.ps1`.
4. **Verify integrity and compatibility**:
   - Run `cargo test --manifest-path src-tauri/Cargo.toml`
   - Run `pnpm tauri build`

### Setup & Run
```bash
# Clone the repository
git clone https://github.com/rayyanbobs-art/sonara-stream.git
cd sonara-stream

# Fetch sidecar binary
powershell -ExecutionPolicy Bypass -File scripts/fetch-ytdlp.ps1

# Install dependencies
pnpm install

# Run in development mode (hot reload)
pnpm tauri dev

# Build optimized production release
pnpm tauri build
```

### Running Tests
```bash
# Run frontend unit tests
pnpm test

# Run offline Rust unit tests
cargo test --manifest-path src-tauri/Cargo.toml

# Run network/binary-dependent tests
cargo test --manifest-path src-tauri/Cargo.toml -- --ignored
```

---

## 📜 License
[MIT License](./LICENSE). Copyright (c) 2026 rayyanbobs-art.
