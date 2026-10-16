/**
 * Cart frontend types — plan task 5.6.
 *
 * Mirrors the Phase 5 cart backend contracts exactly:
 *   `CartResponse` / `CartItemResponse` / `CartShopView`
 *   `AddCartItemRequest` / `UpdateCartItemRequest`
 *
 * Money values mirror the backend `BigDecimal` fields as `number` — the same
 * convention the vendor slice uses for `basePrice`, `deliveryRadiusKm` and
 * friends. The wire format stays a JSON number; arithmetic happens server-side
 * and this slice only formats for display.
 */

/** Mirrors `CartResponse`. */
export interface CartResponse {
  cartId: number;
  totalItems: number;
  subtotal: number;
  shops: CartShopView[];
  items: CartItem[];
}

/** Mirrors `CartItemResponse`. */
export interface CartItem {
  id: number;
  productId: number;
  productName: string;
  productSlug: string;
  vendorId: number;
  vendorName: string;
  quantity: number;
  priceSnapshot: number;
  note: string | null;
  subtotal: number;
  currentPrice: number;
  priceChanged: boolean;
  available: number | null;
  purchasable: boolean;
  notPurchasableReason: string | null;
  createdAt: string;
  updatedAt: string;
}

/** Mirrors `CartShopView`. */
export interface CartShopView {
  vendorId: number;
  vendorName: string;
  itemCount: number;
  subtotal: number;
  purchasable: boolean;
}

/** Mirrors `AddCartItemRequest`. */
export interface AddCartItemRequest {
  productId: number;
  quantity: number;
  note: string | null;
}

/** Mirrors `UpdateCartItemRequest`. */
export interface UpdateCartItemRequest {
  quantity: number;
}

/** The cart API's page-size-style bounds, mirrored from the cart controller. */
export const CART_MAX_QUANTITY = 99;

/** The cart namespace under `/api/v1`. */
export const CART_PATH = "/cart";
