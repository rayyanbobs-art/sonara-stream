use sha2::{Digest, Sha256};
use std::fs::File;
use std::io::Read;
use std::path::Path;

// Pinned yt-dlp release: 2026.08.19 Windows x86_64 standalone executable.
// Why it is pinned:
// 1. Supply-chain security: Sonara Stream invokes yt-dlp directly as a local sidecar subprocess.
//    Pinning an exact release and enforcing SHA-256 verification prevents execution of unvetted,
//    tampered, or malicious external binaries.
// 2. Deterministic builds & stability: YouTube extraction logic and CLI flags can drift across
//    upstream yt-dlp versions. Pinning guarantees full compatibility with internal argument handling.
const EXPECTED_YTDLP_SHA256: &str =
    "66674953fe251b89f4d08c5f0e35e0728679bd67ab3d7d05c0562af101dd3e7a";

fn verify_sidecar_checksum() {
    let sidecar_path = Path::new("binaries/yt-dlp-x86_64-pc-windows-msvc.exe");

    if !sidecar_path.exists() {
        panic!(
            "\n\n======================================================================\n\
             FATAL BUILD ERROR: yt-dlp sidecar binary not found at {:?}!\n\
             Sonara Stream requires the pinned yt-dlp binary (version 2026.08.19).\n\
             To fetch and verify the required binary, run:\n\n\
                 powershell -ExecutionPolicy Bypass -File scripts/fetch-ytdlp.ps1\n\n\
             ======================================================================\n\n",
            sidecar_path
        );
    }

    let mut file = File::open(sidecar_path)
        .unwrap_or_else(|e| panic!("Failed to open {:?} for checksum verification: {}", sidecar_path, e));

    let mut hasher = Sha256::new();
    let mut buffer = [0u8; 65536];
    loop {
        let count = file
            .read(&mut buffer)
            .unwrap_or_else(|e| panic!("Failed to read {:?} during checksum verification: {}", sidecar_path, e));
        if count == 0 {
            break;
        }
        hasher.update(&buffer[..count]);
    }

    let result = hasher.finalize();
    let actual_hash: String = result.iter().map(|b| format!("{:02x}", b)).collect();

    if actual_hash != EXPECTED_YTDLP_SHA256 {
        panic!(
            "\n\nFATAL SECURITY ERROR: yt-dlp sidecar checksum mismatch!\n\
             Expected SHA-256 : {}\n\
             Actual SHA-256   : {}\n\
             The sidecar binary at {:?} may be corrupted or tampered with.\n\n",
            EXPECTED_YTDLP_SHA256, actual_hash, sidecar_path
        );
    }
}

fn main() {
    verify_sidecar_checksum();
    tauri_build::build();
}

