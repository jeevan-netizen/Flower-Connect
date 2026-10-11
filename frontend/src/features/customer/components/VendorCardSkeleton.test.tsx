import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { VendorCardSkeleton, VendorCardSkeletonGrid } from "@/features/customer/components/VendorCardSkeleton";

describe("VendorCardSkeleton", () => {
  it("renders skeleton structure matching card layout", () => {
    const { container } = render(<VendorCardSkeleton />);

    // Should have the article container
    expect(screen.getByRole("article")).toBeInTheDocument();

    // Should have skeleton lines (divs with aria-hidden="true")
    const skeletonLines = container.querySelectorAll('[aria-hidden="true"]');
    expect(skeletonLines.length).toBeGreaterThanOrEqual(7);
  });

  it("has pulse animation class", () => {
    render(<VendorCardSkeleton />);

    const article = screen.getByRole("article");
    expect(article).toHaveClass("animate-pulse");
  });
});

describe("VendorCardSkeletonGrid", () => {
  it("renders specified number of skeletons", () => {
    render(<VendorCardSkeletonGrid count={3} />);

    const articles = screen.getAllByRole("article");
    expect(articles).toHaveLength(3);
  });

  it("defaults to 4 skeletons", () => {
    render(<VendorCardSkeletonGrid />);

    const articles = screen.getAllByRole("article");
    expect(articles).toHaveLength(4);
  });

  it("has status role for accessibility", () => {
    render(<VendorCardSkeletonGrid count={2} />);

    const grid = screen.getByRole("status");
    expect(grid).toHaveAttribute("aria-label", "Loading vendor cards");
  });

  it("uses responsive grid classes", () => {
    render(<VendorCardSkeletonGrid />);

    const grid = screen.getByRole("status");
    expect(grid).toHaveClass("grid");
    expect(grid).toHaveClass("grid-cols-1");
    expect(grid).toHaveClass("sm:grid-cols-2");
    expect(grid).toHaveClass("lg:grid-cols-4");
  });
});