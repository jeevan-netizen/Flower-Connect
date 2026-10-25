/**
 * Vendor order board (plan Phase 6, task 6.7).
 *
 * A Kanban-style board of the calling vendor's own orders, grouped into New,
 * Accepted / Preparing, Out for delivery and Delivered today. Each card offers
 * only the next valid action(s) for its status, and every action goes through
 * the backend state machine — the frontend never invents a client-side
 * transition.
 *
 * The board refetches every few seconds while the tab is visible, shows the
 * today summary strip, and disables all action buttons while a request is in
 * flight to prevent duplicate clicks.
 */

import { useCallback, useState } from "react";
import { useVendorOrders, useVendorOrderSummary, useVendorOrderAction } from "@/features/vendor/orders/queries";
import { usePolling } from "@/hooks/usePolling";
import type { VendorOrderResponse, VendorOrderAction } from "@/features/vendor/orders/types";
import { ORDER_BOARD_COLUMNS, OrderBoardColumnKey } from "@/features/vendor/orders/types";
import { isTerminalStatus } from "@/features/vendor/orders/types";
import { TodaySummaryStrip } from "@/features/vendor/orders/components/TodaySummaryStrip";
import { OrderCard } from "@/features/vendor/orders/components/OrderCard";
import { RejectReasonDialog } from "@/features/vendor/orders/components/RejectReasonDialog";
import { CancelReasonDialog } from "@/features/vendor/orders/components/CancelReasonDialog";
import { VendorErrorState } from "@/features/vendor/components/VendorErrorState";
import { PageHeading } from "@/features/vendor/components/StatCard";
import { FadeIn } from "@/motion/FadeIn";
import { toApiError, type ApiErrorInfo } from "@/shared/lib/api-error";

const PAGE_SIZE = 100;

function todayLocalDate(): string {
  return new Date().toISOString().slice(0, 10);
}

