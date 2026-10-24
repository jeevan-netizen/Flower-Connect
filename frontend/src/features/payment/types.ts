/**
 * Payment frontend types (plan task 7.6).
 *
 * Mirrors the Phase 7 payment backend contracts exactly:
 *   `GET /api/v1/payments/{orderId}`        -> {@link PaymentStateResponse}
 *   `GET /api/v1/payments/group/{paymentGroupId}` -> {@link PaymentGroupStateResponse}
 *   `POST /api/v1/payments/{orderId}/resume` -> {@link PaymentResumeResponse}
 *
 * Money values mirror the backend `BigDecimal` fields as `number` — the same
 * convention the rest of the frontend uses. The wire format stays a JSON
 * number; arithmetic happens server-side and this slice only formats for
 * display.
 *
 * No card numbers, CVV, or other card-like data ever appears here: online
 * payment in v1 is a simulated test-mode provider (D-48/D-49) and the
 * frontend only consumes the server-generated intent reference and group id.
 */

export const PAYMENT_STATUSES = [
  "PENDING",
  "PAID",
  "COLLECTED",
  "FAILED",
  "REFUND_PENDING",
  "REFUNDED",
] as const;
export type PaymentStatus = (typeof PAYMENT_STATUSES)[number];

/** Payment statuses after which polling stops — the payment never changes again. */
export const TERMINAL_PAYMENT_STATUSES = ["PAID", "FAILED", "REFUNDED"] as const;
export type TerminalPaymentStatus = (typeof TERMINAL_PAYMENT_STATUSES)[number];

export function isTerminalPaymentStatus(status: PaymentStatus): status is TerminalPaymentStatus {
  return TERMINAL_PAYMENT_STATUSES.includes(status as TerminalPaymentStatus);
}

/** Labels shown to customers for each payment status. */
export const PAYMENT_STATUS_LABELS: Record<PaymentStatus, string> = {
  PENDING: "Pending",
  PAID: "Paid",
  COLLECTED: "Collected",
  FAILED: "Payment failed",
  REFUND_PENDING: "Refund pending",
  REFUNDED: "Refunded",
};

/** Labels shown to customers for each payment method. */
export const PAYMENT_METHOD_LABELS: Record<string, string> = {
  COD: "Cash on delivery",
  ONLINE: "Online payment",
};

/** Payment methods offered at checkout. COD is the default; ONLINE is only
 *  offered when the online payment gateway (plan task 7.4) is built. */
export const PAYMENT_METHODS = ["COD", "ONLINE"] as const;
export type PaymentMethod = (typeof PAYMENT_METHODS)[number];

/** The current payment state for a single order, from `GET /api/v1/payments/{orderId}`. */
export interface PaymentStateResponse {
  orderId: number;
  paymentGroupId: string | null;
  intentReference: string | null;
  method: PaymentMethod;
  paymentStatus: PaymentStatus;
  orderStatus: string;
  amount: number;
  expiresAt: string | null;
  createdAt: string | null;
}

/** One order entry within a payment group, from `GET /api/v1/payments/group/{paymentGroupId}`. */
export interface PaymentGroupOrderEntry {
  orderId: number;
  paymentStatus: PaymentStatus;
  orderStatus: string;
  method: PaymentMethod;
  amount: number;
}

/** The current state of a payment group (simulated intent + linked orders). */
export interface PaymentGroupStateResponse {
  paymentGroupId: string;
  intentReference: string;
  status: string;
  total: number;
  expiresAt: string | null;
  orders: PaymentGroupOrderEntry[];
}

/** The result of `POST /api/v1/payments/{orderId}/resume`. */
export interface PaymentResumeResponse {
  orderId: number;
  orderStatus: string;
  paymentStatus: PaymentStatus;
  paymentGroupId: string | null;
  intentReference: string | null;
  paidAt: string | null;
}

/** The payments namespace under `/api/v1`. */
export const PAYMENTS_PATH = "/payments";

/** Polling interval for payment state, in milliseconds (plan §9.4: 5–10 s). */
export const PAYMENT_POLL_INTERVAL_MS = 8_000;
