use std::path::{Path, PathBuf};
use std::sync::OnceLock;
use tokio::io::{AsyncReadExt, AsyncSeekExt, AsyncWriteExt};
use tokio::net::{TcpListener, TcpStream};

static LOCAL_SERVER_PORT: OnceLock<u16> = OnceLock::new();

pub fn get_server_port() -> Option<u16> {
    LOCAL_SERVER_PORT.get().copied()
}

pub fn format_local_audio_url(port: u16, path: &str) -> String {
    #[allow(unused_mut)]
    let mut clean_path = if let Some(stripped) = path.strip_prefix("file://") {
        stripped
    } else {
        path
    };
    #[cfg(windows)]
    if clean_path.len() >= 3 && clean_path.starts_with('/') && clean_path.chars().nth(2) == Some(':') {
        clean_path = &clean_path[1..];
    }
    format!(
        "http://127.0.0.1:{}/audio?path={}",
        port,
        urlencoding::encode(clean_path)
    )
}

pub fn format_local_image_url(port: u16, path: &str) -> String {
    #[allow(unused_mut)]
    let mut clean_path = if let Some(stripped) = path.strip_prefix("file://") {
        stripped
    } else {
        path
    };
    #[cfg(windows)]
    if clean_path.len() >= 3 && clean_path.starts_with('/') && clean_path.chars().nth(2) == Some(':') {
        clean_path = &clean_path[1..];
    }
    format!(
        "http://127.0.0.1:{}/image?path={}",
        port,
        urlencoding::encode(clean_path)
    )
}

#[allow(dead_code)]
pub fn get_local_audio_url_sync(path: &str) -> String {
    if let Some(port) = get_server_port() {
        format_local_audio_url(port, path)
    } else {
        let clean_path = if let Some(stripped) = path.strip_prefix("file://") {
            stripped
        } else {
            path
        };
        clean_path.to_string()
    }
}

#[allow(dead_code)]
pub fn get_local_image_url_sync(path: &str) -> String {
    if let Some(port) = get_server_port() {
        format_local_image_url(port, path)
    } else {
        let clean_path = if let Some(stripped) = path.strip_prefix("file://") {
            stripped
        } else {
            path
        };
        clean_path.to_string()
    }
}

fn get_mime_type(path: &Path) -> &'static str {
    match path
        .extension()
        .and_then(|ext| ext.to_str())
        .map(|s| s.to_ascii_lowercase())
        .as_deref()
    {
        Some("m4a") => "audio/mp4",
        Some("mp3") => "audio/mpeg",
        Some("flac") => "audio/flac",
        Some("wav") => "audio/wav",
        Some("ogg") => "audio/ogg",
        Some("opus") => "audio/opus",
        Some("aac") => "audio/aac",
        Some("webm") => "audio/webm",
        Some("jpg") | Some("jpeg") => "image/jpeg",
        Some("png") => "image/png",
        Some("webp") => "image/webp",
        _ => "application/octet-stream",
    }
}

