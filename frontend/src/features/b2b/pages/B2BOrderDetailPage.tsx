/**
 * B2B order detail (Stage 3 of the B2B module).
 *
 * Full read-only detail of one of the caller's own orders: item snapshots,
 * quantities, prices, total, shipment information and the status timeline.
 * Only the actions the backend allows the signed-in vendor for that status are
 * rendered — accept/reject/cancel for a `PLACED` order, ship/cancel for
 * `ACCEPTED`, deliver for `SHIPPED` — and the e-bill download only for
 * `DELIVERED` orders. Every transition still goes through the backend state
 * machine; the frontend never invents one.
 *
 * The page refetches while the tab is visible and stops once the order is
 * terminal, and it refreshes after every action so the rendered actions stay
 * in step with the server.
 */

import { useCallback, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useVendorProfile } from "@/features/vendor/queries";
import {
  useB2BOrder,
  useB2BOrderAction,
} from "@/features/b2b/queries";
import { fetchB2BEbill } from "@/features/b2b/api";
import type { B2BOrderResponse } from "@/features/b2b/types";
import {
  B2B_ORDER_STATUSES,
  B2B_ORDER_STATUS_LABELS,
} from "@/features/b2b/types";
import { VendorErrorState } from "@/features/vendor/components/VendorErrorState";
import { PageHeading } from "@/features/vendor/components/StatCard";
import { formatMoney, formatDateTime } from "@/features/vendor/format";
import { B2BOrderActionDialogs } from "@/features/b2b/components/B2BOrderActionDialogs";
import { FadeIn } from "@/motion/FadeIn";
import { FOCUS_RING, PRESSABLE } from "@/motion/pressable";
import { usePolling } from "@/hooks/usePolling";
import { toApiError } from "@/shared/lib/api-error";

/** Statuses after which a B2B order never changes again. */
const TERMINAL_STATUSES: B2BOrderResponse["status"][] = [
  "DELIVERED",
  "CANCELLED",
  "REJECTED",
];

function isTerminalStatus(status: B2BOrderResponse["status"]): boolean {
  return TERMINAL_STATUSES.includes(status);
}

