import type { Verdict } from "../models";

export function VerdictCard({ verdict }: { verdict: Verdict }) {
  const cls = verdict.label.toLowerCase();
  return (
    <div className={`verdict verdict--${cls}`}>
      <div className="section__head">
        <span className="label">
          <span className="section__index">09</span>Verdict
        </span>
        <span className="label">Confidence {Math.round(verdict.confidence * 100)}%</span>
      </div>
      <div className="verdict__row">
        <span className="verdict__word">{verdict.label}</span>
        <span className="verdict__score mono">
          <span className="verdict__num">{verdict.score}</span>
          <span className="verdict__den">/100</span>
        </span>
      </div>
      <div className="verdict__scale" aria-hidden>
        <span className="verdict__marker" style={{ left: `${verdict.score}%` }} />
        <span className="verdict__tick" style={{ left: "30%" }} />
        <span className="verdict__tick" style={{ left: "65%" }} />
      </div>
      <div className="verdict__legend mono">
        <span>0 safe</span>
        <span>30 suspicious</span>
        <span>65 fraud</span>
        <span>100</span>
      </div>
    </div>
  );
}
