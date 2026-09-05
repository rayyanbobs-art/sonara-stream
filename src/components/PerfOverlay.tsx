import React, { useState, useEffect } from "react";
import { Activity, X, ChevronDown, ChevronUp } from "lucide-react";
import { subscribePerfMetrics, PerfMetrics, perf } from "../utils/perf";

interface PerfOverlayProps {
  visible: boolean;
  onClose?: () => void;
}

export const PerfOverlay: React.FC<PerfOverlayProps> = ({ visible, onClose }) => {
  const [metrics, setMetrics] = useState<PerfMetrics>(perf.getMetrics());
  const [minimized, setMinimized] = useState(false);

  useEffect(() => {
    return subscribePerfMetrics(setMetrics);
  }, []);

  if (!visible) return null;

  const budgets = perf.getBudgets();

  const getStatus = (val: number | null, budget: number, isFps = false) => {
    if (val === null) return "dim";
    if (isFps) {
      return val >= budget ? "hit" : "miss";
    }
    return val <= budget ? "hit" : "miss";
  };

  return (
    <div className={`sonara-perf-overlay ${minimized ? "minimized" : ""}`}>
      <div className="perf-header">
        <div className="perf-title">
          <Activity size={14} className="pulse-anim" />
          <span>PERF BUDGETS (LIVE)</span>
        </div>
        <div className="perf-actions">
          <button
            type="button"
            className="perf-btn"
            onClick={() => setMinimized((m) => !m)}
            title={minimized ? "Expand" : "Minimize"}
          >
            {minimized ? <ChevronUp size={13} /> : <ChevronDown size={13} />}
          </button>
          {onClose && (
            <button
              type="button"
              className="perf-btn"
              onClick={onClose}
              title="Close Overlay"
            >
              <X size={13} />
            </button>
          )}
        </div>
      </div>

      {!minimized && (
        <div className="perf-grid">
          <div className={`perf-row ${getStatus(metrics.b1_coldStartMs, budgets.b1)}`}>
            <span className="metric-id">B1 Cold Start</span>
            <span className="metric-budget">&le;{budgets.b1}ms</span>
            <span className="metric-val">
              {metrics.b1_coldStartMs !== null ? `${metrics.b1_coldStartMs}ms` : "--"}
            </span>
          </div>

          <div className={`perf-row ${getStatus(metrics.b2_keystrokeMs, budgets.b2)}`}>
            <span className="metric-id">B2 Keystroke</span>
            <span className="metric-budget">&le;{budgets.b2}ms</span>
            <span className="metric-val">
              {metrics.b2_keystrokeMs !== null ? `${metrics.b2_keystrokeMs}ms` : "--"}
            </span>
          </div>

          <div className={`perf-row ${getStatus(metrics.b3_firstResultMs, budgets.b3)}`}>
            <span className="metric-id">B3 Search 1st</span>
            <span className="metric-budget">&le;{budgets.b3}ms</span>
            <span className="metric-val">
              {metrics.b3_firstResultMs !== null ? `${metrics.b3_firstResultMs}ms` : "--"}
            </span>
          </div>

          <div className={`perf-row ${getStatus(metrics.b4_allResultsMs, budgets.b4)}`}>
            <span className="metric-id">B4 Search All</span>
            <span className="metric-budget">&le;{budgets.b4}ms</span>
            <span className="metric-val">
              {metrics.b4_allResultsMs !== null ? `${metrics.b4_allResultsMs}ms` : "--"}
            </span>
          </div>

          <div className={`perf-row ${getStatus(metrics.b5_cacheHitPlayMs, budgets.b5)}`}>
            <span className="metric-id">B5 Play (Hit)</span>
            <span className="metric-budget">&le;{budgets.b5}ms</span>
            <span className="metric-val">
              {metrics.b5_cacheHitPlayMs !== null ? `${metrics.b5_cacheHitPlayMs}ms` : "--"}
            </span>
          </div>

          <div className={`perf-row ${getStatus(metrics.b6_cacheMissPlayMs, budgets.b6)}`}>
            <span className="metric-id">B6 Play (Miss)</span>
            <span className="metric-budget">&le;{budgets.b6}ms</span>
            <span className="metric-val">
              {metrics.b6_cacheMissPlayMs !== null ? `${metrics.b6_cacheMissPlayMs}ms` : "--"}
            </span>
          </div>

          <div className={`perf-row ${getStatus(metrics.b7_nextPrefetchedMs, budgets.b7)}`}>
            <span className="metric-id">B7 Next (Prefetch)</span>
            <span className="metric-budget">&le;{budgets.b7}ms</span>
            <span className="metric-val">
              {metrics.b7_nextPrefetchedMs !== null ? `${metrics.b7_nextPrefetchedMs}ms` : "--"}
            </span>
          </div>

          <div className={`perf-row ${getStatus(metrics.b8_scrollFps, budgets.b8_fps, true)}`}>
            <span className="metric-id">B8 Scroll / LongTask</span>
            <span className="metric-budget">&ge;60fps / &le;50ms</span>
            <span className="metric-val">
              {metrics.b8_scrollFps !== null ? `${metrics.b8_scrollFps}fps` : "--"}
              {metrics.b8_maxLongTaskMs !== null ? ` (${metrics.b8_maxLongTaskMs}ms)` : ""}
            </span>
          </div>

          <div className={`perf-row ${getStatus(metrics.b9_buttonFeedbackMs, budgets.b9)}`}>
            <span className="metric-id">B9 Button Feedback</span>
            <span className="metric-budget">&le;16ms</span>
            <span className="metric-val">
              {metrics.b9_buttonFeedbackMs !== null ? `${metrics.b9_buttonFeedbackMs}ms` : "--"}
            </span>
          </div>

          <div className={`perf-row ${getStatus(metrics.b10_memoryMb, budgets.b10)}`}>
            <span className="metric-id">B10 Memory</span>
            <span className="metric-budget">&le;250MB</span>
            <span className="metric-val">
              {metrics.b10_memoryMb !== null ? `${metrics.b10_memoryMb}MB` : "--"}
            </span>
          </div>
        </div>
      )}
    </div>
  );
};