export function VendorOrderBoardPage() {
  const today = todayLocalDate();
  const [rejectTarget, setRejectTarget] = useState<{
    orderId: number;
    orderName: string;
  } | null>(null);
  const [cancelTarget, setCancelTarget] = useState<{
    orderId: number;
    orderName: string;
  } | null>(null);
  const [actionError, setActionError] = useState<ApiErrorInfo | null>(null);

  const {
    data,
    isPending,
    error,
    refetch: refetchOrders,
  } = useVendorOrders({ status: undefined, fromDate: undefined, toDate: undefined, page: 0, size: PAGE_SIZE });

  const summary = useVendorOrderSummary(today);
  const runAction = useVendorOrderAction();

  // Refetch while the tab is visible so new orders and status changes land on
  // the board. Terminal customer orders stop polling on the customer side;
  // here the vendor's own board stays fresh while open.
  usePolling(refetchOrders, { enabled: true });

  const orders: VendorOrderResponse[] = data?.content ?? [];
  const ordersByColumn = new Map<OrderBoardColumnKey, VendorOrderResponse[]>();
  ORDER_BOARD_COLUMNS.forEach((column) => ordersByColumn.set(column.key, []));

  orders.forEach((order) => {
    ORDER_BOARD_COLUMNS.forEach((column) => {
      if (column.statuses.includes(order.status)) {
        ordersByColumn.get(column.key)?.push(order);
      }
    });
  });

  // "Delivered today" keeps only deliveries whose created_at is today.
  const deliveredToday = ordersByColumn.get("delivered-today") ?? [];
  const deliveredTodayFiltered = deliveredToday.filter((o) => o.createdAt.slice(0, 10) === today);
  ordersByColumn.set("delivered-today", deliveredTodayFiltered);

  const handleAction = useCallback(
    async (action: VendorOrderAction, orderId: number) => {
      setActionError(null);
      try {
        await runAction.mutateAsync({ orderId, action });
      } catch (mutationError) {
        setActionError(toApiError(mutationError));
      }
    },
    [runAction],
  );

  const confirmReject = useCallback(
    (reason: string) => {
      if (!rejectTarget) return;
      setActionError(null);
      void runAction
        .mutateAsync({ orderId: rejectTarget.orderId, action: "reject", reason })
        .catch((mutationError) => {
          setActionError(toApiError(mutationError));
        })
        .finally(() => {
          setRejectTarget(null);
        });
    },
    [rejectTarget, runAction],
  );

  const confirmCancel = useCallback(
    (reason: string) => {
      if (!cancelTarget) return;
      setActionError(null);
      void runAction
        .mutateAsync({ orderId: cancelTarget.orderId, action: "cancel", reason })
        .catch((mutationError) => {
          setActionError(toApiError(mutationError));
        })
        .finally(() => {
          setCancelTarget(null);
        });
    },
    [cancelTarget, runAction],
  );

  const hasOrders = orders.length > 0;
  const hasNonTerminal = orders.some((o) => !isTerminalStatus(o.status));

  if (error) {
    return (
      <div className="max-w-5xl mx-auto px-4 py-16">
        <PageHeading title="Orders" description="Your shop's incoming orders." />
        <VendorErrorState error={error} onRetry={() => void refetchOrders()} />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <PageHeading title="Orders" description="Your shop's incoming orders." />

      <TodaySummaryStrip summary={summary.data ?? null} isPending={summary.isPending} />

      {actionError && (
        <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-800">
          <p className="font-semibold">{actionError.message}</p>
          {actionError.code === "INSUFFICIENT_STOCK" && (
            <p className="mt-1 text-xs text-red-700">
              Stock was written off or adjusted since this order was placed.
              Open the order and reject it instead.
            </p>
          )}
          <button
            type="button"
            onClick={() => setActionError(null)}
            className="mt-3 inline-flex items-center rounded-md border border-transparent bg-brand-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-brand-700"
          >
            Dismiss
          </button>
        </div>
      )}

      {!isPending && !error && !hasOrders && (
        <FadeIn className="rounded-lg border border-slate-200 bg-white p-6 text-center shadow-sm">
          <h2 className="text-base font-semibold text-slate-900">No orders yet</h2>
          <p className="mt-2 text-sm text-slate-600">
            When a customer places an order with your shop, it appears here.
          </p>
        </FadeIn>
      )}

      {!isPending && !error && hasOrders && (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {ORDER_BOARD_COLUMNS.map((column) => {
            const columnOrders = ordersByColumn.get(column.key) ?? [];
            return (
              <div key={column.key} className="flex flex-col gap-3">
                <div className="flex items-center gap-2">
                  <h3 className="text-sm font-semibold text-slate-900">{column.label}</h3>
                  <span className="text-xs font-medium text-slate-500">{columnOrders.length}</span>
                </div>
                <div
                  className="flex-1 rounded-lg border border-slate-200 bg-slate-50 min-h-[300px] flex flex-col"
                >
                  {columnOrders.length === 0 && (
                    <div className="flex-1 flex items-center justify-center text-sm text-slate-400">
                      —
                    </div>
                  )}
                  {columnOrders.map((order) => (
                    <OrderCard
                      key={order.id}
                      order={order}
                      isPending={runAction.isPending}
                      error={
                        actionError?.status === 409 && actionError.code === "INSUFFICIENT_STOCK"
                          ? { status: actionError.status, message: actionError.message }
                          : null
                      }
                      onAction={(action) => void handleAction(action, order.id)}
                      onReject={() => {
                        setRejectTarget({
                          orderId: order.id,
                          orderName: `Order #${order.id}`,
                        });
                      }}
                      onCancel={() => {
                        setCancelTarget({
                          orderId: order.id,
                          orderName: `Order #${order.id}`,
                        });
                      }}
                    />
                  ))}
                </div>
              </div>
            );
          })}
        </div>
      )}

      <RejectReasonDialog
        orderName={rejectTarget?.orderName ?? "this order"}
        open={rejectTarget !== null}
        isSubmitting={runAction.isPending && rejectTarget !== null}
        error={actionError?.code === "VALIDATION_FAILED" ? actionError.message : null}
        onConfirm={confirmReject}
        onCancel={() => setRejectTarget(null)}
      />

      {cancelTarget && (
        <CancelReasonDialog
          orderName={cancelTarget.orderName}
          open={true}
          isSubmitting={runAction.isPending && cancelTarget !== null}
          error={actionError?.code === "VALIDATION_FAILED" ? actionError.message : null}
          onConfirm={confirmCancel}
          onCancel={() => setCancelTarget(null)}
        />
      )}

      {hasNonTerminal && (
        <p className="mt-2 text-xs text-slate-500">
          Orders refresh automatically while this tab is open.
        </p>
      )}
    </div>
  );
}
