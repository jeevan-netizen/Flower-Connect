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
import api from "@/shared/lib/api";
import {
  downloadVendorStatementCsv,
  fetchVendorBillingRate,
  fetchVendorLedger,
  fetchVendorOutstandingBalance,
  fetchVendorSettlementDetail,
  fetchVendorSettlements,
} from "./api";

vi.mock("@/shared/lib/api", () => ({
  default: {
    get: vi.fn(),
  },
}));

describe("Vendor Billing API", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("fetches vendor billing commission rate", async () => {
    const mockRate = { vendorId: 10, rate: 12.5 };
    vi.mocked(api.get).mockResolvedValueOnce({ data: mockRate });

    const result = await fetchVendorBillingRate();
    expect(api.get).toHaveBeenCalledWith("/vendor/billing/rate", { signal: undefined });
    expect(result).toEqual(mockRate);
  });

  it("fetches vendor outstanding balance", async () => {
    const mockBalance = { vendorId: 10, outstandingBalance: 450.75 };
    vi.mocked(api.get).mockResolvedValueOnce({ data: mockBalance });

    const result = await fetchVendorOutstandingBalance();
    expect(api.get).toHaveBeenCalledWith("/vendor/billing/outstanding", { signal: undefined });
    expect(result).toEqual(mockBalance);
  });

  it("fetches vendor ledger entries with params", async () => {
    const mockPage = {
      content: [],
      page: 0,
      size: 10,
      totalElements: 0,
      totalPages: 0,
      first: true,
      last: true,
      empty: true,
    };
    vi.mocked(api.get).mockResolvedValueOnce({ data: mockPage });

    const params = { page: 0, size: 10, type: "ONLINE_ORDER_CREDIT" as const };
    const result = await fetchVendorLedger(params);
    expect(api.get).toHaveBeenCalledWith("/vendor/billing/ledger", {
      params,
      signal: undefined,
    });
    expect(result).toEqual(mockPage);
  });

  it("fetches vendor settlements", async () => {
    const mockSettlements = {
      content: [],
      page: 0,
      size: 10,
      totalElements: 0,
      totalPages: 0,
      first: true,
      last: true,
      empty: true,
    };
    vi.mocked(api.get).mockResolvedValueOnce({ data: mockSettlements });

    const params = { page: 1, size: 5 };
    const result = await fetchVendorSettlements(params);
    expect(api.get).toHaveBeenCalledWith("/vendor/billing/settlements", {
      params,
      signal: undefined,
    });
    expect(result).toEqual(mockSettlements);
  });

  it("fetches single settlement detail with entries", async () => {
    const mockDetail = {
      settlement: {
        id: 42,
        vendorId: 10,
        vendorBusinessName: "Flora",
        periodStart: "2026-03-01",
        periodEnd: "2026-03-07",
        totalCredits: 1000,
        totalDues: 200,
        netAmount: 800,
        direction: "PLATFORM_PAYS_VENDOR" as const,
        status: "SETTLED" as const,
        adminReferenceNote: null,
        settledByUserId: 1,
        settledByEmail: "admin@flowerconnect.com",
        settledAt: "2026-03-08T10:00:00Z",
        createdAt: "2026-03-08T00:00:00Z",
      },
      entries: [],
    };
    vi.mocked(api.get).mockResolvedValueOnce({ data: mockDetail });

    const result = await fetchVendorSettlementDetail(42);
    expect(api.get).toHaveBeenCalledWith("/vendor/billing/settlements/42", { signal: undefined });
    expect(result).toEqual(mockDetail);
  });

  it("downloads vendor statement CSV and triggers anchor click", async () => {
    const csvContent = "Period Start,Period End\n2026-03-01,2026-03-07";
    const blobData = new Blob([csvContent], { type: "text/csv" });
    vi.mocked(api.get).mockResolvedValueOnce({ data: blobData });

    // Mock DOM elements
    const createObjectURLMock = vi.fn().mockReturnValue("blob:http://localhost/test");
    const revokeObjectURLMock = vi.fn();
    window.URL.createObjectURL = createObjectURLMock;
    window.URL.revokeObjectURL = revokeObjectURLMock;
    const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});
    const appendChildSpy = vi.spyOn(document.body, "appendChild");

    const result = await downloadVendorStatementCsv("2026-03-01", "2026-03-07");

    expect(api.get).toHaveBeenCalledWith("/vendor/billing/statement", {
      params: { periodStart: "2026-03-01", periodEnd: "2026-03-07" },
      responseType: "blob",
    });
    expect(createObjectURLMock).toHaveBeenCalled();
    expect(appendChildSpy).toHaveBeenCalled();
    expect(clickSpy).toHaveBeenCalled();
    expect(revokeObjectURLMock).toHaveBeenCalled();
    expect(result).toBeDefined();

    clickSpy.mockRestore();
  });
});
