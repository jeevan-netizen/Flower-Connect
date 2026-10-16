import {
  useMutation,
  useQuery,
  useQueryClient,
  type QueryClient,
} from "@tanstack/react-query";
import {
  fetchAddresses,
  fetchCheckoutQuote,
  fetchSlots,
  placeCheckout,
} from "@/features/checkout/api";
import type {
  AddressPageResponse,
  CheckoutQuoteRequest,
  CheckoutResponse,
  SlotResponse,
} from "@/features/checkout/types";

/**
 * Query keys for the customer checkout area (plan task 5.6).
 *
 * Addresses and slots are read during the checkout wizard. Slots are keyed by
 * vendor, address and date, so changing any of them is a cache miss rather than
 * a stale render.
 */
export const checkoutKeys = {
  all: ["checkout"] as const,
  quote: ["checkout", "quote"] as const,
  orders: ["checkout", "orders"] as const,
  addresses: {
    all: ["checkout", "addresses"] as const,
    list: (page: number, size: number) => ["checkout", "addresses", "list", page, size] as const,
  },
  slots: {
    all: ["checkout", "slots"] as const,
    detail: (vendorId: number, addressId: number, date: string) =>
      ["checkout", "slots", "detail", vendorId, addressId, date] as const,
  },
};

/** `GET /api/v1/addresses` — the customer's saved addresses. */
export function useAddresses(page = 0, size = 20) {
  return useQuery<AddressPageResponse>({
    queryKey: checkoutKeys.addresses.list(page, size),
    queryFn: ({ signal }) => fetchAddresses({ page, size }, signal),
  });
}

/** `GET /api/v1/vendors/{vendorId}/slots` — available slots for one vendor. */
export function useSlots(vendorId: number, addressId: number, date: string) {
  return useQuery<SlotResponse[]>({
    queryKey: checkoutKeys.slots.detail(vendorId, addressId, date),
    queryFn: ({ signal }) => fetchSlots(vendorId, { addressId, date }, signal),
    enabled: Boolean(vendorId && addressId && date),
  });
}

/** `POST /api/v1/checkout/quote`. */
export function useCheckoutQuote() {
  return useMutation({
    mutationFn: (payload: CheckoutQuoteRequest) => fetchCheckoutQuote(payload),
  });
}

/** `POST /api/v1/checkout`. On success the cart is cleared and the order cache is written. */
export function usePlaceCheckout() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: Parameters<typeof placeCheckout>[0]) => placeCheckout(payload),
    onSuccess: (orders: CheckoutResponse) => {
      queryClient.setQueryData(checkoutKeys.orders, orders);
      void queryClient.invalidateQueries({ queryKey: checkoutKeys.all });
    },
  });
}

/** Drops every cached checkout query. Called on logout so a different account
 *  signing in on the same tab never sees the previous customer's checkout data. */
export function clearCheckoutCache(queryClient: QueryClient): void {
  queryClient.removeQueries({ queryKey: checkoutKeys.all });
}
