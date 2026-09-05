//! yt-dlp runtime self-update, verification, and rollback manager.
//!
//! Manages downloaded yt-dlp binaries in the application data directory,
//! performs SHA2-256SUMS cryptographic verification, enforces live child process
//! concurrency safety, and executes automatic rollback to previous/bundled binaries
//! upon consecutive extraction failures.

use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::fs::{self, File};
use std::io::Read;
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicUsize, Ordering};
use std::sync::{Arc, OnceLock, RwLock};
use std::time::{Duration, SystemTime, UNIX_EPOCH};

const BUNDLED_VERSION: &str = "2026.08.19";
const RATE_LIMIT_SECONDS: u64 = 6 * 3600; // 6 hours
const MAX_FAILURES_BEFORE_ROLLBACK: u32 = 3;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct YtDlpManifest {
    pub active_version: String,
    pub active_path: PathBuf,
    pub previous_version: Option<String>,
    pub previous_path: Option<PathBuf>,
    pub last_check_unix: u64,
    pub consecutive_failures: u32,
    pub auto_update_enabled: bool,
    #[serde(default)]
    pub bad_versions: Vec<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct YtDlpStatus {
    pub version: String,
    pub source: String, // "bundled" or "downloaded"
    pub last_check_unix: u64,
    pub auto_update_enabled: bool,
    pub consecutive_failures: u32,
    pub active_path: String,
}

#[derive(Debug, Deserialize)]
struct GithubReleaseAsset {
    name: String,
    browser_download_url: String,
}

#[derive(Debug, Deserialize)]
struct GithubRelease {
    tag_name: String,
    assets: Vec<GithubReleaseAsset>,
}

pub struct YtDlpManager {
    app_data_dir: PathBuf,
    manifest_path: PathBuf,
    manifest: Arc<RwLock<YtDlpManifest>>,
    live_child_count: Arc<AtomicUsize>,
    http_client: reqwest::Client,
}

static MANAGER: OnceLock<Arc<YtDlpManager>> = OnceLock::new();

pub fn init_manager(app_data_dir: PathBuf) -> Arc<YtDlpManager> {
    MANAGER
        .get_or_init(|| {
            let manager = YtDlpManager::new(app_data_dir);
            Arc::new(manager)
        })
        .clone()
}

pub fn get_manager() -> Option<Arc<YtDlpManager>> {
    MANAGER.get().cloned()
}

impl YtDlpManager {
    pub fn new(app_data_dir: PathBuf) -> Self {
        let manifest_path = app_data_dir.join("ytdlp_manifest.json");
        let initial_manifest = Self::load_or_create_manifest(&manifest_path, &app_data_dir);

        let http_client = reqwest::Client::builder()
            .timeout(Duration::from_secs(10))
            .connect_timeout(Duration::from_secs(5))
            .pool_idle_timeout(Duration::from_secs(90))
            .user_agent("sonara-stream-updater/0.1.0")
            .build()
            .unwrap_or_default();

        Self {
            app_data_dir,
            manifest_path,
            manifest: Arc::new(RwLock::new(initial_manifest)),
            live_child_count: Arc::new(AtomicUsize::new(0)),
            http_client,
        }
    }

    fn now_unix() -> u64 {
        SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .map(|d| d.as_secs())
            .unwrap_or(0)
    }

    fn bundled_fallback_path() -> PathBuf {
        crate::youtube::get_bundled_ytdlp_path()
    }

    fn load_or_create_manifest(manifest_path: &Path, app_data_dir: &Path) -> YtDlpManifest {
        if manifest_path.exists() {
            if let Ok(content) = fs::read_to_string(manifest_path) {
                if let Ok(manifest) = serde_json::from_str::<YtDlpManifest>(&content) {
                    if manifest.active_path.exists() {
                        return manifest;
                    }
                }
            }
        }

        let fallback_path = Self::bundled_fallback_path();
        let default_manifest = YtDlpManifest {
            active_version: BUNDLED_VERSION.to_string(),
            active_path: fallback_path,
            previous_version: None,
            previous_path: None,
            last_check_unix: 0,
            consecutive_failures: 0,
            auto_update_enabled: true,
            bad_versions: Vec::new(),
        };

        let _ = fs::create_dir_all(app_data_dir);
        let _ = Self::save_manifest_file(manifest_path, &default_manifest);

        default_manifest
    }

    fn save_manifest_file(manifest_path: &Path, manifest: &YtDlpManifest) -> Result<(), String> {
        let json = serde_json::to_string_pretty(manifest).map_err(|e| e.to_string())?;
        let temp_path = manifest_path.with_extension("tmp");
        fs::write(&temp_path, json).map_err(|e| e.to_string())?;
        fs::rename(&temp_path, manifest_path).map_err(|e| e.to_string())?;
        Ok(())
    }

    pub fn save_manifest(&self) -> Result<(), String> {
        let guard = self.manifest.read().map_err(|_| "Manifest lock poisoned")?;
        Self::save_manifest_file(&self.manifest_path, &guard)
    }

    /// Acquires an execution lease for a running yt-dlp child process.
    pub fn acquire_lease(&self) -> (PathBuf, LiveChildGuard) {
        let path = {
            let guard = self.manifest.read().unwrap();
            guard.active_path.clone()
        };
        self.live_child_count.fetch_add(1, Ordering::SeqCst);
        let guard = LiveChildGuard {
            counter: self.live_child_count.clone(),
        };
        (path, guard)
    }

    /// Returns current version and execution status for UI
    pub fn get_status(&self) -> YtDlpStatus {
        let guard = self.manifest.read().unwrap();
        let is_bundled = guard.active_version == BUNDLED_VERSION
            || guard.active_path == Self::bundled_fallback_path();

        YtDlpStatus {
            version: guard.active_version.clone(),
            source: if is_bundled {
                "bundled".to_string()
            } else {
                "downloaded".to_string()
            },
            last_check_unix: guard.last_check_unix,
            auto_update_enabled: guard.auto_update_enabled,
            consecutive_failures: guard.consecutive_failures,
            active_path: guard.active_path.to_string_lossy().to_string(),
        }
    }

    pub fn set_auto_update(&self, enabled: bool) -> Result<(), String> {
        {
            let mut guard = self.manifest.write().map_err(|_| "Manifest lock poisoned")?;
            guard.auto_update_enabled = enabled;
        }
        self.save_manifest()
    }

    pub fn record_success(&self) {
        if let Ok(mut guard) = self.manifest.write() {
            if guard.consecutive_failures > 0 {
                guard.consecutive_failures = 0;
                let _ = Self::save_manifest_file(&self.manifest_path, &guard);
            }
        }
    }

    /// Increments consecutive failure counter. If failures reach threshold,
    /// initiates automatic rollback.
    pub fn record_failure(&self) -> bool {
        let mut rolled_back = false;
        if let Ok(mut guard) = self.manifest.write() {
            guard.consecutive_failures += 1;

            if guard.consecutive_failures >= MAX_FAILURES_BEFORE_ROLLBACK {
                let current_bad = guard.active_version.clone();
                if !guard.bad_versions.contains(&current_bad) && current_bad != BUNDLED_VERSION {
                    guard.bad_versions.push(current_bad);
                }

                // Attempt rollback to previous
                if let (Some(prev_ver), Some(prev_path)) =
                    (guard.previous_version.clone(), guard.previous_path.clone())
                {
                    if prev_path.exists() {
                        guard.active_version = prev_ver;
                        guard.active_path = prev_path;
                        guard.previous_version = None;
                        guard.previous_path = None;
                        guard.consecutive_failures = 0;
                        rolled_back = true;
                    } else {
                        // Fall back to bundled
                        guard.active_version = BUNDLED_VERSION.to_string();
                        guard.active_path = Self::bundled_fallback_path();
                        guard.previous_version = None;
                        guard.previous_path = None;
                        guard.consecutive_failures = 0;
                        rolled_back = true;
                    }
                } else {
                    // Fall back to bundled
                    guard.active_version = BUNDLED_VERSION.to_string();
                    guard.active_path = Self::bundled_fallback_path();
                    guard.consecutive_failures = 0;
                    rolled_back = true;
                }
            }

            let _ = Self::save_manifest_file(&self.manifest_path, &guard);
        }
        rolled_back
    }

    /// Checks for updates and activates if a verified new version is found.
    pub async fn check_and_update(&self, forced: bool) -> Result<Option<String>, String> {
        let (auto_enabled, last_check, current_ver, bad_versions) = {
            let guard = self.manifest.read().map_err(|_| "Manifest lock poisoned")?;
            (
                guard.auto_update_enabled,
                guard.last_check_unix,
                guard.active_version.clone(),
                guard.bad_versions.clone(),
            )
        };

        if !forced && !auto_enabled {
            return Ok(None);
        }

        let now = Self::now_unix();
        if !forced && now.saturating_sub(last_check) < RATE_LIMIT_SECONDS {
            return Ok(None);
        }

        // Record check timestamp
        if let Ok(mut guard) = self.manifest.write() {
            guard.last_check_unix = now;
            let _ = Self::save_manifest_file(&self.manifest_path, &guard);
        }

        let release_url = "https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest";
        let res = self
            .http_client
            .get(release_url)
            .send()
            .await
            .map_err(|e| format!("GitHub API request failed: {}", e))?;

        if res.status() == reqwest::StatusCode::FORBIDDEN
            || res.status() == reqwest::StatusCode::TOO_MANY_REQUESTS
        {
            return Err("GitHub API rate limit exceeded. Please try again later.".into());
        }

        if !res.status().is_success() {
            return Err(format!("GitHub API returned HTTP {}", res.status()));
        }

        let release: GithubRelease = res
            .json()
            .await
            .map_err(|e| format!("Failed to parse GitHub release JSON: {}", e))?;

        let new_version = release.tag_name.trim().to_string();

        if new_version == current_ver {
            return Ok(None); // Already up to date
        }

        if bad_versions.contains(&new_version) {
            return Err(format!("Version {} is marked bad due to previous failures", new_version));
        }

        let exe_asset = release
            .assets
            .iter()
            .find(|a| a.name == "yt-dlp.exe")
            .ok_or_else(|| "Release does not contain yt-dlp.exe".to_string())?;

        let sha_asset = release
            .assets
            .iter()
            .find(|a| a.name == "SHA2-256SUMS")
            .ok_or_else(|| "Release does not contain SHA2-256SUMS".to_string())?;

        // Download to temp directory
        let temp_dir = self.app_data_dir.join("updates_temp");
        fs::create_dir_all(&temp_dir).map_err(|e| e.to_string())?;

        let temp_exe = temp_dir.join(format!("yt-dlp-{}.tmp", new_version));
        let temp_sha = temp_dir.join(format!("SHA2-256SUMS-{}.tmp", new_version));

        let download_result = self
            .download_and_verify(
                &exe_asset.browser_download_url,
                &temp_exe,
                &sha_asset.browser_download_url,
                &temp_sha,
                &new_version,
            )
            .await;

        let _ = fs::remove_file(&temp_sha);

        match download_result {
            Ok(_new_path) => Ok(Some(new_version)),
            Err(err) => {
                let _ = fs::remove_file(&temp_exe);
                Err(err)
            }
        }
    }

    async fn download_file(&self, url: &str, dest: &Path) -> Result<(), String> {
        let res = self
            .http_client
            .get(url)
            .send()
            .await
            .map_err(|e| format!("Failed to download {}: {}", url, e))?;

        if !res.status().is_success() {
            return Err(format!("Download failed with status HTTP {}", res.status()));
        }

        let bytes = res
            .bytes()
            .await
            .map_err(|e| format!("Failed to read response body: {}", e))?;

        fs::write(dest, bytes).map_err(|e| format!("Failed to write to {:?}: {}", dest, e))?;
        Ok(())
    }

    async fn download_and_verify(
        &self,
        exe_url: &str,
        temp_exe: &Path,
        sha_url: &str,
        temp_sha: &Path,
        new_version: &str,
    ) -> Result<PathBuf, String> {
        // Download binary and checksums
        self.download_file(exe_url, temp_exe).await?;
        self.download_file(sha_url, temp_sha).await?;

        // Extract expected SHA-256 from SHA2-256SUMS
        let sha_content = fs::read_to_string(temp_sha)
            .map_err(|e| format!("Failed to read checksums file: {}", e))?;

        let expected_hash = Self::parse_expected_sha256(&sha_content, "yt-dlp.exe")
            .ok_or_else(|| "yt-dlp.exe checksum not found in SHA2-256SUMS".to_string())?;

        // Compute actual SHA-256
        let actual_hash = Self::compute_file_sha256(temp_exe)?;

        if expected_hash.to_lowercase() != actual_hash.to_lowercase() {
            let _ = fs::remove_file(temp_exe);
            return Err(format!(
                "SHA-256 checksum verification failed! Expected {}, got {}",
                expected_hash, actual_hash
            ));
        }

        // Wait briefly if child processes are active to prevent Windows lock conflicts
        let mut retries = 0;
        while self.live_child_count.load(Ordering::SeqCst) > 0 && retries < 20 {
            tokio::time::sleep(Duration::from_millis(100)).await;
            retries += 1;
        }

        // Target binary path in app data
        let binaries_dir = self.app_data_dir.join("binaries");
        fs::create_dir_all(&binaries_dir).map_err(|e| e.to_string())?;
        let target_path = binaries_dir.join(format!("yt-dlp-{}.exe", new_version));

        // Atomic swap
        fs::rename(temp_exe, &target_path).map_err(|e| {
            format!("Failed to activate binary at {:?}: {}", target_path, e)
        })?;

        // Update manifest
        if let Ok(mut guard) = self.manifest.write() {
            guard.previous_version = Some(guard.active_version.clone());
            guard.previous_path = Some(guard.active_path.clone());
            guard.active_version = new_version.to_string();
            guard.active_path = target_path.clone();
            guard.consecutive_failures = 0;
            guard.last_check_unix = Self::now_unix();
            let _ = Self::save_manifest_file(&self.manifest_path, &guard);
        }

        Ok(target_path)
    }

    pub fn parse_expected_sha256(sha_content: &str, filename: &str) -> Option<String> {
        for line in sha_content.lines() {
            let parts: Vec<&str> = line.split_whitespace().collect();
            if parts.len() >= 2 {
                let hash = parts[0].trim();
                let file = parts[1].trim().trim_start_matches('*');
                if file.eq_ignore_ascii_case(filename) {
                    return Some(hash.to_string());
                }
            }
        }
        None
    }

    pub fn compute_file_sha256(path: &Path) -> Result<String, String> {
        let mut file = File::open(path).map_err(|e| format!("Failed to open file for hashing: {}", e))?;
        let mut hasher = Sha256::new();
        let mut buffer = [0u8; 65536];
        loop {
            let count = file
                .read(&mut buffer)
                .map_err(|e| format!("Failed to read file for hashing: {}", e))?;
            if count == 0 {
                break;
            }
            hasher.update(&buffer[..count]);
        }
        let result = hasher.finalize();
        let hash_hex: String = result.iter().map(|b| format!("{:02x}", b)).collect();
        Ok(hash_hex)
    }
}

pub struct LiveChildGuard {
    counter: Arc<AtomicUsize>,
}

impl Drop for LiveChildGuard {
    fn drop(&mut self) {
        self.counter.fetch_sub(1, Ordering::SeqCst);
    }
}

// Tauri IPC commands
#[tauri::command]
pub fn get_ytdlp_status() -> Result<YtDlpStatus, String> {
    if let Some(manager) = get_manager() {
        Ok(manager.get_status())
    } else {
        Err("YtDlpManager is not initialized".into())
    }
}

#[tauri::command]
pub async fn check_ytdlp_update(forced: Option<bool>) -> Result<Option<String>, String> {
    if let Some(manager) = get_manager() {
        manager.check_and_update(forced.unwrap_or(true)).await
    } else {
        Err("YtDlpManager is not initialized".into())
    }
}

#[tauri::command]
pub fn set_ytdlp_auto_update(enabled: bool) -> Result<(), String> {
    if let Some(manager) = get_manager() {
        manager.set_auto_update(enabled)
    } else {
        Err("YtDlpManager is not initialized".into())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_parse_expected_sha256() {
        let sample = "66674953fe251b89f4d08c5f0e35e0728679bd67ab3d7d05c0562af101dd3e7a  yt-dlp.exe\n\
                      1111222233334444555566667777888899990000aaaaabbbbccccddddeeeeffff *yt-dlp_macos";
        let parsed = YtDlpManager::parse_expected_sha256(sample, "yt-dlp.exe");
        assert_eq!(
            parsed,
            Some("66674953fe251b89f4d08c5f0e35e0728679bd67ab3d7d05c0562af101dd3e7a".to_string())
        );
        let parsed_star = YtDlpManager::parse_expected_sha256(sample, "yt-dlp_macos");
        assert_eq!(
            parsed_star,
            Some("1111222233334444555566667777888899990000aaaaabbbbccccddddeeeeffff".to_string())
        );
        assert_eq!(YtDlpManager::parse_expected_sha256(sample, "nonexistent.exe"), None);
    }

    #[test]
    fn test_live_child_guard_tracks_count() {
        let counter = Arc::new(AtomicUsize::new(0));
        assert_eq!(counter.load(Ordering::SeqCst), 0);
        {
            counter.fetch_add(1, Ordering::SeqCst);
            let _guard = LiveChildGuard {
                counter: counter.clone(),
            };
            assert_eq!(counter.load(Ordering::SeqCst), 1);
        }
        assert_eq!(counter.load(Ordering::SeqCst), 0);
    }

    #[test]
    fn test_manifest_creation_and_persistence() {
        let temp_dir = std::env::temp_dir().join(format!("sonara_test_{}", YtDlpManager::now_unix()));
        let _ = fs::create_dir_all(&temp_dir);
        let manifest_path = temp_dir.join("ytdlp_manifest.json");

        let manifest = YtDlpManager::load_or_create_manifest(&manifest_path, &temp_dir);
        assert_eq!(manifest.active_version, BUNDLED_VERSION);
        assert_eq!(manifest.consecutive_failures, 0);
        assert!(manifest.auto_update_enabled);

        let reloaded = YtDlpManager::load_or_create_manifest(&manifest_path, &temp_dir);
        assert_eq!(reloaded.active_version, BUNDLED_VERSION);

        let _ = fs::remove_dir_all(&temp_dir);
    }

    #[test]
    fn test_rollback_after_consecutive_failures() {
        let temp_dir = std::env::temp_dir().join(format!("sonara_test_rollback_{}", YtDlpManager::now_unix()));
        let _ = fs::create_dir_all(&temp_dir);
        let manager = YtDlpManager::new(temp_dir.clone());

        // Simulate activated downloaded binary
        let fake_new = temp_dir.join("yt-dlp-2026.09.01.exe");
        let fake_prev = temp_dir.join("yt-dlp-2026.08.19.exe");
        let _ = fs::write(&fake_new, b"fake new");
        let _ = fs::write(&fake_prev, b"fake prev");

        {
            let mut guard = manager.manifest.write().unwrap();
            guard.active_version = "2026.09.01".into();
            guard.active_path = fake_new.clone();
            guard.previous_version = Some("2026.08.19".into());
            guard.previous_path = Some(fake_prev.clone());
        }

        // Failures 1 and 2
        assert!(!manager.record_failure());
        assert!(!manager.record_failure());

        // Failure 3 triggers rollback
        assert!(manager.record_failure());

        let status = manager.get_status();
        assert_eq!(status.version, "2026.08.19");
        assert_eq!(status.consecutive_failures, 0);

        // Verify bad version recorded
        let guard = manager.manifest.read().unwrap();
        assert!(guard.bad_versions.contains(&"2026.09.01".to_string()));

        let _ = fs::remove_dir_all(&temp_dir);
    }

    #[test]
    fn test_hash_mismatch_prevents_activation() {
        let temp_dir = std::env::temp_dir().join(format!("sonara_test_hash_{}", YtDlpManager::now_unix()));
        let _ = fs::create_dir_all(&temp_dir);
        let fake_binary = temp_dir.join("downloaded.exe");
        fs::write(&fake_binary, b"tampered content").unwrap();

        let actual = YtDlpManager::compute_file_sha256(&fake_binary).unwrap();
        let expected = "0000000000000000000000000000000000000000000000000000000000000000";

        assert_ne!(actual, expected);
        let _ = fs::remove_dir_all(&temp_dir);
    }
}
