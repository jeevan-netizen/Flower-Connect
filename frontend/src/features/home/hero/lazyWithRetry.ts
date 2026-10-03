/**
 * One-shot retry for a lazily imported module, and a way to build a fresh
 * attempt after one has been spent.
 *
 * `React.lazy` caches the payload of a dynamic `import()`, including a rejected
 * one, and re-throws that rejection on every subsequent render. So a single
 * dropped request would otherwise cost the visitor the feature until a full page
 * reload — a transient network blip treated as permanent.
 *
 * The retry is deliberate rather than automatic. A module fetch that failed is
 * not retained in the browser's module map, so a second `import()` really does
 * re-request the chunk, but re-requesting on every render would turn a
 * genuinely missing asset into a request loop. The flag makes it one extra
 * request per attempt, then the original error propagates.
 *
 * `sessionStorage` rather than `localStorage`, because the retry should cover the
 * reload a visitor makes after a bad network moment without outliving the tab.
 * Storage access is guarded so this is still safe where it is unavailable
 * (private modes, non-browser test environments).
 */
import { lazy, type ComponentType, type LazyExoticComponent } from "react";

/** The guarded storage handle, or `null` where storage cannot be used. */
function sessionFlags(): Storage | null {
  try {
    return typeof sessionStorage === "undefined" ? null : sessionStorage;
  } catch {
    return null;
  }
}

export function lazyWithRetry<T>(
  loader: () => Promise<{ default: T }>,
  flagKey: string,
): Promise<{ default: T }> {
  return loader().catch((error: unknown) => {
    const flag = sessionFlags();
    if (!flag || flag.getItem(flagKey)) {
      throw error;
    }
    try {
      flag.setItem(flagKey, "1");
    } catch {
      // A storage quota or policy error must not block the retry itself.
    }
    return loader();
  });
}

export interface RetryableLazy<P> {
  /**
   * The component to render.
   *
   * It is built from `lazy()` by `createRetryableLazy` and carries an empty
   * payload cache, which is the whole point: a rejected payload stays rejected
   * for as long as the component object lives, so recovery requires a *new*
   * component object rather than a re-render of the old one.
   */
  Component: LazyExoticComponent<ComponentType<P>>;
}

/**
 * Builds one attempt at a lazily loaded component, and makes that attempt
 * recoverable by being rebuilt.
 *
 * Call this once per *visit*, not once per module: the returned component holds
 * the rejected-payload cache, so a caller that hoists it to module scope (the
 * obvious way to write this) is exactly the bug this exists to prevent. Building
 * it inside the route component means leaving the route and coming back mounts a
 * component with an empty cache, and the chunk is requested afresh.
 *
 * The retry flag is cleared on each new attempt, so the one-shot budget in
 * `lazyWithRetry` is per visit: a visitor bouncing between routes can ask again,
 * while a component that stays mounted after a genuine failure still stops
 * after one retry instead of looping.
 */
export function createRetryableLazy<P>(
  loader: () => Promise<{ default: ComponentType<P> }>,
  flagKey: string,
): RetryableLazy<P> {
  const flag = sessionFlags();
  if (flag) {
    try {
      flag.removeItem(flagKey);
    } catch {
      // A storage policy error must not block loading the feature at all.
    }
  }
  return { Component: lazy(() => lazyWithRetry(loader, flagKey)) };
}
