/*
 * Copyright 2026 FlowerConnect
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { useState } from "react";
import { PageHeading } from "@/features/vendor/components/StatCard";
import { VendorErrorState } from "@/features/vendor/components/VendorErrorState";
import {
  useVendorAnalyticsOverview,
  useVendorDailyTrends,
} from "../queries";

const DEFAULT_RANGE_DAYS = 30;

function defaultRange(): { from: string; to: string } {
  const to = new Date();
  const from = new Date(to);
  from.setDate(to.getDate() - (DEFAULT_RANGE_DAYS - 1));
  return {
    from: from.toISOString().slice(0, 10),
    to: to.toISOString().slice(0, 10),
  };
}

function currency(value: number | null): string {
  return new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(value ?? 0);
}

export function VendorAnalyticsPage() {
  const [fromDate, setFromDate] = useState(defaultRange().from);
  const [toDate, setToDate] = useState(defaultRange().to);

  const {
    data: overview,
    error: overviewError,
    refetch: refetchOverview,
  } = useVendorAnalyticsOverview(fromDate, toDate);

  const {
    data: trends,
    error: trendsError,
  } = useVendorDailyTrends(fromDate, toDate);

  const hasFatalError = Boolean(overviewError || trendsError);

  if (hasFatalError) {
    return (
      <VendorErrorState
        error={overviewError ?? trendsError}
        onRetry={() => {
          void refetchOverview();
        }}
      />
    );
  }

  const marketplaceRevenue = overview?.marketplaceRevenue ?? 0;
  const codRevenue = overview?.codRevenue ?? 0;
  const posRevenue = overview?.pos.totalRevenue ?? 0;

  return (
    <div className="space-y-8" data-testid="vendor-analytics-page">
      <PageHeading
        title="Analytics"
        description={
          "Marketplace and point-of-sale performance for the selected date range. " +
          "POS revenue is commission-free and reported separately; it is never mixed into marketplace figures."
        }
      />

      {/* Date range picker */}
      <div className="flex flex-col sm:flex-row sm:items-end gap-4">
        <div className="flex-1">
          <label className="block text-xs font-medium text-slate-600 mb-1">
            From
          </label>
          <input
            type="date"
            value={fromDate}
            onChange={(e) => setFromDate(e.target.value)}
            max={toDate}
            className="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm focus:ring-2 focus:ring-brand-500"
          />
        </div>
        <div className="flex-1">
          <label className="block text-xs font-medium text-slate-600 mb-1">
            To
          </label>
          <input
            type="date"
            value={toDate}
            onChange={(e) => setToDate(e.target.value)}
            min={fromDate}
            className="w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm focus:ring-2 focus:ring-brand-500"
          />
        </div>
        <button
          type="button"
          onClick={() => {
            const d = defaultRange();
            setFromDate(d.from);
            setToDate(d.to);
          }}
          className="rounded-md border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-100"
        >
          Reset to 30 days
        </button>
      </div>

      {/* Marketplace summary cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
          <p className="text-xs font-medium text-slate-500">Delivered orders</p>
          <p className="text-2xl font-bold text-slate-900">{overview?.deliveredOrders ?? 0}</p>
        </div>
        <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
          <p className="text-xs font-medium text-slate-500">Marketplace revenue</p>
          <p className="text-2xl font-bold text-slate-900">{currency(marketplaceRevenue)}</p>
        </div>
        <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
          <p className="text-xs font-medium text-slate-500">Online orders</p>
          <p className="text-2xl font-bold text-slate-900">{overview?.onlineOrders ?? 0}</p>
        </div>
        <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
          <p className="text-xs font-medium text-slate-500">COD orders</p>
          <p className="text-2xl font-bold text-slate-900">{overview?.codOrders ?? 0}</p>
        </div>
      </div>

      {/* Revenue breakdown */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
          <p className="text-xs font-medium text-slate-500">Online revenue</p>
          <p className="text-2xl font-bold text-slate-900">{currency(overview?.onlineRevenue ?? 0)}</p>
          <p className="text-xs text-slate-500 mt-1">{overview?.codOrders ?? 0} COD order{(overview?.codOrders ?? 0) === 1 ? "" : "s"}</p>
        </div>
        <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
          <p className="text-xs font-medium text-slate-500">COD revenue</p>
          <p className="text-2xl font-bold text-slate-900">{currency(codRevenue)}</p>
          <p className="text-xs text-slate-500 mt-1">{overview?.codOrders ?? 0} collected{(overview?.codOrders ?? 0) === 1 ? "" : "s"}</p>
        </div>
        <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
          <p className="text-xs font-medium text-slate-500">Cancelled revenue</p>
          <p className="text-2xl font-bold text-slate-900">{currency(overview?.cancelledRevenue ?? 0)}</p>
          <p className="text-xs text-slate-500 mt-1">{overview?.cancelledOrders ?? 0} cancelled{(overview?.cancelledOrders ?? 0) === 1 ? "" : "d"}</p>
        </div>
      </div>

      {/* POS section (commission-free) */}
      <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-sm font-bold text-slate-900">Point of sale (commission-free)</h3>
          <p className="text-xs text-slate-500">
            Reported separately from marketplace revenue; no orders, payments, commissions or ledger rows are created.
          </p>
        </div>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div className="rounded-lg border border-slate-200 bg-white p-4">
            <p className="text-xs font-medium text-slate-500">POS transactions</p>
            <p className="text-2xl font-bold text-slate-900">{overview?.pos.transactionCount ?? 0}</p>
          </div>
          <div className="rounded-lg border border-slate-200 bg-white p-4">
            <p className="text-xs font-medium text-slate-500">POS revenue</p>
            <p className="text-2xl font-bold text-slate-900">{currency(posRevenue)}</p>
          </div>
          <div className="rounded-lg border border-slate-200 bg-white p-4">
            <p className="text-xs font-medium text-slate-500">Payment methods</p>
            <p className="text-2xl font-bold text-slate-900">
              {overview?.pos.breakdownByPaymentMethod.length ?? 0}
            </p>
          </div>
        </div>

        {(overview?.pos?.breakdownByPaymentMethod?.length ?? 0) > 0 && (
          <div className="mt-4">
            <table className="w-full text-left text-sm">
              <thead>
                <tr className="border-b border-slate-200 text-slate-500">
                  <th className="pb-2 font-medium">Payment method</th>
                  <th className="pb-2 font-medium text-right">Transactions</th>
                  <th className="pb-2 font-medium text-right">Revenue</th>
                </tr>
              </thead>
              <tbody>
                {overview?.pos.breakdownByPaymentMethod.map((b) => (
                  <tr key={b.paymentMethod} className="border-b border-slate-100">
                    <td className="py-2">{b.paymentMethod}</td>
                    <td className="py-2 text-right">{b.transactionCount}</td>
                    <td className="py-2 text-right">{currency(b.revenue)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Top products */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        <TopProductsTable
          title="Top products by units"
          products={overview?.topProductsByUnits ?? []}
        />
        <TopProductsTable
          title="Top products by revenue"
          products={overview?.topProductsByRevenue ?? []}
        />
      </div>

      {/* Repeat customer rate */}
      <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
        <div className="flex items-center justify-between">
          <div>
            <p className="text-xs font-medium text-slate-500">Repeat customer rate</p>
            <p className="text-2xl font-bold text-slate-900">
              {overview?.repeatCustomerRate === null
                ? "—"
                : ((overview?.repeatCustomerRate ?? 0) * 100).toFixed(1) + "%"}
            </p>
          </div>
          <p className="text-xs text-slate-500">
            Customers with more than one delivered order ÷ all delivered-order customers in the range.
          </p>
        </div>
      </div>

      {/* Daily trends */}
      <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <h3 className="text-sm font-bold text-slate-900 mb-4">Daily delivered orders</h3>
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead>
              <tr className="border-b border-slate-200 text-slate-500">
                <th className="pb-2 font-medium">Date</th>
                <th className="pb-2 font-medium text-right">Orders</th>
                <th className="pb-2 font-medium text-right">Revenue</th>
              </tr>
            </thead>
            <tbody>
              {(trends ?? []).map((t) => (
                <tr key={t.date} className="border-b border-slate-100">
                  <td className="py-2">{t.date}</td>
                  <td className="py-2 text-right">{t.orderCount}</td>
                  <td className="py-2 text-right">{currency(t.revenue)}</td>
                </tr>
              ))}
              {trends?.length === 0 && (
                <tr>
                  <td colSpan={3} className="py-4 text-center text-slate-500">
                    No delivered orders in this range.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

function TopProductsTable({
  title,
  products,
}: {
  title: string;
  products: { productId: number; productName: string; quantitySold: number; revenue: number }[];
}) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
      <h3 className="text-sm font-bold text-slate-900 mb-3">{title}</h3>
      {products.length === 0 ? (
        <p className="text-xs text-slate-500">No products sold in this range.</p>
      ) : (
        <table className="w-full text-left text-sm">
          <thead>
            <tr className="border-b border-slate-200 text-slate-500">
              <th className="pb-2 font-medium">Product</th>
              <th className="pb-2 font-medium text-right">Units</th>
              <th className="pb-2 font-medium text-right">Revenue</th>
            </tr>
          </thead>
          <tbody>
            {products.map((p) => (
              <tr key={p.productId} className="border-b border-slate-100">
                <td className="py-2">{p.productName}</td>
                <td className="py-2 text-right">{p.quantitySold}</td>
                <td className="py-2 text-right">{currency(p.revenue)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