async fn handle_connection(mut socket: TcpStream) {
    let mut buf = [0u8; 4096];
    let n = match socket.read(&mut buf).await {
        Ok(n) if n > 0 => n,
        _ => return,
    };

    let request_str = String::from_utf8_lossy(&buf[..n]);
    let mut lines = request_str.lines();
    let request_line = match lines.next() {
        Some(line) => line,
        None => return,
    };

    let mut parts = request_line.split_whitespace();
    let method = match parts.next() {
        Some(m) => m,
        None => return,
    };
    let raw_uri = match parts.next() {
        Some(u) => u,
        None => return,
    };

    if method != "GET" && method != "HEAD" {
        let _ = socket
            .write_all(b"HTTP/1.1 405 Method Not Allowed\r\nConnection: close\r\n\r\n")
            .await;
        return;
    }

    // Extract path query parameter: /audio?path=... or /image?path=...
    let query_param = if let Some(idx) = raw_uri.find("?path=") {
        &raw_uri[idx + 6..]
    } else {
        let _ = socket
            .write_all(b"HTTP/1.1 400 Bad Request\r\nConnection: close\r\n\r\n")
            .await;
        return;
    };

    // Remove any trailing parameters
    let encoded_path = if let Some(idx) = query_param.find('&') {
        &query_param[..idx]
    } else {
        query_param
    };

    let decoded_path = match urlencoding::decode(encoded_path) {
        Ok(d) => d.to_string(),
        Err(_) => {
            let _ = socket
                .write_all(b"HTTP/1.1 400 Bad Request\r\nConnection: close\r\n\r\n")
                .await;
            return;
        }
    };

    #[allow(unused_mut)]
    let mut clean_path = if let Some(stripped) = decoded_path.strip_prefix("file://") {
        stripped.to_string()
    } else {
        decoded_path
    };
    #[cfg(windows)]
    if clean_path.len() >= 3 && clean_path.starts_with('/') && clean_path.chars().nth(2) == Some(':') {
        clean_path = clean_path[1..].to_string();
    }

    let raw_file_path = PathBuf::from(clean_path);
    if raw_file_path.components().any(|c| c == std::path::Component::ParentDir) {
        let _ = socket
            .write_all(b"HTTP/1.1 403 Forbidden\r\nConnection: close\r\n\r\n")
            .await;
        return;
    }

    let file_path = match tokio::fs::canonicalize(&raw_file_path).await {
        Ok(p) => p,
        Err(_) => {
            let _ = socket
                .write_all(b"HTTP/1.1 404 Not Found\r\nConnection: close\r\n\r\n")
                .await;
            return;
        }
    };

    let metadata = match tokio::fs::metadata(&file_path).await {
        Ok(m) if m.is_file() => m,
        _ => {
            let _ = socket
                .write_all(b"HTTP/1.1 404 Not Found\r\nConnection: close\r\n\r\n")
                .await;
            return;
        }
    };

    let mime_type = get_mime_type(&file_path);
    if mime_type == "application/octet-stream" {
        let _ = socket
            .write_all(b"HTTP/1.1 403 Forbidden\r\nConnection: close\r\n\r\n")
            .await;
        return;
    }

    let file_size = metadata.len();

    // Look for Range header: "Range: bytes=start-end" or "Range: bytes=start-"
    let mut range_header = None;
    for line in lines {
        if line.is_empty() {
            break;
        }
        if line.to_ascii_lowercase().starts_with("range:") {
            if let Some(val) = line.split(':').nth(1) {
                range_header = Some(val.trim());
            }
            break;
        }
    }

    if let Some(range_val) = range_header {
        if let Some(bytes_spec) = range_val.strip_prefix("bytes=") {
            let mut range_parts = bytes_spec.split('-');
            let start_opt = range_parts.next().and_then(|s| s.parse::<u64>().ok());
            let end_opt = range_parts.next().and_then(|s| s.parse::<u64>().ok());

            let (start, end) = match (start_opt, end_opt) {
                (Some(s), Some(e)) => (s, e.min(file_size.saturating_sub(1))),
                (Some(s), None) => (s, file_size.saturating_sub(1)),
                (None, Some(suffix)) => {
                    let s = file_size.saturating_sub(suffix);
                    (s, file_size.saturating_sub(1))
                }
                _ => (0, file_size.saturating_sub(1)),
            };

            if start >= file_size || (start > end && file_size > 0) {
                let response = format!(
                    "HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */{}\r\nConnection: close\r\n\r\n",
                    file_size
                );
                let _ = socket.write_all(response.as_bytes()).await;
                return;
            }

            let content_length = end - start + 1;
            let header = format!(
                "HTTP/1.1 206 Partial Content\r\n\
                Content-Type: {}\r\n\
                Content-Range: bytes {}-{}/{}\r\n\
                Content-Length: {}\r\n\
                Accept-Ranges: bytes\r\n\
                Connection: close\r\n\r\n",
                mime_type, start, end, file_size, content_length
            );

            if socket.write_all(header.as_bytes()).await.is_err() {
                return;
            }

            if method == "HEAD" {
                return;
            }

            let mut file = match tokio::fs::File::open(&file_path).await {
                Ok(f) => f,
                Err(_) => return,
            };

            if file.seek(std::io::SeekFrom::Start(start)).await.is_err() {
                return;
            }

            let mut remaining = content_length;
            let mut chunk = [0u8; 65536];
            while remaining > 0 {
                let to_read = (remaining as usize).min(chunk.len());
                let n = match file.read(&mut chunk[..to_read]).await {
                    Ok(n) if n > 0 => n,
                    _ => break,
                };
                if socket.write_all(&chunk[..n]).await.is_err() {
                    break;
                }
                remaining -= n as u64;
            }
            return;
        }
    }

    // Full response (no range header or normal GET)
    let header = format!(
        "HTTP/1.1 200 OK\r\n\
        Content-Type: {}\r\n\
        Content-Length: {}\r\n\
        Accept-Ranges: bytes\r\n\
        Connection: close\r\n\r\n",
        mime_type, file_size
    );

    if socket.write_all(header.as_bytes()).await.is_err() {
        return;
    }

    if method == "HEAD" {
        return;
    }

    let mut file = match tokio::fs::File::open(&file_path).await {
        Ok(f) => f,
        Err(_) => return,
    };

    let mut chunk = [0u8; 65536];
    while let Ok(n) = file.read(&mut chunk).await {
        if n == 0 {
            break;
        }
        if socket.write_all(&chunk[..n]).await.is_err() {
            break;
        }
    }
}

