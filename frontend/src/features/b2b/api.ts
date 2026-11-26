import api from "@/shared/lib/api";
import type {
  B2BOrderItemRequest,
  B2BOrderPageResponse,
  B2BOrderResponse,
  B2BOrderListFilters,
  B2BProductSummary,
  B2BStatementPageResponse,
  B2BStatementPreviewResponse,
  B2BStatementResponse,
  CreateB2BStatementRequest,
  PlaceB2BOrderRequest,
  RecordB2BStatementPaymentRequest,
  SellerSummary,
} from "@/features/b2b/types";

/**
 * B2B API client (Stage 3 of the B2B module).
 *
 * Every endpoint is `hasRole("FLORIST")` and resolves the caller's vendor
 * profile from the JWT subject. Order and statement ownership is enforced per
 * handler by the backend: a foreign order or statement is 403, a missing one
 * 404. The frontend never invents a client-side status transition — it only
 * asks the backend to make the next valid one and renders the result (or the
 * 409/400 it refuses with).
 */

/* -------------------------------------------------------------------------- */
/* Sellers                                                                    */
/* -------------------------------------------------------------------------- */

/**
 * `GET /api/v1/vendors/b2b/sellers?query=` — approved vendors (excluding the
 * caller) whose business name matches the optional substring. Only approved
 * vendors may be sellers in a B2B order.
 */
export async function fetchSellers(
  query?: string | null,
  signal?: AbortSignal,
): Promise<SellerSummary[]> {
  const response = await api.get<SellerSummary[]>("/b2b/sellers", {
    params: query ? { query } : undefined,
    signal,
  });
  return response.data;
}

/**
 * `GET /api/v1/vendors/b2b/sellers/{sellerId}/products` — the seller's ACTIVE
 * products with catalog price and available stock, for building a B2B order.
 */
export async function fetchSellerProducts(
  sellerId: number,
  signal?: AbortSignal,
): Promise<B2BProductSummary[]> {
  const response = await api.get<B2BProductSummary[]>(
    `/b2b/sellers/${sellerId}/products`,
    { signal },
  );
  return response.data;
}

/* -------------------------------------------------------------------------- */
/* Orders                                                                     */
/* -------------------------------------------------------------------------- */

/**
 * `POST /api/v1/vendors/b2b/orders` — places a new PLACED order from the
 * authenticated buyer to the selected seller. Prices and totals are computed
 * server-side and snapshotted.
 */
export async function placeB2BOrder(
  payload: PlaceB2BOrderRequest,
): Promise<B2BOrderResponse> {
  const items = payload.items.map(
    ({ productId, quantity }): B2BOrderItemRequest => ({
      productId,
      quantity,
    }),
  );
  const response = await api.post<B2BOrderResponse>("/b2b/orders", {
    sellerId: payload.sellerId,
    items,
    orderNote: payload.orderNote ?? null,
  });
  return response.data;
}

/**
 * `GET /api/v1/vendors/b2b/orders?role=&status=&page=&size=` — the caller's
 * orders as buyer or seller, newest first.
 */
export async function fetchB2BOrders(
  filters: B2BOrderListFilters,
  signal?: AbortSignal,
): Promise<B2BOrderPageResponse> {
  const params: Record<string, string | number> = {
    page: filters.page,
    size: filters.size,
  };
  if (filters.role) {
    params.role = filters.role;
  }
  if (filters.status) {
    params.status = filters.status;
  }

  const response = await api.get<B2BOrderPageResponse>("/b2b/orders", {
    params,
    signal,
  });
  return response.data;
}

/** `GET /api/v1/vendors/b2b/orders/{orderId}` — one B2B order. */
export async function fetchB2BOrder(orderId: number): Promise<B2BOrderResponse> {
  const response = await api.get<B2BOrderResponse>(`/b2b/orders/${orderId}`);
  return response.data;
}

/** `POST /api/v1/vendors/b2b/orders/{orderId}/accept` — seller only. */
export async function acceptB2BOrder(
  orderId: number,
): Promise<B2BOrderResponse> {
  const response = await api.post<B2BOrderResponse>(
    `/b2b/orders/${orderId}/accept`,
  );
  return response.data;
}

/**
 * `POST /api/v1/vendors/b2b/orders/{orderId}/reject` — seller only, reason
 * mandatory.
 */
export async function rejectB2BOrder(
  orderId: number,
  reason: string,
): Promise<B2BOrderResponse> {
  const response = await api.post<B2BOrderResponse>(
    `/b2b/orders/${orderId}/reject`,
    { reason },
  );
  return response.data;
}

