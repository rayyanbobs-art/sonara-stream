# 🎵 Sonara Stream

> A sleek, ultra-fast desktop music streaming player built with **Rust**, **Tauri 2**, and **React**. Stream millions of songs from YouTube and Spotify directly into memory with zero local disk writes and near-zero playback latency.

![Sonara Stream](https://raw.githubusercontent.com/rayyanbobs-art/sonara-stream/main/public/app-banner.png)

---

## ✨ Features

- **⚡ Zero-Disk In-Memory Streaming**: Instant audio streaming directly buffered in RAM — no files are downloaded or saved to your disk.
- **🚀 Sub-Second Playback Latency**: Hover-triggered predictive prefetching, progressive MP4 audio streaming, and thread-safe in-flight deduplication slash playback start times to **< 500ms**.
- **📻 YouTube Music-Style Genre Autoplay**: Automatically detects the genre and vibe of the currently playing song (e.g., Alternative, Rock, Pop, R&B) and curates a continuous **Radio Mix** of iconic similar tracks.
- **📑 "Up Next" Queue Drawer**: Slide-out panel displaying upcoming tracks in the current mix with album artwork, live animated visualizer equalizer, and one-click track jumping.
- **🔄 Anti-Loop Protection**: Intelligent session history tracking and queue preservation prevents repetitive 2-song loops and duplicate covers/remixes.
- **🔍 Real-Time Autocomplete Search**: Sub-25ms search suggestions with full keyboard navigation (`↑`/`↓` and `Enter`).
- **🎨 Glassmorphic Sonara Aesthetic**: Floating rounded sidebar, floating search bar, custom accent colors (Gold, Emerald, Blue, Purple, Red), and Dark / Light theme support.
- **🎛️ Windows Media Session Integration**: Full support for keyboard hardware media keys (`Play/Pause`, `Next`, `Previous`) and Windows system volume overlay.
- **🪶 Featherweight Footprint**: Native compiled Rust binary (~5 MB) using only ~35 MB of RAM (unlike heavy Electron apps consuming 400 MB+).

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
- [Node.js](https://nodejs.org/) & [pnpm](https://pnpm.io/)
- [Rust Toolchain](https://rustup.rs/) (`stable-x86_64-pc-windows-msvc`)
- Visual Studio Build Tools (C++ workload)

### Setup & Run
```bash
# Clone the repository
git clone https://github.com/rayyanbobs-art/sonara-stream.git
cd sonara-stream

# Install dependencies
pnpm install

# Run in development mode (hot reload)
pnpm tauri dev

# Build optimized production release
pnpm tauri build
```

---

## 📜 License
MIT License.
