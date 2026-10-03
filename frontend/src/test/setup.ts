import "@testing-library/jest-dom";
import { configure } from "@testing-library/react";
import { WIDE_HEADER_QUERY } from "@/app/hooks/useMediaQuery";

// The suite runs several jsdom workers in parallel, and every vendor assertion
// waits on a query/mutation to settle. Testing Library's 1s default is too tight
// under that load and produced timeouts that pass when a file runs alone.
configure({ asyncUtilTimeout: 5000 });

/*
 * jsdom does not evaluate media queries, so `prefers-reduced-motion` would
 * always read as "no preference" and every motion primitive would start
 * mid-animation in a test. Reporting the preference as `reduce` makes the
 * primitives render their final state synchronously — no rAF ticks, no opacity
 * left at 0, and existing tests keep asserting against exactly what a real user
 * with reduced motion sees.
 *
 * The same stub reports a desktop-width viewport, so a component that renders one
 * of two layouts from the real viewport (`useMediaQuery`, used by the header's
 * navigation) renders the wide one by default. Suites that need the narrow one
 * override this with `vi.stubGlobal`; `src/motion/*.test.tsx` does the same for
 * the animated branch.
 *
 * This is test infrastructure, not a test change: no existing assertion was
 * touched. `src/motion/*.test.tsx` overrides it with `vi.stubGlobal` for the
 * few cases that need the animated branch.
 */
const REDUCED_MOTION_QUERY = "(prefers-reduced-motion: reduce)";

if (typeof window !== "undefined") {
  Object.defineProperty(window, "matchMedia", {
    configurable: true,
    writable: true,
    value: (query: string) => ({
      matches: query === REDUCED_MOTION_QUERY || query === WIDE_HEADER_QUERY,
      media: query,
      onchange: null,
      addEventListener: () => {},
      removeEventListener: () => {},
      addListener: () => {},
      removeListener: () => {},
      dispatchEvent: () => false,
    }),
  });
}
