import { describe, it, expect, afterEach, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { AnimatedList } from "@/motion/AnimatedList";
import { FadeIn } from "@/motion/FadeIn";
import { SlideUp } from "@/motion/SlideUp";
import { AnimatedCard } from "@/motion/AnimatedCard";
import { usePrefersReducedMotion } from "@/motion/use-reduced-motion";

const REDUCED_MOTION_QUERY = "(prefers-reduced-motion: reduce)";

/**
 * `src/test/setup.ts` reports `prefers-reduced-motion: reduce` for the whole
 * suite, so every primitive renders its final state with no animation applied.
 * These tests flip the preference per case with `vi.stubGlobal` to cover both
 * branches; nothing else in the suite depends on the animated one.
 */
function stubReducedMotion(reduce: boolean) {
  vi.stubGlobal("matchMedia", (query: string) => ({
    matches: query === REDUCED_MOTION_QUERY && reduce,
    media: query,
    onchange: null,
    addEventListener: () => {},
    removeEventListener: () => {},
    addListener: () => {},
    removeListener: () => {},
    dispatchEvent: () => false,
  }));
}

function PreferenceProbe() {
  const reduced = usePrefersReducedMotion();
  return <span data-testid="preference">{reduced ? "reduced" : "animated"}</span>;
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("usePrefersReducedMotion", () => {
  it("reports the preference to code that is not Framer Motion", () => {
    stubReducedMotion(true);
    render(<PreferenceProbe />);

    expect(screen.getByTestId("preference")).toHaveTextContent("reduced");
  });

  it("reports no preference when the user has expressed none", () => {
    stubReducedMotion(false);
    render(<PreferenceProbe />);

    expect(screen.getByTestId("preference")).toHaveTextContent("animated");
  });
});

describe("FadeIn", () => {
  it("renders its children and applies no animation under reduced motion", () => {
    stubReducedMotion(true);

    const { container } = render(<FadeIn>Faded content</FadeIn>);

    expect(screen.getByText("Faded content")).toBeInTheDocument();
    expect(container.firstElementChild).not.toHaveStyle({ opacity: "0" });
  });

  it("starts from the hidden variant when motion is allowed", () => {
    stubReducedMotion(false);

    const { container } = render(<FadeIn>Faded content</FadeIn>);

    expect(screen.getByText("Faded content")).toBeInTheDocument();
    expect(container.firstElementChild).toHaveStyle({ opacity: "0" });
  });
});

describe("SlideUp", () => {
  it("renders its children and applies no animation under reduced motion", () => {
    stubReducedMotion(true);

    const { container } = render(<SlideUp>Slid content</SlideUp>);

    expect(screen.getByText("Slid content")).toBeInTheDocument();
    expect(container.firstElementChild).not.toHaveStyle({ opacity: "0" });
  });
});

describe("AnimatedList", () => {
  it("renders every child under reduced motion", () => {
    stubReducedMotion(true);

    render(
      <AnimatedList>
        <span>First item</span>
        <span>Second item</span>
        <span>Third item</span>
      </AnimatedList>,
    );

    expect(screen.getByText("First item")).toBeInTheDocument();
    expect(screen.getByText("Second item")).toBeInTheDocument();
    expect(screen.getByText("Third item")).toBeInTheDocument();
  });

  it("renders every child when motion is allowed, hiding none of them", () => {
    stubReducedMotion(false);

    render(
      <AnimatedList>
        <span>First item</span>
        <span>Second item</span>
        <span>Third item</span>
      </AnimatedList>,
    );

    // The stagger changes when each child becomes visible, never whether it is
    // mounted: the whole list is in the DOM and focusable from the first frame.
    expect(screen.getByText("First item")).toBeInTheDocument();
    expect(screen.getByText("Second item")).toBeInTheDocument();
    expect(screen.getByText("Third item")).toBeInTheDocument();
  });
});

describe("AnimatedCard", () => {
  it("keeps its children in the accessibility tree under reduced motion", () => {
    stubReducedMotion(true);

    render(
      <AnimatedCard>
        <a href="/vendor/profile">Open the profile</a>
      </AnimatedCard>,
    );

    expect(screen.getByRole("link", { name: /open the profile/i })).toBeInTheDocument();
  });

  it("adds no tabindex of its own", () => {
    stubReducedMotion(true);

    const { container } = render(<AnimatedCard>Card content</AnimatedCard>);

    expect(container.firstElementChild).not.toHaveAttribute("tabindex");
    expect(container.firstElementChild).not.toHaveAttribute("aria-hidden");
  });
});
