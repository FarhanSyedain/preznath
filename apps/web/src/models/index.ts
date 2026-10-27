/** Domain types mirroring the API DTOs (apps/api/satark/presentation/schemas.py). */

export type VerdictLabel = "SAFE" | "SUSPICIOUS" | "FRAUD";
export type StageStatus = "pending" | "running" | "done" | "skipped" | "failed";
export type EntityKind = "upi_vpa" | "phone" | "url" | "amount" | "credential" | "bank" | "app";

export interface Entity {
  kind: EntityKind;
  value: string;
  valid: boolean | null;
  note: string | null;
}

export interface Signal {
  name: string;
  label: string;
  score: number; // 0..1
  weight: number;
  evidence: string;
  latency_ms: number;
  details: Record<string, unknown>;
}

export interface Stage {
  index: number;
  name: string;
  label: string;
  status: StageStatus;
  latency_ms: number;
  signal: Signal | null;
  error: string | null;
}

export interface Verdict {
  label: VerdictLabel;
  score: number; // 0..100
  confidence: number; // 0..1
  reasons: string[];
  advice: string[];
}

export interface AnalysisResult {
  text: string;
  upi_id: string | null;
  url: string | null;
  language: string;
  language_confidence: number;
  translated_text: string | null;
  entities: Entity[];
  signals: Signal[];
  stages: Stage[];
  verdict: Verdict;
  total_latency_ms: number;
}

export interface AnalyzeRequest {
  text: string;
  upi_id?: string | null;
  url?: string | null;
}

export type StageEvent =
  | { type: "plan"; stages: Stage[] }
  | { type: "stage"; stage: Stage }
  | { type: "result"; stage: Stage; result: AnalysisResult }
  | { type: "error"; message: string };

export interface ModelStatus {
  name: string;
  model_id: string;
  loaded: boolean;
}

export interface Health {
  status: "ok" | "loading";
  models: ModelStatus[];
}

export interface Example {
  id: string;
  title: string;
  expected: VerdictLabel;
  text: string;
  upi_id?: string;
  url?: string;
}
