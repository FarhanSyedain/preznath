import type { Stage } from "../models";
import type { Phase } from "../viewmodels/AnalyzeViewModel";

interface Props {
  stages: Stage[];
  phase: Phase;
  elapsedMs: number | null;
}

const pad = (n: number) => n.toString().padStart(2, "0");

export function PipelineTrace({ stages, phase, elapsedMs }: Props) {
  return (
    <div className="trace">
      <div className="section__head">
        <span className="label">
          <span className="section__index">01–08</span>Pipeline trace
        </span>
        <span className="mono trace__total">
          {phase === "running" && "running"}
          {phase === "done" && elapsedMs !== null && `${elapsedMs} ms total`}
          {phase === "idle" && "idle"}
          {phase === "error" && "stopped"}
        </span>
      </div>
      <ol className="trace__list">
        {stages.map((s) => (
          <li key={s.index} className={`trace__row trace__row--${s.status}`}>
            <span className="trace__index mono">{pad(s.index)}</span>
            <span className="trace__label">{s.label}</span>
            <span className="trace__bar" aria-hidden>
              {s.signal && s.signal.weight > 0 && (
                <span className="trace__fill" style={{ width: `${Math.round(s.signal.score * 100)}%` }} />
              )}
              {s.status === "running" && <span className="trace__pulse" />}
            </span>
            <span className="trace__score mono">
              {s.signal && s.signal.weight > 0 ? s.signal.score.toFixed(2) : s.signal && s.index === 8 ? `${Math.round(s.signal.score * 100)}` : "—"}
            </span>
            <span className="trace__latency mono">
              {s.status === "done" ? `${s.latency_ms} ms` : s.status === "running" ? "…" : s.status === "failed" ? "failed" : ""}
            </span>
            <span className={`trace__status mono trace__status--${s.status}`}>{statusGlyph(s.status)}</span>
          </li>
        ))}
      </ol>
    </div>
  );
}

function statusGlyph(status: Stage["status"]): string {
  switch (status) {
    case "pending":
      return "○";
    case "running":
      return "◐";
    case "done":
      return "●";
    case "skipped":
      return "◌";
    case "failed":
      return "×";
  }
}
