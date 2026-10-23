/**
 * Customer order history (plan Phase 6, task 6.6).
 *
 * A paginated, newest-first list of the authenticated customer's own orders,
 * guarded to the CUSTOMER role by the route. Each row links to its detail
 * page, where the full status timeline and fee breakdown live.
 *
 * Polling refetches the list every few seconds while the tab is visible and
 * stops once every order on screen is in a terminal state, so a delivered,
 * cancelled or rejected order stops churning the server.
 */

import { useState } from "react";
import { Link } from "react-router-dom";
import { useOrders } from "@/features/customer/orders/queries";
import { usePolling } from "@/hooks/usePolling";
import type { CustomerOrderListItem } from "@/features/customer/orders/types";
import { isTerminalStatus } from "@/features/customer/orders/types";
import { formatMoney } from "@/features/vendor/format";
import { CustomerErrorState } from "@/features/customer/components/CustomerErrorState";
import { PageHeading } from "@/features/vendor/components/StatCard";
import { Pagination } from "@/shared/components/Pagination";
import { FadeIn } from "@/motion/FadeIn";
import { FOCUS_RING, PRESSABLE } from "@/motion/pressable";

const PAGE_SIZE = 20;

const STATUS_TONES: Record<CustomerOrderListItem["status"], string> = {
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

function statusLabel(status: CustomerOrderListItem["status"]): string {
  return status
    .split("_")
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
    .join(" ");
}

function paymentStatusLabel(status: string): string {
  return status
    .split("_")
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
    .join(" ");
}

function statusBadge(status: CustomerOrderListItem["status"]) {
  return (
    <span
      className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium border ${STATUS_TONES[status]}`}
    >
      {statusLabel(status)}
    </span>
  );
}

export function OrderHistoryPage() {
  const [page, setPage] = useState(0);
  const [statusFilter, setStatusFilter] = useState<string | null>(null);

  const {
    data,
    isPending,
    error,
    refetch: refetchOrders,
  } = useOrders(page, PAGE_SIZE, statusFilter);

  // Refetch while the tab is visible, but stop once every order on screen is
  // terminal — a delivered/cancelled/rejected order never changes again.
  const orders = data?.content ?? [];
  usePolling(refetchOrders, {
    enabled: orders.length > 0 && orders.some((o) => !isTerminalStatus(o.status)),
  });

  const hasNonTerminal = orders.some((o) => !isTerminalStatus(o.status));

  if (error) {
    return (
      <div className="max-w-4xl mx-auto px-4 py-16 text-center">
        <CustomerErrorState error={error} onRetry={() => void refetchOrders()} />
      </div>
    );
  }

  if (isPending || !data) {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <div className="rounded-lg border border-slate-200 bg-white p-6 text-sm text-slate-600">
          Loading your orders…
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <PageHeading
        title="Your orders"
        description="Every order you have placed, newest first."
      />

      <div className="flex flex-wrap items-end gap-3">
        <label
          htmlFor="order-status-filter"
          className="block text-sm font-medium text-slate-700"
        >
          Filter by status
        </label>
        <select
          id="order-status-filter"
          value={statusFilter ?? ""}
          onChange={(event) => {
            const value = event.target.value;
            setStatusFilter(value === "" ? null : value);
            setPage(0);
          }}
          className="mt-1 block rounded-md border-slate-300 text-sm shadow-sm focus:border-brand-500 focus:ring-brand-500"
        >
          <option value="">All statuses</option>
          {[...new Set(orders.map((o) => o.status))].map((status) => (
            <option key={status} value={status}>
              {statusLabel(status)}
            </option>
          ))}
        </select>
      </div>

      {orders.length === 0 ? (
        <FadeIn className="rounded-lg border border-slate-200 bg-white p-6 text-center shadow-sm">
          <h2 className="text-base font-semibold text-slate-900">
            {statusFilter ? `No ${statusFilter.toLowerCase()} orders` : "No orders yet"}
          </h2>
          <p className="mt-2 text-sm text-slate-600">
            {statusFilter
              ? "Try another filter, or place an order to see it here."
              : "Place your first order and it will appear here."}
          </p>
          <Link
            to="/browse"
            className={`mt-4 inline-flex items-center rounded-md border border-transparent bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-700 ${PRESSABLE} ${FOCUS_RING}`}
          >
            Start browsing
          </Link>
        </FadeIn>
      ) : (
        <>
          <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white shadow-sm">
            <table className="w-full text-left text-sm">
              <caption className="sr-only">Customer order history</caption>
              <thead className="text-xs uppercase tracking-wide text-slate-500">
                <tr>
                  <th scope="col" className="py-3 pr-4 font-medium">
                    Order
                  </th>
                  <th scope="col" className="py-3 pr-4 font-medium">
                    Shop
                  </th>
                  <th scope="col" className="py-3 pr-4 font-medium">
                    Status
                  </th>
                  <th scope="col" className="py-3 pr-4 font-medium">
                    Payment
                  </th>
                  <th scope="col" className="py-3 pr-4 font-medium text-right">
                    Total
                  </th>
                  <th scope="col" className="py-3 font-medium">
                    Placed
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {orders.map((order) => (
                  <tr
                    key={order.id}
                    className="align-top group hover:bg-slate-50"
                  >
                    <td className="py-3 pr-4">
                      <Link
                        to={`/orders/${order.id}`}
                        className="font-medium text-slate-900 hover:text-brand-600"
                      >
                        Order #{order.id}
                      </Link>
                    </td>
                    <td className="py-3 pr-4 text-slate-700">{order.vendorName}</td>
                    <td className="py-3 pr-4">{statusBadge(order.status)}</td>
                    <td className="py-3 pr-4 text-slate-700">
                      {order.paymentMethod === "COD" ? "Cash on delivery" : "Online"}
                      {order.paymentStatus !== "PENDING" && (
                        <span className="ml-1 text-xs text-slate-500">
                          ({paymentStatusLabel(order.paymentStatus)})
                        </span>
                      )}
                    </td>
                    <td className="py-3 pr-4 text-right font-medium text-slate-900">
                      {formatMoney(order.total)}
                    </td>
                    <td className="py-3 text-slate-600">{order.createdAt}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="mt-4">
            <Pagination
              page={data}
              onPageChange={(next) => setPage(next)}
              disabled={isPending || !hasNonTerminal}
            />
            {hasNonTerminal && (
              <p className="mt-2 text-xs text-slate-500">
                Orders in progress refresh automatically while this tab is open.
              </p>
            )}
          </div>
        </>
      )}
    </div>
  );
}
