import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  fetchAdminDisputes,
  fetchAdminDispute,
  fetchCustomerDispute,
  fetchCustomerDisputes,
  fetchVendorDispute,
  fetchVendorDisputes,
  raiseDispute,
  resolveDispute,
  respondToDispute,
} from "@/features/dispute/api";
import type {
  DisputeStatus,
  RaiseDisputeRequest,
  ResolveDisputeRequest,
  VendorDisputeResponseRequest,
} from "@/features/dispute/types";

export const disputeKeys = {
  all: ["disputes"] as const,
  customer: {
    all: ["disputes", "customer"] as const,
    list: (page: number) => ["disputes", "customer", "list", page] as const,
    detail: (id: number) => ["disputes", "customer", "detail", id] as const,
  },
  vendor: {
    all: ["disputes", "vendor"] as const,
    list: (page: number) => ["disputes", "vendor", "list", page] as const,
    detail: (id: number) => ["disputes", "vendor", "detail", id] as const,
  },
  admin: {
    all: ["disputes", "admin"] as const,
    list: (status: DisputeStatus | null, page: number) =>
      ["disputes", "admin", "list", status, page] as const,
    detail: (id: number) => ["disputes", "admin", "detail", id] as const,
  },
};

export function useCustomerDisputes(page = 0) {
  return useQuery({
    queryKey: disputeKeys.customer.list(page),
    queryFn: () => fetchCustomerDisputes(page),
  });
}

export function useCustomerDispute(disputeId: number) {
  return useQuery({
    queryKey: disputeKeys.customer.detail(disputeId),
    queryFn: () => fetchCustomerDispute(disputeId),
  });
}

export function useRaiseDispute() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ orderId, payload }: { orderId: number; payload: RaiseDisputeRequest }) =>
      raiseDispute(orderId, payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: disputeKeys.customer.all });
    },
  });
}

export function useVendorDisputes(page = 0) {
  return useQuery({
    queryKey: disputeKeys.vendor.list(page),
    queryFn: () => fetchVendorDisputes(page),
  });
}

export function useVendorDispute(disputeId: number) {
  return useQuery({
    queryKey: disputeKeys.vendor.detail(disputeId),
    queryFn: () => fetchVendorDispute(disputeId),
  });
}

export function useRespondToDispute() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ disputeId, payload }: { disputeId: number; payload: VendorDisputeResponseRequest }) =>
      respondToDispute(disputeId, payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: disputeKeys.vendor.all });
    },
  });
}

export function useAdminDisputes(status: DisputeStatus | null, from: string | null, to: string | null, page = 0) {
  return useQuery({
    queryKey: disputeKeys.admin.list(status, page),
    queryFn: () => fetchAdminDisputes(status, from, to, page),
  });
}

export function useAdminDispute(disputeId: number) {
  return useQuery({
    queryKey: disputeKeys.admin.detail(disputeId),
    queryFn: () => fetchAdminDispute(disputeId),
  });
}

export function useResolveDispute() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ disputeId, payload }: { disputeId: number; payload: ResolveDisputeRequest }) =>
      resolveDispute(disputeId, payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: disputeKeys.admin.all });
    },
  });
}