/**
 * Payment status page (plan task 7.6).
 *
 * Polls `GET /api/v1/payments/group/{paymentGroupId}` while the payment group
 * intent is still `CREATED`, and stops once it reaches a terminal state
 * (`PAID`, `FAILED`, `EXPIRED`). Provides a "Resume payment" action that calls
 * `POST /api/v1/payments/{orderId}/resume` — a refresh that returns the current
 * pending state and marks the intent expired on timeout. Only the verified
 * webhook flow transitions a payment to `PAID`/`PLACED`; the resume action
 * never marks a payment or order as paid.
 *
 * No card numbers, CVV, or other card-like data are collected, rendered,
 * stored, logged, or submitted: online payment in v1 is a simulated test-mode
 * provider (D-48/D-49).
 */

import { useCallback } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { useGroupState, useResumePayment } from "@/features/payment/queries";
import type { PaymentStatus } from "@/features/payment/types";
import {
  isTerminalPaymentStatus,
  PAYMENT_STATUS_LABELS,
} from "@/features/payment/types";
import { usePolling } from "@/hooks/usePolling";
import { formatMoney } from "@/features/vendor/format";
import { CustomerErrorState } from "@/features/customer/components/CustomerErrorState";
import { PageHeading } from "@/features/vendor/components/StatCard";
import { FOCUS_RING, PRESSABLE } from "@/motion/pressable";

