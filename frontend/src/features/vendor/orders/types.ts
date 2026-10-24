/**
 * Vendor order types (plan Phase 6, tasks 6.2–6.4).
 *
 * Mirrors the backend contracts exactly:
 *   `GET /api/v1/vendor/orders`            -> {@link VendorOrderPageResponse}
 *   `GET /api/v1/vendor/orders/summary`    -> {@link VendorOrderSummaryResponse}
 *   `POST /api/v1/vendor/orders/{id}/...`  -> {@link VendorOrderResponse}
 *
 * `OrderStatus` is imported from the customer order types so both slices name
 * the same lifecycle values (the backend enum is the single source of truth).
 */

import type { OrderStatus } from "@/features/customer/orders/types";
import { ORDER_STATUSES, ORDER_STATUS_LABELS, isTerminalStatus } from "@/features/customer/orders/types";

export type { OrderStatus };
export { ORDER_STATUSES, ORDER_STATUS_LABELS, isTerminalStatus };

/** One order in the vendor's order listing. */
export interface VendorOrderResponse {
  id: number;
  status: OrderStatus;
  paymentStatus: string;
  total: number;
  createdAt: string;
  slotStart: string | null;
  slotEnd: string | null;
  deliveryAddressLine1: string;
  deliveryAddressLine2: string | null;
  note: string | null;
}

/** Paginated wrapper for the vendor's order listing. */
export interface VendorOrderPageResponse {
  content: VendorOrderResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

/** Today's order summary for a vendor. */
export interface VendorOrderSummaryResponse {
  today: string;
  ordersByStatus: Record<OrderStatus, number>;
  todayOrderCount: number;
  todayTotalValue: number;
  expectedCodCash: number;
}

/** The valid next vendor action for each non-terminal status. */
export const NEXT_ACTIONS: Record<OrderStatus, VendorOrderAction[]> = {
  PLACED: ["accept", "reject"],
  CONFIRMED: ["prepare", "cancel"],
  PREPARING: ["out-for-delivery", "cancel"],
  OUT_FOR_DELIVERY: ["delivered"],
  PENDING_PAYMENT: [],
  DELIVERED: [],
  CANCELLED: [],
  REJECTED: [],
  REFUNDED: [],
};

export type VendorOrderAction =
  | "accept"
  | "reject"
  | "cancel"
  | "prepare"
  | "out-for-delivery"
  | "delivered";

/** Labels shown on the board's action buttons. */
export const VENDOR_ORDER_ACTION_LABELS: Record<VendorOrderAction, string> = {
  accept: "Accept",
  reject: "Reject",
  cancel: "Cancel",
  prepare: "Mark preparing",
  "out-for-delivery": "Out for delivery",
  delivered: "Mark delivered",
};

/** The Kanban columns and which statuses they hold. */
export const ORDER_BOARD_COLUMNS: {
  key: string;
  label: string;
  statuses: OrderStatus[];
}[] = [
  { key: "new", label: "New", statuses: ["PLACED"] },
  {
    key: "accepted-preparing",
    label: "Accepted / Preparing",
    statuses: ["CONFIRMED", "PREPARING"],
  },
  { key: "out-for-delivery", label: "Out for delivery", statuses: ["OUT_FOR_DELIVERY"] },
  { key: "delivered-today", label: "Delivered today", statuses: ["DELIVERED"] },
] as const;

export type OrderBoardColumnKey = (typeof ORDER_BOARD_COLUMNS)[number]["key"];

/** The orders namespace under `/api/v1/vendor`. */
export const VENDOR_ORDERS_PATH = "/vendor/orders";
