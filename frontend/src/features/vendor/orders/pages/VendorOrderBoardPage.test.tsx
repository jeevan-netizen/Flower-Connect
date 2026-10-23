import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { VendorOrderBoardPage } from "@/features/vendor/orders/pages/VendorOrderBoardPage";
import { renderWithProviders } from "@/test/render";
import {
  makeVendorOrderResponse,
  makeVendorOrderPageResponse,
  makeVendorOrderSummaryResponse,
} from "@/test/factories";
import { apiError, businessError } from "@/test/api-errors";
import type { VendorOrderResponse, VendorOrderPageResponse } from "@/features/vendor/orders/types";

const mockFetchVendorOrders = vi.hoisted(() => vi.fn());
const mockFetchVendorOrderSummary = vi.hoisted(() => vi.fn());
const mockAcceptOrder = vi.hoisted(() => vi.fn());
const mockRejectOrder = vi.hoisted(() => vi.fn());
const mockPrepareOrder = vi.hoisted(() => vi.fn());
const mockOutForDeliveryOrder = vi.hoisted(() => vi.fn());
const mockDeliveredOrder = vi.hoisted(() => vi.fn());

vi.mock("@/features/vendor/orders/api", () => ({
  fetchVendorOrders: mockFetchVendorOrders,
  fetchVendorOrderSummary: mockFetchVendorOrderSummary,
  acceptOrder: mockAcceptOrder,
  rejectOrder: mockRejectOrder,
  prepareOrder: mockPrepareOrder,
  outForDeliveryOrder: mockOutForDeliveryOrder,
  deliveredOrder: mockDeliveredOrder,
}));

function renderBoard() {
  return renderWithProviders(<VendorOrderBoardPage />);
}

function primeBoard(
  page: VendorOrderPageResponse = makeVendorOrderPageResponse(),
  summary = makeVendorOrderSummaryResponse(),
) {
  mockFetchVendorOrders.mockResolvedValue(page);
  mockFetchVendorOrderSummary.mockResolvedValue(summary);
  return { page, summary };
}

function placedOrder(overrides: Partial<VendorOrderResponse> = {}): VendorOrderResponse {
  return makeVendorOrderResponse({ status: "PLACED", ...overrides });
}