/**
 * `POST /api/v1/vendors/b2b/orders/{orderId}/cancel` — buyer may cancel a
 * PLACED order; either party may cancel an ACCEPTED order.
 */
export async function cancelB2BOrder(
  orderId: number,
  reason: string,
): Promise<B2BOrderResponse> {
  const response = await api.post<B2BOrderResponse>(
    `/b2b/orders/${orderId}/cancel`,
    { reason },
  );
  return response.data;
}

/**
 * `POST /api/v1/vendors/b2b/orders/{orderId}/ship` — seller only, shipment
 * reference mandatory.
 */
export async function shipB2BOrder(
  orderId: number,
  shipmentReference: string,
): Promise<B2BOrderResponse> {
  const response = await api.post<B2BOrderResponse>(
    `/b2b/orders/${orderId}/ship`,
    { shipmentReference },
  );
  return response.data;
}

/** `POST /api/v1/vendors/b2b/orders/{orderId}/deliver` — seller only. */
export async function deliverB2BOrder(
  orderId: number,
): Promise<B2BOrderResponse> {
  const response = await api.post<B2BOrderResponse>(
    `/b2b/orders/${orderId}/deliver`,
  );
  return response.data;
}

/**
 * `GET /api/v1/vendors/b2b/orders/{orderId}/e-bill` — the e-bill PDF for a
 * DELIVERED order. Returns the raw bytes; the caller is responsible for
 * triggering a download.
 */
export async function fetchB2BEbill(orderId: number): Promise<Blob> {
  const response = await api.get<Blob>(`/b2b/orders/${orderId}/e-bill`, {
    responseType: "blob",
  });
  return response.data;
}

/* -------------------------------------------------------------------------- */
/* Statements                                                                 */
/* -------------------------------------------------------------------------- */

/**
 * `GET /api/v1/vendors/b2b/statements/preview?buyerId=&from=&to=` — eligible
 * delivered, unpaid orders for the selected buyer within the inclusive range,
 * plus their combined total.
 */
export async function previewB2BStatement(
  buyerId: number,
  from?: string | null,
  to?: string | null,
  signal?: AbortSignal,
): Promise<B2BStatementPreviewResponse> {
  const params: Record<string, string | number> = { buyerId };
  if (from) {
    params.from = from;
  }
  if (to) {
    params.to = to;
  }

  const response = await api.get<B2BStatementPreviewResponse>(
    "/b2b/statements/preview",
    { params, signal },
  );
  return response.data;
}

/**
 * `POST /api/v1/vendors/b2b/statements` — creates an OPEN statement from the
 * currently eligible orders; the total is computed server-side.
 */
export async function createB2BStatement(
  payload: CreateB2BStatementRequest,
): Promise<B2BStatementResponse> {
  const response = await api.post<B2BStatementResponse>("/b2b/statements", {
    buyerId: payload.buyerId,
    from: payload.from ?? null,
    to: payload.to ?? null,
  });
  return response.data;
}

/**
 * `GET /api/v1/vendors/b2b/statements?role=&page=&size=` — the caller's
 * statements as SELLER or BUYER, newest first.
 */
export async function fetchB2BStatements(
  filters: {
    role?: "SELLER" | "BUYER" | null;
    page: number;
    size: number;
  },
  signal?: AbortSignal,
): Promise<B2BStatementPageResponse> {
  const params: Record<string, string | number> = {
    page: filters.page,
    size: filters.size,
  };
  if (filters.role) {
    params.role = filters.role;
  }

  const response = await api.get<B2BStatementPageResponse>("/b2b/statements", {
    params,
    signal,
  });
  return response.data;
}

/** `GET /api/v1/vendors/b2b/statements/{statementId}` — one statement. */
export async function fetchB2BStatement(
  statementId: number,
): Promise<B2BStatementResponse> {
  const response = await api.get<B2BStatementResponse>(
    `/b2b/statements/${statementId}`,
  );
  return response.data;
}

/**
 * `POST /api/v1/vendors/b2b/statements/{statementId}/payment` — seller only.
 * Marks an OPEN statement PAID and every linked order's payment_status PAID in
 * one transaction. Full payment only.
 */
export async function recordB2BStatementPayment(
  statementId: number,
  payload: RecordB2BStatementPaymentRequest,
): Promise<B2BStatementResponse> {
  const response = await api.post<B2BStatementResponse>(
    `/b2b/statements/${statementId}/payment`,
    {
      paymentMode: payload.paymentMode,
      paymentNote: payload.paymentNote ?? null,
      paidAt: payload.paidAt ?? null,
    },
  );
  return response.data;
}