pub async fn start_local_server() -> Result<u16, String> {
    if let Some(port) = LOCAL_SERVER_PORT.get() {
        return Ok(*port);
    }

    static INIT_LOCK: tokio::sync::Mutex<()> = tokio::sync::Mutex::const_new(());
    let _guard = INIT_LOCK.lock().await;

    if let Some(port) = LOCAL_SERVER_PORT.get() {
        return Ok(*port);
    }

    let listener = match TcpListener::bind("127.0.0.1:0").await {
        Ok(l) => l,
        Err(e1) => {
            tracing::warn!(target: "sonara_stream::local_server", "Failed to bind 127.0.0.1:0 ({}), attempting 0.0.0.0:0", e1);
            TcpListener::bind("0.0.0.0:0")
                .await
                .map_err(|e2| format!("Failed to bind local media streaming server (127.0.0.1: {}, 0.0.0.0: {})", e1, e2))?
        }
    };

    let port = listener
        .local_addr()
        .map_err(|e| format!("Failed to retrieve local server port: {}", e))?
        .port();

    let _ = LOCAL_SERVER_PORT.set(port);

    tokio::spawn(async move {
        while let Ok((socket, _)) = listener.accept().await {
            tokio::spawn(handle_connection(socket));
        }
    });

    tracing::info!(
        target: "sonara_stream::local_server",
        "Local media streaming server initialized on port {}",
        port
    );

    Ok(port)
}

#[tauri::command]
pub async fn get_local_audio_url(path: String) -> Result<String, String> {
    let port = start_local_server().await?;
    Ok(format_local_audio_url(port, &path))
}

#[cfg(test)]
mod tests {
    use super::*;

    async fn start_test_server() -> (u16, tokio::task::JoinHandle<()>) {
        let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
        let port = listener.local_addr().unwrap().port();
        let handle = tokio::spawn(async move {
            while let Ok((socket, _)) = listener.accept().await {
                tokio::spawn(handle_connection(socket));
            }
        });
        (port, handle)
    }

    #[tokio::test]
    async fn test_1_server_bind_and_port() {
        let port = start_local_server().await.expect("Server failed to bind");
        assert!(port > 0, "Expected a valid non-zero ephemeral port");
        let active_port = get_server_port().expect("Expected get_server_port to return Some");
        assert_eq!(port, active_port);
    }

    #[tokio::test]
    async fn test_2_serve_full_audio_file() {
        let (port, _server) = start_test_server().await;
        let temp_dir = std::env::temp_dir();
        let test_file = temp_dir.join(format!("test_full_{}.m4a", std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap().as_nanos()));
        let content = b"SONARA_AUDIO_TEST_PAYLOAD_FULL_FILE_DATA_1234567890";
        tokio::fs::write(&test_file, content).await.unwrap();

        let client = reqwest::Client::new();
        let url = format!("http://127.0.0.1:{}/audio?path={}", port, urlencoding::encode(&test_file.to_string_lossy()));
        let res = client.get(&url).send().await.unwrap();

        assert_eq!(res.status(), reqwest::StatusCode::OK);
        assert_eq!(res.headers().get("content-type").unwrap(), "audio/mp4");
        assert_eq!(res.headers().get("accept-ranges").unwrap(), "bytes");
        let body = res.bytes().await.unwrap();
        assert_eq!(&body[..], content);

        let _ = tokio::fs::remove_file(test_file).await;
    }

    #[tokio::test]
    async fn test_3_serve_range_request_partial_content() {
        let (port, _server) = start_test_server().await;
        let temp_dir = std::env::temp_dir();
        let test_file = temp_dir.join(format!("test_range_{}.mp3", std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap().as_nanos()));
        let content = b"0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        tokio::fs::write(&test_file, content).await.unwrap();

        let client = reqwest::Client::new();
        let url = format!("http://127.0.0.1:{}/audio?path={}", port, urlencoding::encode(&test_file.to_string_lossy()));
        let res = client
            .get(&url)
            .header("Range", "bytes=10-19")
            .send()
            .await
            .unwrap();

        assert_eq!(res.status(), reqwest::StatusCode::PARTIAL_CONTENT);
        assert_eq!(res.headers().get("content-type").unwrap(), "audio/mpeg");
        assert_eq!(res.headers().get("content-range").unwrap().to_str().unwrap(), format!("bytes 10-19/{}", content.len()));
        assert_eq!(res.headers().get("content-length").unwrap(), "10");
        let body = res.bytes().await.unwrap();
        assert_eq!(&body[..], &content[10..20]);

        let _ = tokio::fs::remove_file(test_file).await;
    }

