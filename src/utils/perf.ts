// Performance Measurement Utility for Sonara Stream
export interface PerfMetrics {
  b1_coldStartMs: number | null;
  b2_keystrokeMs: number | null;
  b3_firstResultMs: number | null;
  b4_allResultsMs: number | null;
  b5_cacheHitPlayMs: number | null;
  b6_cacheMissPlayMs: number | null;
  b7_nextPrefetchedMs: number | null;
  b8_scrollFps: number | null;
  b8_maxLongTaskMs: number | null;
  b9_buttonFeedbackMs: number | null;
  b10_memoryMb: number | null;
  b11_installerMb: number | null;
  lastUpdated: number;
}

const BUDGETS = {
  b1: 400,
  b2: 100,
  b3: 700,
  b4: 1500,
  b5: 150,
  b6: 1200,
  b7: 100,
  b8_fps: 60,
  b8_task: 50,
  b9: 16,
  b10: 250,
};

let currentMetrics: PerfMetrics = {
  b1_coldStartMs: null,
  b2_keystrokeMs: null,
  b3_firstResultMs: null,
  b4_allResultsMs: null,
  b5_cacheHitPlayMs: null,
  b6_cacheMissPlayMs: null,
  b7_nextPrefetchedMs: null,
  b8_scrollFps: null,
  b8_maxLongTaskMs: null,
  b9_buttonFeedbackMs: null,
  b10_memoryMb: null,
  b11_installerMb: null,
  lastUpdated: Date.now(),
};

type MetricListener = (metrics: PerfMetrics) => void;
const listeners = new Set<MetricListener>();

export function subscribePerfMetrics(listener: MetricListener): () => void {
  listeners.add(listener);
  listener(currentMetrics);
  return () => {
    listeners.delete(listener);
  };
}

function notifyListeners() {
  currentMetrics = { ...currentMetrics, lastUpdated: Date.now() };
  listeners.forEach((fn) => fn(currentMetrics));
}

export const perf = {
  mark(name: string) {
    if (typeof performance !== 'undefined' && performance.mark) {
      performance.mark(name);
    }
  },

  measure(name: string, startMark: string, endMark?: string): number | null {
    if (typeof performance === 'undefined' || !performance.measure) return null;
    try {
      const entry = endMark
        ? performance.measure(name, startMark, endMark)
        : performance.measure(name, startMark);
      return Math.round(entry.duration);
    } catch {
      return null;
    }
  },

  recordColdStart(ms: number) {
    currentMetrics.b1_coldStartMs = ms;
    notifyListeners();
  },

  recordKeystroke(ms: number) {
    currentMetrics.b2_keystrokeMs = ms;
    notifyListeners();
  },

  recordFirstResult(ms: number) {
    currentMetrics.b3_firstResultMs = ms;
    notifyListeners();
  },

  recordAllResults(ms: number) {
    currentMetrics.b4_allResultsMs = ms;
    notifyListeners();
  },

  recordPlaybackStart(ms: number, isCacheHit: boolean, isPrefetchedNext: boolean) {
    if (isPrefetchedNext) {
      currentMetrics.b7_nextPrefetchedMs = ms;
    } else if (isCacheHit) {
      currentMetrics.b5_cacheHitPlayMs = ms;
    } else {
      currentMetrics.b6_cacheMissPlayMs = ms;
    }
    notifyListeners();
  },

  recordButtonFeedback(ms: number) {
    currentMetrics.b9_buttonFeedbackMs = ms;
    notifyListeners();
  },

  recordMemory(mb: number) {
    currentMetrics.b10_memoryMb = mb;
    notifyListeners();
  },

  recordScroll(fps: number, maxLongTaskMs: number) {
    currentMetrics.b8_scrollFps = Math.round(fps);
    currentMetrics.b8_maxLongTaskMs = Math.round(maxLongTaskMs);
    notifyListeners();
  },

  getMetrics(): PerfMetrics {
    return { ...currentMetrics };
  },

  getBudgets() {
    return BUDGETS;
  },
};

if (typeof window !== 'undefined' && 'PerformanceObserver' in window) {
  try {
    const observer = new PerformanceObserver((list) => {
      for (const entry of list.getEntries()) {
        const duration = entry.duration;
        if (
          currentMetrics.b8_maxLongTaskMs === null ||
          duration > currentMetrics.b8_maxLongTaskMs
        ) {
          currentMetrics.b8_maxLongTaskMs = Math.round(duration);
          notifyListeners();
        }
      }
    });
    observer.observe({ entryTypes: ['longtask'] });
  } catch {
    // Ignore in unsupported environments
  }
}