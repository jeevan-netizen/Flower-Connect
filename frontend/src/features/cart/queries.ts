import {
  useMutation,
  useQuery,
  useQueryClient,
  type QueryClient,
} from "@tanstack/react-query";
import {
  addCartItem,
  clearCart,
  deleteCartItem,
  fetchCart,
  updateCartItem,
} from "@/features/cart/api";
import type { CartResponse } from "@/features/cart/types";

/**
 * Query keys for the customer cart area (plan task 5.6).
 *
 * The cart is a single resource resolved from the JWT subject — there is one
 * cart per customer, so one key serves every cart screen.
 *
 * Mutations invalidate this key rather than patching it: an item add/update/
 * remove can change the vendor grouping and the per-shop purchasable flags,
 * so the list has to be re-read to stay truthful.
 */
export const cartKeys = {
  all: ["cart"] as const,
  detail: ["cart", "detail"] as const,
};

/** `GET /api/v1/cart` — the customer's own cart. */
export function useCart() {
  return useQuery<CartResponse>({
    queryKey: cartKeys.detail,
    queryFn: fetchCart,
  });
}

/** `POST /api/v1/cart/items`. On success the cart is re-read wholesale. */
export function useAddCartItem() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: Parameters<typeof addCartItem>[0]) => addCartItem(payload),
    onSuccess: (_cart: CartResponse) => {
      void queryClient.invalidateQueries({ queryKey: cartKeys.all });
    },
  });
}

/** `PATCH /api/v1/cart/items/{id}`. On success the cart is re-read wholesale. */
export function useUpdateCartItem() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      itemId,
      payload,
    }: { itemId: number; payload: Parameters<typeof updateCartItem>[1] }) =>
      updateCartItem(itemId, payload),
    onSuccess: (_cart: CartResponse) => {
      void queryClient.invalidateQueries({ queryKey: cartKeys.all });
    },
  });
}

/** `DELETE /api/v1/cart/items/{id}`. On success the cart is re-read wholesale. */
export function useDeleteCartItem() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (itemId: number) => deleteCartItem(itemId),
    onSuccess: (_cart: CartResponse) => {
      void queryClient.invalidateQueries({ queryKey: cartKeys.all });
    },
  });
}

/** `DELETE /api/v1/cart`. On success the cart query is dropped entirely. */
export function useClearCart() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: () => clearCart(),
    onSuccess: () => {
      queryClient.removeQueries({ queryKey: cartKeys.all });
    },
  });
}

/** Drops every cached cart query. Called on logout so a different account
 *  signing in on the same tab never sees the previous customer's cart. */
export function clearCartCache(queryClient: QueryClient): void {
  queryClient.removeQueries({ queryKey: cartKeys.all });
}
