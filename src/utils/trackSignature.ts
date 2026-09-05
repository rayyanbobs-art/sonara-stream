import { Track } from "../types";

/**
 * Checks if two tracks represent the same song.
 * Uses the canonical signature computed by the Rust backend, falling back to ID comparison,
 * subset token matching, and high token overlap.
 */
export function isSameSong(
  a: Track | null | undefined,
  b: Track | null | undefined
): boolean {
  if (!a || !b) return false;
  if (a.id === b.id) return true;

  const sigA = (a.signature || `${a.artist} ${a.title}`).toLowerCase().trim();
  const sigB = (b.signature || `${b.artist} ${b.title}`).toLowerCase().trim();

  if (!sigA || !sigB) return false;
  if (sigA === sigB) return true;

  const wordsA = new Set(sigA.split(/\s+/).filter(Boolean));
  const wordsB = new Set(sigB.split(/\s+/).filter(Boolean));

  if (wordsA.size === 0 || wordsB.size === 0) return false;

  let common = 0;
  for (const w of wordsA) {
    if (wordsB.has(w)) common++;
  }

  const minSize = Math.min(wordsA.size, wordsB.size);
  // Full subset match (e.g. {"atif", "aslam", "lamhe", "woh"} inside {"atif", "aslam", "baatein", "lamhe", "woh"})
  if (minSize >= 2 && common >= minSize) {
    return true;
  }

  // 75%+ word similarity
  const unionSize = wordsA.size + wordsB.size - common;
  if (unionSize > 0 && common / unionSize >= 0.75) {
    return true;
  }

  return false;
}
