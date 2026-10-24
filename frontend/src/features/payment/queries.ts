import { useMutation, useQuery, useQueryClient, type QueryClient } from "@tanstack/react-query";
import {
  fetchGroupState,
  fetchPaymentState,
  resumePayment,
} from "@/features/payment/api";
import type {
  PaymentGroupStateResponse,
  PaymentStateResponse,
} from "@/features/payment/types";

/**
 * Query keys for the customer payment area (plan task 7.6).
 *
 * Payment state is keyed by order id so each order's poll is independent.
 * Group state is keyed by payment group id so all orders in a group share
 * one poll.
 */
export const paymentKeys = {
  all: ["payment"] as const,
  state: (orderId: number) => ["payment", "state", orderId] as const,
  group: (paymentGroupId: string) => ["payment", "group", paymentGroupId] as const,
};

/** `GET /api/v1/payments/{orderId}` — current payment state for one order.
 *
 * Polls at `PAYMENT_POLL_INTERVAL_MS` (8 s) while the order is awaiting
 * payment. Polling stops automatically once the payment reaches a terminal
 * state (`PAID`, `FAILED`, `REFUNDED`) — the webhook flow is the only
 * transition path and the frontend never needs to poll after that.
 */
export function usePaymentState(orderId: number | null) {
  return useQuery<PaymentStateResponse>({
    queryKey: paymentKeys.state(orderId ?? 0),
    queryFn: ({ signal }) => fetchPaymentState(orderId as number, signal),
    enabled: orderId !== null,
  });
}

/** `GET /api/v1/payments/group/{paymentGroupId}` — current state of a payment group.
 *
 * Polls while the group intent is still `CREATED`. Stops once the intent
 * reaches `PAID`, `FAILED`, or `EXPIRED`.
 */
export function useGroupState(paymentGroupId: string | null) {
  return useQuery<PaymentGroupStateResponse>({
    queryKey: paymentKeys.group(paymentGroupId ?? ""),
    queryFn: ({ signal }) => fetchGroupState(paymentGroupId!, signal),
    enabled: paymentGroupId !== null,
  });
}

/** `POST /api/v1/payments/{orderId}/resume` — confirm payment for a pending order.
 *
 * A resume is a refresh, not a completion: it returns the current pending
 * state and marks the intent expired on timeout. Only the verified webhook
 * flow transitions a payment to `PAID`/`PLACED`. On success the payment and
 * group query caches are invalidated so the next poll reads fresh state.
 */
export function useResumePayment() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (orderId: number) => resumePayment(orderId),
    onSuccess: (result) => {
      // Invalidate the order's payment state so the next poll reflects the
      // resume outcome (pending, expired, etc.).
      if (result.orderId != null) {
        void queryClient.invalidateQueries({
          queryKey: paymentKeys.state(result.orderId),
        });
      }
      // Invalidate the group so all linked orders refresh too.
      if (result.paymentGroupId != null) {
        void queryClient.invalidateQueries({
          queryKey: paymentKeys.group(result.paymentGroupId),
        });
      }
    },
  });
}

/** Drops every cached payment query. Called on logout so a different account
 *  signing in on the same tab never sees the previous customer's payment data. */
export function clearPaymentCache(queryClient: QueryClient): void {
  queryClient.removeQueries({ queryKey: paymentKeys.all });
}
