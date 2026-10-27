import type { Example } from "../models";

interface Props {
  examples: Example[];
  activeId: string | null;
  onPick: (e: Example) => void;
}

export function ExampleChips({ examples, activeId, onPick }: Props) {
  return (
    <div className="section">
      <div className="section__head">
        <span className="label">
          <span className="section__index">B</span>Examples
        </span>
        <span className="label" style={{ color: "var(--ink-3)" }}>
          Expected verdict
        </span>
      </div>
      <ul className="examples">
        {examples.map((ex) => (
          <li key={ex.id}>
            <button
              type="button"
              className={`example ${activeId === ex.id ? "example--active" : ""}`}
              onClick={() => onPick(ex)}
            >
              <span className="example__title">{ex.title}</span>
              <span className={`example__tag example__tag--${ex.expected.toLowerCase()} mono`}>{ex.expected}</span>
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}