    #[tokio::test]
    async fn test_4_serve_range_open_ended() {
        let (port, _server) = start_test_server().await;
        let temp_dir = std::env::temp_dir();
        let test_file = temp_dir.join(format!("test_open_range_{}.flac", std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap().as_nanos()));
        let content = b"HEADER_DATA_FOLLOWED_BY_FLAC_STREAM_BYTES";
        tokio::fs::write(&test_file, content).await.unwrap();

        let client = reqwest::Client::new();
        let url = format!("http://127.0.0.1:{}/audio?path={}", port, urlencoding::encode(&test_file.to_string_lossy()));
        let res = client
            .get(&url)
            .header("Range", "bytes=12-")
            .send()
            .await
            .unwrap();

        assert_eq!(res.status(), reqwest::StatusCode::PARTIAL_CONTENT);
        let expected_len = content.len() - 12;
        assert_eq!(res.headers().get("content-range").unwrap().to_str().unwrap(), format!("bytes 12-{}/{}", content.len() - 1, content.len()));
        assert_eq!(res.headers().get("content-length").unwrap().to_str().unwrap(), expected_len.to_string());
        let body = res.bytes().await.unwrap();
        assert_eq!(&body[..], &content[12..]);

        let _ = tokio::fs::remove_file(test_file).await;
    }

    #[tokio::test]
    async fn test_5_serve_range_suffix() {
        let (port, _server) = start_test_server().await;
        let temp_dir = std::env::temp_dir();
        let test_file = temp_dir.join(format!("test_suffix_{}.wav", std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap().as_nanos()));
        let content = b"1234567890TAIL_BYTES_END";
        tokio::fs::write(&test_file, content).await.unwrap();

        let client = reqwest::Client::new();
        let url = format!("http://127.0.0.1:{}/audio?path={}", port, urlencoding::encode(&test_file.to_string_lossy()));
        let res = client
            .get(&url)
            .header("Range", "bytes=-14")
            .send()
            .await
            .unwrap();

        assert_eq!(res.status(), reqwest::StatusCode::PARTIAL_CONTENT);
        let body = res.bytes().await.unwrap();
        assert_eq!(&body[..], b"TAIL_BYTES_END");

        let _ = tokio::fs::remove_file(test_file).await;
    }

    #[tokio::test]
    async fn test_6_serve_head_request() {
        let (port, _server) = start_test_server().await;
        let temp_dir = std::env::temp_dir();
        let test_file = temp_dir.join(format!("test_head_{}.ogg", std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap().as_nanos()));
        let content = b"OGG_HEADER_CONTENT_TEST";
        tokio::fs::write(&test_file, content).await.unwrap();

        let client = reqwest::Client::new();
        let url = format!("http://127.0.0.1:{}/audio?path={}", port, urlencoding::encode(&test_file.to_string_lossy()));
        let res = client.head(&url).send().await.unwrap();

        assert_eq!(res.status(), reqwest::StatusCode::OK);
        assert_eq!(res.headers().get("content-length").unwrap().to_str().unwrap(), content.len().to_string());
        assert_eq!(res.headers().get("accept-ranges").unwrap(), "bytes");
        let body = res.bytes().await.unwrap();
        assert!(body.is_empty(), "HEAD response body must be empty");

        let _ = tokio::fs::remove_file(test_file).await;
    }

    #[tokio::test]
    async fn test_7_serve_404_on_missing_file() {
        let (port, _server) = start_test_server().await;
        let client = reqwest::Client::new();
        let url = format!("http://127.0.0.1:{}/audio?path=nonexistent_file_path_12345.m4a", port);
        let res = client.get(&url).send().await.unwrap();

        assert_eq!(res.status(), reqwest::StatusCode::NOT_FOUND);
    }

