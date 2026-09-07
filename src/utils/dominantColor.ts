// In-memory cache keyed by track.id
const dominantColorCache = new Map<string, string>();

/**
 * Computes dominant color from image URL using an offscreen 10x10 canvas.
 * Caches result by trackId. Falls back to a dark tone if CORS or load fails.
 */
export async function getDominantColor(trackId: string, imageUrl: string): Promise<string> {
  if (dominantColorCache.has(trackId)) {
    return dominantColorCache.get(trackId)!;
  }

  if (!imageUrl) {
    const fallback = "rgba(30, 32, 40, 0.95)";
    dominantColorCache.set(trackId, fallback);
    return fallback;
  }

  return new Promise((resolve) => {
    const img = new Image();
    img.crossOrigin = "anonymous";
    img.referrerPolicy = "no-referrer";

    const timeout = setTimeout(() => {
      const fallback = "rgba(35, 30, 45, 0.95)";
      dominantColorCache.set(trackId, fallback);
      resolve(fallback);
    }, 1500);

    img.onload = () => {
      clearTimeout(timeout);
      try {
        const canvas = document.createElement("canvas");
        canvas.width = 10;
        canvas.height = 10;
        const ctx = canvas.getContext("2d");
        if (!ctx) {
          throw new Error("Could not get 2d context");
        }

        ctx.drawImage(img, 0, 0, 10, 10);
        const imgData = ctx.getImageData(0, 0, 10, 10).data;

        let totalR = 0;
        let totalG = 0;
        let totalB = 0;
        let count = 0;

        for (let i = 0; i < imgData.length; i += 4) {
          const r = imgData[i];
          const g = imgData[i + 1];
          const b = imgData[i + 2];
          const a = imgData[i + 3];

          // Skip transparent or near-black/near-white extremes for richer color
          if (a < 128) continue;
          const lum = 0.299 * r + 0.587 * g + 0.114 * b;
          if (lum < 15 || lum > 240) continue;

          totalR += r;
          totalG += g;
          totalB += b;
          count++;
        }

        if (count === 0) {
          // If all pixels were extreme, average all non-transparent pixels
          for (let i = 0; i < imgData.length; i += 4) {
            totalR += imgData[i];
            totalG += imgData[i + 1];
            totalB += imgData[i + 2];
            count++;
          }
        }

        const avgR = Math.round(totalR / Math.max(1, count));
        const avgG = Math.round(totalG / Math.max(1, count));
        const avgB = Math.round(totalB / Math.max(1, count));

        const color = `rgba(${avgR}, ${avgG}, ${avgB}, 0.85)`;
        dominantColorCache.set(trackId, color);
        resolve(color);
      } catch {
        const fallback = "rgba(35, 30, 45, 0.95)";
        dominantColorCache.set(trackId, fallback);
        resolve(fallback);
      }
    };

    img.onerror = () => {
      clearTimeout(timeout);
      const fallback = "rgba(35, 30, 45, 0.95)";
      dominantColorCache.set(trackId, fallback);
      resolve(fallback);
    };

    img.src = imageUrl;
  });
}

export function clearDominantColorCache(): void {
  dominantColorCache.clear();
}
