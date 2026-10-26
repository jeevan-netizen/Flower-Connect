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
import { VendorBillingPage } from "@/features/vendor/pages/VendorBillingPage";
import { renderWithProviders } from "@/test/render";
import { apiError } from "@/test/api-errors";
import * as billingApi from "../billing/api";

vi.mock("../billing/api", () => ({
  fetchVendorBillingRate: vi.fn(),
  fetchVendorOutstandingBalance: vi.fn(),
  fetchVendorSettlements: vi.fn(),
  fetchVendorLedger: vi.fn(),
  fetchVendorSettlementDetail: vi.fn(),
  downloadVendorStatementCsv: vi.fn(),
}));

const mockRate = { vendorId: 1, rate: 15.0 };
const mockBalance = { vendorId: 1, outstandingBalance: 1250.5 };
const mockSettlements = {
  content: [
    {
      id: 1,
      vendorId: 1,
      vendorBusinessName: "Petal & Stem",
      periodStart: "2026-03-01",
      periodEnd: "2026-03-07",
      totalCredits: 2000.0,
      totalDues: 300.0,
      netAmount: 1700.0,
      direction: "PLATFORM_PAYS_VENDOR" as const,
      status: "SETTLED" as const,
      adminReferenceNote: "Settled on time",
      settledByUserId: 1,
      settledByEmail: "admin@flowerconnect.com",
      settledAt: "2026-03-08T10:00:00Z",
      createdAt: "2026-03-08T00:00:00Z",
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
      id: 101,
      vendorId: 1,
      orderId: 55,
      disputeId: null,
      settlementId: null,
      type: "ONLINE_ORDER_CREDIT" as const,
      amount: 450.0,
      reference: "Order delivery #55",
      createdAt: "2026-03-09T14:30:00Z",
    },
    {
      id: 102,
      vendorId: 1,
      orderId: 56,
      disputeId: null,
      settlementId: null,
      type: "COD_DUES" as const,
      amount: -50.0,
      reference: "Order delivery #56",
      createdAt: "2026-03-09T15:30:00Z",
    },
  ],
  page: 0,
  size: 10,
  totalElements: 2,
  totalPages: 1,
  first: true,
  last: true,
  empty: false,
};

function setupSuccessful() {
  vi.mocked(billingApi.fetchVendorBillingRate).mockResolvedValue(mockRate);
  vi.mocked(billingApi.fetchVendorOutstandingBalance).mockResolvedValue(mockBalance);
  vi.mocked(billingApi.fetchVendorSettlements).mockResolvedValue(mockSettlements);
  vi.mocked(billingApi.fetchVendorLedger).mockResolvedValue(mockLedger);
  return renderWithProviders(<VendorBillingPage />);
}

describe("VendorBillingPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders page header, commission rate, and unattached balance", async () => {
    setupSuccessful();

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: "Billing & Payouts" })).toBeInTheDocument();
      expect(screen.getByTestId("commission-rate-card")).toBeInTheDocument();
      expect(screen.getByText(/15\.00%/)).toBeInTheDocument();
      expect(screen.getByTestId("outstanding-balance-card")).toBeInTheDocument();
      expect(screen.getByText(/1,250\.50/)).toBeInTheDocument();
      expect(screen.getByText("Platform Owes You")).toBeInTheDocument();
    });
  });

  it("renders statement download form with CSV export action", async () => {
    setupSuccessful();

    await waitFor(() => {
      expect(screen.getByTestId("statement-download-card")).toBeInTheDocument();
    });

    expect(screen.getByRole("button", { name: /export csv/i })).toBeInTheDocument();
  });

  it("renders settlement history table and ledger transactions", async () => {
    setupSuccessful();

    await waitFor(() => {
      expect(screen.getByTestId("settlement-history-table")).toBeInTheDocument();
      expect(screen.getByText("2026-03-01 → 2026-03-07")).toBeInTheDocument();
      expect(screen.getByText("SETTLED")).toBeInTheDocument();
      expect(screen.getByTestId("ledger-transaction-table")).toBeInTheDocument();
      expect(screen.getAllByText("Online Order Credit").length).toBeGreaterThan(0);
      expect(screen.getAllByText("COD Commission Due").length).toBeGreaterThan(0);
    });
  });

  it("handles fatal API errors with retry button", async () => {
    vi.mocked(billingApi.fetchVendorBillingRate).mockRejectedValue(
      apiError(500, { message: "Internal server error" }),
    );
    vi.mocked(billingApi.fetchVendorOutstandingBalance).mockResolvedValue(mockBalance);
    vi.mocked(billingApi.fetchVendorSettlements).mockResolvedValue(mockSettlements);
    vi.mocked(billingApi.fetchVendorLedger).mockResolvedValue(mockLedger);

    renderWithProviders(<VendorBillingPage />);

    expect(await screen.findByText(/something went wrong/i)).toBeInTheDocument();

    vi.mocked(billingApi.fetchVendorBillingRate).mockResolvedValue(mockRate);
    await userEvent.click(screen.getByRole("button", { name: /try again/i }));

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: "Billing & Payouts" })).toBeInTheDocument();
    });
  });
});
