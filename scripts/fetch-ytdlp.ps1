# Pinned yt-dlp version and expected SHA-256 hash
$YTDLP_VERSION = "2026.08.19"
$EXPECTED_SHA256 = "66674953fe251b89f4d08c5f0e35e0728679bd67ab3d7d05c0562af101dd3e7a"

$TargetDir = Join-Path $PSScriptRoot "..\src-tauri\binaries"
if (!(Test-Path $TargetDir)) {
    New-Item -ItemType Directory -Force -Path $TargetDir | Out-Null
}

$TargetPath = Join-Path $TargetDir "yt-dlp-x86_64-pc-windows-msvc.exe"
$DownloadUrl = "https://github.com/yt-dlp/yt-dlp/releases/download/$YTDLP_VERSION/yt-dlp.exe"

Write-Host "Downloading yt-dlp $YTDLP_VERSION from $DownloadUrl..."
Invoke-WebRequest -Uri $DownloadUrl -OutFile $TargetPath

Write-Host "Verifying SHA-256 checksum..."
$ActualHash = (Get-FileHash -Path $TargetPath -Algorithm SHA256).Hash.ToLower()

if ($ActualHash -ne $EXPECTED_SHA256.ToLower()) {
    Remove-Item -Force $TargetPath
    Write-Error "Checksum verification FAILED!`nExpected: $EXPECTED_SHA256`nActual:   $ActualHash"
    exit 1
}

Write-Host "yt-dlp sidecar successfully downloaded and verified at: $TargetPath"
