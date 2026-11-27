import {
  useMutation,
  useQuery,
  useQueryClient,
  type QueryClient,
} from "@tanstack/react-query";
import {
  acceptB2BOrder,
  cancelB2BOrder,
  createB2BStatement,
  deliverB2BOrder,
  fetchB2BOrder,
  fetchB2BOrders,
  fetchB2BStatements,
  fetchB2BStatement,
  fetchSellerProducts,
  fetchSellers,
  previewB2BStatement,
  recordB2BStatementPayment,
  rejectB2BOrder,
  shipB2BOrder,
} from "@/features/b2b/api";
import type {
  B2BOrderListFilters,
  B2BOrderPageResponse,
  B2BOrderResponse,
  B2BProductSummary,
  B2BStatementPageResponse,
  B2BStatementPreviewResponse,
  B2BStatementResponse,
  SellerSummary,
} from "@/features/b2b/types";

export const b2bKeys = {
  all: ["b2b"] as const,
  sellers: ["b2b", "sellers"] as const,
  sellerProducts: (sellerId: number) =>
    ["b2b", "sellers", sellerId, "products"] as const,
  orders: {
    all: ["b2b", "orders"] as const,
    list: (params: {
      role?: "BUYER" | "SELLER" | null;
      status?: string | null;
      page: number;
      size: number;
    }) =>
      [
        "b2b",
        "orders",
        "list",
        params.role ?? null,
        params.status ?? null,
        params.page,
        params.size,
      ] as const,
    detail: (orderId: number) => ["b2b", "orders", "detail", orderId] as const,
  },
  statements: {
    all: ["b2b", "statements"] as const,
    preview: (buyerId: number, from?: string | null, to?: string | null) =>
      ["b2b", "statements", "preview", buyerId, from ?? null, to ?? null] as const,
    list: (params: {
      role?: "SELLER" | "BUYER" | null;
      page: number;
      size: number;
    }) =>
      [
        "b2b",
        "statements",
        "list",
        params.role ?? null,
        params.page,
        params.size,
      ] as const,
    detail: (statementId: number) =>
      ["b2b", "statements", "detail", statementId] as const,
  },
};

/** Approved vendors (excluding the caller) whose name matches the optional query. */
export function useSellers(query?: string | null) {
  return useQuery<SellerSummary[]>({
    queryKey: [...b2bKeys.sellers, query ?? null],
    queryFn: ({ signal }) => fetchSellers(query, signal),
    enabled: query !== undefined,
  });
}

/** One seller's orderable products. */
export function useSellerProducts(sellerId: number | null) {
  return useQuery<B2BProductSummary[]>({
    queryKey: b2bKeys.sellerProducts(sellerId ?? 0),
    queryFn: () => fetchSellerProducts(sellerId as number),
    enabled: sellerId !== null,
  });
}

/** The caller's B2B orders, keyed by every filter and page. */
export function useB2BOrders(filters: B2BOrderListFilters) {
  return useQuery<B2BOrderPageResponse>({
    queryKey: b2bKeys.orders.list({
      role: filters.role,
      status: filters.status,
      page: filters.page,
      size: filters.size,
    }),
    queryFn: ({ signal }) => fetchB2BOrders(filters, signal),
  });
}

/** One B2B order. */
export function useB2BOrder(orderId: number | null) {
  return useQuery<B2BOrderResponse>({
    queryKey: b2bKeys.orders.detail(orderId ?? 0),
    queryFn: () => fetchB2BOrder(orderId as number),
    enabled: orderId !== null,
  });
}

/** One mutation for every B2B order action. */
export function useB2BOrderAction() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (variables: {
      orderId: number;
      action: "accept" | "reject" | "cancel" | "ship" | "deliver";
      reason?: string;
      shipmentReference?: string;
    }) => {
      const { orderId, action, reason, shipmentReference } = variables;

      switch (action) {
        case "accept":
          return acceptB2BOrder(orderId);
        case "reject":
          if (reason === undefined) {
            throw new Error("reject requires a reason");
          }
          return rejectB2BOrder(orderId, reason);
        case "cancel":
          if (reason === undefined) {
            throw new Error("cancel requires a reason");
          }
          return cancelB2BOrder(orderId, reason);
        case "ship":
          if (shipmentReference === undefined) {
            throw new Error("ship requires a shipment reference");
          }
          return shipB2BOrder(orderId, shipmentReference);
        case "deliver":
          return deliverB2BOrder(orderId);
      }
    },
    onSuccess: (_data, variables) => {
      void queryClient.invalidateQueries({ queryKey: b2bKeys.orders.all });
      void queryClient.invalidateQueries({
        queryKey: b2bKeys.orders.detail(variables.orderId),
      });
    },
  });
}

/** Eligible orders for a statement plus their combined total. Advisory only. */
export function useB2BStatementPreview(
  buyerId: number | null,
  from?: string | null,
  to?: string | null,
) {
  return useQuery<B2BStatementPreviewResponse>({
    queryKey: b2bKeys.statements.preview(buyerId ?? 0, from, to),
    queryFn: ({ signal }) => previewB2BStatement(buyerId as number, from, to, signal),
    enabled: buyerId !== null,
  });
}

/** The caller's statements as SELLER or BUYER, newest first. */
export function useB2BStatements(params: {
  role?: "SELLER" | "BUYER" | null;
  page: number;
  size: number;
}) {
  return useQuery<B2BStatementPageResponse>({
    queryKey: b2bKeys.statements.list(params),
    queryFn: ({ signal }) => fetchB2BStatements(params, signal),
  });
}

/** One B2B statement. */
export function useB2BStatement(statementId: number | null) {
  return useQuery<B2BStatementResponse>({
    queryKey: b2bKeys.statements.detail(statementId ?? 0),
    queryFn: () => fetchB2BStatement(statementId as number),
    enabled: statementId !== null,
  });
}

/** Create an OPEN statement from currently eligible orders. */
export function useCreateB2BStatement() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: {
      buyerId: number;
      from?: string | null;
      to?: string | null;
    }) => createB2BStatement(payload),
    onSuccess: (_statement, variables) => {
      void queryClient.invalidateQueries({ queryKey: b2bKeys.statements.all });
      void queryClient.invalidateQueries({
        queryKey: b2bKeys.statements.preview(variables.buyerId, variables.from, variables.to),
      });
    },
  });
}

/** Record full payment for an OPEN statement (seller only). */
export function useRecordB2BStatementPayment() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (variables: {
      statementId: number;
      paymentMode: "CASH" | "UPI" | "BANK_TRANSFER";
      paymentNote?: string | null;
      paidAt?: string | null;
    }) =>
      recordB2BStatementPayment(variables.statementId, {
        paymentMode: variables.paymentMode,
        paymentNote: variables.paymentNote,
        paidAt: variables.paidAt,
      }),
    onSuccess: (_statement, variables) => {
      void queryClient.invalidateQueries({
        queryKey: b2bKeys.statements.detail(variables.statementId),
      });
      void queryClient.invalidateQueries({ queryKey: b2bKeys.statements.all });
    },
  });
}

/** Drops every cached B2B query. Called on logout. */
export function clearB2BCache(queryClient: QueryClient): void {
  queryClient.removeQueries({ queryKey: b2bKeys.all });
}
