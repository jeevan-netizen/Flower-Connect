/**
 * Admin order lookup and cancel (plan Phase 8, task 8.6).
 *
 * There is no pre-existing admin order view, so this is the smallest one:
 * an administrator enters an order id, optionally adds a mandatory reason,
 * and cancels the order through `POST /api/v1/orders/{id}/cancel`. The
 * backend enforces the state machine — admin can cancel `PLACED`,
 * `CONFIRMED`, `PREPARING` or `OUT_FOR_DELIVERY` orders, and every transition
 * requires a reason.
 *
 * The lookup form is deliberately minimal: it does not try to render order
 * details before canceling, because no admin order-read endpoint exists yet.
 * The cancel response confirms the new status.
 */

import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAdminCancelOrder } from "@/features/admin/queries";
import type { ApiErrorInfo } from "@/shared/lib/api-error";
import { toApiError } from "@/shared/lib/api-error";
import { PageHeading } from "@/features/vendor/components/StatCard";
import { FOCUS_RING, PRESSABLE } from "@/motion/pressable";

export function AdminOrderLookupPage() {
  const navigate = useNavigate();
  const [orderId, setOrderId] = useState("");
  const [reason, setReason] = useState("");
  const [error, setError] = useState<ApiErrorInfo | null>(null);
  const cancelOrder = useAdminCancelOrder();

  const isSubmitting = cancelOrder.isPending;

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    setError(null);
    const id = Number(orderId);
    if (!Number.isFinite(id) || id <= 0) {
      setError({ status: null, code: null, message: "Enter a valid order id.", validation: {} });
      return;
    }
    if (!reason.trim()) {
      setError({ status: null, code: null, message: "A reason is required for an admin cancellation.", validation: {} });
      return;
    }
    try {
      await cancelOrder.mutateAsync({ orderId: id, reason: reason.trim() });
      // After a successful cancel, show the order history so the admin sees
      // the order in its new state.
      navigate("/orders");
    } catch (mutationError) {
      setError(toApiError(mutationError));
    }
  };

  return (
    <div className="max-w-md mx-auto px-4 py-16">
      <PageHeading
        title="Cancel an order"
        description="Admin cancellation of any order. A reason is mandatory and is stored on the order's status timeline."
      />

      <form className="rounded-lg border border-bolder-line bg-bolder-surface p-5 shadow-sm space-y-4" onSubmit={handleSubmit}>
        {error && (
          <p className="text-sm text-bolder-danger bg-bolder-danger-surface rounded-lg p-2 border bolder-danger-border">
            {error.message}
          </p>
        )}
        <label className="block text-sm font-medium text-bolder-text">Order id</label>
        <input
          data-testid="admin-cancel-order-id"
          type="number"
          min="1"
          value={orderId}
          onChange={(event) => setOrderId(event.target.value)}
          className="mt-1 block w-full rounded-md border-bolder-line-control text-sm shadow-sm focus:border-bolder-rose focus:ring-bolder-rose"
          placeholder="e.g. 5001"
        />
        <label className="block text-sm font-medium text-bolder-text">Reason</label>
        <textarea
          data-testid="admin-cancel-reason"
          rows={3}
          value={reason}
          onChange={(event) => setReason(event.target.value)}
          className="mt-1 block w-full rounded-md border-bolder-line-control text-sm shadow-sm focus:border-bolder-rose focus:ring-bolder-rose"
          placeholder="Why is this order being cancelled?"
        />
        <p className="text-xs text-bolder-muted">
          Required for an admin cancellation. Stored on the order's status timeline.
        </p>
        <div className="mt-6 flex justify-end gap-2">
          <button
            type="button"
            onClick={() => navigate("/admin")}
            disabled={isSubmitting}
            className={`rounded-md border border-bolder-line-control bg-bolder-surface px-3 py-2 text-sm font-medium text-bolder-text hover:bg-bolder-bg disabled:cursor-not-allowed disabled:opacity-50 ${PRESSABLE} ${FOCUS_RING}`}
          >
            Back to admin
          </button>
          <button
            type="submit"
            disabled={isSubmitting || !reason.trim()}
            className={`rounded-md border border-transparent bg-bolder-rose-action px-4 py-2 text-sm font-medium text-white hover:bg-bolder-rose-deep disabled:opacity-50 disabled:cursor-not-allowed ${PRESSABLE} ${FOCUS_RING}`}
          >
            {isSubmitting ? "Canceling…" : "Cancel order"}
          </button>
        </div>
      </form>
    </div>
  );
}
