/*
 * Copyright 2026 FlowerConnect
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AdminBillingPage } from "@/features/admin/pages/AdminBillingPage";
import { renderWithProviders } from "@/test/render";
import { apiError } from "@/test/api-errors";
import * as adminBillingApi from "../billing/api";

vi.mock("../billing/api", () => ({
  fetchPlatformCommissionRate: vi.fn(),
  updatePlatformCommissionRate: vi.fn(),
  updateVendorCommissionRate: vi.fn(),
  fetchAdminVendorOutstanding: vi.fn(),
  fetchAdminSettlements: vi.fn(),
  fetchAdminSettlementDetail: vi.fn(),
  runAdminSettlement: vi.fn(),
  finalizeAdminSettlement: vi.fn(),
  fetchAdminLedger: vi.fn(),
  downloadAdminStatementCsv: vi.fn(),
  fetchAdminOverdueVendors: vi.fn(),
}));

const mockPlatformRate = { configKey: "commission_rate_default", rate: 10.0 };
const mockSettlements = {
  content: [
    {
      id: 5,
      vendorId: 2,
      vendorBusinessName: "Bloom & Wild",
      periodStart: "2026-10-01",
      periodEnd: "2026-10-07",
      totalCredits: 3500.0,
      totalDues: 500.0,
      netAmount: 3000.0,
      direction: "PLATFORM_PAYS_VENDOR" as const,
      status: "PENDING" as const,
      adminReferenceNote: null,
      settledByUserId: null,
      settledByEmail: null,
      settledAt: null,
      createdAt: "2026-10-08T00:00:00Z",
    },
  ],
  page: 0,
  size: 10,
  totalElements: 1,
  totalPages: 1,
  first: true,
  last: true,
  empty: false,
};
const mockLedger = {
  content: [
    {
      id: 201,
      vendorId: 2,
      orderId: 88,
      disputeId: null,
      settlementId: null,
      type: "ONLINE_ORDER_CREDIT" as const,
      amount: 600.0,
      reference: "Order #88 delivered",
      createdAt: "2026-10-09T10:00:00Z",
    },
  ],
  page: 0,
  size: 10,
  totalElements: 1,
  totalPages: 1,
  first: true,
  last: true,
  empty: false,
};

function setupSuccessful() {
  vi.mocked(adminBillingApi.fetchPlatformCommissionRate).mockResolvedValue(mockPlatformRate);
  vi.mocked(adminBillingApi.fetchAdminSettlements).mockResolvedValue(mockSettlements);
  vi.mocked(adminBillingApi.fetchAdminLedger).mockResolvedValue(mockLedger);
  vi.mocked(adminBillingApi.fetchAdminOverdueVendors).mockResolvedValue([]);
  return renderWithProviders(<AdminBillingPage />);
}

describe("AdminBillingPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders page title and platform commission rate card", async () => {
    setupSuccessful();

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: "Billing & Settlements" })).toBeInTheDocument();
      expect(screen.getByTestId("platform-commission-card")).toBeInTheDocument();
      expect(screen.getByText(/10\.00%/)).toBeInTheDocument();
    });
  });

  it("renders vendor rate override card and supports lookup", async () => {
    vi.mocked(adminBillingApi.fetchAdminVendorOutstanding).mockResolvedValue({
      vendorId: 2,
      outstandingBalance: -150.0,
    });
    setupSuccessful();

    await waitFor(() => {
      expect(screen.getByTestId("vendor-rate-override-card")).toBeInTheDocument();
    });

    const card = screen.getByTestId("vendor-rate-override-card");
    const vendorInput = screen.getByPlaceholderText("Enter Vendor ID");
    await userEvent.type(vendorInput, "2");
    await userEvent.click(screen.getByRole("button", { name: /inspect vendor/i }));

    await waitFor(() => {
      expect(card).toHaveTextContent("Vendor #2");
      expect(card).toHaveTextContent("-₹150.00");
    });
  });

  it("renders settlements table with action buttons and ledger transactions", async () => {
    setupSuccessful();

    await waitFor(() => {
      expect(screen.getByTestId("admin-settlement-table")).toBeInTheDocument();
      expect(screen.getByText("Bloom & Wild")).toBeInTheDocument();
      expect(screen.getByRole("button", { name: /finalize/i })).toBeInTheDocument();
      expect(screen.getByRole("button", { name: /\+ run settlement/i })).toBeInTheDocument();

      expect(screen.getByTestId("admin-ledger-table")).toBeInTheDocument();
      expect(screen.getAllByText("Online Order Credit").length).toBeGreaterThan(0);
      expect(screen.getByText("Order #88 delivered")).toBeInTheDocument();
    });
  });

  it("opens run settlement modal when run settlement button clicked", async () => {
    setupSuccessful();

    await waitFor(() => {
      expect(screen.getByRole("button", { name: /\+ run settlement/i })).toBeInTheDocument();
    });

    await userEvent.click(screen.getByRole("button", { name: /\+ run settlement/i }));

    await waitFor(() => {
      expect(screen.getByTestId("run-settlement-modal")).toBeInTheDocument();
      expect(screen.getByRole("button", { name: /generate settlement/i })).toBeInTheDocument();
    });
  });

  it("opens finalize modal when finalize button clicked on pending settlement", async () => {
    setupSuccessful();

    await waitFor(() => {
      expect(screen.getByRole("button", { name: /finalize/i })).toBeInTheDocument();
    });

    await userEvent.click(screen.getByRole("button", { name: /finalize/i }));

    await waitFor(() => {
      expect(screen.getByTestId("finalize-settlement-modal")).toBeInTheDocument();
      expect(screen.getByLabelText(/admin reference note/i)).toBeInTheDocument();
    });
  });

  it("handles API error with retry button", async () => {
    vi.mocked(adminBillingApi.fetchPlatformCommissionRate).mockResolvedValue(mockPlatformRate);
    vi.mocked(adminBillingApi.fetchAdminSettlements).mockRejectedValue(
      apiError(500, { message: "Internal server error" }),
    );
    vi.mocked(adminBillingApi.fetchAdminLedger).mockResolvedValue(mockLedger);

    renderWithProviders(<AdminBillingPage />);

    expect(await screen.findByText(/something went wrong/i)).toBeInTheDocument();

    vi.mocked(adminBillingApi.fetchAdminSettlements).mockResolvedValue(mockSettlements);
    await userEvent.click(screen.getByRole("button", { name: /try again/i }));

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: "Billing & Settlements" })).toBeInTheDocument();
    });
  });

  it("renders overdue accounts notice and filters ledger when View Ledger clicked", async () => {
    vi.mocked(adminBillingApi.fetchPlatformCommissionRate).mockResolvedValue(mockPlatformRate);
    vi.mocked(adminBillingApi.fetchAdminSettlements).mockResolvedValue(mockSettlements);
    vi.mocked(adminBillingApi.fetchAdminLedger).mockResolvedValue(mockLedger);
    vi.mocked(adminBillingApi.fetchAdminOverdueVendors).mockResolvedValue([
      {
        vendorId: 10,
        vendorBusinessName: "Rose Garden",
        outstandingBalance: -150.0,
        outstandingDues: 150.0,
        daysOverdue: 35,
        oldestDuesDate: "2026-09-21T10:00:00Z",
      },
    ]);

    renderWithProviders(<AdminBillingPage />);

    await waitFor(() => {
      expect(screen.getByTestId("overdue-vendors-card")).toBeInTheDocument();
      expect(screen.getByText("Rose Garden")).toBeInTheDocument();
      expect(screen.getByText("35 days")).toBeInTheDocument();
      expect(screen.getByText("₹150.00")).toBeInTheDocument();
    });

    const viewLedgerBtn = screen.getByRole("button", { name: /view ledger/i });
    await userEvent.click(viewLedgerBtn);

    const ledgerCard = screen.getByTestId("admin-ledger-table");
    const vendorInput = ledgerCard.querySelector("input[type='number']") as HTMLInputElement;
    expect(vendorInput.value).toBe("10");
  });
});
