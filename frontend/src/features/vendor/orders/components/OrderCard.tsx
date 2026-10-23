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
}

const STATUS_TONES: Record<VendorOrderResponse["status"], string> = {
  PLACED: "border-amber-200 bg-amber-50 text-amber-900",
  PENDING_PAYMENT: "border-slate-200 bg-slate-50 text-slate-800",
  CONFIRMED: "border-brand-100 bg-brand-50 text-brand-900",
  PREPARING: "border-brand-100 bg-brand-50 text-brand-900",
  OUT_FOR_DELIVERY: "border-blue-200 bg-blue-50 text-blue-900",
  DELIVERED: "border-green-200 bg-green-50 text-green-900",
  CANCELLED: "border-red-200 bg-red-50 text-red-900",
  REJECTED: "border-red-200 bg-red-50 text-red-900",
  REFUNDED: "border-slate-200 bg-slate-50 text-slate-800",
};

function actionButtonClass(disabled: boolean): string {
  return [
    "rounded-md border border-slate-300 bg-white px-2.5 py-1 text-xs font-medium text-slate-700",
    "hover:bg-slate-100 disabled:opacity-50 disabled:cursor-not-allowed",
    disabled ? "" : `${PRESSABLE} ${FOCUS_RING}`,
  ].join(" ");
}

export function OrderCard({
  order,
  isPending,
  error,
  onAction,
  onReject,
}: OrderCardProps) {
  const actions = NEXT_ACTIONS[order.status] ?? [];

  const handleAction = useCallback(
    (action: VendorOrderAction) => {
      if (isPending) return;
      if (action === "reject") {
        onReject();
        return;
      }
      onAction(action);
    },
    [isPending, onReject, onAction],
  );

  const insufficientStock = error?.status === 409 && order.status === "PLACED";

  return (
    <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
      <div className="flex justify-between items-start mb-2">
        <span className="text-sm font-medium text-slate-900">
          Order #{order.id}
        </span>
        <span className="text-xs font-bold text-slate-900">
          {formatMoney(order.total)}
        </span>
      </div>

      <div className="flex justify-between items-start mb-2">
        <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium border ${STATUS_TONES[order.status]}`}>
          {ORDER_STATUS_LABELS[order.status]}
        </span>
      </div>

      <p className="text-sm text-slate-700 mb-3 truncate">{order.deliveryAddressLine1}</p>

      <div className="text-xs text-slate-500 mb-3">
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
        <p className="mt-3 text-xs text-red-800 bg-red-50 rounded-lg p-2 border border-red-100">
          Insufficient stock to accept this order — it may have been written off
          or adjusted since checkout. {order.status === "PLACED" ? "Reject the order instead." : ""}
        </p>
      )}
    </div>
  );
}
