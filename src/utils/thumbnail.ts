/**
 * Optimizes image URLs to request appropriate high-resolution thumbnail variants,
 * saving network bandwidth while preventing blurry previews on high-DPI/mobile displays.
 */
export function getOptimizedThumbnail(
  url: string | undefined | null,
  variant: "list" | "card" | "full" = "list"
): string {
  if (!url) return "";

  // 1. YouTube / Google video thumbnails:
  // Strip downsampling query parameters (e.g. ?sqp=...) which force tiny previews.
  // Upgrade default.jpg / mqdefault.jpg to hqdefault.jpg (480x360) which is guaranteed to exist.
  // Do NOT blindly force maxresdefault.jpg because videos without custom 1080p thumbnails return HTTP 404 with a 120x90 blurry placeholder.
  if (url.includes("ytimg.com") || url.includes("youtube.com")) {
    const cleanUrl = url.split("?")[0];
    return cleanUrl.replace(/\/(?:default|mqdefault)\.(jpg|webp)$/, "/hqdefault.$1");
  }

  // 2. Google user content / channel avatars (yt3.ggpht.com, lh3.googleusercontent.com)
  // Upgrade tiny =s88 or =w120 previews to 512px for sharp rendering on mobile/high-DPI screens
  if (url.includes("ggpht.com") || url.includes("googleusercontent.com")) {
    return url
      .replace(/=s\d+(-[a-zA-Z0-9_-]+)?/, "=s512$1")
      .replace(/=w\d+-h\d+/, "=w512-h512");
  }

  // 3. iTunes / Apple Music artwork (mzstatic.com)
  if (url.includes("mzstatic.com")) {
    const size = variant === "list" ? "300x300bb" : "600x600bb";
    return url.replace(/\d+x\d+bb/, size);
  }

  return url;
}
