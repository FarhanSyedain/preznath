export function AdvicePanel({ advice }: { advice: string[] }) {
  return (
    <div className="section section--advice">
      <div className="section__head">
        <span className="label">
          <span className="section__index">12</span>What to do
        </span>
      </div>
      <ul className="advice">
        {advice.map((a, i) => (
          <li key={i} className="advice__item">
            {a}
          </li>
        ))}
      </ul>
    </div>
  );
}
