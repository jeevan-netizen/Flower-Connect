import { useEffect, useState } from "react";

/**
 * The width at which the header's links fit on one row: from here up they are a
 * bar, below it they are a disclosure panel. `xl` in Tailwind is the same 1280px,
 * so the CSS breakpoints on the header and this query agree.
 *
 * Exported as the query string rather than a pixel number so the component, the
 * shared test stub and the stylesheet cannot name the breakpoint three ways.
 */
export const WIDE_HEADER_QUERY = "(min-width: 1280px)";

/**
 * Subscribe to a media query.
 *
 * Used where a component must render *one* of two layouts rather than both and
 * hide one with CSS. Two copies in the DOM would mean two copies in the tab
 * order and two copies read out by a screen reader, so the choice is made in
 * JavaScript from the real viewport instead.
 *
 * Reads synchronously on first render, so the first paint is already the right
 * layout — there is no wide-then-narrow flash to correct.
 */
export function useMediaQuery(query: string): boolean {
  const [matches, setMatches] = useState(
    () => typeof window !== "undefined" && window.matchMedia(query).matches,
  );

  useEffect(() => {
    const list = window.matchMedia(query);
    const onChange = (event: MediaQueryListEvent) => setMatches(event.matches);

    // The query can already have changed between the first render and this
    // effect, so re-read rather than trusting the render-time value.
    setMatches(list.matches);
    list.addEventListener("change", onChange);
    return () => list.removeEventListener("change", onChange);
  }, [query]);

  return matches;
}
