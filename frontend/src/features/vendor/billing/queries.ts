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

import { useQuery } from "@tanstack/react-query";
import {
  fetchVendorBillingRate,
  fetchVendorLedger,
  fetchVendorOutstandingBalance,
  fetchVendorSettlementDetail,
  fetchVendorSettlements,
} from "./api";
import type { SettlementEntryType } from "./types";

export const VENDOR_BILLING_KEYS = {
  all: ["vendor", "billing"] as const,
  rate: () => [...VENDOR_BILLING_KEYS.all, "rate"] as const,
  outstanding: () => [...VENDOR_BILLING_KEYS.all, "outstanding"] as const,
  ledger: (params?: { from?: string; to?: string; type?: SettlementEntryType; page?: number; size?: number }) =>
    [...VENDOR_BILLING_KEYS.all, "ledger", params] as const,
  settlements: (params?: { page?: number; size?: number }) =>
    [...VENDOR_BILLING_KEYS.all, "settlements", params] as const,
  settlementDetail: (id: number) =>
    [...VENDOR_BILLING_KEYS.all, "settlement", id] as const,
};

export function useVendorBillingRate() {
  return useQuery({
    queryKey: VENDOR_BILLING_KEYS.rate(),
    queryFn: ({ signal }) => fetchVendorBillingRate(signal),
  });
}

export function useVendorOutstandingBalance() {
  return useQuery({
    queryKey: VENDOR_BILLING_KEYS.outstanding(),
    queryFn: ({ signal }) => fetchVendorOutstandingBalance(signal),
  });
}

export function useVendorLedger(params?: {
  from?: string;
  to?: string;
  type?: SettlementEntryType;
  page?: number;
  size?: number;
}) {
  return useQuery({
    queryKey: VENDOR_BILLING_KEYS.ledger(params),
    queryFn: ({ signal }) => fetchVendorLedger(params, signal),
  });
}

export function useVendorSettlements(params?: {
  page?: number;
  size?: number;
}) {
  return useQuery({
    queryKey: VENDOR_BILLING_KEYS.settlements(params),
    queryFn: ({ signal }) => fetchVendorSettlements(params, signal),
  });
}

export function useVendorSettlementDetail(id: number | null) {
  return useQuery({
    queryKey: VENDOR_BILLING_KEYS.settlementDetail(id ?? 0),
    queryFn: ({ signal }) => fetchVendorSettlementDetail(id!, signal),
    enabled: id != null && id > 0,
  });
}
