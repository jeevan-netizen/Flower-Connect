import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderWithProviders } from "@/test/render";
import { AdminDisputesPage } from "@/features/admin/pages/AdminDisputesPage";
import type { DisputeListItem, DisputeStatus } from "@/features/dispute/types";

const mockFetchAdminDisputes = vi.hoisted(() => vi.fn());
const mockResolveDispute = vi.hoisted(() => vi.fn());

vi.mock("@/features/dispute/api", () => ({
  raiseDispute: vi.fn(),
  fetchCustomerDisputes: vi.fn(),
  fetchCustomerDispute: vi.fn(),
  fetchVendorDisputes: vi.fn(),
  fetchVendorDispute: vi.fn(),
  respondToDispute: vi.fn(),
  fetchAdminDisputes: mockFetchAdminDisputes,
  fetchAdminDispute: vi.fn(),
  resolveDispute: mockResolveDispute,
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
    status: "RESOLVED_PARTIAL" as DisputeStatus,
    reasonCode: "ITEM_DAMAGED",
    customerName: "Bob Buyer",
    vendorName: "Petal & Stem",
    orderTotal: 50,
    refundAmount: 20,
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

describe("AdminDisputesPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockFetchAdminDisputes.mockResolvedValue(populatedPage);
    mockResolveDispute.mockResolvedValue({
      id: 1,
      orderId: 1001,
      orderNumber: "Order #1001",
      status: "RESOLVED_REFUND",
      reasonCode: "ITEM_NOT_AS_DESCRIBED",
      description: "The flowers were wilted",
      customerName: "Jane Customer",
      customerEmail: "jane@example.com",
      vendorName: "Petal & Stem",
      orderTotal: 100,
      refundAmount: 100,
      vendorResponse: null,
      resolutionNote: "Full refund granted",
      createdAt: "2026-10-01T10:00:00Z",
      updatedAt: "2026-10-05T14:30:00Z",
      resolvedAt: "2026-10-05T14:30:00Z",
    });
  });

  it("renders the dispute queue with status filter", async () => {
    renderWithProviders(<AdminDisputesPage />, { route: "/admin/disputes" });

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: /disputes/i })).toBeInTheDocument();
    });
    expect(screen.getByLabelText(/status/i)).toBeInTheDocument();
    expect(screen.getAllByText(/open/i).length).toBeGreaterThan(0);
    expect(screen.getByText(/resolve/i)).toBeInTheDocument();
  });

  it("shows empty state when no disputes match filters", async () => {
    mockFetchAdminDisputes.mockResolvedValue(emptyPage);
    renderWithProviders(<AdminDisputesPage />, { route: "/admin/disputes" });

    await waitFor(() => {
      expect(screen.getAllByText(/no disputes/i).length).toBeGreaterThan(0);
    });
  });

  it("opens resolution dialog and submits full refund", async () => {
    const user = userEvent.setup();
    renderWithProviders(<AdminDisputesPage />, { route: "/admin/disputes" });

    await waitFor(() => {
      const buttons = screen.getAllByRole("button", { name: /resolve/i });
      expect(buttons.length).toBeGreaterThan(0);
    });
    const resolveButtons = screen.getAllByRole("button", { name: /resolve/i });
    await user.click(resolveButtons[0]);

    await screen.findByLabelText(/outcome/i);
    await user.selectOptions(screen.getByLabelText(/outcome/i), "RESOLVED_REFUND");
    await user.type(screen.getByLabelText(/resolution note/i), "Full refund granted - verified");
    await user.click(screen.getByRole("button", { name: /resolve dispute/i }));

    await waitFor(() => {
      expect(mockResolveDispute).toHaveBeenCalledTimes(1);
    });
  });

  it("requires a resolution note", async () => {
    const user = userEvent.setup();
    renderWithProviders(<AdminDisputesPage />, { route: "/admin/disputes" });

    await waitFor(() => {
      const buttons = screen.getAllByRole("button", { name: /resolve/i });
      expect(buttons.length).toBeGreaterThan(0);
    });
    const resolveButtons = screen.getAllByRole("button", { name: /resolve/i });
    await user.click(resolveButtons[0]);

    await screen.findByLabelText(/outcome/i);
    await user.selectOptions(screen.getByLabelText(/outcome/i), "RESOLVED_REJECTED");
    await user.click(screen.getByRole("button", { name: /resolve dispute/i }));

    expect(await screen.findByText(/resolution note is required/i)).toBeInTheDocument();
    expect(mockResolveDispute).not.toHaveBeenCalled();
  });

  it("rejects partial refund exceeding order total", async () => {
    const user = userEvent.setup();
    renderWithProviders(<AdminDisputesPage />, { route: "/admin/disputes" });

    await waitFor(() => {
      const buttons = screen.getAllByRole("button", { name: /resolve/i });
      expect(buttons.length).toBeGreaterThan(0);
    });
    const resolveButtons = screen.getAllByRole("button", { name: /resolve/i });
    await user.click(resolveButtons[0]);

    await screen.findByLabelText(/outcome/i);
    await user.selectOptions(screen.getByLabelText(/outcome/i), "RESOLVED_PARTIAL");
    await user.type(screen.getByLabelText(/refund amount/i), "150");
    await user.type(screen.getByLabelText(/resolution note/i), "Partial refund");
    await user.click(screen.getByRole("button", { name: /resolve dispute/i }));

    await waitFor(() => {
      expect(screen.getAllByText(/cannot exceed order total/i).length).toBeGreaterThan(0);
    });
    expect(mockResolveDispute).not.toHaveBeenCalled();
  });

  it("allows full refund at order total", async () => {
    const user = userEvent.setup();
    renderWithProviders(<AdminDisputesPage />, { route: "/admin/disputes" });

    await waitFor(() => {
      const buttons = screen.getAllByRole("button", { name: /resolve/i });
      expect(buttons.length).toBeGreaterThan(0);
    });
    const resolveButtons = screen.getAllByRole("button", { name: /resolve/i });
    await user.click(resolveButtons[0]);

    await screen.findByLabelText(/outcome/i);
    await user.selectOptions(screen.getByLabelText(/outcome/i), "RESOLVED_REFUND");
    await user.type(screen.getByLabelText(/resolution note/i), "Full refund");
    await user.click(screen.getByRole("button", { name: /resolve dispute/i }));

    await waitFor(() => {
      expect(mockResolveDispute).toHaveBeenCalledTimes(1);
    });
  });

  it("shows saved refund amount on resolved disputes", async () => {
    renderWithProviders(<AdminDisputesPage />, { route: "/admin/disputes" });

    await waitFor(() => {
      expect(screen.getByText(/20\.00/i)).toBeInTheDocument();
    });
  });

  it("shows loading state while fetching disputes", () => {
    mockFetchAdminDisputes.mockReturnValue(new Promise(() => {}));
    renderWithProviders(<AdminDisputesPage />, { route: "/admin/disputes" });

    expect(screen.getByText(/loading disputes/i)).toBeInTheDocument();
  });
});
