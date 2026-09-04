# 🎵 Sonara Stream

> A sleek, high-performance desktop music streaming player built with **Rust**, **Tauri 2**, and **React**. Stream millions of songs from YouTube and Spotify directly into memory with zero local disk writes and sub-millisecond audio playback.

![Sonara Stream](https://raw.githubusercontent.com/rayyanbobs-art/sonara-stream/main/public/app-banner.png)

## ✨ Features

- **⚡ Zero-Disk Streaming**: Instant audio streaming directly buffered in memory — no files downloaded or saved to disk.
- **🚀 Sub-Millisecond Latency**: Powered by a native Rust backend with thread-safe `OnceLock` caching and speculative lookahead pre-buffering.
- **🎨 Stunning Sonara Aesthetic**: Floating rounded glassmorphic sidebar, top floating search bar, custom accent colors (Gold, Emerald, Blue, Purple, Red), and full Dark / Light theme support.
- **🔍 Multi-Source Discovery**: Search YouTube or paste Spotify track links directly.
- **🎛️ Windows Media Session Integration**: Full support for hardware media keys (Play/Pause, Next, Previous) and Windows volume overlay.
- **🪶 Featherweight Footprint**: Native Rust binary (~5 MB) using only ~30 MB of RAM (unlike heavy Electron apps that consume 300MB+).

## 📥 Downloads

Download the latest version from the [Releases](https://github.com/rayyanbobs-art/sonara-stream/releases) page:

| Edition | File | Description |
|---|---|---|
| **Windows Installer** | `Sonara-Stream-0.1.0-Setup.exe` | Standard 1-click Windows installer with Desktop & Start Menu shortcuts. |
| **Portable Edition** | `Sonara-Stream-0.1.0-Portable.zip` | Standalone portable folder. Unzip and double-click `sonara-stream.exe` anywhere (runs from USB). |

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

# Install frontend dependencies
pnpm install

# Run in development mode (hot reload)
pnpm tauri dev

# Build optimized production release
pnpm tauri build
```

## 📜 License
MIT License.
