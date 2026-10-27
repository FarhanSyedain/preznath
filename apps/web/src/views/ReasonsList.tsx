export function ReasonsList({ reasons }: { reasons: string[] }) {
  return (
    <div className="section">
      <div className="section__head">
        <span className="label">
          <span className="section__index">11</span>Why
        </span>
      </div>
      <ol className="reasons">
        {reasons.map((r, i) => (
          <li key={i} className="reasons__item">
            <span className="mono reasons__num">{(i + 1).toString().padStart(2, "0")}</span>
            <span>{r}</span>
          </li>
        ))}
      </ol>
    </div>
  );
}
