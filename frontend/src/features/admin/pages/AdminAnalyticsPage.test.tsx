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
import { screen, waitFor, fireEvent } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AdminAnalyticsPage } from "@/features/admin/pages/AdminAnalyticsPage";
import { renderWithProviders } from "@/test/render";

const mockOverview = {
  totalDeliveredOrders: 5,
  marketplaceSales: 1250.0,
  platformRevenue: 125.0,
  activeVendors: 2,
  activeCustomers: 3,
  topVendors: [],
  disputeRate: 0.1,
};
const mockTrends = [
  { date: "2026-11-01", orderCount: 2, revenue: 500.0 },
  { date: "2026-11-02", orderCount: 3, revenue: 750.0 },
];

vi.mock("@/features/admin/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/features/admin/api")>();
  return {
    ...actual,
    fetchAnalyticsOverview: vi.fn(),
    fetchAnalyticsDailyTrends: vi.fn(),
  };
});

import * as adminApi from "@/features/admin/api";

const fetchOverview = vi.mocked(adminApi.fetchAnalyticsOverview);
const fetchTrends = vi.mocked(adminApi.fetchAnalyticsDailyTrends);

function setInputValue(input: HTMLInputElement, value: string) {
  fireEvent.change(input, { target: { value } });
}

function input(testId: string): HTMLInputElement {
  return screen.getByTestId(testId) as HTMLInputElement;
}

function setup() {
  fetchOverview.mockResolvedValue(mockOverview);
  fetchTrends.mockResolvedValue(mockTrends);
  return renderWithProviders(<AdminAnalyticsPage />);
}

describe("AdminAnalyticsPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("fetches with a full default range on load and shows metrics", async () => {
    setup();

    await waitFor(() => {
      expect(fetchOverview).toHaveBeenCalledTimes(1);
      expect(fetchOverview).toHaveBeenCalledWith(expect.any(String), expect.any(String));
    });

    expect(screen.getByText("Delivered orders")).toBeInTheDocument();
    expect(input("from-date-input").value).toBeTruthy();
    expect(input("to-date-input").value).toBeTruthy();
  });

  it("keeps current metrics visible and does not refetch while editing a draft range", async () => {
    setup();

    const fromInput = input("from-date-input");
    const toInput = input("to-date-input");
    setInputValue(fromInput, "2026-12-01");
    setInputValue(toInput, "2026-12-07");

    // Draft is valid, so no validation error and no re-fetch until Apply.
    expect(screen.queryByTestId("range-error")).not.toBeInTheDocument();
    expect(fetchOverview).toHaveBeenCalledTimes(1);

    // Current (default-range) metrics remain visible while the draft is edited.
    expect(screen.getByText("Delivered orders")).toBeInTheDocument();
  });

  it("shows an inline validation message and does not refetch for an incomplete range", async () => {
    setup();

    const fromInput = input("from-date-input");
    const toInput = input("to-date-input");
    setInputValue(fromInput, "2026-12-05");
    setInputValue(toInput, ""); // leave end date empty

    await waitFor(() => {
      expect(screen.getByTestId("range-error")).toHaveTextContent(
        "Please enter both start and end dates.",
      );
    });

    await userEvent.click(screen.getByTestId("apply-range-button"));

    // No re-fetch: applied range is unchanged, so call count stays at the initial load.
    expect(fetchOverview).toHaveBeenCalledTimes(1);
    expect(screen.getByText("5")).toBeInTheDocument(); // current metrics still visible
  });

  it("shows an inline validation message and does not refetch for a reversed range", async () => {
    setup();

    const fromInput = input("from-date-input");
    const toInput = input("to-date-input");
    setInputValue(fromInput, "2026-12-10");
    setInputValue(toInput, "2026-12-01");

    await waitFor(() => {
      expect(screen.getByTestId("range-error")).toHaveTextContent(
        "Start date must be on or before end date.",
      );
    });

    await userEvent.click(screen.getByTestId("apply-range-button"));

    expect(fetchOverview).toHaveBeenCalledTimes(1);
    expect(screen.getByText("5")).toBeInTheDocument();
  });

  it("applies a valid range and refetches with the selected dates", async () => {
    setup();
    fetchOverview.mockResolvedValueOnce({ ...mockOverview, totalDeliveredOrders: 9 });

    const fromInput = input("from-date-input");
    const toInput = input("to-date-input");
    setInputValue(fromInput, "2026-12-01");
    setInputValue(toInput, "2026-12-07");

    await userEvent.click(screen.getByTestId("apply-range-button"));

    expect(screen.queryByTestId("range-error")).not.toBeInTheDocument();
    expect(fetchOverview).toHaveBeenCalledTimes(2);
    expect(fetchOverview).toHaveBeenLastCalledWith("2026-12-01", "2026-12-07");
    expect(fromInput).toHaveValue("2026-12-01");
    expect(toInput).toHaveValue("2026-12-07");

    await waitFor(() => {
      expect(screen.getByText("9")).toBeInTheDocument(); // updated metrics
    });
  });

  it("Reset to 30 days updates both fields and re-applies the default range", async () => {
    setup();

    const fromInput = input("from-date-input");
    const toInput = input("to-date-input");
    setInputValue(fromInput, "2026-12-01");
    setInputValue(toInput, "2026-12-07");
    await userEvent.click(screen.getByTestId("apply-range-button"));

    await userEvent.click(screen.getByTestId("reset-range-button"));

    expect(fromInput.value).not.toBe("");
    expect(toInput.value).not.toBe("");
    const from = new Date(fromInput.value);
    const to = new Date(toInput.value);
    expect(to.getTime() - from.getTime()).toBe(29 * 24 * 60 * 60 * 1000); // 30-day window
    expect(fetchOverview).toHaveBeenCalledTimes(3);
    expect(fetchOverview).toHaveBeenLastCalledWith(fromInput.value, toInput.value);
  });

  it("Clear range empties both fields and applies the backend default", async () => {
    setup();

    const fromInput = input("from-date-input");
    const toInput = input("to-date-input");
    setInputValue(fromInput, "2026-12-01");
    setInputValue(toInput, "2026-12-07");
    await userEvent.click(screen.getByTestId("apply-range-button"));

    await userEvent.click(screen.getByTestId("clear-range-button"));

    expect(fromInput).toHaveValue("");
    expect(toInput).toHaveValue("");
    expect(fetchOverview).toHaveBeenCalledTimes(3);
    expect(fetchOverview).toHaveBeenLastCalledWith(undefined, undefined);
  });
});
