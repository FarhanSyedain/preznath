import type { Entity } from "../models";

interface Props {
  entities: Entity[];
  language: string;
  translated: string | null;
}

const KIND_LABEL: Record<Entity["kind"], string> = {
  upi_vpa: "UPI ID",
  phone: "Phone",
  url: "Link",
  amount: "Amount",
  credential: "Credential",
  bank: "Bank",
  app: "App",
};

export function EntitiesPanel({ entities, language, translated }: Props) {
  return (
    <div className="section">
      <div className="section__head">
        <span className="label">
          <span className="section__index">C</span>What we found
        </span>
        <span className="mono" style={{ fontSize: 12, color: "var(--ink-2)" }}>
          lang {language}
        </span>
      </div>
      {translated && (
        <div className="translated">
          <span className="label">Translated</span>
          <p>{translated}</p>
        </div>
      )}
      {entities.length === 0 ? (
        <p className="muted">No UPI IDs, links, phone numbers or amounts in this message.</p>
      ) : (
        <table className="entities">
          <tbody>
            {entities.map((e, i) => (
              <tr key={`${e.kind}-${e.value}-${i}`} className={e.valid === false ? "entities__row--bad" : ""}>
                <td className="label entities__kind">{KIND_LABEL[e.kind]}</td>
                <td className="mono entities__value">{e.value}</td>
                <td className="entities__note">
                  {e.valid === true && <span className="mono entities__ok">valid</span>}
                  {e.valid === false && <span className="mono entities__bad">{e.note ?? "unknown"}</span>}
                  {e.valid === true && e.note && <span className="entities__sub"> · {e.note}</span>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
