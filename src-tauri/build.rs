use sha2::{Digest, Sha256};
use std::fs::File;
use std::io::Read;
use std::path::Path;

// Pinned yt-dlp release: 2026.08.19 Windows x86_64 standalone executable
const EXPECTED_YTDLP_SHA256: &str =
    "66674953fe251b89f4d08c5f0e35e0728679bd67ab3d7d05c0562af101dd3e7a";

fn verify_sidecar_checksum() {
    let sidecar_path = Path::new("binaries/yt-dlp-x86_64-pc-windows-msvc.exe");

    if !sidecar_path.exists() {
        println!(
            "cargo:warning=yt-dlp sidecar binary not found at {:?}. Development runs will fail unless installed.",
            sidecar_path
        );
        return;
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
    let actual_hash = format!("{:x}", result);

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

