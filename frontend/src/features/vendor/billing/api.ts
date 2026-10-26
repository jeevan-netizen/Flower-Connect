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
  OutstandingBalanceResponse,
  SettlementDetailResponse,
  SettlementEntryPageResponse,
  SettlementEntryType,
  SettlementPageResponse,
  VendorBillingRateResponse,
} from "./types";

/** `GET /api/v1/vendor/billing/rate` — vendor's current commission rate percentage. */
export async function fetchVendorBillingRate(signal?: AbortSignal): Promise<VendorBillingRateResponse> {
  const response = await api.get<VendorBillingRateResponse>("/vendor/billing/rate", { signal });
  return response.data;
}

/** `GET /api/v1/vendor/billing/outstanding` — vendor's current unattached signed balance. */
export async function fetchVendorOutstandingBalance(signal?: AbortSignal): Promise<OutstandingBalanceResponse> {
  const response = await api.get<OutstandingBalanceResponse>("/vendor/billing/outstanding", { signal });
  return response.data;
}

/** `GET /api/v1/vendor/billing/ledger` — paged vendor ledger entries with optional date/type filter. */
export async function fetchVendorLedger(
  params?: {
    from?: string;
    to?: string;
    type?: SettlementEntryType;
    page?: number;
    size?: number;
  },
  signal?: AbortSignal,
): Promise<SettlementEntryPageResponse> {
  const response = await api.get<SettlementEntryPageResponse>("/vendor/billing/ledger", {
    params,
    signal,
  });
  return response.data;
}

/** `GET /api/v1/vendor/billing/settlements` — paged vendor weekly settlement records. */
export async function fetchVendorSettlements(
  params?: {
    page?: number;
    size?: number;
  },
  signal?: AbortSignal,
): Promise<SettlementPageResponse> {
  const response = await api.get<SettlementPageResponse>("/vendor/billing/settlements", {
    params,
    signal,
  });
  return response.data;
}

/** `GET /api/v1/vendor/billing/settlements/{id}` — single settlement detail with attached entries. */
export async function fetchVendorSettlementDetail(
  id: number,
  signal?: AbortSignal,
): Promise<SettlementDetailResponse> {
  const response = await api.get<SettlementDetailResponse>(`/vendor/billing/settlements/${id}`, { signal });
  return response.data;
}

/**
 * `GET /api/v1/vendor/billing/statement` — downloads CSV statement for a period.
 * Automatically initiates browser file download.
 */
export async function downloadVendorStatementCsv(
  periodStart: string,
  periodEnd: string,
): Promise<Blob> {
  const response = await api.get<Blob>("/vendor/billing/statement", {
    params: { periodStart, periodEnd },
    responseType: "blob",
  });

  const blob = new Blob([response.data], { type: "text/csv;charset=utf-8;" });
  const url = window.URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.setAttribute("download", `statement-vendor-${periodStart}-${periodEnd}.csv`);
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.URL.revokeObjectURL(url);

  return blob;
}
