import { useEffect, useState } from "react";
import type { Health } from "../models";
import type { SatarkApi } from "../services";

/** Polls /api/health until every model reports loaded. */
export function useHealth(api: SatarkApi, intervalMs = 3000): Health | null {
  const [health, setHealth] = useState<Health | null>(null);
  useEffect(() => {
    let timer: ReturnType<typeof setTimeout> | undefined;
    let cancelled = false;
    const tick = async () => {
      try {
        const h = await api.health();
        if (cancelled) return;
        setHealth(h);
        if (h.status !== "ok") timer = setTimeout(tick, intervalMs);
      } catch {
        if (cancelled) return;
        setHealth({ status: "loading", models: [] });
        timer = setTimeout(tick, intervalMs);
      }
    };
    void tick();
    return () => {
      cancelled = true;
      if (timer) clearTimeout(timer);
    };
  }, [api, intervalMs]);
  return health;
}
