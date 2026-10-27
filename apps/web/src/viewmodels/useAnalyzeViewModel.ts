import { useMemo, useSyncExternalStore } from "react";
import type { SatarkApi } from "../services";
import { SatarkApiClient } from "../services";
import { AnalyzeViewModel, type AnalyzeState } from "./AnalyzeViewModel";

export interface AnalyzeBindings {
  state: AnalyzeState;
  canAnalyze: boolean;
  setMessage: (v: string) => void;
  setUpiId: (v: string) => void;
  setUrl: (v: string) => void;
  analyze: () => Promise<void>;
  reset: () => void;
  loadExample: AnalyzeViewModel["loadExample"];
}

/** Binds a headless AnalyzeViewModel to React. Views only ever see `AnalyzeBindings`. */
export function useAnalyzeViewModel(api?: SatarkApi): AnalyzeBindings {
  const vm = useMemo(() => new AnalyzeViewModel(api ?? new SatarkApiClient()), [api]);
  const state = useSyncExternalStore(vm.subscribe, vm.getState, vm.getState);
  return {
    state,
    canAnalyze: vm.canAnalyze,
    setMessage: vm.setMessage,
    setUpiId: vm.setUpiId,
    setUrl: vm.setUrl,
    analyze: vm.analyze,
    reset: vm.reset,
    loadExample: vm.loadExample,
  };
}
