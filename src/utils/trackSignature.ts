import { Track } from "../types";

/**
 * Checks if two tracks represent the same song.
 * Uses the canonical signature computed by the Rust backend, falling back to ID comparison.
 */
export function isSameSong(
  a: Track | null | undefined,
  b: Track | null | undefined
): boolean {
  if (!a || !b) return false;
  if (a.id === b.id) return true;
  if (a.signature && b.signature && a.signature.trim() === b.signature.trim()) {
    return true;
  }
  return false;
}
