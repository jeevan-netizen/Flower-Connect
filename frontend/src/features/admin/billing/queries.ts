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

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
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
import type { AdminLedgerFilters, AdminSettlementFilters, RunSettlementRequest } from "./types";

export const ADMIN_BILLING_KEYS = {
  all: ["admin", "billing"] as const,
  platformRate: () => [...ADMIN_BILLING_KEYS.all, "platform-rate"] as const,
  vendorOutstanding: (vendorId: number) =>
    [...ADMIN_BILLING_KEYS.all, "outstanding", vendorId] as const,
  settlements: (filters?: AdminSettlementFilters) =>
    [...ADMIN_BILLING_KEYS.all, "settlements", filters] as const,
  settlementDetail: (id: number) =>
    [...ADMIN_BILLING_KEYS.all, "settlement", id] as const,
  ledger: (filters?: AdminLedgerFilters) =>
    [...ADMIN_BILLING_KEYS.all, "ledger", filters] as const,
};

export function usePlatformCommissionRate() {
  return useQuery({
    queryKey: ADMIN_BILLING_KEYS.platformRate(),
    queryFn: ({ signal }) => fetchPlatformCommissionRate(signal),
  });
}

export function useUpdatePlatformCommissionRate() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (rate: number) => updatePlatformCommissionRate(rate),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ADMIN_BILLING_KEYS.platformRate() });
    },
  });
}

export function useUpdateVendorCommissionRate() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ vendorId, rate }: { vendorId: number; rate: number }) =>
      updateVendorCommissionRate(vendorId, rate),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ADMIN_BILLING_KEYS.all });
    },
  });
}

export function useAdminVendorOutstanding(vendorId: number | null) {
  return useQuery({
    queryKey: ADMIN_BILLING_KEYS.vendorOutstanding(vendorId ?? 0),
    queryFn: ({ signal }) => fetchAdminVendorOutstanding(vendorId!, signal),
    enabled: vendorId != null && vendorId > 0,
  });
}

export function useAdminSettlements(filters?: AdminSettlementFilters) {
  return useQuery({
    queryKey: ADMIN_BILLING_KEYS.settlements(filters),
    queryFn: ({ signal }) => fetchAdminSettlements(filters, signal),
  });
}

export function useAdminSettlementDetail(id: number | null) {
  return useQuery({
    queryKey: ADMIN_BILLING_KEYS.settlementDetail(id ?? 0),
    queryFn: ({ signal }) => fetchAdminSettlementDetail(id!, signal),
    enabled: id != null && id > 0,
  });
}

export function useRunAdminSettlement() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: RunSettlementRequest) => runAdminSettlement(data),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ADMIN_BILLING_KEYS.all });
    },
  });
}

export function useFinalizeAdminSettlement() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, adminReferenceNote }: { id: number; adminReferenceNote: string }) =>
      finalizeAdminSettlement(id, adminReferenceNote),
    onSuccess: (_data, variables) => {
      void queryClient.invalidateQueries({ queryKey: ADMIN_BILLING_KEYS.settlements() });
      void queryClient.invalidateQueries({
        queryKey: ADMIN_BILLING_KEYS.settlementDetail(variables.id),
      });
    },
  });
}

export function useAdminLedger(filters?: AdminLedgerFilters) {
  return useQuery({
    queryKey: ADMIN_BILLING_KEYS.ledger(filters),
    queryFn: ({ signal }) => fetchAdminLedger(filters, signal),
  });
}
