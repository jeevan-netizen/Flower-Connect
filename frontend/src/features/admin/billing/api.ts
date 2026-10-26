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

import api from "@/shared/lib/api";
import type {
  AdminLedgerFilters,
  AdminSettlementFilters,
  OutstandingBalanceResponse,
  PlatformCommissionRateResponse,
  RunSettlementRequest,
  SettlementDetailResponse,
  SettlementEntryPageResponse,
  SettlementPageResponse,
  SettlementResponse,
  VendorCommissionRateResponse,
} from "./types";

/** `GET /api/v1/admin/platform-settings/commission-rate` — read platform default commission rate percentage. */
export async function fetchPlatformCommissionRate(
  signal?: AbortSignal,
): Promise<PlatformCommissionRateResponse> {
  const response = await api.get<PlatformCommissionRateResponse>(
    "/admin/platform-settings/commission-rate",
    { signal },
  );
  return response.data;
}

/** `PATCH /api/v1/admin/platform-settings/commission-rate` — update platform default commission rate percentage. */
export async function updatePlatformCommissionRate(
  rate: number,
): Promise<PlatformCommissionRateResponse> {
  const response = await api.patch<PlatformCommissionRateResponse>(
    "/admin/platform-settings/commission-rate",
    { rate },
  );
  return response.data;
}

/** `PATCH /api/v1/admin/vendors/{vendorId}/commission-rate` — set per-vendor commission rate override. */
export async function updateVendorCommissionRate(
  vendorId: number,
  rate: number,
): Promise<VendorCommissionRateResponse> {
  const response = await api.patch<VendorCommissionRateResponse>(
    `/admin/vendors/${vendorId}/commission-rate`,
    { rate },
  );
  return response.data;
}

/** `GET /api/v1/admin/billing/outstanding/{vendorId}` — read specific vendor unattached balance. */
export async function fetchAdminVendorOutstanding(
  vendorId: number,
  signal?: AbortSignal,
): Promise<OutstandingBalanceResponse> {
  const response = await api.get<OutstandingBalanceResponse>(
    `/admin/billing/outstanding/${vendorId}`,
    { signal },
  );
  return response.data;
}

/** `GET /api/v1/admin/billing/settlements` — paged list of settlements across vendors. */
export async function fetchAdminSettlements(
  params?: AdminSettlementFilters,
  signal?: AbortSignal,
): Promise<SettlementPageResponse> {
  const response = await api.get<SettlementPageResponse>("/admin/billing/settlements", {
    params,
    signal,
  });
  return response.data;
}

/** `GET /api/v1/admin/billing/settlements/{id}` — single settlement details with attached entries. */
export async function fetchAdminSettlementDetail(
  id: number,
  signal?: AbortSignal,
): Promise<SettlementDetailResponse> {
  const response = await api.get<SettlementDetailResponse>(`/admin/billing/settlements/${id}`, {
    signal,
  });
  return response.data;
}

/** `POST /api/v1/admin/billing/settlements/run` — trigger settlement generation for vendor or all vendors. */
export async function runAdminSettlement(
  data: RunSettlementRequest,
): Promise<SettlementResponse[]> {
  const response = await api.post<SettlementResponse[]>(
    "/admin/billing/settlements/run",
    data,
  );
  return response.data;
}

/** `PATCH /api/v1/admin/billing/settlements/{id}/finalize` — finalize pending settlement with audit reference note. */
export async function finalizeAdminSettlement(
  id: number,
  adminReferenceNote: string,
): Promise<SettlementResponse> {
  const response = await api.patch<SettlementResponse>(
    `/admin/billing/settlements/${id}/finalize`,
    { adminReferenceNote },
  );
  return response.data;
}

/** `GET /api/v1/admin/billing/ledger` — paged all-vendor ledger entries with filters. */
export async function fetchAdminLedger(
  params?: AdminLedgerFilters,
  signal?: AbortSignal,
): Promise<SettlementEntryPageResponse> {
  const response = await api.get<SettlementEntryPageResponse>("/admin/billing/ledger", {
    params,
    signal,
  });
  return response.data;
}

/**
 * `GET /api/v1/admin/billing/statement` — download official CSV statement for a vendor.
 */
export async function downloadAdminStatementCsv(
  vendorId: number,
  periodStart: string,
  periodEnd: string,
): Promise<Blob> {
  const response = await api.get<Blob>("/admin/billing/statement", {
    params: { vendorId, periodStart, periodEnd },
    responseType: "blob",
  });

  const blob = new Blob([response.data], { type: "text/csv;charset=utf-8;" });
  const url = window.URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.setAttribute("download", `statement-vendor-${vendorId}-${periodStart}-${periodEnd}.csv`);
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.URL.revokeObjectURL(url);

  return blob;
}
