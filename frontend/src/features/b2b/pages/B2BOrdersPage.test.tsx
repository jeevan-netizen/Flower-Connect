import { describe, expect, it, vi, beforeEach } from "vitest";
import { fireEvent, screen } from "@testing-library/react";

import { B2BOrdersPage } from "@/features/b2b/pages/B2BOrdersPage";
import * as b2bQueries from "@/features/b2b/queries";
import { renderWithProviders } from "@/test/render";
import { makeB2BOrderResponse } from "@/test/factories";
import type { B2BOrderPageResponse, B2BOrderResponse } from "@/features/b2b/types";
import type { UseQueryResult } from "@tanstack/react-query";

vi.mock("@/features/b2b/queries");

const mockUseB2BOrders = vi.mocked(b2bQueries.useB2BOrders);

const order: B2BOrderResponse = makeB2BOrderResponse({
  id: 1,
  orderNo: "B2B-1001",
  buyerName: "Buyer Florist",
  sellerName: "Seller Florist",
  status: "PLACED",
  total: 1823,
});

const page = {
  content: [order],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
  first: true,
  last: true,
  empty: false,
};

describe("B2BOrdersPage", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    mockUseB2BOrders.mockReturnValue({
      data: page,
      isPending: false,
      error: null,
    } as unknown as UseQueryResult<B2BOrderPageResponse, Error>);
  });

  it("shows the buying tab orders by default", () => {
    renderWithProviders(<B2BOrdersPage />, { route: "/vendor/b2b/orders" });

    expect(screen.getByText("#B2B-1001")).toBeInTheDocument();
    expect(screen.getAllByText("Seller Florist")).toHaveLength(2);
  });

  it("switches to the selling tab", () => {
    renderWithProviders(<B2BOrdersPage />, { route: "/vendor/b2b/orders" });

    expect(screen.getByText("#B2B-1001")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /selling/i }));

    // The query is keyed by role/page so the hook re-runs; here the mock still
    // returns the same page, but the call should reflect SELLER.
    expect(mockUseB2BOrders).toHaveBeenLastCalledWith(
      { role: "SELLER", status: null, page: 0, size: 20 },
    );
  });

  it("shows an empty state when there are no orders", () => {
    mockUseB2BOrders.mockReturnValueOnce({
      data: { ...page, content: [], totalElements: 0, empty: true },
      isPending: false,
      error: null,
    } as unknown as UseQueryResult<B2BOrderPageResponse, Error>);
  });

  it("shows an empty state when there are no orders", () => {
    mockUseB2BOrders.mockReturnValueOnce({
      data: { ...page, content: [], totalElements: 0, empty: true },
      isPending: false,
      error: null,
    } as unknown as UseQueryResult<B2BOrderPageResponse, Error>);

    renderWithProviders(<B2BOrdersPage />, { route: "/vendor/b2b/orders" });

    expect(screen.getByText(/no placed orders yet/i)).toBeInTheDocument();
  });

  it("renders each row linking to its detail page", () => {
    renderWithProviders(<B2BOrdersPage />, { route: "/vendor/b2b/orders" });

    expect(screen.getByText("#B2B-1001")).toBeInTheDocument();
    const link = screen.getByText("#B2B-1001").closest("a");
    expect(link).toHaveAttribute("href", "/vendor/b2b/orders/1");
  });
});
