import api from "@/shared/lib/api";
import type {
  AddCartItemRequest,
  CartResponse,
  UpdateCartItemRequest,
} from "@/features/cart/types";

/** `GET /api/v1/cart` — the authenticated customer's own cart. Requires `ROLE_CUSTOMER`. */
export async function fetchCart(): Promise<CartResponse> {
  const response = await api.get<CartResponse>(`/cart`);
  return response.data;
}

/** `POST /api/v1/cart/items` — add a product (or bump its quantity) in the cart. */
export async function addCartItem(payload: AddCartItemRequest): Promise<CartResponse> {
  const response = await api.post<CartResponse>(`/cart/items`, payload);
  return response.data;
}

/** `PATCH /api/v1/cart/items/{id}` — change a line's quantity (0 removes it). */
export async function updateCartItem(itemId: number, payload: UpdateCartItemRequest): Promise<CartResponse> {
  const response = await api.patch<CartResponse>(`/cart/items/${itemId}`, payload);
  return response.data;
}

/** `DELETE /api/v1/cart/items/{id}` — remove one line from the cart. */
export async function deleteCartItem(itemId: number): Promise<CartResponse> {
  const response = await api.delete<CartResponse>(`/cart/items/${itemId}`);
  return response.data;
}

/** `DELETE /api/v1/cart` — clear the entire cart (called after checkout places an order). */
export async function clearCart(): Promise<void> {
  await api.delete<void>(`/cart`);
}
