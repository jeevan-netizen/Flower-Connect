import { useSyncExternalStore } from "react";

/**
 * How much the scene may respond to the pointer.
 *
 * - `full` — desktop: a fine pointer that can hover, so the bouquet leans toward
 *   the cursor.
 * - `reduced` — tablet: a coarse pointer. The lean still happens (a touch drag
 *   moves it) but with a fraction of the amplitude.
 * - `none` — mobile: no pointer interaction, and the scene renders statically.
 *
 * Nothing here reads a motion token: it is a capability, not an animation.
 */
export type InteractionLevel = "none" | "reduced" | "full";

const DESKTOP_QUERY = "(min-width: 768px) and (hover: hover) and (pointer: fine)";
const TABLET_QUERY = "(min-width: 768px) and (pointer: coarse)";

function subscribe(onStoreChange: () => void): () => void {
  if (typeof window === "undefined" || typeof window.matchMedia !== "function") {
    return () => {};
  }
  const queries = [window.matchMedia(DESKTOP_QUERY), window.matchMedia(TABLET_QUERY)];
  for (const query of queries) {
    if (typeof query.addEventListener === "function") {
      query.addEventListener("change", onStoreChange);
    } else {
      query.addListener(onStoreChange);
    }
  }
  return () => {
    for (const query of queries) {
      if (typeof query.removeEventListener === "function") {
        query.removeEventListener("change", onStoreChange);
      } else {
        query.removeListener(onStoreChange);
      }
    }
  };
}

function read(): InteractionLevel {
  if (typeof window === "undefined" || typeof window.matchMedia !== "function") {
    return "none";
  }
  if (window.matchMedia(DESKTOP_QUERY).matches) {
    return "full";
  }
  if (window.matchMedia(TABLET_QUERY).matches) {
    return "reduced";
  }
  return "none";
}

/** Resolves to a primitive, which is what `useSyncExternalStore` needs. */
export function useHeroInteractionLevel(): InteractionLevel {
  return useSyncExternalStore(subscribe, read, () => "none" as const);
}