    #[tokio::test]
    async fn test_8_serve_416_on_invalid_range() {
        let (port, _server) = start_test_server().await;
        let temp_dir = std::env::temp_dir();
        let test_file = temp_dir.join(format!("test_416_{}.m4a", std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap().as_nanos()));
        tokio::fs::write(&test_file, b"SHORT").await.unwrap();

        let client = reqwest::Client::new();
        let url = format!("http://127.0.0.1:{}/audio?path={}", port, urlencoding::encode(&test_file.to_string_lossy()));
        let res = client
            .get(&url)
            .header("Range", "bytes=999-1000")
            .send()
            .await
            .unwrap();

        assert_eq!(res.status(), reqwest::StatusCode::RANGE_NOT_SATISFIABLE);

        let _ = tokio::fs::remove_file(test_file).await;
    }

    #[test]
    fn test_9_mime_type_detection() {
        assert_eq!(get_mime_type(Path::new("song.m4a")), "audio/mp4");
        assert_eq!(get_mime_type(Path::new("song.mp3")), "audio/mpeg");
        assert_eq!(get_mime_type(Path::new("song.flac")), "audio/flac");
        assert_eq!(get_mime_type(Path::new("song.wav")), "audio/wav");
        assert_eq!(get_mime_type(Path::new("song.ogg")), "audio/ogg");
        assert_eq!(get_mime_type(Path::new("song.opus")), "audio/opus");
        assert_eq!(get_mime_type(Path::new("cover.jpg")), "image/jpeg");
        assert_eq!(get_mime_type(Path::new("cover.jpeg")), "image/jpeg");
        assert_eq!(get_mime_type(Path::new("cover.png")), "image/png");
        assert_eq!(get_mime_type(Path::new("cover.webp")), "image/webp");
        assert_eq!(get_mime_type(Path::new("unknown.xyz")), "application/octet-stream");
    }

    #[test]
    fn test_10_url_generation_with_spaces_and_special_chars() {
        let raw_path = "/data/user/0/com.sonara.stream/files/downloads/Laufey - From The Start [abc_123].m4a";
        let formatted = format_local_audio_url(12345, raw_path);
        assert_eq!(
            formatted,
            "http://127.0.0.1:12345/audio?path=%2Fdata%2Fuser%2F0%2Fcom.sonara.stream%2Ffiles%2Fdownloads%2FLaufey%20-%20From%20The%20Start%20%5Babc_123%5D.m4a"
        );
        assert!(formatted.contains("/audio?path="));
        assert!(formatted.contains("Laufey%20-%20From%20The%20Start%20%5Babc_123%5D.m4a"));
    }

    #[test]
    fn test_11_file_uri_prefix_handling() {
        let uri = "file:///storage/emulated/0/Download/song.mp3";
        let formatted = format_local_audio_url(8080, uri);
        assert!(formatted.contains("path=%2Fstorage%2Femulated%2F0%2FDownload%2Fsong.mp3"));

        #[cfg(windows)]
        {
            let win_uri = "file:///C:/Users/Music/song.mp3";
            let formatted_win = format_local_audio_url(8080, win_uri);
            assert!(formatted_win.contains("path=C%3A%2FUsers%2FMusic%2Fsong.mp3"));
        }
    }

    #[tokio::test]
    async fn test_12_concurrent_scrubbing_stress_test() {
        let (port, _server) = start_test_server().await;
        let temp_dir = std::env::temp_dir();
        let test_file = temp_dir.join(format!("test_stress_{}.m4a", std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap().as_nanos()));
        let mut content = Vec::with_capacity(100_000);
        for i in 0..100_000 {
            content.push((i % 256) as u8);
        }
        tokio::fs::write(&test_file, &content).await.unwrap();

        let client = reqwest::Client::new();
        let encoded_path = urlencoding::encode(&test_file.to_string_lossy()).to_string();

        let mut tasks = Vec::new();
        for i in 0..50 {
            let client = client.clone();
            let url = format!("http://127.0.0.1:{}/audio?path={}", port, encoded_path);
            let start = (i * 1000) as u64;
            let end = start + 500;
            let expected_slice = content[start as usize..=end as usize].to_vec();

            tasks.push(tokio::spawn(async move {
                let res = client
                    .get(&url)
                    .header("Range", format!("bytes={}-{}", start, end))
                    .send()
                    .await
                    .expect("Request failed");

                assert_eq!(res.status(), reqwest::StatusCode::PARTIAL_CONTENT);
                let body = res.bytes().await.expect("Failed to read body");
                assert_eq!(&body[..], &expected_slice[..]);
            }));
        }

        for task in tasks {
            task.await.unwrap();
        }

        let _ = tokio::fs::remove_file(test_file).await;
    }
}
