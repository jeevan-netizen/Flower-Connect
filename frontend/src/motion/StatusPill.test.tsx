import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { StatusPill } from "@/motion/StatusPill";

describe("StatusPill", () => {
  it("renders the label inside the pill's tone classes", () => {
    const { container } = render(<StatusPill tone="bg-amber-100 text-amber-800" label="Awaiting approval" />);

    expect(screen.getByText("Awaiting approval")).toBeInTheDocument();
    expect(container.firstElementChild).toHaveClass("bg-amber-100", "text-amber-800");
  });

  it("forwards rest props so a screen can keep its own test hook", () => {
    render(<StatusPill tone="bg-slate-200 text-slate-800" label="Rejected" data-testid="pill" />);

    expect(screen.getByTestId("pill")).toHaveTextContent("Rejected");
  });

  /**
   * Under reduced motion (the suite default) a changed label is replaced in one
   * tick. Asserting on the final text rather than on an animation is what keeps
   * this meaningful: a pill that kept the old label would still read "Awaiting
   * approval" here.
   */
  it("shows the new label and not the old one when the status changes", () => {
    const { rerender } = render(<StatusPill tone="bg-amber-100 text-amber-800" label="Awaiting approval" />);

    rerender(<StatusPill tone="bg-brand-100 text-brand-900" label="Approved" />);

    expect(screen.getByText("Approved")).toBeInTheDocument();
    expect(screen.queryByText("Awaiting approval")).not.toBeInTheDocument();
  });
});
