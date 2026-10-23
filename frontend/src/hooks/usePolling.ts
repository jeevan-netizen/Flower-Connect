import { useEffect, useRef } from "react";

/**
 * Refs a callback on a fixed interval, honouring the page-visibility API so a
 * background tab does not keep refetching, and cleaning up the timer and the
 * listener on unmount.
 *
 * The callback is only invoked while the tab is visible. When the tab becomes
 * visible again the callback runs once, so the user never returns to stale
 * data. Return value is nothing: the hook owns the timer entirely and the
 * caller decides whether polling is on via `enabled`.
 *
 * Callers stop polling themselves — e.g. when every order on screen is in a
 * terminal state — by passing `enabled: false`. The hook never decides what
 * "terminal" means; that stays with the page that knows its own data.
 */
export function usePolling(
  callback: () => void,
  { interval = 8_000, enabled = true }: { interval?: number; enabled?: boolean } = {},
) {
  const callbackRef = useRef(callback);
  callbackRef.current = callback;

  useEffect(() => {
    if (!enabled) {
      return;
    }

    const timer = window.setInterval(() => {
      if (typeof document !== "undefined" && !document.hidden) {
        callbackRef.current();
      }
    }, interval);

    const handleVisibilityChange = () => {
      if (typeof document !== "undefined" && !document.hidden) {
        callbackRef.current();
      }
    };
    document.addEventListener("visibilitychange", handleVisibilityChange);

    return () => {
      window.clearInterval(timer);
      document.removeEventListener("visibilitychange", handleVisibilityChange);
    };
  }, [enabled, interval]);
}