export function PaymentStatusPage() {
  const navigate = useNavigate();
  const { paymentGroupId } = useParams();

  const {
    data: group,
    isPending,
    error,
    refetch: refetchGroup,
  } = useGroupState(paymentGroupId ?? null);

  const resumePayment = useResumePayment();

  // Poll while the group intent is still CREATED; stop for terminal states.
  // Tab-visibility pause and terminal-stop are handled by `usePolling` and
  // the query's `refetchInterval` returning `false` for non-CREATED statuses.
  usePolling(refetchGroup, {
    enabled: group != null && group.status === "CREATED",
  });

  const onResume = useCallback(
    (orderId: number) => {
      void resumePayment.mutateAsync(orderId).catch(() => {
        // Error is surfaced by the query's error state on the next poll.
      });
    },
    [resumePayment],
  );

  const onBack = useCallback(() => {
    navigate("/orders");
  }, [navigate]);

  if (error) {
    return (
      <div className="max-w-4xl mx-auto px-4 py-16 text-center">
        <CustomerErrorState error={error} onRetry={() => void refetchGroup()} />
        <p className="mt-4">
          <Link
            to="/orders"
            className={`inline-flex items-center rounded-md border border-transparent bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-700 ${PRESSABLE} ${FOCUS_RING}`}
          >
            Back to your orders
          </Link>
        </p>
      </div>
    );
  }

  if (isPending || !group) {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <div className="rounded-lg border border-slate-200 bg-white p-6 text-sm text-slate-600">
          Checking payment status…
        </div>
      </div>
    );
  }

  const terminal = isTerminalPaymentStatus(group.status as PaymentStatus);

  const nextStepMessage = group.status === "PAID"
    ? "Your payment has been confirmed. The florist will now accept or reject your order."
    : group.status === "FAILED"
      ? "Your payment did not succeed. No money was taken and no stock was reserved. You can place a new order from the catalogue."
      : "Your payment intent has expired. The order is no longer awaiting payment and can no longer be resumed. Place a new order from the catalogue.";

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap justify-between items-end gap-4">
        <PageHeading title="Payment status" description={group.paymentGroupId} />
        <Link
          to="/orders"
          className={`inline-flex items-center rounded-md border border-transparent bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-700 ${PRESSABLE} ${FOCUS_RING}`}
        >
          Back to your orders
        </Link>
      </div>

      {/* Payment group status */}
      <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
        <h2 className="text-sm font-semibold text-slate-900 mb-3">Payment group</h2>
        <dl className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div className="border-b border-glass-border-soft pb-2">
            <dt className="text-xs text-slate-500">Group id</dt>
            <dd className="text-sm text-slate-900 truncate">{group.paymentGroupId}</dd>
          </div>
          <div className="border-b border-glass-border-soft pb-2">
            <dt className="text-xs text-slate-500">Intent</dt>
            <dd className="text-sm text-slate-900 truncate">{group.intentReference}</dd>
          </div>
          <div className="border-b border-glass-border-soft pb-2">
            <dt className="text-xs text-slate-500">Status</dt>
            <dd
              className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium border ${
                group.status === "PAID"
                  ? "text-green-800 bg-green-50 border-green-200"
                  : group.status === "FAILED" || group.status === "EXPIRED"
                    ? "text-red-800 bg-red-50 border-red-200"
                    : "text-amber-800 bg-amber-50 border-amber-200"
              }`}
            >
              {group.status === "CREATED" ? "Awaiting payment" : group.status}
            </dd>
          </div>
          <div className="border-b border-glass-border-soft pb-2">
            <dt className="text-xs text-slate-500">Total</dt>
            <dd className="text-sm font-medium text-slate-900 text-right">
              {formatMoney(group.total)}
            </dd>
          </div>
          {group.expiresAt && (
            <div className="border-b border-glass-border-soft pb-2">
              <dt className="text-xs text-slate-500">Expires at</dt>
              <dd className="text-sm text-slate-900">{group.expiresAt}</dd>
            </div>
          )}
        </dl>
      </div>

      {/* Orders in the group */}
      <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
        <h2 className="text-sm font-semibold text-slate-900 mb-3">Orders</h2>
        <table className="w-full text-left text-sm divide-y divide-slate-100">
          <thead className="text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th scope="col" className="py-3 pr-4 font-medium">Order</th>
              <th scope="col" className="py-3 pr-4 font-medium">Vendor</th>
              <th scope="col" className="py-3 pr-4 font-medium text-right">Amount</th>
              <th scope="col" className="py-3 font-medium text-right">Payment</th>
              <th scope="col" className="py-3 font-medium text-right">Action</th>
            </tr>
          </thead>
          <tbody>
            {group.orders.map((entry) => (
              <tr key={entry.orderId} className="align-top">
                <td className="py-3 pr-4">
                  <p className="font-medium text-slate-900">#{entry.orderId}</p>
                  <p className="text-xs text-slate-500">{entry.orderStatus}</p>
                </td>
                <td className="py-3 pr-4 text-slate-900">{entry.amount != null ? formatMoney(entry.amount) : "-"}</td>
                <td className="py-3 pr-4 text-right">
                  <span
                    className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium border ${
                      entry.paymentStatus === "PAID" || entry.paymentStatus === "COLLECTED"
                        ? "text-green-800 bg-green-50 border-green-200"
                        : entry.paymentStatus === "FAILED"
                          ? "text-red-800 bg-red-50 border-red-200"
                          : "text-amber-800 bg-amber-50 border-amber-200"
                    }`}
                  >
                    {PAYMENT_STATUS_LABELS[entry.paymentStatus]}
                  </span>
                </td>
                <td className="py-3 pr-4 text-right">
                  {entry.paymentStatus === "PENDING" && !terminal ? (
                    <button
                      type="button"
                      onClick={() => onResume(entry.orderId)}
                      disabled={resumePayment.isPending}
                      className={`rounded-md border border-brand-600 bg-white px-3 py-1 text-xs font-medium text-brand-600 hover:bg-brand-50 disabled:opacity-50 disabled:cursor-not-allowed ${PRESSABLE} ${FOCUS_RING}`}
                    >
                      {resumePayment.isPending ? "Resuming…" : "Resume"}
                    </button>
                  ) : (
                    <span className="text-xs text-slate-400">—</span>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {terminal && (
        <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
          <h2 className="text-sm font-semibold text-slate-900 mb-3">What happens next</h2>
          <p className="text-sm text-slate-600">
            {nextStepMessage}
          </p>
        </div>
      )}

      <div className="mt-6 flex justify-end">
        <button
          type="button"
          onClick={onBack}
          className={`rounded-md border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-100 ${PRESSABLE} ${FOCUS_RING}`}
        >
          Back to your orders
        </button>
      </div>
    </div>
  );
}
