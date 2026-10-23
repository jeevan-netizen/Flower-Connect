import api from "@/shared/lib/api";
import type {
  VendorOrderPageResponse,
  VendorOrderResponse,
  VendorOrderSummaryResponse,
} from "@/features/vendor/orders/types";

/**
 * Vendor order API (plan Phase 6, tasks 6.2 and 6.4).
 *
 * Every endpoint is `hasRole("FLORIST")` and resolves the caller's vendor
 * profile from the JWT subject. Order ownership is enforced per handler by the
 * backend: a foreign order is 403, a missing one 404.
 *
 * Mutations delegate to `OrderStateMachine` on the backend, so the frontend
 * never invents a client-side status transition — it only asks the backend to
 * make the next valid one and renders the result (or the 409/400 it refuses
 * with).
 */

/** `GET /api/v1/vendor/orders` — paged, optional status/date filters. */
export async function fetchVendorOrders(
  params?: {
    status?: string;
    fromDate?: string;
    toDate?: string;
    page?: number;
    size?: number;
  },
  signal?: AbortSignal,
): Promise<VendorOrderPageResponse> {
  const response = await api.get<VendorOrderPageResponse>("/vendor/orders", {
    params,
    signal,
  });
  return response.data;
}

/** `GET /api/v1/vendor/orders/summary` — today's summary. */
export async function fetchVendorOrderSummary(
  params?: { date?: string | null },
  signal?: AbortSignal,
): Promise<VendorOrderSummaryResponse> {
  const response = await api.get<VendorOrderSummaryResponse>("/vendor/orders/summary", {
    params,
    signal,
  });
  return response.data;
}

/** `POST /api/v1/vendor/orders/{orderId}/accept`. */
export async function acceptOrder(orderId: number): Promise<VendorOrderResponse> {
  const response = await api.post<VendorOrderResponse>(`/vendor/orders/${orderId}/accept`);
  return response.data;
}

/** `POST /api/v1/vendor/orders/{orderId}/reject` — reason is mandatory. */
export async function rejectOrder(
  orderId: number,
  reason: string,
): Promise<VendorOrderResponse> {
  const response = await api.post<VendorOrderResponse>(
    `/vendor/orders/${orderId}/reject`,
    { reason },
  );
  return response.data;
}

/** `POST /api/v1/vendor/orders/{orderId}/prepare`. */
export async function prepareOrder(orderId: number): Promise<VendorOrderResponse> {
  const response = await api.post<VendorOrderResponse>(`/vendor/orders/${orderId}/prepare`);
  return response.data;
}

/** `POST /api/v1/vendor/orders/{orderId}/out-for-delivery`. */
export async function outForDeliveryOrder(orderId: number): Promise<VendorOrderResponse> {
  const response = await api.post<VendorOrderResponse>(`/vendor/orders/${orderId}/out-for-delivery`);
  return response.data;
}

/** `POST /api/v1/vendor/orders/{orderId}/delivered` — records COD collection. */
export async function deliveredOrder(orderId: number): Promise<VendorOrderResponse> {
  const response = await api.post<VendorOrderResponse>(`/vendor/orders/${orderId}/delivered`);
  return response.data;
}
