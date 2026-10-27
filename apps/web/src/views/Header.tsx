import type { Health } from "../models";

export function Header({ health }: { health: Health | null }) {
  const loaded = health?.models.filter((m) => m.loaded).length ?? 0;
  const total = health?.models.length ?? 0;
  const ready = health?.status === "ok";
  return (
    <header className="header">
      <div className="header__brand">
        <h1 className="wordmark">Preznath</h1>
        <p className="tagline">Check a payment message before you act on it.</p>
      </div>
      <div className="header__meta">
        <div className="status">
          <span className={`status__dot ${ready ? "status__dot--ok" : "status__dot--loading"}`} aria-hidden />
          <span className="label">
            {health === null ? "Connecting to API" : ready ? "Models ready" : `Loading models ${loaded}/${total}`}
          </span>
        </div>
        <dl className="meta">
          <div>
            <dt className="label">Pipeline</dt>
            <dd className="mono">8 stages · 6 models · CPU</dd>
          </div>
          <div>
            <dt className="label">Theme</dt>
            <dd className="mono">Digital safety · UPI fraud</dd>
          </div>
        </dl>
      </div>
    </header>
  );
}
