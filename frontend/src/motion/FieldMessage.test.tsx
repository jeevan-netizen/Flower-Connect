import { describe, it, expect, afterEach, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { FieldMessage } from "@/motion/FieldMessage";

const REDUCED_MOTION_QUERY = "(prefers-reduced-motion: reduce)";

/**
 * `src/test/setup.ts` reports `prefers-reduced-motion: reduce` suite-wide, so
 * the message renders in its final state. Only the cases that need the animated
 * branch stub the preference back, matching `primitives.test.tsx`.
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

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("FieldMessage", () => {
  it("shows the hint while there is nothing wrong with the field", () => {
    render(<FieldMessage hint="Optional. 7-15 digits." />);

    expect(screen.getByText("Optional. 7-15 digits.")).toBeInTheDocument();
  });

  it("prefers the validation message over the hint", () => {
    render(<FieldMessage hint="Optional. 7-15 digits." message="Enter a valid phone number" />);

    expect(screen.getByText("Enter a valid phone number")).toBeInTheDocument();
    expect(screen.queryByText("Optional. 7-15 digits.")).not.toBeInTheDocument();
  });

  it("renders one element either way, so aria-describedby keeps pointing at the message", () => {
    const { rerender } = render(<FieldMessage id="phone-note" hint="Optional." />);
    const hint = screen.getByText("Optional.").closest("p");
    expect(hint).toHaveAttribute("id", "phone-note");

    rerender(<FieldMessage id="phone-note" hint="Optional." message="Enter a valid phone number" />);
    expect(screen.getByText("Enter a valid phone number").closest("p")).toHaveAttribute(
      "id",
      "phone-note",
    );
  });

  it("reserves a line so a message cannot push the fields below it down", () => {
    const { container } = render(<FieldMessage id="reserved" reserve />);

    const reserved = container.querySelector("p");
    expect(reserved).toHaveAttribute("id", "reserved");
    expect(reserved).toHaveClass("min-h-5");
    expect(reserved).toHaveTextContent("");
  });

  it("renders no element at all when there is nothing to say and nothing to reserve", () => {
    const { container } = render(<FieldMessage />);

    expect(container).toBeEmptyDOMElement();
  });

  it("presents the message immediately under reduced motion", () => {
    stubReducedMotion(true);
    render(<FieldMessage message="Email is required" />);

    expect(screen.getByText("Email is required")).not.toHaveStyle({ opacity: "0" });
  });

  it("reveals the message from a short offset when motion is allowed", () => {
    stubReducedMotion(false);
    render(<FieldMessage message="Email is required" />);

    // The travel is `distances.reveal`, not the page-entry distance: a message
    // arriving must not look like the form shifting.
    expect(screen.getByText("Email is required")).toHaveStyle({ opacity: "0" });
  });
});
