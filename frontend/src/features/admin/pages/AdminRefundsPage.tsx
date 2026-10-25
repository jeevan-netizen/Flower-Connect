/**
 * Admin refund queue (plan Phase 8, task 8.3).
 *
 * A paginated listing of payments whose status is `REFUND_PENDING` — i.e.
 * cancelled online orders whose payment has been marked for refund but not
 * yet completed by an administrator. Supports optional vendor and date-range
 * filters, and every row opens the mark-refunded dialog which requires a
 * reference note.
 *
 * The queue is keyed by every filter and page, so a filter change is a cache
 * miss rather than a stale render of the previous page's rows. Marking a
 * refund invalidates the whole queue: the refunded payment drops out.
 */

import { useCallback, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useRefundQueue, useMarkRefunded } from "@/features/admin/queries";
import { adminKeys } from "@/features/admin/queries";
import type { RefundQueueItem, RefundQueueFilters } from "@/features/admin/types";
import { PageHeading } from "@/features/vendor/components/StatCard";
import { AdminErrorState } from "@/features/admin/components/AdminErrorState";
import { MarkRefundedDialog } from "@/features/admin/components/MarkRefundedDialog";
import { Pagination } from "@/shared/components/Pagination";
import { FadeIn } from "@/motion/FadeIn";
import { toApiError, type ApiErrorInfo } from "@/shared/lib/api-error";
import { FIELD_TRANSITION, FOCUS_RING, PRESSABLE } from "@/motion/pressable";
import { formatMoney } from "@/features/vendor/format";
import { formatDateTime } from "@/features/vendor/format";

const PAYMENT_STATUS_OPTIONS: { value: string | null; label: string }[] = [
  { value: null, label: "All statuses" },
  { value: "REFUND_PENDING", label: "Refund pending" },
  { value: "REFUNDED", label: "Refunded" },
];

