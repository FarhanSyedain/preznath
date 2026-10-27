import type { AnalysisResult, Example, Stage, StageEvent } from "../models";
import type { SatarkApi } from "../services";

export type Phase = "idle" | "running" | "done" | "error";

export interface AnalyzeState {
  phase: Phase;
  message: string;
  upiId: string;
  url: string;
  stages: Stage[];
  result: AnalysisResult | null;
  error: string | null;
  activeExampleId: string | null;
  startedAt: number | null;
  elapsedMs: number;
}

export const INITIAL_STAGES: Stage[] = [
  "Normalize & language",
  "Entity extraction",
  "Spam classifier",
  "Phishing classifier",
  "Scam intent (zero-shot)",
  "Known-scam similarity",
  "Rule signals",
  "Risk fusion",
].map((label, i) => ({
  index: i + 1,
  name: label.toLowerCase().replace(/[^a-z]+/g, "_"),
  label,
  status: "pending" as const,
  latency_ms: 0,
  signal: null,
  error: null,
}));

export const initialState = (): AnalyzeState => ({
  phase: "idle",
  message: "",
  upiId: "",
  url: "",
  stages: INITIAL_STAGES,
  result: null,
  error: null,
  activeExampleId: null,
  startedAt: null,
  elapsedMs: 0,
});

/** Pure transition: fold one stream event into state. Exported for tests. */
export function applyEvent(state: AnalyzeState, e: StageEvent): AnalyzeState {
  switch (e.type) {
    case "plan":
      return { ...state, stages: e.stages };
    case "stage":
      return { ...state, stages: upsertStage(state.stages, e.stage) };
    case "result":
      return {
        ...state,
        phase: "done",
        stages: upsertStage(state.stages, e.stage),
        result: e.result,
        elapsedMs: state.startedAt ? Date.now() - state.startedAt : state.elapsedMs,
      };
    case "error":
      return { ...state, phase: "error", error: e.message };
  }
}

function upsertStage(stages: Stage[], stage: Stage): Stage[] {
  const i = stages.findIndex((s) => s.index === stage.index);
  if (i === -1) return [...stages, stage].sort((a, b) => a.index - b.index);
  const next = stages.slice();
  next[i] = stage;
  return next;
}

type Listener = () => void;

/**
 * Headless view model. Owns state and commands; knows nothing about React or the DOM.
 * The React hook subscribes to it with useSyncExternalStore.
 */
export class AnalyzeViewModel {
  private state: AnalyzeState = initialState();
  private listeners = new Set<Listener>();
  private abort: AbortController | null = null;

  constructor(private readonly api: SatarkApi, private readonly now: () => number = () => Date.now()) {}

  // -- observable --------------------------------------------------------
  getState = (): AnalyzeState => this.state;

  subscribe = (l: Listener): (() => void) => {
    this.listeners.add(l);
    return () => this.listeners.delete(l);
  };

  private set(patch: Partial<AnalyzeState> | ((s: AnalyzeState) => AnalyzeState)): void {
    this.state = typeof patch === "function" ? patch(this.state) : { ...this.state, ...patch };
    this.listeners.forEach((l) => l());
  }

  // -- derived -------------------------------------------------------------
  get canAnalyze(): boolean {
    return this.state.message.trim().length > 0 && this.state.phase !== "running";
  }

  // -- commands ------------------------------------------------------------
  setMessage = (message: string): void => this.set({ message, activeExampleId: null });
  setUpiId = (upiId: string): void => this.set({ upiId });
  setUrl = (url: string): void => this.set({ url });

  loadExample = (example: Example): void => {
    this.cancel();
    this.set({
      ...initialState(),
      message: example.text,
      upiId: example.upi_id ?? "",
      url: example.url ?? "",
      activeExampleId: example.id,
    });
  };

  reset = (): void => {
    this.cancel();
    this.set(initialState());
  };

  cancel = (): void => {
    this.abort?.abort();
    this.abort = null;
  };

  analyze = async (): Promise<void> => {
    if (!this.canAnalyze) return;
    this.cancel();
    const controller = new AbortController();
    this.abort = controller;
    const startedAt = this.now();
    this.set({
      phase: "running",
      result: null,
      error: null,
      stages: INITIAL_STAGES,
      startedAt,
      elapsedMs: 0,
    });
    const { message, upiId, url } = this.state;
    try {
      await this.api.analyzeStream(
        { text: message, upi_id: upiId.trim() || null, url: url.trim() || null },
        (e) => this.set((s) => applyEvent(s, e)),
        controller.signal,
      );
      if (this.state.phase === "running") {
        this.set({ phase: "error", error: "The stream ended before a verdict was produced." });
      }
    } catch (err) {
      if (controller.signal.aborted) return;
      this.set({ phase: "error", error: err instanceof Error ? err.message : String(err) });
    } finally {
      if (this.abort === controller) this.abort = null;
    }
  };
}
