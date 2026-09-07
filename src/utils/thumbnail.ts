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
  // Strip downsampling query parameters (e.g. ?sqp=...) which force tiny previews,
  // and ensure at least hqdefault.jpg (480x360) or maxresdefault.jpg is requested.
  if (url.includes("ytimg.com") || url.includes("youtube.com")) {
    const cleanUrl = url.split("?")[0];
    if (variant === "full") {
      return cleanUrl.replace(/\/(?:default|mqdefault|hqdefault)\.jpg$/, "/maxresdefault.jpg");
    }
    return cleanUrl.replace(/\/(?:default|mqdefault)\.jpg$/, "/hqdefault.jpg");
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
