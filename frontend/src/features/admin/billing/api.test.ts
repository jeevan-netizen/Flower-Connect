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
  downloadAdminStatementCsv,
  fetchAdminLedger,
  fetchAdminSettlementDetail,
  fetchAdminSettlements,
  fetchAdminVendorOutstanding,
  fetchPlatformCommissionRate,
  finalizeAdminSettlement,
  runAdminSettlement,
  updatePlatformCommissionRate,
  updateVendorCommissionRate,
} from "./api";

vi.mock("@/shared/lib/api", () => ({
  default: {
    get: vi.fn(),
    patch: vi.fn(),
    post: vi.fn(),
  },
}));

describe("Admin Billing API", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("fetches platform commission rate", async () => {
    const mockRate = { configKey: "commission_rate_default", rate: 10.0 };
    vi.mocked(api.get).mockResolvedValueOnce({ data: mockRate });

    const result = await fetchPlatformCommissionRate();
    expect(api.get).toHaveBeenCalledWith("/admin/platform-settings/commission-rate", {
      signal: undefined,
    });
    expect(result).toEqual(mockRate);
  });

  it("updates platform commission rate", async () => {
    const mockRate = { configKey: "commission_rate_default", rate: 12.5 };
    vi.mocked(api.patch).mockResolvedValueOnce({ data: mockRate });

    const result = await updatePlatformCommissionRate(12.5);
    expect(api.patch).toHaveBeenCalledWith("/admin/platform-settings/commission-rate", {
      rate: 12.5,
    });
    expect(result).toEqual(mockRate);
  });

  it("updates vendor commission rate override", async () => {
    const mockRes = { vendorId: 5, rate: 8.5 };
    vi.mocked(api.patch).mockResolvedValueOnce({ data: mockRes });

    const result = await updateVendorCommissionRate(5, 8.5);
    expect(api.patch).toHaveBeenCalledWith("/admin/vendors/5/commission-rate", {
      rate: 8.5,
    });
    expect(result).toEqual(mockRes);
  });

  it("fetches vendor outstanding balance for admin", async () => {
    const mockBalance = { vendorId: 5, outstandingBalance: -250.0 };
    vi.mocked(api.get).mockResolvedValueOnce({ data: mockBalance });

    const result = await fetchAdminVendorOutstanding(5);
    expect(api.get).toHaveBeenCalledWith("/admin/billing/outstanding/5", { signal: undefined });
    expect(result).toEqual(mockBalance);
  });

  it("fetches admin settlements list", async () => {
    const mockPage = { content: [], totalElements: 0, totalPages: 0 };
    vi.mocked(api.get).mockResolvedValueOnce({ data: mockPage });

    const params = { page: 0, size: 20, status: "PENDING" as const };
    const result = await fetchAdminSettlements(params);
    expect(api.get).toHaveBeenCalledWith("/admin/billing/settlements", {
      params,
      signal: undefined,
    });
    expect(result).toEqual(mockPage);
  });

  it("fetches admin settlement detail", async () => {
    const mockDetail = { settlement: { id: 1 }, entries: [] };
    vi.mocked(api.get).mockResolvedValueOnce({ data: mockDetail });

    const result = await fetchAdminSettlementDetail(1);
    expect(api.get).toHaveBeenCalledWith("/admin/billing/settlements/1", { signal: undefined });
    expect(result).toEqual(mockDetail);
  });

  it("runs settlement generation", async () => {
    const mockList = [{ id: 1, netAmount: 500.0 }];
    vi.mocked(api.post).mockResolvedValueOnce({ data: mockList });

    const req = { periodStart: "2026-10-01", periodEnd: "2026-10-07", vendorId: 5 };
    const result = await runAdminSettlement(req);
    expect(api.post).toHaveBeenCalledWith("/admin/billing/settlements/run", req);
    expect(result).toEqual(mockList);
  });

  it("finalizes settlement with admin note", async () => {
    const mockSettlement = { id: 1, status: "SETTLED" };
    vi.mocked(api.patch).mockResolvedValueOnce({ data: mockSettlement });

    const result = await finalizeAdminSettlement(1, "NEFT-12345");
    expect(api.patch).toHaveBeenCalledWith("/admin/billing/settlements/1/finalize", {
      adminReferenceNote: "NEFT-12345",
    });
    expect(result).toEqual(mockSettlement);
  });

  it("fetches admin ledger entries", async () => {
    const mockLedger = { content: [], totalElements: 0, totalPages: 0 };
    vi.mocked(api.get).mockResolvedValueOnce({ data: mockLedger });

    const params = { vendorId: 5, page: 0, size: 10 };
    const result = await fetchAdminLedger(params);
    expect(api.get).toHaveBeenCalledWith("/admin/billing/ledger", {
      params,
      signal: undefined,
    });
    expect(result).toEqual(mockLedger);
  });

  it("downloads admin statement CSV", async () => {
    const csvContent = "Period,Vendor\n2026-10-01,5";
    const blobData = new Blob([csvContent], { type: "text/csv" });
    vi.mocked(api.get).mockResolvedValueOnce({ data: blobData });

    const createObjectURLMock = vi.fn().mockReturnValue("blob:http://localhost/test");
    const revokeObjectURLMock = vi.fn();
    window.URL.createObjectURL = createObjectURLMock;
    window.URL.revokeObjectURL = revokeObjectURLMock;
    const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});
    const appendChildSpy = vi.spyOn(document.body, "appendChild");

    const result = await downloadAdminStatementCsv(5, "2026-10-01", "2026-10-07");

    expect(api.get).toHaveBeenCalledWith("/admin/billing/statement", {
      params: { vendorId: 5, periodStart: "2026-10-01", periodEnd: "2026-10-07" },
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
