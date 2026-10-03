import { useSyncExternalStore } from "react";

const QUERY = "(prefers-reduced-motion: reduce)";

function subscribe(onStoreChange: () => void): () => void {
  if (typeof window === "undefined" || typeof window.matchMedia !== "function") {
    return () => {};
  }
  const query = window.matchMedia(QUERY);
  if (typeof query.addEventListener === "function") {
    query.addEventListener("change", onStoreChange);
    return () => query.removeEventListener("change", onStoreChange);
  }
  // Safari < 14 only has the deprecated listener API.
  query.addListener(onStoreChange);
  return () => query.removeListener(onStoreChange);
}

function getSnapshot(): boolean {
  if (typeof window === "undefined" || typeof window.matchMedia !== "function") {
    return false;
  }
  return window.matchMedia(QUERY).matches;
}

/**
 * Reads `prefers-reduced-motion` live.
 *
 * `<MotionConfig reducedMotion="user">` covers every Framer Motion component in
 * the tree, and this hook covers everything else: the CSS keyframes in
 * `index.css`, plain Tailwind `transition-*` utilities, and any imperative
 * animation a later stage adds (the 3D scene).
 *
 * It deliberately reads `matchMedia` directly rather than Framer's own
 * `useReducedMotion`, whose value is captured once per page load: a test that
 * changes the preference at runtime would otherwise keep reading the value from
 * the first render.
 */
export function usePrefersReducedMotion(): boolean {
  return useSyncExternalStore(subscribe, getSnapshot, () => false);
}
