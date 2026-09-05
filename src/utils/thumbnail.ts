/**
 * Optimizes image URLs to request appropriate thumbnail variants,
 * saving network bandwidth and image decoding overhead.
 */
export function getOptimizedThumbnail(
  url: string | undefined | null,
  variant: 'list' | 'card' | 'full' = 'list'
): string {
  if (!url) return '';

  // YouTube thumbnails:
  // mqdefault (320x180) is ~12-18KB vs hqdefault (~40-60KB) or maxresdefault (~150KB)
  if (variant === 'list' && (url.includes('ytimg.com') || url.includes('youtube.com'))) {
    return url.replace(/hqdefault\.jpg|maxresdefault\.jpg|sddefault\.jpg/, 'mqdefault.jpg');
  }

  // iTunes/Apple Music artwork
  if (url.includes('mzstatic.com')) {
    if (variant === 'list') {
      return url.replace(/\d+x\d+bb/, '120x120bb');
    }
  }

  return url;
}
