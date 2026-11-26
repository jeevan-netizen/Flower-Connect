import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { StarRating } from "@/features/review/components/StarRating";

function renderStarRating(props: Parameters<typeof StarRating>[0]) {
  return render(<StarRating {...props} />);
}

describe("StarRating", () => {
  it("renders read-only stars and label for a rated vendor", () => {
    renderStarRating({ rating: 4.5, reviewCount: 12 });

    expect(screen.getByRole("img", { name: "4.5 stars (12 reviews)" })).toBeInTheDocument();
    expect(screen.getByText("4.5 stars (12 reviews)")).toBeInTheDocument();
  });

  it("renders read-only stars with integer rating", () => {
    renderStarRating({ rating: 5, reviewCount: 3 });

    expect(screen.getByRole("img", { name: "5.0 stars (3 reviews)" })).toBeInTheDocument();
    expect(screen.getByText("5.0 stars (3 reviews)")).toBeInTheDocument();
  });

  it("shows 'New' label when there are no reviews", () => {
    renderStarRating({ rating: null, reviewCount: 0 });

    expect(screen.getByText("New")).toBeInTheDocument();
    expect(
      screen.getByLabelText("No reviews yet"),
    ).toBeInTheDocument();
  });

  it("shows 'New' label when rating is null but reviews exist", () => {
    renderStarRating({ rating: null, reviewCount: 0 });

    expect(screen.getByText("New")).toBeInTheDocument();
  });

  it("renders a custom empty label", () => {
    renderStarRating({ rating: null, reviewCount: 0, emptyLabel: "Not yet rated" });

    expect(screen.getByText("Not yet rated")).toBeInTheDocument();
  });

  it("applies a custom className", () => {
    renderStarRating({ rating: 4, reviewCount: 2, className: "my-rating" });

    expect(screen.getByRole("img", { name: "4.0 stars (2 reviews)" })).toHaveClass(
      "my-rating",
    );
  });

  it("renders interactive mode with rate buttons", () => {
    renderStarRating({ rating: null, reviewCount: 0, interactive: true });

    // 5 star buttons, none pressed initially (value=0)
    const starButtons = screen.getAllByRole("button", { name: /rate \d of 5/i });
    expect(starButtons).toHaveLength(5);
    starButtons.forEach((btn) => expect(btn).not.toHaveAttribute("aria-pressed", "true"));

    expect(screen.getByRole("button", { name: "Rate 4 of 5" })).toHaveAttribute(
      "aria-pressed",
      "false",
    );
  });

  it("calls onSelect when a star is clicked in interactive mode", async () => {
    const onSelect = vi.fn();
    const user = userEvent.setup();
    renderStarRating({ rating: null, reviewCount: 0, interactive: true, value: 0, onSelect });

    await user.click(screen.getByRole("button", { name: "Rate 4 of 5" }));

    expect(onSelect).toHaveBeenCalledWith(4);
  });

  it("sets aria-pressed on the selected star in interactive mode", () => {
    renderStarRating({ rating: null, reviewCount: 0, interactive: true, value: 3 });

    expect(screen.getByRole("button", { name: "Rate 3 of 5" })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
    expect(screen.getByRole("button", { name: "Rate 4 of 5" })).toHaveAttribute(
      "aria-pressed",
      "false",
    );
  });

  it("displays half stars correctly for fractional ratings", () => {
    renderStarRating({ rating: 3.5, reviewCount: 8 });

    expect(screen.getByRole("img", { name: "3.5 stars (8 reviews)" })).toBeInTheDocument();
  });

  it("renders without crashing for a zero rating with reviews", () => {
    renderStarRating({ rating: 0, reviewCount: 1 });

    expect(screen.getByRole("img", { name: "0.0 stars (1 review)" })).toBeInTheDocument();
  });
});
