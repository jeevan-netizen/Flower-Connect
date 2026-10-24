import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderWithProviders } from "@/test/render";
import { RaiseDisputePage, DisputeDetailPage } from "@/features/dispute/pages/DisputePages";
import { DISPUTE_STATUSES, DISPUTE_REASON_CODES } from "@/features/dispute/types";
import type { DisputeDetailResponse } from "@/features/dispute/types";

const mockRaiseDispute = vi.hoisted(() => vi.fn());
const mockFetchCustomerDispute = vi.hoisted(() => vi.fn());

vi.mock("@/features/dispute/api", () => ({
  raiseDispute: mockRaiseDispute,
  fetchCustomerDispute: mockFetchCustomerDispute,
  fetchCustomerDisputes: vi.fn(),
  fetchVendorDisputes: vi.fn(),
  fetchVendorDispute: vi.fn(),
  respondToDispute: vi.fn(),
  fetchAdminDisputes: vi.fn(),
  fetchAdminDispute: vi.fn(),
  resolveDispute: vi.fn(),
}));

const disputeDetail: DisputeDetailResponse = {
  id: 42,
  orderId: 1001,
  orderNumber: "Order #1001",
  status: "RESOLVED_PARTIAL",
  reasonCode: "ITEM_DAMAGED",
  description: "The flowers arrived with crushed petals",
  customerName: "Jane Customer",
  customerEmail: "jane@example.com",
  vendorName: "Petal & Stem",
  orderTotal: 100,
  refundAmount: 30,
  vendorResponse: "We apologise for the damage",
  resolutionNote: "Partial refund granted - item damaged",
  createdAt: "2026-10-01T10:00:00Z",
  updatedAt: "2026-10-05T14:30:00Z",
  resolvedAt: "2026-10-05T14:30:00Z",
};

describe("RaiseDisputePage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockRaiseDispute.mockResolvedValue(disputeDetail);
  });

  it("renders reason code select and description textarea", () => {
    renderWithProviders(<RaiseDisputePage />, { route: "/orders/1001/dispute" });

    expect(screen.getByLabelText(/reason/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/description/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /submit dispute/i })).toBeInTheDocument();
  });

  it("requires a non-empty description before submitting", async () => {
    const user = userEvent.setup();
    renderWithProviders(<RaiseDisputePage />, { route: "/orders/1001/dispute" });

    await user.click(screen.getByRole("button", { name: /submit dispute/i }));

    expect(await screen.findByText(/description is required/i)).toBeInTheDocument();
    expect(mockRaiseDispute).not.toHaveBeenCalled();
  });

  it("renders successfully after a valid dispute submission", async () => {
    const user = userEvent.setup();
    renderWithProviders(<RaiseDisputePage />, { route: "/orders/1001/dispute" });

    await user.selectOptions(screen.getByLabelText(/reason/i), "ITEM_NOT_AS_DESCRIBED");
    await user.type(screen.getByLabelText(/description/i), "The flowers were wilted");
    await user.click(screen.getByRole("button", { name: /submit dispute/i }));

    await waitFor(() => {
      expect(screen.getByText(/dispute submitted/i)).toBeInTheDocument();
    });
    expect(mockRaiseDispute).toHaveBeenCalledTimes(1);
  });
});

describe("DisputeDetailPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockFetchCustomerDispute.mockResolvedValue(disputeDetail);
  });

  it("renders dispute status, reason, vendor response, and refund amount", async () => {
    renderWithProviders(<DisputeDetailPage />, { route: "/disputes/42" });

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: /dispute #42/i })).toBeInTheDocument();
    });
    expect(screen.getAllByText(/ITEM DAMAGED/i).length).toBeGreaterThan(0);
    expect(screen.getByText(/30\.00/i)).toBeInTheDocument();
    expect(screen.getByText(/we apologise for the damage/i)).toBeInTheDocument();
    expect(screen.getByText(/partial refund granted - item damaged/i)).toBeInTheDocument();
  });

  it("shows loading state while fetching", () => {
    mockFetchCustomerDispute.mockReturnValue(new Promise(() => {}));
    renderWithProviders(<DisputeDetailPage />, { route: "/disputes/42" });

    expect(screen.getByText(/loading dispute/i)).toBeInTheDocument();
  });
});

describe("Dispute types and constants", () => {
  it("exposes all five dispute statuses", () => {
    expect(DISPUTE_STATUSES).toEqual([
      "OPEN",
      "UNDER_REVIEW",
      "RESOLVED_REFUND",
      "RESOLVED_PARTIAL",
      "RESOLVED_REJECTED",
    ]);
  });

  it("exposes all seven reason codes", () => {
    expect(DISPUTE_REASON_CODES).toHaveLength(7);
    expect(DISPUTE_REASON_CODES).toContain("ITEM_NOT_AS_DESCRIBED");
    expect(DISPUTE_REASON_CODES).toContain("OTHER");
  });
});
