import type { AnalysisResult, AnalyzeRequest, Health, StageEvent } from "../models";

export interface SatarkApi {
  health(): Promise<Health>;
  analyze(req: AnalyzeRequest, signal?: AbortSignal): Promise<AnalysisResult>;
  /** Streams one StageEvent per pipeline transition; resolves when the stream closes. */
  analyzeStream(req: AnalyzeRequest, onEvent: (e: StageEvent) => void, signal?: AbortSignal): Promise<void>;
}

/**
 * Parses a text/event-stream body. Exposed for unit tests.
 * Yields `{event, data}` records as they complete.
 */
export function parseSseChunk(buffer: string): { records: { event: string; data: string }[]; rest: string } {
  const records: { event: string; data: string }[] = [];
  let idx: number;
  // Servers may use \r\n (sse-starlette does). Normalise first.
  buffer = buffer.replace(/\r\n/g, "\n");
  // Events are separated by a blank line.
  while ((idx = buffer.indexOf("\n\n")) !== -1) {
    const raw = buffer.slice(0, idx);
    buffer = buffer.slice(idx + 2);
    let event = "message";
    const data: string[] = [];
    for (const line of raw.split("\n")) {
      if (line.startsWith("event:")) event = line.slice(6).trim();
      else if (line.startsWith("data:")) data.push(line.slice(5).trimStart());
    }
    if (data.length) records.push({ event, data: data.join("\n") });
  }
  return { records, rest: buffer };
}

export class SatarkApiClient implements SatarkApi {
  constructor(private readonly baseUrl: string = "") {}

  async health(): Promise<Health> {
    const res = await fetch(`${this.baseUrl}/api/health`);
    if (!res.ok) throw new Error(`health ${res.status}`);
    return (await res.json()) as Health;
  }

  async analyze(req: AnalyzeRequest, signal?: AbortSignal): Promise<AnalysisResult> {
    const res = await fetch(`${this.baseUrl}/api/analyze`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify(req),
      signal,
    });
    if (!res.ok) throw new Error(`analyze ${res.status}: ${await res.text()}`);
    return (await res.json()) as AnalysisResult;
  }

  async analyzeStream(req: AnalyzeRequest, onEvent: (e: StageEvent) => void, signal?: AbortSignal): Promise<void> {
    const res = await fetch(`${this.baseUrl}/api/analyze/stream`, {
      method: "POST",
      headers: { "content-type": "application/json", accept: "text/event-stream" },
      body: JSON.stringify(req),
      signal,
    });
    if (!res.ok || !res.body) throw new Error(`stream ${res.status}: ${await res.text()}`);

    const reader = res.body.getReader();
    const decoder = new TextDecoder();
    let buffer = "";
    for (;;) {
      const { value, done } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      const { records, rest } = parseSseChunk(buffer);
      buffer = rest;
      for (const r of records) {
        if (r.event === "ping") continue;
        onEvent(JSON.parse(r.data) as StageEvent);
      }
    }
  }
}
