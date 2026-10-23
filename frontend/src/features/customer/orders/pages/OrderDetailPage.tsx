/**
 * Customer order detail (plan Phase 6, task 6.6).
 *
 * Full read-only detail of one of the customer's own orders: a status
 * stepper plus the chronological timeline, the payment method and status,
 * the address snapshot and delivery slot, the item snapshots and the fee
 * breakdown. Nothing here changes status — every transition goes through
 * the backend state machine.
 */

import { useCallback } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { useOrder } from "@/features/customer/orders/queries";
import { usePolling } from "@/hooks/usePolling";
import type { CustomerOrderResponse } from "@/features/customer/orders/types";
import { isTerminalStatus, ORDER_STATUS_LABELS, PAYMENT_STATUS_LABELS } from "@/features/customer/orders/types";
import { OrderStatusStepper } from "@/features/customer/orders/components/OrderStatusStepper";
import { formatMoney } from "@/features/vendor/format";
import { formatDateTime } from "@/features/vendor/format";
import { CustomerErrorState } from "@/features/customer/components/CustomerErrorState";
import { PageHeading } from "@/features/vendor/components/StatCard";
import { FOCUS_RING, PRESSABLE } from "@/motion/pressable";

/** The amounts a customer agreed to pay at checkout are tax-inclusive (D-3). */
const TAX_INCLUSIVE = " (incl. taxes)";

function paymentStatusTone(status: CustomerOrderResponse["paymentStatus"]): string {
  switch (status) {
    case "COLLECTED":
    case "PAID":
      return "text-green-800 bg-green-50 border-green-200";
    case "PENDING":
      return "text-amber-800 bg-amber-50 border-amber-200";
    case "FAILED":
    case "REFUND_PENDING":
    case "REFUNDED":
      return "text-red-800 bg-red-50 border-red-200";
    default:
      return "text-slate-800 bg-slate-50 border-slate-200";
  }
}

