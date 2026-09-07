use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AppUpdateInfo {
    pub available: bool,
    pub latest_version: String,
    pub current_version: String,
    pub download_url: String,
    pub release_notes: String,
}

#[derive(Deserialize)]
struct GitHubReleaseAsset {
    name: String,
    browser_download_url: String,
}

#[derive(Deserialize)]
struct GitHubReleaseResponse {
    tag_name: String,
    body: Option<String>,
    assets: Option<Vec<GitHubReleaseAsset>>,
}

pub fn parse_semver(v: &str) -> (u32, u32, u32) {
    let clean = v.trim().trim_start_matches('v');
    let mut parts = clean.split('.');
    let major = parts.next().and_then(|p| p.parse().ok()).unwrap_or(0);
    let minor = parts.next().and_then(|p| p.parse().ok()).unwrap_or(0);
    let patch = parts
        .next()
        .and_then(|p| p.split('-').next())
        .and_then(|p| p.parse().ok())
        .unwrap_or(0);
    (major, minor, patch)
}

pub fn is_newer_version(latest: &str, current: &str) -> bool {
    let l = parse_semver(latest);
    let c = parse_semver(current);
    l > c
}

#[tauri::command]
pub async fn check_app_update() -> Result<AppUpdateInfo, String> {
    let current_version = env!("CARGO_PKG_VERSION").to_string();
    let client = reqwest::Client::builder()
        .user_agent("SonaraStream")
        .timeout(std::time::Duration::from_secs(10))
        .build()
        .map_err(|e| e.to_string())?;

    let url = "https://api.github.com/repos/rayyanbobs-art/sonara-stream/releases/latest";
    let resp = client.get(url).send().await.map_err(|e| e.to_string())?;

    if !resp.status().is_success() {
        return Err(format!("GitHub API returned status: {}", resp.status()));
    }

    let release: GitHubReleaseResponse = resp.json().await.map_err(|e| e.to_string())?;
    let latest_tag = release.tag_name.trim().trim_start_matches('v').to_string();

    let mut download_url =
        "https://github.com/rayyanbobs-art/sonara-stream/releases/latest/download/SonaraStream-Android.apk"
            .to_string();

    if let Some(assets) = release.assets {
        if let Some(apk_asset) = assets.into_iter().find(|a| a.name.ends_with(".apk")) {
            download_url = apk_asset.browser_download_url;
        }
    }

    let available = is_newer_version(&latest_tag, &current_version);

    Ok(AppUpdateInfo {
        available,
        latest_version: latest_tag,
        current_version,
        download_url,
        release_notes: release.body.unwrap_or_default(),
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_semver_parsing() {
        assert_eq!(parse_semver("0.5.0"), (0, 5, 0));
        assert_eq!(parse_semver("v1.2.3"), (1, 2, 3));
        assert_eq!(parse_semver("v2.10.4-beta"), (2, 10, 4));
    }

    #[test]
    fn test_is_newer_version() {
        assert!(is_newer_version("0.5.1", "0.5.0"));
        assert!(is_newer_version("1.0.0", "0.5.0"));
        assert!(!is_newer_version("0.5.0", "0.5.0"));
        assert!(!is_newer_version("0.4.9", "0.5.0"));
    }
}
