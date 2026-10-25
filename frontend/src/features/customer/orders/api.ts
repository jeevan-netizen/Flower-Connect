import api from "@/shared/lib/api";
import type {
  CancelOrderResponse,
  CustomerOrderPageResponse,
  CustomerOrderResponse,
} from "@/features/customer/orders/types";

/**
 * Customer order reads (plan Phase 6, task 6.3).
 *
 * Both endpoints are `hasRole("CUSTOMER")` and resolve the caller from the JWT
 * subject, so no route accepts a customer identifier and cross-customer access
 * is not expressible in the request at all.
 */

/** `GET /api/v1/orders` — one page of the caller's orders, newest first. */
export async function fetchOrders(
  params?: { page?: number; size?: number; status?: string | null },
  signal?: AbortSignal,
): Promise<CustomerOrderPageResponse> {
  const response = await api.get<CustomerOrderPageResponse>("/orders", {
    params,
    signal,
  });
  return response.data;
}

/** `GET /api/v1/orders/{id}` — full detail of one of the caller's orders. */
export async function fetchOrder(
  orderId: number,
  signal?: AbortSignal,
): Promise<CustomerOrderResponse> {
  const response = await api.get<CustomerOrderResponse>(`/orders/${orderId}`, {
    signal,
  });
  return response.data;
}

/** `POST /api/v1/orders/{id}/cancel` — cancels the caller's own order. */
export async function cancelOrder(
  orderId: number,
  reason?: string,
): Promise<CancelOrderResponse> {
  const response = await api.post<CancelOrderResponse>(
    `/orders/${orderId}/cancel`,
    { reason },
  );
  return response.data;
}
