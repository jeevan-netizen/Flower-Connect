import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { OrderHistoryPage } from "@/features/customer/orders/pages/OrderHistoryPage";
import { renderWithProviders } from "@/test/render";
import {
  makeCustomerOrderListItem,
  makeCustomerOrderPageResponse,
} from "@/test/factories";
import { apiError } from "@/test/api-errors";
import type { CustomerOrderPageResponse } from "@/features/customer/orders/types";

const mockFetchOrders = vi.hoisted(() => vi.fn());

vi.mock("@/features/customer/orders/api", () => ({
  fetchOrders: mockFetchOrders,
}));

function renderHistory() {
  return renderWithProviders(<OrderHistoryPage />);
}

function primeHistory(page: CustomerOrderPageResponse = makeCustomerOrderPageResponse()) {
  mockFetchOrders.mockResolvedValue(page);
  return page;
}

describe("OrderHistoryPage", () => {
  beforeEach(() => {
    mockFetchOrders.mockResolvedValue(makeCustomerOrderPageResponse());
  });

  it("lists orders newest first with status badges and totals", async () => {
    primeHistory(
      makeCustomerOrderPageResponse({
        content: [
          makeCustomerOrderListItem({
            id: 5002,
            status: "DELIVERED",
            paymentMethod: "COD",
            total: 999,
            vendorName: "Rose & Co",
          }),
          makeCustomerOrderListItem({
            id: 5001,
            status: "PLACED",
            paymentMethod: "COD",
            total: 1823,
            vendorName: "Petal & Stem",
          }),
        ],
      }),
    );

    renderHistory();

    await screen.findByText("Order #5002");
    await screen.findByText("Order #5001");
    expect(await screen.findByText("Rose & Co")).toBeInTheDocument();
    expect(await screen.findByText("Petal & Stem")).toBeInTheDocument();
    expect(screen.getAllByText("Delivered").length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText("Placed").length).toBeGreaterThanOrEqual(1);
  });

  it("shows cash on delivery wording for COD", async () => {
    primeHistory();
    renderHistory();
    await screen.findByText("Order #5001");
    expect(await screen.findByText("Cash on delivery")).toBeInTheDocument();
  });

  it("shows an empty state with a browse link", async () => {
    primeHistory(makeCustomerOrderPageResponse({ content: [], totalElements: 0, empty: true }));
    renderHistory();
    expect(await screen.findByText(/no orders yet/i)).toBeInTheDocument();
    expect(await screen.findByRole("link", { name: /start browsing/i })).toHaveAttribute(
      "href",
      "/browse",
    );
  });

  it("shows an error state with retry", async () => {
    mockFetchOrders.mockRejectedValue(apiError(500, { message: "Boom" }));
    renderHistory();
    expect(await screen.findByText(/something went wrong/i)).toBeInTheDocument();

    primeHistory();
    await userEvent.click(screen.getByRole("button", { name: /try again/i }));
    expect(await screen.findByText("Order #5001")).toBeInTheDocument();
  });

  it("shows a loading state while fetching", async () => {
    mockFetchOrders.mockImplementation(() => new Promise(() => {}));
    renderHistory();
    expect(await screen.findByText(/loading your orders/i)).toBeInTheDocument();
  });

  it("paginates to the next page", async () => {
    primeHistory(
      makeCustomerOrderPageResponse({
        content: Array.from({ length: 20 }, (_, i) =>
          makeCustomerOrderListItem({ id: 5001 + i }),
        ),
        totalElements: 40,
        totalPages: 2,
        first: true,
        last: false,
        empty: false,
      }),
    );
    renderHistory();
    await screen.findByText("Order #5001");
    const next = screen.getByRole("button", { name: /next/i });
    expect(next).not.toBeDisabled();
    await userEvent.click(next);
    await waitFor(() => {
      expect(mockFetchOrders).toHaveBeenLastCalledWith(
        { page: 1, size: 20, status: null },
        expect.anything(),
      );
    });
  });
});
