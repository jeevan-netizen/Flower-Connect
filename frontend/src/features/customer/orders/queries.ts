import { useQuery } from "@tanstack/react-query";
import { fetchOrder, fetchOrders } from "@/features/customer/orders/api";
import type { CustomerOrderPageResponse, CustomerOrderResponse } from "@/features/customer/orders/types";

/**
 * Query keys for the customer's orders (plan Phase 6, task 6.3).
 *
 * The listing is keyed by page and status filter so a filter change is a cache
 * miss rather than a stale render of the previous page's rows.
 */
export const customerOrderKeys = {
  all: ["customer", "orders"] as const,
  list: (page: number, size: number, status: string | null) =>
    ["customer", "orders", "list", page, size, status] as const,
  detail: (orderId: number) => ["customer", "orders", "detail", orderId] as const,
};

/**
 * One page of the authenticated customer's own orders, newest first, with an
 * optional status filter.
 *
 * Orders are read-only here: nothing on this screen changes status, so there
 * is no write path to invalidate against.
 */
export function useOrders(page: number, size: number, status: string | null) {
  return useQuery<CustomerOrderPageResponse>({
    queryKey: customerOrderKeys.list(page, size, status),
    queryFn: ({ signal }) => fetchOrders({ page, size, status }, signal),
  });
}

/** Full detail of one of the authenticated customer's orders. */
export function useOrder(orderId: number | null) {
  return useQuery<CustomerOrderResponse>({
    queryKey: customerOrderKeys.detail(orderId ?? 0),
    queryFn: ({ signal }) => fetchOrder(orderId as number, signal),
    enabled: orderId !== null,
  });
}
