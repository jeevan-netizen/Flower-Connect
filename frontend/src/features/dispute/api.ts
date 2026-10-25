import api from "@/shared/lib/api";
import type {
  DisputeDetailResponse,
  DisputePageResponse,
  DisputeStatus,
  RaiseDisputeRequest,
  ResolveDisputeRequest,
  VendorDisputeResponseRequest,
} from "@/features/dispute/types";

export async function raiseDispute(
  orderId: number,
  payload: RaiseDisputeRequest,
): Promise<DisputeDetailResponse> {
  const response = await api.post<DisputeDetailResponse>(
    `/disputes/orders/${orderId}`,
    payload,
  );
  return response.data;
}

export async function fetchCustomerDisputes(
  page: number = 0,
  size: number = 20,
): Promise<DisputePageResponse> {
  const response = await api.get<DisputePageResponse>("/disputes", {
    params: { page, size },
  });
  return response.data;
}

export async function fetchCustomerDispute(disputeId: number): Promise<DisputeDetailResponse> {
  const response = await api.get<DisputeDetailResponse>(`/disputes/${disputeId}`);
  return response.data;
}

export async function fetchVendorDisputes(
  page: number = 0,
  size: number = 20,
): Promise<DisputePageResponse> {
  const response = await api.get<DisputePageResponse>("/vendor/disputes", {
    params: { page, size },
  });
  return response.data;
}

export async function fetchVendorDispute(disputeId: number): Promise<DisputeDetailResponse> {
  const response = await api.get<DisputeDetailResponse>(`/vendor/disputes/${disputeId}`);
  return response.data;
}

export async function respondToDispute(
  disputeId: number,
  payload: VendorDisputeResponseRequest,
): Promise<DisputeDetailResponse> {
  const response = await api.post<DisputeDetailResponse>(
    `/vendor/disputes/${disputeId}/respond`,
    payload,
  );
  return response.data;
}

export async function fetchAdminDisputes(
  status: DisputeStatus | null,
  from: string | null,
  to: string | null,
  page: number = 0,
  size: number = 20,
): Promise<DisputePageResponse> {
  const params: Record<string, string> = { page: String(page), size: String(size) };
  if (status) params.status = status;
  if (from) params.from = from;
  if (to) params.to = to;
  const response = await api.get<DisputePageResponse>("/admin/disputes", { params });
  return response.data;
}

export async function fetchAdminDispute(disputeId: number): Promise<DisputeDetailResponse> {
  const response = await api.get<DisputeDetailResponse>(`/admin/disputes/${disputeId}`);
  return response.data;
}

export async function resolveDispute(
  disputeId: number,
  payload: ResolveDisputeRequest,
): Promise<DisputeDetailResponse> {
  const response = await api.post<DisputeDetailResponse>(
    `/admin/disputes/${disputeId}/resolve`,
    payload,
  );
  return response.data;
}