export function OrderDetailPage() {
  const navigate = useNavigate();
  const { orderId } = useParams();
  const id = orderId ? Number(orderId) : NaN;

  const {
    data: order,
    isPending,
    error,
    refetch: refetchOrder,
  } = useOrder(id);

  // Refetch while the tab is visible, but stop once the order is terminal — a
  // delivered/cancelled/rejected order never changes again.
  usePolling(refetchOrder, {
    enabled: order != null && !isTerminalStatus(order.status),
  });

  const onBack = useCallback(() => {
    navigate("/orders");
  }, [navigate]);

  if (error) {
    return (
      <div className="max-w-4xl mx-auto px-4 py-16 text-center">
        <CustomerErrorState error={error} onRetry={() => void refetchOrder()} />
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

  if (isPending || !order) {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <div className="rounded-lg border border-slate-200 bg-white p-6 text-sm text-slate-600">
          Loading order…
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap justify-between items-end gap-4">
        <PageHeading title={`Order #${order.id}`} description={order.vendorName} />
        <Link
          to="/orders"
          className={`inline-flex items-center rounded-md border border-transparent bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-700 ${PRESSABLE} ${FOCUS_RING}`}
        >
          Back to your orders
        </Link>
      </div>

      {/* Status stepper */}
      <Card title="Order status">
        <OrderStatusStepper status={order.status} heading="Status" />
      </Card>

      {/* Payment */}
      <Card title="Payment">
        <dl className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div className="border-b border-glass-border-soft pb-2">
            <dt className="text-xs text-slate-500">Method</dt>
            <dd className="text-sm font-medium text-slate-900">
              {order.paymentMethod === "COD" ? "Cash on delivery" : "Online payment"}
            </dd>
          </div>
          <div className="border-b border-glass-border-soft pb-2">
            <dt className="text-xs text-slate-500">Status</dt>
            <dd
              className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium border ${paymentStatusTone(order.paymentStatus)}`}
            >
              {PAYMENT_STATUS_LABELS[order.paymentStatus]}
            </dd>
          </div>
        </dl>
      </Card>

      {/* Address and slot */}
      <Card title="Delivery">
        <dl className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div className="col-span-full border-b border-glass-border-soft pb-2">
            <dt className="text-xs text-slate-500">Address</dt>
            <dd className="text-sm text-slate-900">{order.deliveryAddressLine1}</dd>
            {order.deliveryAddressLine2 && (
              <dd className="text-sm text-slate-600">{order.deliveryAddressLine2}</dd>
            )}
          </div>
          <div className="border-b border-glass-border-soft pb-2">
            <dt className="text-xs text-slate-500">Delivery slot</dt>
            <dd className="text-sm text-slate-900">
              {order.slotStart && order.slotEnd ? (
                <>
                  {formatDateTime(order.slotStart)} – {formatDateTime(order.slotEnd)}
                </>
              ) : (
                <span className="text-slate-500">Not scheduled</span>
              )}
            </dd>
          </div>
        </dl>
      </Card>

      {/* Items */}
      <Card title="Items">
        <table className="w-full text-left text-sm divide-y divide-slate-100">
          <thead className="text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th scope="col" className="py-3 pr-4 font-medium">
                Item
              </th>
              <th scope="col" className="py-3 pr-4 font-medium text-right">
                Price
              </th>
              <th scope="col" className="py-3 pr-4 font-medium text-right">
                Qty
              </th>
              <th scope="col" className="py-3 font-medium text-right">
                Subtotal
              </th>
            </tr>
          </thead>
          <tbody>
            {order.items.map((item) => (
              <tr key={item.productName} className="align-top">
                <td className="py-3 pr-4">
                  <p className="font-medium text-slate-900">{item.productName}</p>
                  {item.note && (
                    <p className="mt-1 text-xs text-slate-500">{item.note}</p>
                  )}
                </td>
                <td className="py-3 pr-4 text-right text-slate-900">
                  {formatMoney(item.priceSnapshot)}
                </td>
                <td className="py-3 pr-4 text-right text-slate-900">{item.quantity}</td>
                <td className="py-3 font-medium text-right text-slate-900">
                  {formatMoney(item.subtotal)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </Card>

      {/* Fee breakdown */}
      <Card title="Fees">
        <dl className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div className="col-span-full border-b border-glass-border-soft pb-2">
            <dt className="text-xs text-slate-500">Subtotal</dt>
            <dd className="text-sm font-medium text-slate-900 text-right">
              {formatMoney(order.subtotal)}{TAX_INCLUSIVE}
            </dd>
          </div>
          <div className="border-b border-glass-border-soft pb-2">
            <dt className="text-xs text-slate-500">Delivery fee</dt>
            <dd className="text-sm text-slate-900 text-right">
              {formatMoney(order.deliveryFee)}{TAX_INCLUSIVE}
            </dd>
          </div>
          <div className="border-b border-glass-border-soft pb-2">
            <dt className="text-xs text-slate-500">Platform fee</dt>
            <dd className="text-sm text-slate-900 text-right">
              {formatMoney(order.platformFee)}{TAX_INCLUSIVE}
            </dd>
          </div>
          <div className="col-span-full pt-2 border-t border-slate-200">
            <dt className="text-xs text-slate-500">Total</dt>
            <dd className="text-sm font-bold text-slate-900 text-right">
              {formatMoney(order.total)}{TAX_INCLUSIVE}
            </dd>
          </div>
        </dl>
      </Card>

      {/* Timeline */}
      <Card title="Status timeline">
        {order.timeline.length === 0 ? (
          <p className="text-sm text-slate-600">No status changes yet.</p>
        ) : (
          <ul className="space-y-3">
            {order.timeline.map((entry, i) => (
              <li key={i} className="flex gap-3 py-2 border-b border-slate-100 last:border-0">
                <span className="shrink-0 w-6 h-6 rounded-full flex items-center justify-center text-[10px] font-bold text-slate-900 border border-slate-200">
                  {i + 1}
                </span>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-slate-900">
                    {entry.fromStatus == null
                      ? "Order created"
                      : `${ORDER_STATUS_LABELS[entry.fromStatus]} → ${ORDER_STATUS_LABELS[entry.toStatus]}`}
                  </p>
                  <p className="text-xs text-slate-600">
                    {entry.actorType}
                    {entry.actorName ? ` — ${entry.actorName}` : ""}
                    {entry.reason ? ` · ${entry.reason}` : ""}
                    {` · ${entry.createdAt}`}
                  </p>
                </div>
              </li>
            ))}
          </ul>
        )}
      </Card>

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

function Card({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
      <h2 className="text-sm font-semibold text-slate-900 mb-3">{title}</h2>
      <div className="prose-sm max-w-none">{children}</div>
    </div>
  );
}
