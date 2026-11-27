/**
 * B2B frontend types (Stage 3 of the B2B module).
 *
 * Mirrors the backend contracts exactly:
 *   `GET /api/v1/vendors/b2b/sellers`              -> {@link SellerSummary}[]
 *   `GET /api/v1/vendors/b2b/sellers/{id}/products` -> {@link B2BProductSummary}[]
 *   `POST /api/v1/vendors/b2b/orders`               -> {@link B2BOrderResponse}
 *   `GET /api/v1/vendors/b2b/orders`                -> {@link B2BOrderPageResponse}
 *   `GET /api/v1/vendors/b2b/orders/{id}`           -> {@link B2BOrderResponse}
 *   `POST /api/v1/vendors/b2b/orders/{id}/...`      -> {@link B2BOrderResponse}
 *   `GET /api/v1/vendors/b2b/statements/preview`    -> {@link B2BStatementPreviewResponse}
 *   `POST /api/v1/vendors/b2b/statements`           -> {@link B2BStatementResponse}
 *   `GET /api/v1/vendors/b2b/statements`            -> {@link B2BStatementPageResponse}
 *   `GET /api/v1/vendors/b2b/statements/{id}`       -> {@link B2BStatementResponse}
 *   `POST /api/v1/vendors/b2b/statements/{id}/payment` -> {@link B2BStatementResponse}
 *
 * Money values mirror the backend `BigDecimal` fields as `number` — the same
 * convention the rest of the frontend uses. The wire format stays a JSON number
 * and no arithmetic is done in the browser.
 *
 * `Status` is a closed union taken from the backend `B2BOrder.Status` enum
 * (native MySQL `ENUM`, D-20), so a request can never carry a value the column
 * would truncate.
 */

export const B2B_ORDER_STATUSES = [
  "PLACED",
  "ACCEPTED",
  "REJECTED",
  "CANCELLED",
  "SHIPPED",
  "DELIVERED",
] as const;

export type B2BOrderStatus = (typeof B2B_ORDER_STATUSES)[number];

export const B2B_PAYMENT_STATUSES = ["UNPAID", "PAID"] as const;
export type B2BPaymentStatus = (typeof B2B_PAYMENT_STATUSES)[number];

export const B2B_STATEMENT_STATUSES = ["OPEN", "PAID"] as const;
export type B2BStatementStatus = (typeof B2B_STATEMENT_STATUSES)[number];

export const B2B_PAYMENT_MODES = ["CASH", "UPI", "BANK_TRANSFER"] as const;
export type B2BPaymentMode = (typeof B2B_PAYMENT_MODES)[number];

export const B2B_ORDER_ACTIONS: Record<B2BOrderStatus, string[]> = {
  PLACED: ["accept", "reject", "cancel"],
  ACCEPTED: ["ship", "cancel"],
  REJECTED: [],
  CANCELLED: [],
  SHIPPED: ["deliver"],
  DELIVERED: [],
} as const;

export const B2B_ORDER_STATUS_LABELS: Record<B2BOrderStatus, string> = {
  PLACED: "Placed",
  ACCEPTED: "Accepted",
  REJECTED: "Rejected",
  CANCELLED: "Cancelled",
  SHIPPED: "Shipped",
  DELIVERED: "Delivered",
};

export const B2B_PAYMENT_MODE_LABELS: Record<B2BPaymentMode, string> = {
  CASH: "Cash",
  UPI: "UPI",
  BANK_TRANSFER: "Bank transfer",
};

/** An approved vendor that may act as a seller in a B2B order. */
export interface SellerSummary {
  vendorId: number;
  businessName: string;
  addressLine1: string;
  city: string;
  area: string;
  pincode: string;
  deliveryRadiusKm: number;
}

/** One orderable product from a seller's catalog. */
export interface B2BProductSummary {
  productId: number;
  productName: string;
  unitPrice: number;
  unit: string;
  availableStock: number;
}

/** One line item in a place-order request: product id and quantity. */
export interface B2BOrderItemRequest {
  productId: number;
  quantity: number;
}

/** Body of `POST /api/v1/vendors/b2b/orders`. */
export interface PlaceB2BOrderRequest {
  sellerId: number;
  items: B2BOrderItemRequest[];
  orderNote?: string | null;
}

/** One line item snapshot within a B2B order. */
export interface B2BOrderItemResponse {
  id: number;
  productId: number;
  productName: string;
  unit: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
}

/** Full read model for one B2B order (list and detail share this shape). */
export interface B2BOrderResponse {
  id: number;
  orderNo: string;
  buyerId: number;
  buyerName: string;
  sellerId: number;
  sellerName: string;
  status: B2BOrderStatus;
  paymentStatus: B2BPaymentStatus;
  total: number;
  orderNote: string | null;
  shipmentReference: string | null;
  shippedAt: string | null;
  deliveredAt: string | null;
  billNumber: string | null;
  placedAt: string;
  updatedAt: string;
  items: B2BOrderItemResponse[];
}

/** Paginated wrapper for the caller's B2B orders. */
export interface B2BOrderPageResponse {
  content: B2BOrderResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

/** The query parameters `GET /api/v1/vendors/b2b/orders` accepts. */
export interface B2BOrderListFilters {
  /** `null` = no role filter (server defaults to BUYER). */
  role: "BUYER" | "SELLER" | null;
  /** `null` = no status filter. */
  status: B2BOrderStatus | null;
  page: number;
  size: number;
}

/** One order summarised within a statement preview or detail. */
export interface B2BStatementOrderSummary {
  id: number;
  orderNo: string;
  status: B2BOrderStatus;
  paymentStatus: B2BPaymentStatus;
  total: number;
  deliveredAt: string | null;
  buyerId: number;
  sellerId: number;
}

/** Result of previewing eligible orders for a statement. */
export interface B2BStatementPreviewResponse {
  orders: B2BStatementOrderSummary[];
  totalAmount: number;
}

/** Body of `POST /api/v1/vendors/b2b/statements`. */
export interface CreateB2BStatementRequest {
  buyerId: number;
  from?: string | null;
  to?: string | null;
}

/** Read model for a B2B statement (create, list and detail share this shape). */
export interface B2BStatementResponse {
  id: number;
  statementNo: string;
  sellerId: number;
  sellerName: string;
  buyerId: number;
  buyerName: string;
  periodStart: string;
  periodEnd: string;
  totalAmount: number;
  status: B2BStatementStatus;
  paidAt: string | null;
  paymentMode: B2BPaymentMode | null;
  paymentNote: string | null;
  createdAt: string;
  updatedAt: string;
  orders: B2BStatementOrderSummary[];
}

/** Paginated wrapper for the caller's B2B statements. */
export interface B2BStatementPageResponse {
  content: B2BStatementResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

/** Body of `POST /api/v1/vendors/b2b/statements/{id}/payment`. */
export interface RecordB2BStatementPaymentRequest {
  paymentMode: B2BPaymentMode;
  paymentNote?: string | null;
  paidAt?: string | null;
}

/** The B2B statements namespace under `/api/v1/vendors/b2b`. */
export const B2B_STATEMENTS_PATH = "/b2b/statements";

/** The B2B orders namespace under `/api/v1/vendors/b2b`. */
export const B2B_ORDERS_PATH = "/b2b/orders";

/** The B2B sellers namespace under `/api/v1/vendors/b2b`. */
export const B2B_SELLERS_PATH = "/b2b/sellers";

/** Page-size bounds shared with the backend listing. */
export const B2B_PAGE_SIZE = 20;
export const B2B_MIN_PAGE_SIZE = 1;
export const B2B_MAX_PAGE_SIZE = 100;
