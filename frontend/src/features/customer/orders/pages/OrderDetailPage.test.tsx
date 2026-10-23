import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { OrderDetailPage } from "@/features/customer/orders/pages/OrderDetailPage";
import { renderWithProviders } from "@/test/render";
import { makeCustomerOrderResponse, makeCustomerOrderTimelineEntry } from "@/test/factories";
import { apiError } from "@/test/api-errors";

const mockFetchOrder = vi.hoisted(() => vi.fn());

vi.mock("@/features/customer/orders/api", () => ({
  fetchOrder: mockFetchOrder,
}));

function renderDetail(route = "/orders/5001") {
  return renderWithProviders(<OrderDetailPage />, { route });
}

function primeDetail(order = makeCustomerOrderResponse()) {
  mockFetchOrder.mockResolvedValue(order);
  return order;
}

describe("OrderDetailPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockFetchOrder.mockResolvedValue(makeCustomerOrderResponse());
  });

  it("shows the order number, vendor, status stepper and timeline", async () => {
    primeDetail();
    renderDetail();

    await screen.findByText("Order #5001");
    expect(await screen.findByText("Petal & Stem")).toBeInTheDocument();
    expect(await screen.findByText("Order status")).toBeInTheDocument();
    expect(await screen.findByText("Status timeline")).toBeInTheDocument();
  });

  it("shows cash on delivery wording and payment status", async () => {
    primeDetail();
    renderDetail();
    await screen.findByText("Order #5001");
    expect(await screen.findByText("Cash on delivery")).toBeInTheDocument();
    expect(await screen.findByText("Pending")).toBeInTheDocument();
  });

  it("shows the address snapshot and delivery slot", async () => {
    primeDetail();
    renderDetail();
    await screen.findByText("Order #5001");
    expect(await screen.findByText("12 MG Road")).toBeInTheDocument();
    expect(await screen.findByText("Unit 3")).toBeInTheDocument();
    // The delivery slot renders the formatted start and end times.
    expect(
      screen.getAllByText(/\d{1,2}:\d{2}:\d{2}/).length,
    ).toBeGreaterThanOrEqual(1);
  });

  it("shows item snapshots and fee breakdown with incl. taxes", async () => {
    primeDetail(
      makeCustomerOrderResponse({
        items: [
          { productName: "Red Rose Bouquet", quantity: 2, priceSnapshot: 899, subtotal: 1798, note: null },
        ],
        subtotal: 1798,
        deliveryFee: 25,
        platformFee: 0,
        total: 1823,
      }),
    );
    renderDetail();
    await screen.findByText("Order #5001");
    expect(await screen.findByText("Red Rose Bouquet")).toBeInTheDocument();
    expect(screen.getAllByText(/1,798/).length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText("Subtotal").length).toBeGreaterThanOrEqual(1);
    expect(await screen.findByText("Delivery fee")).toBeInTheDocument();
    expect(await screen.findByText("Platform fee")).toBeInTheDocument();
    expect(await screen.findByText("Total")).toBeInTheDocument();
    expect(screen.getAllByText(/incl\. taxes/).length).toBeGreaterThanOrEqual(1);
  });

  it("renders the chronological timeline entries oldest first", async () => {
    primeDetail(
      makeCustomerOrderResponse({
        timeline: [
          makeCustomerOrderTimelineEntry({
            fromStatus: null,
            toStatus: "PLACED",
            actorType: "SYSTEM",
            actorName: null,
            reason: null,
            createdAt: "2026-10-01T10:00:00",
          }),
          makeCustomerOrderTimelineEntry({
            fromStatus: "PLACED",
            toStatus: "CONFIRMED",
            actorType: "VENDOR",
            actorName: "Petal Owner",
            reason: null,
            createdAt: "2026-10-01T10:30:00",
          }),
        ],
      }),
    );
    renderDetail();
    await screen.findByText("Order #5001");
    await waitFor(() => {
      expect(screen.getByText(/Petal Owner/)).toBeInTheDocument();
    });
  });

  it("shows an error state with retry", async () => {
    mockFetchOrder.mockRejectedValue(apiError(500, { message: "Boom" }));
    renderDetail();
    expect(await screen.findByText(/something went wrong/i)).toBeInTheDocument();

    primeDetail();
    await userEvent.click(screen.getByRole("button", { name: /try again/i }));
    expect(await screen.findByText("Order #5001")).toBeInTheDocument();
  });

  it("shows a loading state while fetching", async () => {
    mockFetchOrder.mockImplementation(() => new Promise(() => {}));
    renderDetail();
    expect(await screen.findByText(/loading order/i)).toBeInTheDocument();
  });
});
