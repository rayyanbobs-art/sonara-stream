import { describe, it, expect } from "vitest";
import { getFormattedDuration, shuffleQueue, parseLRC } from "../lib/helpers";

describe("helpers", () => {
  describe("getFormattedDuration", () => {
    it("formats sub-minute duration correctly", () => {
      expect(getFormattedDuration(0)).toBe("0:00");
      expect(getFormattedDuration(5)).toBe("0:05");
      expect(getFormattedDuration(42)).toBe("0:42");
    });

    it("formats minutes duration correctly", () => {
      expect(getFormattedDuration(60)).toBe("1:00");
      expect(getFormattedDuration(75)).toBe("1:15");
      expect(getFormattedDuration(234)).toBe("3:54");
    });

    it("formats hours duration correctly with padded minutes and seconds", () => {
      expect(getFormattedDuration(3600)).toBe("1:00:00");
      expect(getFormattedDuration(3665)).toBe("1:01:05");
      expect(getFormattedDuration(7325)).toBe("2:02:05");
    });
  });

  describe("shuffleQueue", () => {
    it("returns an array of the same length containing all original elements", () => {
      const mockItems: QueueItem[] = [
        { id: "item-1", songId: 101 },
        { id: "item-2", songId: 102 },
        { id: "item-3", songId: 103 },
        { id: "item-4", songId: 104 },
      ];

      const shuffled = shuffleQueue(mockItems);
      expect(shuffled).toHaveLength(mockItems.length);
      expect(shuffled.map((i) => i.id).sort()).toEqual(mockItems.map((i) => i.id).sort());
    });

    it("handles empty and single-item queues without mutation", () => {
      expect(shuffleQueue([])).toEqual([]);
      const single: QueueItem[] = [{ id: "1", songId: 1 }];
      expect(shuffleQueue(single)).toEqual(single);
    });
  });

  describe("parseLRC", () => {
    it("parses valid LRC formatted text into sorted lyric lines", () => {
      const sampleLrc = `
[00:12.34]First line of lyrics
[00:05.10]Intro line
[01:02.500]Chorus line
`;
      const result = parseLRC(sampleLrc);
      expect(result).toHaveLength(3);

      // Verify sorted by time
      expect(result[0].text).toBe("Intro line");
      expect(result[0].time).toBeCloseTo(5.1, 2);

      expect(result[1].text).toBe("First line of lyrics");
      expect(result[1].time).toBeCloseTo(12.34, 2);

      expect(result[2].text).toBe("Chorus line");
      expect(result[2].time).toBeCloseTo(62.5, 2);
    });

    it("ignores lines without valid timestamp format", () => {
      const invalidLrc = `
[ti:Song Title]
[ar:Artist Name]
Plain text line without timestamp
[00:10.00]Valid line
`;
      const result = parseLRC(invalidLrc);
      expect(result).toHaveLength(1);
      expect(result[0].text).toBe("Valid line");
      expect(result[0].time).toBeCloseTo(10.0, 2);
    });
  });
});
