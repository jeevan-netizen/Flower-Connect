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
import { VendorAnalyticsPage } from "@/features/vendor/pages/VendorAnalyticsPage";
import { renderWithProviders } from "@/test/render";
import { apiError } from "@/test/api-errors";
import * as vendorApi from "../api";

vi.mock("../api", () => ({
  fetchVendorAnalyticsOverview: vi.fn(),
  fetchVendorDailyTrends: vi.fn(),
}));

const mockOverview = {
  deliveredOrders: 3,
  marketplaceRevenue: 350.0,
  onlineOrders: 2,
  onlineRevenue: 300.0,
  codOrders: 1,
  codRevenue: 50.0,
  cancelledOrders: 1,
  cancelledRevenue: 75.0,
  repeatCustomerRate: 0.5,
  topProductsByUnits: [
    { productId: 10, productName: "Roses", quantitySold: 7, revenue: 300.0 },
    { productId: 11, productName: "Tulips", quantitySold: 1, revenue: 50.0 },
  ],
  topProductsByRevenue: [
    { productId: 10, productName: "Roses", quantitySold: 7, revenue: 300.0 },
    { productId: 11, productName: "Tulips", quantitySold: 1, revenue: 50.0 },
  ],
  pos: {
    transactionCount: 2,
    totalRevenue: 100.0,
    breakdownByPaymentMethod: [
      { paymentMethod: "CASH", transactionCount: 1, revenue: 30.0 },
      { paymentMethod: "UPI", transactionCount: 1, revenue: 70.0 },
    ],
  },
};

const mockTrends = [
  { date: "2026-11-01", orderCount: 0, revenue: 0.0 },
  { date: "2026-11-03", orderCount: 1, revenue: 100.0 },
  { date: "2026-11-05", orderCount: 2, revenue: 250.0 },
];

function setupSuccessful() {
  vi.mocked(vendorApi.fetchVendorAnalyticsOverview).mockResolvedValue(mockOverview);
  vi.mocked(vendorApi.fetchVendorDailyTrends).mockResolvedValue(mockTrends);
  return renderWithProviders(<VendorAnalyticsPage />);
}

describe("VendorAnalyticsPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders page heading and marketplace summary", async () => {
    setupSuccessful();

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: "Analytics" })).toBeInTheDocument();
      expect(screen.getByText("3")).toBeInTheDocument();
      expect(screen.getByText(/₹350\.00/)).toBeInTheDocument();
    });
  });

  it("renders online and COD revenue breakdown", async () => {
    setupSuccessful();

    await waitFor(() => {
      expect(screen.getAllByText(/₹300\.00/)).toHaveLength(3);
      expect(screen.getAllByText(/₹50\.00/)).toHaveLength(3);
    });
  });

  it("renders POS section as commission-free", async () => {
    setupSuccessful();

    await screen.findByText("Point of sale (commission-free)");
  });

  it("renders POS payment method breakdown", async () => {
    setupSuccessful();

    await waitFor(() => {
      expect(screen.getByText("CASH")).toBeInTheDocument();
      expect(screen.getByText("UPI")).toBeInTheDocument();
    });
  });

  it("renders top products table", async () => {
    setupSuccessful();

    await waitFor(() => {
      expect(screen.getAllByText("Roses")).toHaveLength(2);
      expect(screen.getAllByText("7")).toHaveLength(2);
    });
  });

  it("renders repeat customer rate", async () => {
    setupSuccessful();

    await waitFor(() => {
      expect(screen.getByText("50.0%")).toBeInTheDocument();
    });
  });

  it("renders daily trends table", async () => {
    setupSuccessful();

    await waitFor(() => {
      expect(screen.getByText("2026-11-01")).toBeInTheDocument();
      expect(screen.getByText("2026-11-03")).toBeInTheDocument();
      expect(screen.getByText("2026-11-05")).toBeInTheDocument();
    });
  });

  it("handles API errors with error state", async () => {
    vi.mocked(vendorApi.fetchVendorAnalyticsOverview).mockRejectedValue(
      apiError(500, { message: "Internal server error" }),
    );
    vi.mocked(vendorApi.fetchVendorDailyTrends).mockResolvedValue(mockTrends);

    renderWithProviders(<VendorAnalyticsPage />);

    expect(await screen.findByText(/something went wrong/i)).toBeInTheDocument();
  });

  it("renders empty state when no data", async () => {
    vi.mocked(vendorApi.fetchVendorAnalyticsOverview).mockResolvedValue({
      ...mockOverview,
      deliveredOrders: 0,
      marketplaceRevenue: 0,
      onlineOrders: 0,
      codOrders: 0,
      cancelledOrders: 0,
      repeatCustomerRate: null,
      topProductsByUnits: [],
      topProductsByRevenue: [],
      pos: {
        transactionCount: 0,
        totalRevenue: 0,
        breakdownByPaymentMethod: [],
      },
    });
    vi.mocked(vendorApi.fetchVendorDailyTrends).mockResolvedValue([]);

    renderWithProviders(<VendorAnalyticsPage />);

    await waitFor(() => {
      expect(screen.getByText("No delivered orders in this range.")).toBeInTheDocument();
    });
    await waitFor(() => {
      expect(screen.getAllByText("No products sold in this range.")).toHaveLength(2);
    });
  });
});
