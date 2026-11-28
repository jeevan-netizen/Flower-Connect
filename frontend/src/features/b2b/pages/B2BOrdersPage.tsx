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
      <div className="flex gap-1 border-b border-bolder-line pb-2">
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
                ? "border-bolder-rose-action text-bolder-rose-deep"
                : "border-transparent text-bolder-muted hover:text-bolder-text"
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {isPending && !data && (
        <FadeIn className="rounded-lg border bolder-line bg-bolder-surface p-12 text-center text-sm text-bolder-muted">
          Loading orders…
        </FadeIn>
      )}

      {!isPending && data && orders.length === 0 && (
        <FadeIn className="rounded-lg border bolder-line bg-bolder-surface p-12 text-center">
          <h2 className="text-base font-semibold text-bolder-text">
            No {tab === "BUYER" ? "placed" : "received"} orders yet
          </h2>
          <p className="mt-2 text-sm text-bolder-muted">
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
            <span className="text-xs font-medium text-bolder-muted uppercase tracking-wider">
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
                    ? "border-bolder-rose-action bg-bolder-blush-light text-bolder-rose-deep"
                    : "border-bolder-line-control bg-bolder-surface text-bolder-text hover:bg-bolder-bg"
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
                  ? "border-bolder-rose-action bg-bolder-blush-light text-bolder-rose-deep"
                  : "border-bolder-line-control bg-bolder-surface text-bolder-text hover:bg-bolder-bg"
              }`}
            >
              All
            </button>
          </div>

          <table className="w-full text-left text-sm divide-y divide-bolder-line">
            <thead>
              <tr className="text-xs uppercase tracking-wide text-bolder-muted">
                <th scope="col" className="py-3 pr-4 font-medium">Order</th>
                <th scope="col" className="py-3 pr-4 font-medium">Counterparty</th>
                <th scope="col" className="py-3 pr-4 font-medium">Status</th>
                <th scope="col" className="py-3 pr-4 font-medium text-right">Total</th>
                <th scope="col" className="py-3 font-medium text-right">Placed</th>
              </tr>
            </thead>
            <tbody>
              {orders.map((order) => (
                <tr key={order.id} className="align-top group hover:bg-bolder-bg/80">
                  <td className="py-3 pr-4">
                    <Link
                      to={`/vendor/b2b/orders/${order.id}`}
                      className={`font-medium text-bolder-text hover:underline ${PRESSABLE} ${FOCUS_RING}`}
                    >
                      #{order.orderNo}
                    </Link>
                    {order.orderNote && (
                      <p className="mt-1 text-xs text-bolder-muted truncate">
                        "{order.orderNote}"
                      </p>
                    )}
                  </td>
                  <td className="py-3 pr-4">
                    <p className="font-medium text-bolder-text">
                      {tab === "BUYER" ? order.sellerName : order.buyerName}
                    </p>
                    <p className="text-xs text-bolder-muted">
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
                  <td className="py-3 font-medium text-right text-bolder-text">
                    {formatMoney(order.total)}
                  </td>
                  <td className="py-3 text-right text-sm text-bolder-muted">
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
      return "text-bolder-warning bg-bolder-warning-surface border-bolder-warning-border";
    case "ACCEPTED":
    case "SHIPPED":
      return "text-bolder-info bg-bolder-info-surface border-bolder-info-border";
    case "DELIVERED":
      return "text-bolder-success bg-bolder-success-surface border-bolder-success-border";
    case "REJECTED":
    case "CANCELLED":
      return "text-bolder-danger bg-bolder-danger-surface border-bolder-danger-border";
    default:
      return "text-bolder-text bg-bolder-bg border-bolder-line";
  }
}
