/**
 * One-shot retry for a lazily imported module.
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
 * request per session, then the original error propagates.
 *
 * `sessionStorage` rather than `localStorage`, because the retry should cover the
 * reload a visitor makes after a bad network moment without outliving the tab.
 * Storage access is guarded so this is still safe where it is unavailable
 * (private modes, non-browser test environments).
 */
export function lazyWithRetry<T>(
  loader: () => Promise<{ default: T }>,
  flagKey: string,
): Promise<{ default: T }> {
  return loader().catch((error: unknown) => {
    let flag: Storage | null = null;
    try {
      flag = typeof sessionStorage === "undefined" ? null : sessionStorage;
    } catch {
      flag = null;
    }
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