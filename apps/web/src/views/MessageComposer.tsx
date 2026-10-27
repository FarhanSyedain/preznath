import type { Phase } from "../viewmodels/AnalyzeViewModel";

interface Props {
  message: string;
  upiId: string;
  url: string;
  phase: Phase;
  canAnalyze: boolean;
  onMessage: (v: string) => void;
  onUpiId: (v: string) => void;
  onUrl: (v: string) => void;
  onAnalyze: () => void;
  onReset: () => void;
}

export function MessageComposer(p: Props) {
  const running = p.phase === "running";
  return (
    <form
      className="composer"
      onSubmit={(e) => {
        e.preventDefault();
        p.onAnalyze();
      }}
    >
      <div className="section__head">
        <span className="label">
          <span className="section__index">A</span>Message
        </span>
        <span className="mono composer__count">{p.message.length} chars</span>
      </div>
      <textarea
        className="composer__text"
        value={p.message}
        onChange={(e) => p.onMessage(e.target.value)}
        onKeyDown={(e) => {
          if ((e.metaKey || e.ctrlKey) && e.key === "Enter" && p.canAnalyze) {
            e.preventDefault();
            p.onAnalyze();
          }
        }}
        placeholder="Paste the SMS or WhatsApp message here."
        rows={7}
        spellCheck={false}
        disabled={running}
      />

      <div className="composer__row">
        <label className="field">
          <span className="label">UPI ID (optional)</span>
          <input
            className="field__input mono"
            value={p.upiId}
            onChange={(e) => p.onUpiId(e.target.value)}
            placeholder="name@okaxis"
            autoComplete="off"
            spellCheck={false}
            disabled={running}
          />
        </label>
        <label className="field">
          <span className="label">Link (optional)</span>
          <input
            className="field__input mono"
            value={p.url}
            onChange={(e) => p.onUrl(e.target.value)}
            placeholder="https://"
            autoComplete="off"
            spellCheck={false}
            disabled={running}
          />
        </label>
      </div>

      <div className="composer__actions">
        <button type="submit" className="btn btn--primary" disabled={!p.canAnalyze}>
          {running ? "Checking…" : "Check message"}
        </button>
        <button type="button" className="btn btn--ghost" onClick={p.onReset} disabled={running && !p.message}>
          Clear
        </button>
        <span className="composer__hint mono">⌘↵ to run</span>
      </div>
    </form>
  );
}
