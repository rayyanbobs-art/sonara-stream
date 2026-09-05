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

Sonara Stream maintains extraction reliability through two complementary update paths:

#### Path 1: Automatic Runtime Self-Updater (For Users)
The desktop application autonomously manages the `yt-dlp` streaming engine at runtime without requiring full app reinstallation:
- **How it works**: On startup (and periodically rate-limited to once every 6 hours), Sonara checks `https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest`.
- **Integrity Verification**: It downloads `yt-dlp.exe` alongside `SHA2-256SUMS` to a temporary directory in app data, hashes the binary, and strictly validates that its SHA-256 matches the release checksum. Any mismatch immediately purges the files without touching active state.
- **Atomic Activation**: On hash verification, the binary is moved into `%APPDATA%/com.sonara.stream/binaries/yt-dlp-{version}.exe` and safely activated once in-flight child processes complete.
- **Automatic Rollback**: If consecutive extraction errors reach 3 on a downloaded binary, Sonara automatically rolls back to the previous stable binary (or falls back to the bundled sidecar) and blacklists the faulty version.
- **User Control**: Automatic updates can be toggled on/off at any time in **Settings**, where users can also trigger a manual "Check for Engine Updates" action.

#### Path 2: Maintainer Pinned Sidecar Bump (For Releases)
When creating new Sonara releases with a fresh bundled sidecar baseline:
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

#### Path 3: Desktop Application Updates (Tauri Updater)
Sonara Stream desktop updates are built with `@tauri-apps/plugin-updater` and cryptographically signed with minisign keys:
- **Non-blocking Notification**: On startup (or via manual check in **Settings**), Sonara queries GitHub Releases for `latest.json`. If a newer version is available, a non-intrusive update banner displays release details and download progress.
- **Playback Protection**: An update will never interrupt active audio playback. Users can restart at their convenience or dismiss the notification.
- **Signer Key Setup (For Maintainers)**:
  1. Generate a Tauri signer private/public keypair:
     ```bash
     pnpm tauri signer generate -w ~/.tauri/sonara.key
     ```
  2. Set the generated public key in `src-tauri/tauri.conf.json`:
     ```json
     "plugins": {
       "updater": {
         "pubkey": "<MINISIGN_PUBLIC_KEY>"
       }
     }
     ```
  3. Store the private key and password in repository GitHub Secrets:
     - `TAURI_SIGNING_PRIVATE_KEY`: Content of `~/.tauri/sonara.key`
     - `TAURI_SIGNING_PRIVATE_KEY_PASSWORD`: Password supplied during key creation
- **Consequences of Private Key Loss**:
  Tauri updater binaries verify release archives against the hardcoded minisign public key. If the private signing key is lost, future updates cannot be verified or applied automatically by existing desktop installations; users will be required to download and install a fresh installer manually.
- **Windows SmartScreen Notice**:
  Minisign code signing secures in-app update archives against tampering. However, it does **not** bypass Microsoft Defender SmartScreen untrusted publisher warnings when running newly downloaded Windows `.msi` or `.exe` installers. Bypassing SmartScreen requires an EV (Extended Validation) code-signing certificate or sufficient download reputation accumulated over time.

#### Path 4: Repository Dependency Maintenance (Dependabot)
- Automated weekly pull requests scan and update `npm` (`package.json`) and `cargo` (`src-tauri/Cargo.toml`) dependencies.
- Minor and patch updates are grouped into consolidated PRs to minimize noise, while major version upgrades receive individual pull requests.
- Every PR automatically executes the full CI test suite defined in `.github/workflows/ci.yml`.
- **Scope Distinction**: Dependabot manages compile-time repository dependencies. It does not touch runtime `yt-dlp` binaries (managed by Path 1) or user application installs (managed by Path 3).

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

## 🌐 Network Requests This App Makes

Sonara Stream runs entirely on your local machine and collects no telemetry, analytics, or user data. The app interacts with the following external endpoints:

| Destination | Initiator | Purpose | Data Leaving Your Machine |
|---|---|---|---|
| **YouTube** (`*.googlevideo.com`, `*.ytimg.com`) | Backend sidecar (`yt-dlp`) & Webview `<audio>` / `<img>` | Searching songs, extracting direct audio stream URLs, streaming audio playback, and displaying track thumbnails. | Search keywords entered in the search bar, YouTube video IDs for stream resolution, and standard HTTP streaming range headers. |
| **`suggestqueries.google.com`** | Frontend Webview (`fetch`) | Providing real-time autocomplete search suggestions as you type in the search bar. | Typed query strings directly sent from the search input field. |
| **`itunes.apple.com`** (`*.mzstatic.com`) | Rust backend (`reqwest`) & Webview `<img>` | Identifying song genre and fetching diverse auto-mix recommendations; loading album artwork. | URL-encoded track title, artist name, and detected genre keyword; thumbnail image requests sent to Apple CDN (`*.mzstatic.com`). |
| **Spotify** (`open.spotify.com/oembed`, `*.scdn.co`, `*.spotifycdn.com`) | Rust backend (`reqwest`) & Webview `<img>` | Resolving track title, artist name, and cover artwork when a Spotify link is pasted. | The public Spotify track URL pasted by the user sent to Spotify's oEmbed endpoint; artwork image requests sent to Spotify CDN. |
| **GitHub Releases (`yt-dlp`)** (`api.github.com`, `objects.githubusercontent.com`) | Rust backend (`reqwest`) | Checking for latest `yt-dlp` updates, downloading release checksums (`SHA2-256SUMS`), and fetching verified binary updates. | Standard HTTPS GET requests for release assets and version tags; no personal or user data sent. |
| **GitHub Releases (Sonara App)** (`github.com/rayyanbobs-art/sonara-stream`) | Rust backend (`tauri-plugin-updater`) | Checking for newer application releases (`latest.json`), downloading signed installer updates. | Standard HTTPS GET requests for version manifest and update bundles; no telemetry sent. |

---

## 📜 License
[MIT License](./LICENSE). Copyright (c) 2026 rayyanbobs-art.

