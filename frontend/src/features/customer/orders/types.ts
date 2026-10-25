/**
 * Customer order types (plan Phase 6, task 6.3).
 *
 * Mirrors the backend contracts exactly:
 *   `GET /api/v1/orders`                -> {@link CustomerOrderPageResponse}
 *   `GET /api/v1/orders/{id}`           -> {@link CustomerOrderResponse}
 *
 * Money values mirror the backend `BigDecimal` fields as `number` — the same
 * convention the rest of the frontend uses. The wire format stays a JSON
 * number; arithmetic happens server-side and this slice only formats for
 * display.
 *
 * `DELIVERED`, `CANCELLED` and `REJECTED` are terminal per the state machine
 * (plan section 6.1): no further transition is possible, so the customer UI
 * stops polling once an order reaches one of them.
 */

import type { PageResponse } from "@/shared/types";

export const ORDER_STATUSES = [
  "PLACED",
  "PENDING_PAYMENT",
  "CONFIRMED",
  "PREPARING",
  "OUT_FOR_DELIVERY",
  "DELIVERED",
  "CANCELLED",
  "REJECTED",
  "REFUNDED",
] as const;

export type OrderStatus = (typeof ORDER_STATUSES)[number];

export const PAYMENT_METHODS = ["COD", "ONLINE"] as const;
export type PaymentMethod = (typeof PAYMENT_METHODS)[number];

export const PAYMENT_STATUSES = [
  "PENDING",
  "PAID",
  "COLLECTED",
  "FAILED",
  "REFUND_PENDING",
  "REFUNDED",
] as const;
export type PaymentStatus = (typeof PAYMENT_STATUSES)[number];

export const ACTOR_TYPES = ["CUSTOMER", "VENDOR", "ADMIN", "SYSTEM"] as const;
export type ActorType = (typeof ACTOR_TYPES)[number];

/** Statuses after which an order never changes again. */
export const TERMINAL_STATUSES = ["DELIVERED", "CANCELLED", "REJECTED"] as const;
export type TerminalStatus = (typeof TERMINAL_STATUSES)[number];

export function isTerminalStatus(status: OrderStatus): status is TerminalStatus {
  return TERMINAL_STATUSES.includes(status as TerminalStatus);
}

/** One order in the customer's history listing. */
export interface CustomerOrderListItem {
  id: number;
  status: OrderStatus;
  paymentMethod: PaymentMethod;
  paymentStatus: PaymentStatus;
  vendorName: string;
  total: number;
  createdAt: string;
  slotStart: string | null;
  slotEnd: string | null;
}

/** Full detail of one of the customer's orders. */
export interface CustomerOrderResponse {
  id: number;
  status: OrderStatus;
  paymentMethod: PaymentMethod;
  paymentStatus: PaymentStatus;
  vendorName: string;
  subtotal: number;
  deliveryFee: number;
  platformFee: number;
  total: number;
  createdAt: string;
  updatedAt: string;
  deliveryAddressLine1: string;
  deliveryAddressLine2: string | null;
  deliveryLatitude: number;
  deliveryLongitude: number;
  slotStart: string | null;
  slotEnd: string | null;
  note: string | null;
  items: CustomerOrderItemResponse[];
  timeline: CustomerOrderTimelineEntry[];
  /** When the payment was marked refunded (`payments.status` moved
   *  `REFUND_PENDING -> REFUNDED`). Null until then. */
  refundedAt: string | null;
}

/** One item snapshot within a customer order. */
export interface CustomerOrderItemResponse {
  productName: string;
  quantity: number;
  priceSnapshot: number;
  subtotal: number;
  note: string | null;
}

/** One row of an order's status timeline (oldest first). */
export interface CustomerOrderTimelineEntry {
  fromStatus: OrderStatus | null;
  toStatus: OrderStatus;
  actorType: ActorType;
  actorName: string | null;
  reason: string | null;
  createdAt: string;
}

/** Paginated wrapper for the customer's order listing. */
export interface CustomerOrderPageResponse {
  content: CustomerOrderListItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

export type CustomerOrderPage = PageResponse<CustomerOrderListItem>;

/** Request body for `POST /api/v1/orders/{id}/cancel` (customer cancels own order). */
export interface CancelOrderRequest {
  reason?: string;
}

/** Response body for `POST /api/v1/orders/{id}/cancel`. */
export interface CancelOrderResponse {
  id: number;
  status: OrderStatus;
  paymentStatus: PaymentStatus;
  total: number;
  refundPending: boolean;
}

/** The orders namespace under `/api/v1`. */
export const ORDERS_PATH = "/orders";

/** Labels shown to customers for each status, in lifecycle order. */
export const ORDER_STATUS_LABELS: Record<OrderStatus, string> = {
  PLACED: "Placed",
  PENDING_PAYMENT: "Payment pending",
  CONFIRMED: "Confirmed",
  PREPARING: "Preparing",
  OUT_FOR_DELIVERY: "Out for delivery",
  DELIVERED: "Delivered",
  CANCELLED: "Cancelled",
  REJECTED: "Rejected",
  REFUNDED: "Refunded",
};

/** Labels shown to customers for each payment status. */
export const PAYMENT_STATUS_LABELS: Record<PaymentStatus, string> = {
  PENDING: "Pending",
  PAID: "Paid",
  COLLECTED: "Collected",
  FAILED: "Failed",
  REFUND_PENDING: "Refund pending",
  REFUNDED: "Refunded",
};

/** The next step shown in the status stepper for each non-terminal status. */
export const ORDER_STATUS_STEPS: OrderStatus[] = [
  "PLACED",
  "CONFIRMED",
  "PREPARING",
  "OUT_FOR_DELIVERY",
  "DELIVERED",
];
