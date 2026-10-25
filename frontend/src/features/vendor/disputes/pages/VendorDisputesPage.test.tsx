import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderWithProviders } from "@/test/render";
import { VendorDisputesPage } from "@/features/vendor/disputes/pages/VendorDisputesPage";
import type { DisputeListItem, DisputeStatus } from "@/features/dispute/types";

const mockFetchVendorDisputes = vi.hoisted(() => vi.fn());
const mockRespondToDispute = vi.hoisted(() => vi.fn());

vi.mock("@/features/dispute/api", () => ({
  raiseDispute: vi.fn(),
  fetchCustomerDisputes: vi.fn(),
  fetchCustomerDispute: vi.fn(),
  fetchVendorDisputes: mockFetchVendorDisputes,
  fetchVendorDispute: vi.fn(),
  respondToDispute: mockRespondToDispute,
  fetchAdminDisputes: vi.fn(),
  fetchAdminDispute: vi.fn(),
  resolveDispute: vi.fn(),
}));

const disputeItems: DisputeListItem[] = [
  {
    id: 1,
    orderId: 1001,
    orderNumber: "Order #1001",
    status: "OPEN" as DisputeStatus,
    reasonCode: "ITEM_NOT_AS_DESCRIBED",
    customerName: "Jane Customer",
    vendorName: "Petal & Stem",
    orderTotal: 100,
    refundAmount: null,
    createdAt: "2026-10-01T10:00:00Z",
    resolvedAt: null,
  },
  {
    id: 2,
    orderId: 1002,
    orderNumber: "Order #1002",
    status: "RESOLVED_REFUND" as DisputeStatus,
    reasonCode: "ITEM_DAMAGED",
    customerName: "Bob Buyer",
    vendorName: "Petal & Stem",
    orderTotal: 50,
    refundAmount: 50,
    createdAt: "2026-09-25T10:00:00Z",
    resolvedAt: "2026-10-02T10:00:00Z",
  },
];

const emptyPage = {
  content: [] as DisputeListItem[],
  page: 0,
  size: 20,
  totalElements: 0,
  totalPages: 0,
  first: true,
  last: true,
  empty: true,
};

const populatedPage = {
  content: disputeItems,
  page: 0,
  size: 20,
  totalElements: 2,
  totalPages: 1,
  first: true,
  last: true,
  empty: false,
};

describe("VendorDisputesPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockFetchVendorDisputes.mockResolvedValue(populatedPage);
    mockRespondToDispute.mockResolvedValue({
      id: 1,
      orderId: 1001,
      orderNumber: "Order #1001",
      status: "UNDER_REVIEW",
      reasonCode: "ITEM_NOT_AS_DESCRIBED",
      description: "The flowers were wilted",
      customerName: "Jane Customer",
      customerEmail: "jane@example.com",
      vendorName: "Petal & Stem",
      orderTotal: 100,
      refundAmount: null,
      vendorResponse: "We will make it right",
      resolutionNote: null,
      createdAt: "2026-10-01T10:00:00Z",
      updatedAt: "2026-10-03T10:00:00Z",
      resolvedAt: null,
    });
  });

  it("renders the disputes table with status and totals", async () => {
    renderWithProviders(<VendorDisputesPage />, { route: "/vendor/disputes" });

    await screen.findByRole("heading", { name: /disputes/i });
    await screen.findByRole("button", { name: /respond/i });
    expect(screen.getByText(/100\.00/i)).toBeInTheDocument();
    expect(screen.getByText(/item not as described/i)).toBeInTheDocument();
  });

  it("shows empty state when no disputes", async () => {
    mockFetchVendorDisputes.mockResolvedValue(emptyPage);
    renderWithProviders(<VendorDisputesPage />, { route: "/vendor/disputes" });

    await waitFor(() => {
      expect(screen.getAllByText(/no disputes/i).length).toBeGreaterThan(0);
    });
  });

  it("opens respond dialog and submits a vendor response", async () => {
    const user = userEvent.setup();
    renderWithProviders(<VendorDisputesPage />, { route: "/vendor/disputes" });

    await waitFor(() => {
      expect(screen.getByRole("button", { name: /respond/i })).toBeInTheDocument();
    });
    await user.click(screen.getByRole("button", { name: /respond/i }));

    await screen.findByLabelText(/response/i);
    await user.type(screen.getByLabelText(/response/i), "We will send a replacement");
    await user.click(screen.getByRole("button", { name: /submit response/i }));

    await waitFor(() => {
      expect(mockRespondToDispute).toHaveBeenCalledWith(1, {
        response: "We will send a replacement",
      });
    });
  });

  it("requires a non-empty vendor response", async () => {
    const user = userEvent.setup();
    renderWithProviders(<VendorDisputesPage />, { route: "/vendor/disputes" });

    await waitFor(() => {
      expect(screen.getByRole("button", { name: /respond/i })).toBeInTheDocument();
    });
    await user.click(screen.getByRole("button", { name: /respond/i }));

    await screen.findByLabelText(/response/i);
    await user.click(screen.getByRole("button", { name: /submit response/i }));

    expect(await screen.findByText(/response is required/i)).toBeInTheDocument();
    expect(mockRespondToDispute).not.toHaveBeenCalled();
  });

  it("shows loading state while fetching disputes", () => {
    mockFetchVendorDisputes.mockReturnValue(new Promise(() => {}));
    renderWithProviders(<VendorDisputesPage />, { route: "/vendor/disputes" });

    expect(screen.getByText(/loading disputes/i)).toBeInTheDocument();
  });
});