export function AdminRefundsPage() {
  const [filters, setFilters] = useState<RefundQueueFilters>({
    status: "REFUND_PENDING",
    vendorId: null,
    fromDate: null,
    toDate: null,
    page: 0,
  });
  const [markTarget, setMarkTarget] = useState<{
    orderId: number;
    orderName: string;
  } | null>(null);
  const [actionError, setActionError] = useState<ApiErrorInfo | null>(null);

  const queryClient = useQueryClient();
  const { data, isPending, error } = useRefundQueue(filters);
  const markRefunded = useMarkRefunded();

  const refetch = useCallback(() => {
    void queryClient.invalidateQueries({ queryKey: adminKeys.refunds.all });
  }, [queryClient]);

  const openMarkDialog = (orderId: number, orderName: string) => {
    setActionError(null);
    setMarkTarget({ orderId, orderName });
  };

  const closeMarkDialog = () => {
    setMarkTarget(null);
    setActionError(null);
  };

  const confirmMark = async (referenceNote: string) => {
    if (!markTarget) return;
    setActionError(null);
    try {
      await markRefunded.mutateAsync({
        orderId: markTarget.orderId,
        referenceNote,
      });
      setMarkTarget(null);
    } catch (mutationError) {
      setActionError(toApiError(mutationError));
    }
  };

  const items: RefundQueueItem[] = data?.content ?? [];
  const pageData = data;

  if (error) {
    return (
      <div className="max-w-5xl mx-auto px-4 py-16">
        <PageHeading title="Refund queue" description="Refunds awaiting admin completion." />
        <AdminErrorState error={error} onRetry={() => void refetch()} />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <PageHeading
        title="Refund queue"
        description="Online orders whose payment has been marked refund-pending and awaits admin completion."
      />

      <div className="flex flex-wrap items-end gap-3 rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
        <div className="flex-1 min-w-[200px]">
          <label
            htmlFor="refund-status-filter"
            className="block text-sm font-medium text-slate-700"
          >
            Payment status
          </label>
          <select
            id="refund-status-filter"
            value={filters.status ?? ""}
            onChange={(event) => {
              const value = event.target.value;
              setFilters({ ...filters, status: value === "" ? null : value, page: 0 });
            }}
            className={`mt-1 block rounded-md border-slate-300 text-sm shadow-sm ${FIELD_TRANSITION} focus:border-brand-500 focus:ring-brand-500`}
          >
            {PAYMENT_STATUS_OPTIONS.map((option) => (
              <option key={option.label} value={option.value ?? ""}>
                {option.label}
              </option>
            ))}
          </select>
        </div>
        <div className="flex-1 min-w-[200px]">
          <label htmlFor="refund-vendor-filter" className="block text-sm font-medium text-slate-700">
            Vendor id
          </label>
          <input
            id="refund-vendor-filter"
            type="number"
            value={filters.vendorId ?? ""}
            onChange={(event) => {
              const value = event.target.value;
              setFilters({
                ...filters,
                vendorId: value === "" ? null : Number(value),
                page: 0,
              });
            }}
            className={`mt-1 block rounded-md border-slate-300 text-sm shadow-sm ${FIELD_TRANSITION} focus:border-brand-500 focus:ring-brand-500`}
            placeholder="e.g. 7"
          />
        </div>
        <div className="flex-1 min-w-[200px]">
          <label htmlFor="refund-from-date" className="block text-sm font-medium text-slate-700">
            From date
          </label>
          <input
            id="refund-from-date"
            type="date"
            value={filters.fromDate ?? ""}
            onChange={(event) => {
              setFilters({ ...filters, fromDate: event.target.value, page: 0 });
            }}
            className={`mt-1 block rounded-md border-slate-300 text-sm shadow-sm ${FIELD_TRANSITION} focus:border-brand-500 focus:ring-brand-500`}
          />
        </div>
        <div className="flex-1 min-w-[200px]">
          <label htmlFor="refund-to-date" className="block text-sm font-medium text-slate-700">
            To date
          </label>
          <input
            id="refund-to-date"
            type="date"
            value={filters.toDate ?? ""}
            onChange={(event) => {
              setFilters({ ...filters, toDate: event.target.value, page: 0 });
            }}
            className={`mt-1 block rounded-md border-slate-300 text-sm shadow-sm ${FIELD_TRANSITION} focus:border-brand-500 focus:ring-brand-500`}
          />
        </div>
      </div>

      {isPending && (
        <div className="rounded-lg border border-slate-200 bg-white p-5 text-sm text-slate-600">
          Loading refunds...
        </div>
      )}

      {!isPending && error && (
        <AdminErrorState error={error} onRetry={() => void refetch()} />
      )}

      {!isPending && !error && pageData && pageData.empty && (
        <div className="rounded-lg border border-slate-200 bg-white p-5 text-sm text-slate-600">
          <h3 className="text-base font-semibold text-slate-900">No refunds pending</h3>
          <p className="mt-2 text-sm text-slate-600">
            No payments match the current filters. Refunds that have been
            completed by an administrator no longer appear here.
          </p>
        </div>
      )}

      {!isPending && !error && pageData && !pageData.empty && (
        <FadeIn
          key={`${filters.status}:${filters.vendorId}:${filters.fromDate}:${filters.toDate}:${pageData.page}:${items.map((i) => i.orderId).join(",")}`}
          className="overflow-x-auto rounded-lg border border-slate-200 bg-white p-5 shadow-sm"
        >
          <table className="w-full text-left text-sm">
            <caption className="sr-only">Refund queue — payments awaiting admin completion</caption>
            <thead className="text-xs uppercase tracking-wide text-slate-500">
              <tr>
                <th scope="col" className="py-2 pr-4 font-medium">Order</th>
                <th scope="col" className="py-2 pr-4 font-medium">Shop</th>
                <th scope="col" className="py-2 pr-4 font-medium">Method</th>
                <th scope="col" className="py-2 pr-4 font-medium">Amount</th>
                <th scope="col" className="py-2 pr-4 font-medium">Customer</th>
                <th scope="col" className="py-2 pr-4 font-medium">Placed</th>
                <th scope="col" className="py-2 font-medium">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {items.map((item) => (
                <tr key={item.orderId} className="align-top group">
                  <td className="py-3 pr-4 font-medium text-slate-900">
                    Order #{item.orderId}
                  </td>
                  <td className="py-3 pr-4 text-slate-700">{item.vendorName}</td>
                  <td className="py-3 pr-4 text-slate-700">
                    {item.paymentMethod === "COD" ? "Cash on delivery" : "Online"}
                  </td>
                  <td className="py-3 pr-4 text-right font-medium text-slate-900">
                    {formatMoney(item.amount)}
                  </td>
                  <td className="py-3 pr-4 text-slate-700">{item.customerEmail}</td>
                  <td className="py-3 text-slate-600">
                    {formatDateTime(item.createdAt)}
                  </td>
                  <td className="py-3">
                    <button
                      type="button"
                      disabled={markRefunded.isPending}
                      onClick={() => openMarkDialog(item.orderId, `Order #${item.orderId}`)}
                      className={`rounded-md border border-transparent bg-brand-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-brand-700 disabled:opacity-50 disabled:cursor-not-allowed ${PRESSABLE} ${FOCUS_RING}`}
                    >
                      Mark refunded
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>

          <div className="mt-4">
            <Pagination
              page={pageData}
              disabled={markRefunded.isPending}
              onPageChange={(page) =>
                setFilters((current) => ({ ...current, page }))
              }
            />
          </div>
        </FadeIn>
      )}

      <MarkRefundedDialog
        orderName={markTarget?.orderName ?? "this order"}
        open={markTarget !== null}
        isSubmitting={markRefunded.isPending}
        error={actionError}
        onConfirm={confirmMark}
        onCancel={closeMarkDialog}
      />
    </div>
  );
}