export function B2BOrderDetailPage() {
  const navigate = useNavigate();
  const { orderId } = useParams();
  const id = orderId ? Number(orderId) : NaN;

  const profile = useVendorProfile();
  const callerId = profile.data?.id;
  const orderQuery = useB2BOrder(id > 0 ? id : null);
  const order: B2BOrderResponse | undefined = orderQuery.data;
  const isPending = orderQuery.isPending;
  const error = orderQuery.error;

  const runAction = useB2BOrderAction();
  const [openAction, setOpenAction] = useState<"reject" | "cancel" | "ship" | null>(null);
  const [actionError, setActionError] = useState<{ message: string } | null>(null);

  const role = callerId != null && order != null
    ? order.buyerId === callerId
      ? "BUYER"
      : order.sellerId === callerId
      ? "SELLER"
      : null
    : null;

  // Refetch while the tab is visible, but stop once the order is terminal.
  const refetchOrder = useB2BOrder(id > 0 ? id : null).refetch;
  usePolling(refetchOrder, {
    enabled: order != null && !isTerminalStatus(order.status),
  });
  const allowedActions: string[] = [];
  if (order != null && role != null && !isTerminalStatus(order.status)) {
    if (order.status === "PLACED") {
      if (role === "SELLER") {
        allowedActions.push("accept", "reject");
      } else if (role === "BUYER") {
        allowedActions.push("cancel");
      }
    } else if (order.status === "ACCEPTED") {
      if (role === "SELLER") {
        allowedActions.push("ship");
      }
      allowedActions.push("cancel");
    } else if (order.status === "SHIPPED") {
      if (role === "SELLER") {
        allowedActions.push("deliver");
      }
    }
  }

  const canDownloadEbill =
    order != null && order.status === "DELIVERED" && order.billNumber != null;

  const confirmAction = useCallback(
    async (action: "accept" | "reject" | "cancel" | "ship" | "deliver") => {
      setActionError(null);
      if (!order) return;
      if (action === "reject" || action === "cancel" || action === "ship") {
        setOpenAction(action);
        return;
      }
      try {
        await runAction.mutateAsync({ orderId: order.id, action });
        void runAction; // invalidate happens in the mutation's onSuccess
      } catch (caught) {
        setActionError({ message: toApiError(caught).message });
      }
    },
    [runAction, order],
  );

  const confirmDialogAction = useCallback(
    async (payload: { reason?: string; shipmentReference?: string }) => {
      setActionError(null);
      if (!order) return;
      try {
        if (openAction === "reject") {
          await runAction.mutateAsync({
            orderId: order.id,
            action: "reject",
            reason: payload.reason,
          });
        } else if (openAction === "cancel") {
          await runAction.mutateAsync({
            orderId: order.id,
            action: "cancel",
            reason: payload.reason,
          });
        } else if (openAction === "ship") {
          await runAction.mutateAsync({
            orderId: order.id,
            action: "ship",
            shipmentReference: payload.shipmentReference,
          });
        }
        setOpenAction(null);
      } catch (caught) {
        setActionError({ message: toApiError(caught).message });
      }
    },
    [runAction, order, openAction],
  );

  const downloadEbill = async () => {
    if (!order || !canDownloadEbill) return;
    setActionError(null);
    try {
      const blob = await fetchB2BEbill(order.id);
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = (order.billNumber ?? `order-${order.id}`) + ".pdf";
      document.body.appendChild(anchor);
      anchor.click();
      document.body.removeChild(anchor);
      URL.revokeObjectURL(url);
    } catch (caught) {
      setActionError({ message: toApiError(caught).message });
    }
  };

  if (error) {
    return (
      <div className="max-w-4xl mx-auto px-4 py-16">
        <PageHeading title={`Order #${id || "—"}`} description="B2B order detail" />
        <VendorErrorState error={error} />
      </div>
    );
  }

  if (isPending || !order) {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <div className="rounded-lg border border-slate-200 bg-white p-6 text-sm text-slate-600">
          Loading order…
        </div>
      </div>
    );
  }

  const isActing = runAction.isPending || openAction !== null;

  return (
    <>
      {actionError && (
        <FadeIn role="alert" className="max-w-4xl mx-auto px-4 rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-800">
          <p className="font-semibold">Action failed</p>
          <p className="mt-1">{actionError.message}</p>
          <button
            type="button"
            onClick={() => setActionError(null)}
            className="mt-3 inline-flex items-center rounded-md border border-transparent bg-brand-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-brand-700"
          >
            Dismiss
          </button>
        </FadeIn>
      )}
      <div className="max-w-4xl mx-auto px-4 py-8 space-y-8">
        <div className="flex flex-wrap justify-between items-end gap-4">
          <PageHeading title={`Order #${order.id}`} description={`#${order.orderNo}`} />
          <button
            type="button"
            onClick={() => navigate("/vendor/b2b/orders")}
            className={`rounded-md border border-transparent bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-700 ${PRESSABLE} ${FOCUS_RING}`}
          >
            Back to orders
          </button>
        </div>

        {/* Status */}
        <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
          <h2 className="text-sm font-semibold text-slate-900 mb-3">Status</h2>
          <span
            className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium border ${statusTone(order.status)}`}
          >
            {B2B_ORDER_STATUS_LABELS[order.status]}
          </span>
          {order.paymentStatus === "PAID" && (
            <span className="ml-2 inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium border text-green-800 bg-green-50 border-green-200">
              Payment paid
            </span>
          )}
          {order.paymentStatus === "UNPAID" && (
            <span className="ml-2 inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium border text-amber-800 bg-amber-50 border-amber-200">
              Payment unpaid
            </span>
          )}
        </div>

        {/* Items */}
        <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
          <h2 className="text-sm font-semibold text-slate-900 mb-3">Items</h2>
          <table className="w-full text-left text-sm divide-y divide-slate-100">
            <thead>
              <tr className="text-xs uppercase tracking-wide text-slate-500">
                <th scope="col" className="py-3 pr-4 font-medium">Item</th>
                <th scope="col" className="py-3 pr-4 font-medium text-right">Price</th>
                <th scope="col" className="py-3 pr-4 font-medium text-right">Qty</th>
                <th scope="col" className="py-3 font-medium text-right">Subtotal</th>
              </tr>
            </thead>
            <tbody>
              {order.items.map((item) => (
                <tr key={item.id} className="align-top">
                  <td className="py-3 pr-4">
                    <p className="font-medium text-slate-900">{item.productName}</p>
                    <p className="text-xs text-slate-500">{item.unit}</p>
                  </td>
                  <td className="py-3 pr-4 text-right text-slate-900">
                    {formatMoney(item.unitPrice)}
                  </td>
                  <td className="py-3 pr-4 text-right text-slate-900">
                    {item.quantity}
                  </td>
                  <td className="py-3 font-medium text-right text-slate-900">
                    {formatMoney(item.lineTotal)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {/* Totals and shipment */}
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
            <h2 className="text-sm font-semibold text-slate-900 mb-3">Total</h2>
            <p className="text-sm font-medium text-slate-900">
              {formatMoney(order.total)}
            </p>
            {order.orderNote && (
              <p className="mt-2 text-xs text-slate-600">
                <span className="font-semibold">Note:</span>{" "}
                {order.orderNote}
              </p>
            )}
          </div>
          <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
            <h2 className="text-sm font-semibold text-slate-900 mb-3">Shipment</h2>
            <dl className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div className="col-span-full border-b border-slate-100 pb-2">
                <dt className="text-xs text-slate-500">Reference</dt>
                <dd className="text-sm text-slate-900">
                  {order.shipmentReference ?? "—"}
                </dd>
              </div>
              {order.shippedAt && (
                <div className="border-b border-slate-100 pb-2">
                  <dt className="text-xs text-slate-500">Shipped at</dt>
                  <dd className="text-sm text-slate-900">
                    {formatDateTime(order.shippedAt)}
                  </dd>
                </div>
              )}
              {order.deliveredAt && (
                <div className="pb-2">
                  <dt className="text-xs text-slate-500">Delivered at</dt>
                  <dd className="text-sm text-slate-900">
                    {formatDateTime(order.deliveredAt)}
                  </dd>
                </div>
              )}
            </dl>
          </div>
        </div>

        {/* Timeline */}
        <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
          <h2 className="text-sm font-semibold text-slate-900 mb-3">
            Status timeline
          </h2>
          {order.items.length > 0 && (
            <ul className="space-y-3">
              <li key="placed" className="flex gap-3 py-2 border-b border-slate-100 last:border-0">
                <span className="shrink-0 w-6 h-6 rounded-full flex items-center justify-center text-[10px] font-bold text-slate-900 border border-slate-200">
                  1
                </span>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-slate-900">Order placed</p>
                  <p className="text-xs text-slate-600">
                    {order.placedAt}
                  </p>
                </div>
              </li>
              {B2B_ORDER_STATUSES.slice(1).map((status, i) => {
                const reached = order.status === status;
                return (
                  <li
                    key={status}
                    className={`flex gap-3 py-2 border-b border-slate-100 last:border-0 ${
                      reached ? "" : "opacity-50"
                    }`}
                  >
                    <span className="shrink-0 w-6 h-6 rounded-full flex items-center justify-center text-[10px] font-bold text-slate-900 border border-slate-200">
                      {i + 2}
                    </span>
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-medium text-slate-900">
                        {B2B_ORDER_STATUS_LABELS[status]}
                      </p>
                      <p className="text-xs text-slate-600">
                        {reached ? order.updatedAt : "Not yet"}
                      </p>
                    </div>
                  </li>
                );
              })}
            </ul>
          )}
        </div>

        {/* Actions */}
        {allowedActions.length > 0 && (
          <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
            <h2 className="text-sm font-semibold text-slate-900 mb-3">Actions</h2>
            <p className="text-xs text-slate-500 mb-4">
              Only the next valid action(s) for this status are shown; every
              change goes to the backend state machine.
            </p>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
        {allowedActions.map((action) => (
          <button
            key={action}
            type="button"
            disabled={isActing}
            onClick={() =>
              void confirmAction(
                action as "accept" | "reject" | "cancel" | "ship" | "deliver",
              )
            }
            className={`rounded-md border px-3 py-2 text-sm font-medium transition-colors ${
              action === "accept"
                ? "border-brand-600 bg-brand-50 text-brand-900 hover:bg-brand-100"
                : action === "reject" || action === "cancel"
                ? "border-red-600 bg-red-50 text-red-900 hover:bg-red-100"
                : "border-slate-600 bg-slate-50 text-slate-900 hover:bg-slate-100"
            } disabled:opacity-60 disabled:cursor-not-allowed ${PRESSABLE} ${FOCUS_RING}`}
          >
            {isActing ? "Working…" : actionLabel(action)}
          </button>
        ))}
      </div>
    </div>
  )}

        {allowedActions.length === 0 && !isTerminalStatus(order.status) && (
          <p className="text-sm text-slate-500">
            No action is available to you on this order right now.
          </p>
        )}

  {isTerminalStatus(order.status) && (
    <div className="rounded-lg border border-slate-200 bg-slate-50 p-4 text-sm text-slate-600">
      <p className="font-semibold">Order complete</p>
      <p className="mt-1 text-sm">
        {`This order is ${B2B_ORDER_STATUS_LABELS[order.status]}`}{" "}
        and no further action is available.
      </p>
      {canDownloadEbill && (
        <div className="mt-3">
          <button
            type="button"
            disabled={isActing}
            onClick={downloadEbill}
            className="rounded-md border border-brand-600 bg-brand-50 px-3 py-2 text-sm font-medium text-brand-900 hover:bg-brand-100 disabled:opacity-60 disabled:cursor-not-allowed"
          >
            {isActing ? "Downloading…" : "Download e-bill"}
          </button>
        </div>
      )}
    </div>
  )}

        <div className="mt-6 flex justify-end">
          <button
            type="button"
            onClick={() => navigate("/vendor/b2b/orders")}
            className={`rounded-md border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-100 ${PRESSABLE} ${FOCUS_RING}`}
          >
            Back to orders
          </button>
        </div>
      </div>

      <B2BOrderActionDialogs
        orderName={`Order #${order.id}`}
        openAction={openAction}
        isSubmitting={runAction.isPending}
        error={
          actionError?.message &&
          (openAction === "reject" || openAction === "cancel" || openAction === "ship")
            ? actionError.message
            : null
        }
        onConfirm={confirmDialogAction}
        onCancel={() => setOpenAction(null)}
      />
    </>
  );
}

function actionLabel(action: string): string {
  switch (action) {
    case "accept":
      return "Accept order";
    case "reject":
      return "Reject order";
    case "cancel":
      return "Cancel order";
    case "ship":
      return "Ship order";
    case "deliver":
      return "Mark delivered";
    default:
      return action;
  }
}

function statusTone(status: string): string {
  switch (status) {
    case "PLACED":
      return "text-amber-800 bg-amber-50 border-amber-200";
    case "ACCEPTED":
    case "SHIPPED":
      return "text-blue-800 bg-blue-50 border-blue-200";
    case "DELIVERED":
      return "text-green-800 bg-green-50 border-green-200";
    case "REJECTED":
    case "CANCELLED":
      return "text-red-800 bg-red-50 border-red-200";
    default:
      return "text-slate-800 bg-slate-50 border-slate-200";
  }
}
