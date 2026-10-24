/**
 * Checkout frontend types — plan task 5.6.
 *
 * Mirrors the Phase 5 checkout backend contracts exactly:
 *   `CheckoutRequest` / `CheckoutResponse` / `OrderSummary` / `OrderItemSummary`
 *   `CheckoutQuoteRequest` / `CheckoutQuoteResponse` / `CheckoutShopQuote` / `CheckoutItemQuote`
 *   `SlotResponse` / `AddressResponse` / `AddressPageResponse`
 *
 * Money values mirror the backend `BigDecimal` fields as `number` — the same
 * convention the vendor slice uses for `basePrice` and friends. The wire format
 * stays a JSON number; arithmetic happens server-side and this slice only
 * formats for display.
 */

/** Mirrors `SlotResponse`. `slotStart` / `slotEnd` are ISO-8601 UTC strings. */
export interface SlotResponse {
  slotStart: string;
  slotEnd: string;
  weekday: string;
  available: boolean;
}

/** Mirrors `AddressResponse`. */
export interface AddressResponse {
  id: number;
  label: string;
  line1: string;
  line2: string | null;
  serviceLocationId: number;
  city: string;
  area: string;
  pincode: string;
  latitude: number;
  longitude: number;
  defaultAddress: boolean;
  createdAt: string;
  updatedAt: string;
}

/** Mirrors `AddressPageResponse`. */
export interface AddressPageResponse {
  content: AddressResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

/** One vendor's slot in the checkout request/quote request. */
export interface CheckoutSlotSelection {
  vendorId: number;
  slotStart: string;
}

/** Mirrors `CheckoutRequest`. */
export interface CheckoutRequest {
  addressId: number;
  items: CheckoutSlotSelection[];
  clientOrderId: string;
  quoteHash: string;
  confirmPriceChanges: boolean;
  paymentMethod: "COD" | "ONLINE";
}

/** Mirrors `CheckoutResponse`. */
export interface CheckoutResponse {
  clientOrderId: string;
  idempotentReplay: boolean;
  orders: OrderSummary[];
  total: number;
  paymentMethod: "COD" | "ONLINE";
  paymentGroupId: string | null;
  intentReference: string | null;
  intentExpiresAt: string | null;
}

/** Mirrors `OrderSummary`. */
export interface OrderSummary {
  orderId: number;
  vendorId: number;
  vendorName: string;
  status: string;
  paymentStatus: string;
  paymentMethod: string;
  subtotal: number;
  deliveryFee: number;
  platformFee: number;
  total: number;
  slotStart: string;
  slotEnd: string;
  items: OrderItemSummary[];
}

/** Mirrors `OrderItemSummary`. */
export interface OrderItemSummary {
  productId: number;
  productName: string;
  quantity: number;
  priceSnapshot: number;
  subtotal: number;
}

/** Mirrors `CheckoutQuoteRequest`. */
export interface CheckoutQuoteRequest {
  addressId: number;
  items: CheckoutSlotSelection[];
}

/** Mirrors `CheckoutQuoteResponse`. */
export interface CheckoutQuoteResponse {
  shops: CheckoutShopQuote[];
  total: number;
  hasErrors: boolean;
  hasPriceChanges: boolean;
  quoteHash: string;
}

/** Mirrors `CheckoutShopQuote`. */
export interface CheckoutShopQuote {
  vendorId: number;
  vendorName: string;
  items: CheckoutItemQuote[];
  subtotal: number;
  deliveryFee: number;
  platformFee: number;
  minOrderAmount: number;
  total: number;
  distanceKm: number;
  withinRadius: boolean;
  minOrderMet: boolean;
  slotAvailable: boolean;
  warnings: string[];
  errors: string[];
}

/** Mirrors `CheckoutItemQuote`. */
export interface CheckoutItemQuote {
  productId: number;
  productName: string;
  productSlug: string;
  quantity: number;
  priceSnapshot: number;
  currentPrice: number;
  priceChanged: boolean;
  note: string | null;
  subtotal: number;
  available: number | null;
  purchasable: boolean;
  notPurchasableReason: string | null;
}

/** The checkout wizard steps. */
export type CheckoutStep = "address" | "slots" | "review" | "confirmation";

/** The checkout namespace under `/api/v1`. */
export const CHECKOUT_PATH = "/checkout";

/** The slots namespace under `/api/v1/vendors/{vendorId}/slots`. */
export const SLOTS_PATH = "/vendors";

/** The address namespace under `/api/v1/addresses`. */
export const ADDRESSES_PATH = "/addresses";
