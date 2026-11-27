/**
 * B2B orders (Stage 3 of the B2B module).
 *
 * Two tabs — Buying (orders the caller placed as buyer) and Selling (orders the
 * caller took as seller) — each paginated and filterable by status. Rows link
 * to the order detail page where the caller can act on the order.
 *
 * The listing is keyed by tab, status and page so a filter change is a cache
 * miss rather than a stale render. Loading, empty and API error states are
 * explicit.
 */

import { useState } from "react";
import { Link } from "react-router-dom";
import { useB2BOrders } from "@/features/b2b/queries";
import type {
  B2BOrderListFilters,
  B2BOrderResponse,
  B2BOrderStatus,
} from "@/features/b2b/types";
import { B2B_ORDER_STATUSES, B2B_ORDER_STATUS_LABELS } from "@/features/b2b/types";
import { VendorErrorState } from "@/features/vendor/components/VendorErrorState";
import { PageHeading } from "@/features/vendor/components/StatCard";
import { formatMoney, formatDateTime } from "@/features/vendor/format";
import { Pagination } from "@/shared/components/Pagination";
import { FadeIn } from "@/motion/FadeIn";
import { FOCUS_RING, PRESSABLE } from "@/motion/pressable";

const PAGE_SIZE = 20;

const TABS: { key: "BUYER" | "SELLER"; label: string }[] = [
  { key: "BUYER", label: "Buying" },
  { key: "SELLER", label: "Selling" },
];

export function B2BOrdersPage() {
  const [tab, setTab] = useState<"BUYER" | "SELLER">("BUYER");
  const [statusFilter, setStatusFilter] = useState<B2BOrderStatus | null>(null);
  const [page, setPage] = useState(0);

  const filters: B2BOrderListFilters = {
    role: tab,
    status: statusFilter ?? null,
    page,
    size: PAGE_SIZE,
  };

  const {
    data,
    isPending,
    error,
    refetch: refetchOrders,
  } = useB2BOrders(filters);

  const orders: B2BOrderResponse[] = data?.content ?? [];

  if (error) {
    return (
      <div className="max-w-5xl mx-auto px-4 py-16">
        <PageHeading title="B2B orders" description="Your wholesale orders as buyer and seller." />
        <VendorErrorState error={error} onRetry={() => void refetchOrders()} />
      </div>
    );
  }

  return (
    <div className="max-w-5xl mx-auto px-4 py-8 space-y-8">
      <PageHeading
        title="B2B orders"
        description="Orders you placed as a buyer, and orders placed with you as a seller."
      />

      {/* Tabs */}
      <div className="flex gap-1 border-b border-slate-200 pb-2">
        {TABS.map((t) => (
          <button
            key={t.key}
            type="button"
            onClick={() => {
              setTab(t.key);
              setPage(0);
              setStatusFilter(null);
            }}
            className={`-mb-px border-b-2 px-3 py-2 text-sm font-medium transition-colors ${
              tab === t.key
                ? "border-brand-600 text-brand-900"
                : "border-transparent text-slate-500 hover:text-slate-700"
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {isPending && !data && (
        <FadeIn className="rounded-lg border border-slate-200 bg-white p-12 text-center text-sm text-slate-600">
          Loading orders…
        </FadeIn>
      )}

      {!isPending && data && orders.length === 0 && (
        <FadeIn className="rounded-lg border border-slate-200 bg-white p-12 text-center">
          <h2 className="text-base font-semibold text-slate-900">
            No {tab === "BUYER" ? "placed" : "received"} orders yet
          </h2>
          <p className="mt-2 text-sm text-slate-600">
            {tab === "BUYER"
              ? "Place an order with a seller and it will appear here."
              : "When a buyer places an order with your shop, it will appear here."}
          </p>
        </FadeIn>
      )}

      {!isPending && data && orders.length > 0 && (
        <>
          {/* Status filter */}
          <div className="flex items-center gap-4 mb-4">
            <span className="text-xs font-medium text-slate-500 uppercase tracking-wider">
              Filter by status
            </span>
            {B2B_ORDER_STATUSES.map((status) => (
              <button
                key={status}
                type="button"
                onClick={() => {
                  setStatusFilter(status);
                  setPage(0);
                }}
                className={`rounded-md border px-3 py-1.5 text-xs font-medium transition-colors ${
                  statusFilter === status
                    ? "border-brand-600 bg-brand-50 text-brand-900"
                    : "border-slate-200 bg-white text-slate-600 hover:bg-slate-50"
                }`}
              >
                {B2B_ORDER_STATUS_LABELS[status]}
              </button>
            ))}
            <button
              type="button"
              onClick={() => setStatusFilter(null)}
              className={`rounded-md border px-3 py-1.5 text-xs font-medium transition-colors ${
                statusFilter === null
                  ? "border-brand-600 bg-brand-50 text-brand-900"
                  : "border-slate-200 bg-white text-slate-600 hover:bg-slate-50"
              }`}
            >
              All
            </button>
          </div>

          <table className="w-full text-left text-sm divide-y divide-slate-100">
            <thead>
              <tr className="text-xs uppercase tracking-wide text-slate-500">
                <th scope="col" className="py-3 pr-4 font-medium">Order</th>
                <th scope="col" className="py-3 pr-4 font-medium">Counterparty</th>
                <th scope="col" className="py-3 pr-4 font-medium">Status</th>
                <th scope="col" className="py-3 pr-4 font-medium text-right">Total</th>
                <th scope="col" className="py-3 font-medium text-right">Placed</th>
              </tr>
            </thead>
            <tbody>
              {orders.map((order) => (
                <tr key={order.id} className="align-top group hover:bg-slate-50">
                  <td className="py-3 pr-4">
                    <Link
                      to={`/vendor/b2b/orders/${order.id}`}
                      className={`font-medium text-slate-900 hover:underline ${PRESSABLE} ${FOCUS_RING}`}
                    >
                      #{order.orderNo}
                    </Link>
                    {order.orderNote && (
                      <p className="mt-1 text-xs text-slate-500 truncate">
                        "{order.orderNote}"
                      </p>
                    )}
                  </td>
                  <td className="py-3 pr-4">
                    <p className="font-medium text-slate-900">
                      {tab === "BUYER" ? order.sellerName : order.buyerName}
                    </p>
                    <p className="text-xs text-slate-500">
                      {tab === "BUYER" ? order.sellerName : order.buyerName}
                    </p>
                  </td>
                  <td className="py-3 pr-4">
                    <span
                      className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium border ${statusTone(order.status)}`}
                    >
                      {B2B_ORDER_STATUS_LABELS[order.status]}
                    </span>
                  </td>
                  <td className="py-3 font-medium text-right text-slate-900">
                    {formatMoney(order.total)}
                  </td>
                  <td className="py-3 text-right text-sm text-slate-600">
                    {formatDateTime(order.placedAt)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>

          <Pagination
            page={data}
            onPageChange={(nextPage) => setPage(nextPage)}
          />
        </>
      )}
    </div>
  );
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
