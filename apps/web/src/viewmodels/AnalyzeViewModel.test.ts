import { describe, expect, it } from "vitest";
import type { AnalysisResult, Stage, StageEvent } from "../models";
import type { SatarkApi } from "../services";
import { parseSseChunk } from "../services";
import { AnalyzeViewModel, applyEvent, initialState, INITIAL_STAGES } from "./AnalyzeViewModel";

const stage = (index: number, status: Stage["status"], latency = 0): Stage => ({
  index,
  name: `s${index}`,
  label: `Stage ${index}`,
  status,
  latency_ms: latency,
  signal: null,
  error: null,
});

const result = (): AnalysisResult => ({
  text: "x",
  upi_id: null,
  url: null,
  language: "en",
  language_confidence: 0.9,
  translated_text: null,
  entities: [],
  signals: [],
  stages: [],
  verdict: { label: "FRAUD", score: 88, confidence: 0.9, reasons: ["r"], advice: ["a"] },
  total_latency_ms: 500,
});

class FakeApi implements SatarkApi {
  constructor(private readonly events: StageEvent[], private readonly fail = false) {}
  health = async () => ({ status: "ok" as const, models: [] });
  analyze = async () => result();
  async analyzeStream(_req: unknown, onEvent: (e: StageEvent) => void): Promise<void> {
    if (this.fail) throw new Error("boom");
    for (const e of this.events) {
      await Promise.resolve();
      onEvent(e);
    }
  }
}

describe("applyEvent", () => {
  it("replaces the plan", () => {
    const s = applyEvent(initialState(), { type: "plan", stages: [stage(1, "pending")] });
    expect(s.stages).toHaveLength(1);
  });

  it("upserts a stage by index and keeps order", () => {
    let s = initialState();
    s = applyEvent(s, { type: "stage", stage: stage(3, "running") });
    expect(s.stages[2]?.status).toBe("running");
    s = applyEvent(s, { type: "stage", stage: stage(3, "done", 42) });
    expect(s.stages[2]?.status).toBe("done");
    expect(s.stages[2]?.latency_ms).toBe(42);
    expect(s.stages).toHaveLength(INITIAL_STAGES.length);
  });

  it("moves to done on result", () => {
    const s = applyEvent(initialState(), { type: "result", stage: stage(8, "done"), result: result() });
    expect(s.phase).toBe("done");
    expect(s.result?.verdict.label).toBe("FRAUD");
  });

  it("moves to error on error", () => {
    const s = applyEvent(initialState(), { type: "error", message: "bad" });
    expect(s.phase).toBe("error");
    expect(s.error).toBe("bad");
  });
});

describe("AnalyzeViewModel", () => {
  it("starts idle and cannot analyze with empty message", () => {
    const vm = new AnalyzeViewModel(new FakeApi([]));
    expect(vm.getState().phase).toBe("idle");
    expect(vm.canAnalyze).toBe(false);
    vm.setMessage("hello");
    expect(vm.canAnalyze).toBe(true);
  });

  it("runs idle -> running -> done and notifies subscribers", async () => {
    const events: StageEvent[] = [
      { type: "plan", stages: [stage(1, "pending"), stage(2, "pending")] },
      { type: "stage", stage: stage(1, "running") },
      { type: "stage", stage: stage(1, "done", 10) },
      { type: "stage", stage: stage(2, "running") },
      { type: "result", stage: stage(2, "done", 5), result: result() },
    ];
    const vm = new AnalyzeViewModel(new FakeApi(events), () => 1000);
    const phases: string[] = [];
    vm.subscribe(() => phases.push(vm.getState().phase));
    vm.setMessage("scam text");
    await vm.analyze();
    expect(phases[0]).toBe("idle"); // setMessage notification
    expect(phases).toContain("running");
    expect(vm.getState().phase).toBe("done");
    expect(vm.getState().result?.verdict.score).toBe(88);
    expect(vm.getState().stages.map((s) => s.status)).toEqual(["done", "done"]);
  });

  it("goes to error if the stream fails", async () => {
    const vm = new AnalyzeViewModel(new FakeApi([], true));
    vm.setMessage("x");
    await vm.analyze();
    expect(vm.getState().phase).toBe("error");
    expect(vm.getState().error).toBe("boom");
  });

  it("goes to error if the stream ends without a result", async () => {
    const vm = new AnalyzeViewModel(new FakeApi([{ type: "stage", stage: stage(1, "done") }]));
    vm.setMessage("x");
    await vm.analyze();
    expect(vm.getState().phase).toBe("error");
  });

  it("loadExample fills fields and reset clears them", () => {
    const vm = new AnalyzeViewModel(new FakeApi([]));
    vm.loadExample({ id: "e", title: "t", expected: "SAFE", text: "hi", upi_id: "a@okaxis" });
    expect(vm.getState().message).toBe("hi");
    expect(vm.getState().upiId).toBe("a@okaxis");
    expect(vm.getState().activeExampleId).toBe("e");
    vm.setMessage("edited");
    expect(vm.getState().activeExampleId).toBeNull();
    vm.reset();
    expect(vm.getState()).toEqual(initialState());
  });
});

describe("parseSseChunk", () => {
  it("parses complete records and keeps the partial tail", () => {
    const { records, rest } = parseSseChunk('event: stage\ndata: {"a":1}\n\nevent: result\ndata: {"b"');
    expect(records).toEqual([{ event: "stage", data: '{"a":1}' }]);
    expect(rest).toBe('event: result\ndata: {"b"');
  });

  it("accepts CRLF line endings as sent by sse-starlette", () => {
    const { records, rest } = parseSseChunk('event: stage\r\ndata: {"a":1}\r\n\r\nevent: plan\r\n');
    expect(records).toEqual([{ event: "stage", data: '{"a":1}' }]);
    expect(rest).toBe("event: plan\n");
  });

  it("defaults event name to message and joins multi-line data", () => {
    const { records } = parseSseChunk("data: a\ndata: b\n\n");
    expect(records).toEqual([{ event: "message", data: "a\nb" }]);
  });
});
