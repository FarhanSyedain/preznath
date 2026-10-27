import { useEffect, useMemo, useRef } from "react";
import { EXAMPLES } from "../models/examples";
import { SatarkApiClient } from "../services";
import { useAnalyzeViewModel } from "../viewmodels/useAnalyzeViewModel";
import { useHealth } from "../viewmodels/useHealth";
import { AdvicePanel } from "../views/AdvicePanel";
import { EntitiesPanel } from "../views/EntitiesPanel";
import { ExampleChips } from "../views/ExampleChips";
import { MessageComposer } from "../views/MessageComposer";
import { PipelineTrace } from "../views/PipelineTrace";
import { ReasonsList } from "../views/ReasonsList";
import { SignalBreakdown } from "../views/SignalBreakdown";
import { VerdictCard } from "../views/VerdictCard";
import { Header } from "../views/Header";
import "../views/views.css";
import "./App.css";

export function App() {
  const api = useMemo(() => new SatarkApiClient(), []);
  const vm = useAnalyzeViewModel(api);
  const health = useHealth(api);
  const { state } = vm;
  const result = state.result;

  // Deep link for demos: /?example=kyc loads that example and runs it once models are ready.
  const autoRan = useRef(false);
  useEffect(() => {
    if (autoRan.current || health?.status !== "ok") return;
    const id = new URLSearchParams(window.location.search).get("example");
    const ex = EXAMPLES.find((e) => e.id === id);
    if (!ex) return;
    autoRan.current = true;
    vm.loadExample(ex);
    void vm.analyze();
  }, [health, vm]);

  return (
    <div className="app">
      <Header health={health} />

      <main className="grid">
        <section className="col col--input">
          <MessageComposer
            message={state.message}
            upiId={state.upiId}
            url={state.url}
            phase={state.phase}
            canAnalyze={vm.canAnalyze}
            onMessage={vm.setMessage}
            onUpiId={vm.setUpiId}
            onUrl={vm.setUrl}
            onAnalyze={() => void vm.analyze()}
            onReset={vm.reset}
          />
          <ExampleChips examples={EXAMPLES} activeId={state.activeExampleId} onPick={vm.loadExample} />
          {result && <EntitiesPanel entities={result.entities} language={result.language} translated={result.translated_text} />}
        </section>

        <section className="col col--output">
          <PipelineTrace stages={state.stages} phase={state.phase} elapsedMs={result?.total_latency_ms ?? null} />
          {state.phase === "error" && (
            <div className="error">
              <span className="label">Error</span>
              <p className="mono">{state.error}</p>
            </div>
          )}
          {result && (
            <>
              <VerdictCard verdict={result.verdict} />
              <SignalBreakdown signals={result.signals} />
              <ReasonsList reasons={result.verdict.reasons} />
              <AdvicePanel advice={result.verdict.advice} />
            </>
          )}
          {!result && state.phase === "idle" && (
            <div className="placeholder">
              <p>
                Paste a message on the left, or pick an example. Eight checks run in sequence — language, entities,
                two classifiers, zero-shot intent, similarity to known scams, rules, then a weighted verdict.
              </p>
            </div>
          )}
        </section>
      </main>

      <footer className="footer">
        <span className="mono">Preznath · Team Yimberzol · Hack for Social Cause</span>
        <span className="mono">
          Report fraud: 1930 · <a href="https://cybercrime.gov.in">cybercrime.gov.in</a>
        </span>
      </footer>
    </div>
  );
}