describe("VendorOrderBoardPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    primeBoard();
  });

  it("renders the four Kanban columns", async () => {
    primeBoard(
      makeVendorOrderPageResponse({ content: [placedOrder({ id: 1 })] }),
    );
    renderBoard();

    await screen.findByText("New");
    await screen.findByText("Accepted / Preparing");
    await screen.findByText("Out for delivery");
    await screen.findByText("Delivered today");
  });

  it("shows today's summary strip", async () => {
    primeBoard();
    renderBoard();
    await screen.findByText("Today's summary");
    expect(await screen.findByText("Orders today")).toBeInTheDocument();
    expect(await screen.findByText("Total value")).toBeInTheDocument();
  });

  it("shows a placed order in the New column with an Accept button", async () => {
    primeBoard(
      makeVendorOrderPageResponse({ content: [placedOrder({ id: 1 })] }),
    );
    renderBoard();
    await screen.findByText("Order #1");
    expect(await screen.getByRole("button", { name: /accept/i })).toBeInTheDocument();
    expect(await screen.getByRole("button", { name: "Reject" })).toBeInTheDocument();
  });

  it("shows a confirmed order with a Prepare button but no Accept", async () => {
    primeBoard(
      makeVendorOrderPageResponse({
        content: [makeVendorOrderResponse({ id: 2, status: "CONFIRMED" })],
      }),
    );
    renderBoard();
    await screen.findByText("Order #2");
    expect(await screen.getByRole("button", { name: /mark preparing/i })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /accept/i })).not.toBeInTheDocument();
  });

  it("shows an empty state with no orders", async () => {
    primeBoard(makeVendorOrderPageResponse({ content: [], totalElements: 0, empty: true }));
    renderBoard();
    expect(await screen.findByText(/no orders yet/i)).toBeInTheDocument();
  });

  it("shows a loading state", async () => {
    mockFetchVendorOrders.mockImplementation(() => new Promise(() => {}));
    renderBoard();
    expect(await screen.findByText(/today's summary/i)).toBeInTheDocument();
  });

  it("shows an error state", async () => {
    mockFetchVendorOrders.mockRejectedValue(apiError(500, { message: "Boom" }));
    renderBoard();
    expect(await screen.findByText(/something went wrong/i)).toBeInTheDocument();
  });

  it("accepts an order and calls the accept API", async () => {
    const user = userEvent.setup();
    primeBoard(
      makeVendorOrderPageResponse({ content: [placedOrder({ id: 1 })] }),
    );
    mockAcceptOrder.mockResolvedValue(
      makeVendorOrderResponse({ id: 1, status: "CONFIRMED" }),
    );
    renderBoard();
    await screen.findByText("Order #1");
    await user.click(screen.getByRole("button", { name: /accept/i }));
    await waitFor(() => {
      expect(mockAcceptOrder).toHaveBeenCalledWith(1);
    });
  });

  it("rejects an order through the reason dialog with validation", async () => {
    const user = userEvent.setup();
    primeBoard(
      makeVendorOrderPageResponse({ content: [placedOrder({ id: 1 })] }),
    );
    mockRejectOrder.mockResolvedValue(
      makeVendorOrderResponse({ id: 1, status: "REJECTED" }),
    );
    renderBoard();
    await screen.findByText("Order #1");
    await user.click(screen.getByRole("button", { name: "Reject" }));

    // Dialog opens and confirm is disabled with no reason.
    expect(await screen.findByRole("dialog")).toBeInTheDocument();
    const confirm = screen.getByRole("button", { name: /reject order/i });
    expect(confirm).toBeDisabled();

    // Typing a reason enables confirm.
    await user.type(screen.getByTestId("reject-reason"), "Out of stock");
    expect(confirm).not.toBeDisabled();
    await user.click(confirm);
    await waitFor(() => {
      expect(mockRejectOrder).toHaveBeenCalledWith(1, "Out of stock");
    });
  });

  it("surfaces a 409 insufficient-stock error on accept", async () => {
    const user = userEvent.setup();
    primeBoard(
      makeVendorOrderPageResponse({ content: [placedOrder({ id: 1 })] }),
    );
    mockAcceptOrder.mockRejectedValue(
      businessError(409, "INSUFFICIENT_STOCK", "Cannot accept order for product 'Red Rose Bouquet': 2 unit(s) on hand, 5 required"),
    );
    renderBoard();
    await screen.findByText("Order #1");
    await user.click(screen.getByRole("button", { name: /accept/i }));
    await waitFor(() => {
      expect(screen.getByText(/insufficient stock/i)).toBeInTheDocument();
    });
    expect(await screen.findByText(/reject the order instead/i)).toBeInTheDocument();
  });

  it("disables action buttons while a request is in flight (double-click protection)", async () => {
    const user = userEvent.setup();
    let resolveAccept: () => void = () => {};
    mockAcceptOrder.mockImplementation(
      () =>
        new Promise<VendorOrderResponse>((resolve) => {
          resolveAccept = () => resolve(makeVendorOrderResponse({ id: 1, status: "CONFIRMED" }));
        }),
    );
    primeBoard(
      makeVendorOrderPageResponse({ content: [placedOrder({ id: 1 })] }),
    );
    renderBoard();
    await screen.findByText("Order #1");
    const accept = screen.getByRole("button", { name: /accept/i });
    await user.click(accept);
    expect(accept).toBeDisabled();
    // A second click while in flight does not call the API again.
    await user.click(accept);
    expect(mockAcceptOrder).toHaveBeenCalledTimes(1);
    resolveAccept();
    await waitFor(() => {
      expect(accept).not.toBeDisabled();
    });
  });
});
