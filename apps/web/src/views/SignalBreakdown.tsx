import type { Signal } from "../models";

export function SignalBreakdown({ signals }: { signals: Signal[] }) {
  const weighted = signals.filter((s) => s.weight > 0);
  const total = weighted.reduce((a, s) => a + s.weight, 0) || 1;
  return (
    <div className="section">
      <div className="section__head">
        <span className="label">
          <span className="section__index">10</span>Signal breakdown
        </span>
        <span className="label" style={{ color: "var(--ink-3)" }}>
          score × weight
        </span>
      </div>
      <ul className="signals">
        {weighted.map((s) => (
          <li key={s.name} className="signal">
            <div className="signal__head">
              <span className="signal__label">{s.label}</span>
              <span className="signal__nums mono">
                {s.score.toFixed(2)} × {(s.weight / total).toFixed(2)}
              </span>
            </div>
            <div className="signal__bar">
              <span className="signal__fill" style={{ width: `${Math.round(s.score * 100)}%` }} />
            </div>
            <p className="signal__evidence">{s.evidence}</p>
          </li>
        ))}
      </ul>
    </div>
  );
}
