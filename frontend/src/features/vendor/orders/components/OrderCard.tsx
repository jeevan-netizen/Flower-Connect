/**
 * One order card on the vendor board (plan Phase 6, task 6.7).
 *
 * Renders the order's status badge, total, slot and address, plus the next
 * valid action(s) for its status. Every action button is disabled while any
 * action is in flight, so a double click cannot fire the same transition twice
 * and one submission cannot race another. The backend state machine is the
 * authority — this card only asks it to make the next valid transition.
 */

import { useCallback } from "react";
import type { VendorOrderResponse, VendorOrderAction } from "@/features/vendor/orders/types";
import { ORDER_STATUS_LABELS } from "@/features/vendor/orders/types";
import { formatMoney } from "@/features/vendor/format";
import { formatDateTime } from "@/features/vendor/format";
import { VENDOR_ORDER_ACTION_LABELS, NEXT_ACTIONS } from "@/features/vendor/orders/types";
import { FOCUS_RING, PRESSABLE } from "@/motion/pressable";

interface OrderCardProps {
  order: VendorOrderResponse;
  /** True while any board action is in flight — all buttons disable. */
  isPending: boolean;
  /** Normalised failure from the previous action attempt, if any. */
  error: { status: number | null; message: string } | null;
  /** Runs the chosen action for this order. */
  onAction: (action: VendorOrderAction, reason?: string) => void;
  /** Opens the reject-reason dialog for this order. */
  onReject: () => void;
  /** Opens the cancel-reason dialog for this order. */
  onCancel: () => void;
}

const STATUS_TONES: Record<VendorOrderResponse["status"], string> = {
  PLACED: "border-bolder-warning-border bg-bolder-warning-surface text-bolder-warning",
  PENDING_PAYMENT: "border-bolder-neutral-border bg-bolder-neutral-surface text-bolder-neutral",
  CONFIRMED: "border-bolder-blush-light bg-bolder-blush-light text-bolder-rose-deep",
  PREPARING: "border-bolder-blush-light bg-bolder-blush-light text-bolder-rose-deep",
  OUT_FOR_DELIVERY: "border-bolder-info-border bg-bolder-info-surface text-bolder-info",
  DELIVERED: "border-bolder-success-border bg-bolder-success-surface text-bolder-success",
  CANCELLED: "border-bolder-danger-border bg-bolder-danger-surface text-bolder-danger",
  REJECTED: "border-bolder-danger-border bg-bolder-danger-surface text-bolder-danger",
  REFUNDED: "border-bolder-neutral-border bg-bolder-neutral-surface text-bolder-neutral",
};

function actionButtonClass(disabled: boolean): string {
  return [
    "rounded-md border bolder-line-control bg-bolder-surface px-2.5 py-1.5 text-xs font-medium text-bolder-text",
    "hover:bg-bolder-bg disabled:opacity-50 disabled:cursor-not-allowed",
    disabled ? "" : `${PRESSABLE} ${FOCUS_RING}`,
  ].join(" ");
}

export function OrderCard({
  order,
  isPending,
  error,
  onAction,
  onReject,
  onCancel,
}: OrderCardProps) {
  const actions = NEXT_ACTIONS[order.status] ?? [];

  const handleAction = useCallback(
    (action: VendorOrderAction) => {
      if (isPending) return;
      if (action === "reject") {
        onReject();
        return;
      }
      if (action === "cancel") {
        onCancel();
        return;
      }
      onAction(action);
    },
    [isPending, onReject, onCancel, onAction],
  );

  const insufficientStock = error?.status === 409 && order.status === "PLACED";

  return (
    <div className="rounded-lg border bolder-line bg-bolder-surface p-4 shadow-sm">
      <div className="flex justify-between items-start mb-2">
        <span className="text-sm font-medium text-bolder-text">
          Order #{order.id}
        </span>
        <span className="text-xs font-bold text-bolder-text">
          {formatMoney(order.total)}
        </span>
      </div>

      <div className="flex justify-between items-start mb-2">
        <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium border ${STATUS_TONES[order.status]}`}>
          {ORDER_STATUS_LABELS[order.status]}
        </span>
      </div>

      <p className="text-sm text-bolder-text mb-3 truncate">{order.deliveryAddressLine1}</p>

      <div className="text-xs text-bolder-muted mb-3">
        {order.slotStart && order.slotEnd
          ? `${formatDateTime(order.slotStart)} – ${formatDateTime(order.slotEnd)}`
          : "Slot not scheduled"}
        {order.paymentStatus && order.paymentStatus !== "PENDING" && (
          <span className="ml-2">· {order.paymentStatus.replace(/_/g, " ")}</span>
        )}
      </div>

      {actions.length > 0 && (
        <div className="flex flex-wrap gap-2">
          {actions.map((action) => {
            const disabled = isPending || insufficientStock;
            const isInsufficientStockReject =
              insufficientStock && action === "reject";
            return (
              <button
                key={action}
                type="button"
                disabled={disabled}
                onClick={() => handleAction(action)}
                className={actionButtonClass(disabled)}
              >
                {isInsufficientStockReject
                  ? `${VENDOR_ORDER_ACTION_LABELS[action]} (stock short)`
                  : VENDOR_ORDER_ACTION_LABELS[action]}
              </button>
            );
          })}
        </div>
      )}

      {insufficientStock && (
        <p className="mt-3 text-xs text-bolder-danger bg-bolder-danger-surface rounded-lg p-2 border bolder-danger-border">
          Insufficient stock to accept this order — it may have been written off
          or adjusted since checkout. {order.status === "PLACED" ? "Reject the order instead." : ""}
        </p>
      )}
    </div>
  );
}
