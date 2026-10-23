import {
  useMutation,
  useQuery,
  useQueryClient,
  type QueryClient,
} from "@tanstack/react-query";
import {
  acceptOrder,
  deliveredOrder,
  fetchVendorOrderSummary,
  fetchVendorOrders,
  outForDeliveryOrder,
  prepareOrder,
  rejectOrder,
} from "@/features/vendor/orders/api";
import type {
  VendorOrderAction,
  VendorOrderPageResponse,
  VendorOrderSummaryResponse,
} from "@/features/vendor/orders/types";

export const vendorOrderKeys = {
  all: ["vendor", "orders"] as const,
  list: (params: {
    status?: string;
    fromDate?: string;
    toDate?: string;
    page: number;
    size: number;
  }) =>
    [
      "vendor",
      "orders",
      "list",
      params.status ?? null,
      params.fromDate ?? null,
      params.toDate ?? null,
      params.page,
      params.size,
    ] as const,
  summary: (date: string | null) =>
    ["vendor", "orders", "summary", date] as const,
  detail: (orderId: number) => ["vendor", "orders", "detail", orderId] as const,
};

/**
 * The vendor's orders, keyed by every filter and page so a filter change is a
 * cache miss rather than a stale render of the previous page's rows.
 */
export function useVendorOrders(params: {
  status?: string;
  fromDate?: string;
  toDate?: string;
  page: number;
  size: number;
}) {
  return useQuery<VendorOrderPageResponse>({
    queryKey: vendorOrderKeys.list(params),
    queryFn: ({ signal }) => fetchVendorOrders(params, signal),
  });
}

/** Today's order summary for the calling vendor. */
export function useVendorOrderSummary(date: string | null) {
  return useQuery<VendorOrderSummaryResponse>({
    queryKey: vendorOrderKeys.summary(date),
    queryFn: ({ signal }) => fetchVendorOrderSummary({ date }, signal),
    enabled: date !== null,
  });
}

/**
 * One mutation for every vendor order action.
 *
 * The backend validates each transition against its state machine, so the
 * frontend only picks the route for the action the card offers and renders the
 * returned order (or the 409/400 the backend refuses with). On success the
 * whole listing is invalidated: an accepted order moves out of "New", a
 * rejected one leaves the board entirely, and today's summary changes.
 */
export function useVendorOrderAction() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (variables: { orderId: number; action: VendorOrderAction; reason?: string }) => {
      const { orderId, action, reason } = variables;

      switch (action) {
        case "accept":
          return acceptOrder(orderId);
        case "reject":
          if (reason === undefined) {
            throw new Error("reject requires a reason");
          }
          return rejectOrder(orderId, reason);
        case "prepare":
          return prepareOrder(orderId);
        case "out-for-delivery":
          return outForDeliveryOrder(orderId);
        case "delivered":
          return deliveredOrder(orderId);
      }
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: vendorOrderKeys.all });
    },
  });
}

/** Drops every cached vendor order query. Called on logout. */
export function clearVendorOrderCache(queryClient: QueryClient): void {
  queryClient.removeQueries({ queryKey: vendorOrderKeys.all });
}
