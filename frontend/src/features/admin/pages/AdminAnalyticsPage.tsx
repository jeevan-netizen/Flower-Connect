/**
 * Admin analytics page (plan Phase 12, task 12.4).
 *
 * Platform-wide sales and platform-revenue analytics for a configurable date
 * range (default last 30 days, maximum 365 days). POS revenue (Phase 11) is
 * commission-free and outside the marketplace ledger (D-66): it is excluded
 * from all marketplace counts, sales, and repeat-customer metrics.
 *
 * All numbers are rendered in an accessible data table; an SVG bar chart
 * visualises the daily trend series as a progressive enhancement. No charting
 * dependency is added.
 *
 * Requires `ROLE_ADMIN`; `SecurityConfig` matches `/api/v1/admin/analytics/**`
 * with `hasRole("ADMIN")`.
 */
import { useState } from "react";
import { formatMoney } from "@/features/vendor/format";
import {
  useAnalyticsDailyTrends,
  useAnalyticsOverview,
} from "@/features/review/queries";
import { CustomerErrorState } from "@/features/customer/components/CustomerErrorState";
import { PageHeading } from "@/features/vendor/components/StatCard";
import type { AnalyticsDailyTrend } from "@/features/review/types";

const DEFAULT_RANGE_DAYS = 30;
const MAX_RANGE_DAYS = 365;

function addDays(date: Date, days: number): Date {
  const result = new Date(date);
  result.setDate(result.getDate() + days);
  return result;
}

function formatDateInput(date: Date): string {
  return date.toISOString().split("T")[0];
}

function formatDisplayDate(isoDate: string): string {
  return new Date(isoDate + "T00:00:00").toLocaleDateString(undefined, {
    month: "short",
    day: "numeric",
  });
}

interface MetricCardProps {
  label: string;
  value: string | number;
  sublabel?: string;
}

/** KPI card for an overview metric. */
function MetricCard({ label, value, sublabel }: MetricCardProps) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
      <p className="text-xs uppercase tracking-wide text-slate-500">{label}</p>
      <p className="mt-1 text-xl font-bold text-slate-900">{value}</p>
      {sublabel && <p className="mt-0.5 text-xs text-slate-500">{sublabel}</p>}
    </div>
  );
}

/**
 * Lightweight SVG bar chart for the daily trend series. Falls back gracefully:
 * the data table below is always rendered and is the authoritative source.
 */
function DailyTrendChart({ trends }: { trends: AnalyticsDailyTrend[] }) {
  if (trends.length === 0) {
    return null;
  }

  const maxRevenue = Math.max(...trends.map((t) => Number(t.revenue)));
  const height = 160;
  const barWidth = 14;

  return (
    <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
      <h2 className="text-base font-semibold text-slate-900 mb-4">
        Daily delivered orders
      </h2>
      <div className="flex items-end gap-1 h-[160px] pb-4">
        {trends.map((trend, idx) => {
          const pct = maxRevenue > 0 ? Number(trend.revenue) / maxRevenue : 0;
          const barHeight = Math.max(4, pct * (height - 20));
          return (
            <div key={idx} className="flex flex-col items-center gap-1">
              <svg
                width={barWidth}
                height={barHeight}
                className="text-brand-600"
              >
                <rect width={barWidth} height={barHeight} rx={2} className="fill-current" />
              </svg>
              <span className="text-[10px] text-slate-500">
                {formatDisplayDate(trend.date)}
              </span>
            </div>
          );
        })}
      </div>
      <div className="mt-2 flex justify-between text-[10px] text-slate-500">
        <span>Orders per day</span>
        <span>UTC buckets</span>
      </div>
    </div>
  );
}

/** Data table for the daily trend series (always rendered, accessibility fallback). */
function DailyTrendTable({ trends }: { trends: AnalyticsDailyTrend[] }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm mt-6">
      <h2 className="text-base font-semibold text-slate-900 mb-3">
        Daily trend detail
      </h2>
      <table className="w-full text-left text-sm divide-y divide-slate-100">
        <thead className="text-xs uppercase tracking-wide text-slate-500">
          <tr>
            <th scope="col" className="py-3 pr-4 font-medium">
              Date
            </th>
            <th scope="col" className="py-3 pr-4 font-medium text-right">
              Orders
            </th>
            <th scope="col" className="py-3 font-medium text-right">
              Revenue
            </th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-50">
          {trends.map((trend) => (
            <tr key={trend.date} className="align-top">
              <td className="py-3 pr-4 text-slate-900">
                {formatDisplayDate(trend.date)}
              </td>
              <td className="py-3 pr-4 text-right text-slate-900">
                {trend.orderCount}
              </td>
              <td className="py-3 font-medium text-right text-slate-900">
                {formatMoney(trend.revenue)}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export function AdminAnalyticsPage() {
  const [fromDate, setFromDate] = useState<string>("");
  const [toDate, setToDate] = useState<string>("");

  const overview = useAnalyticsOverview(fromDate || undefined, toDate || undefined);
  const trends = useAnalyticsDailyTrends(fromDate || undefined, toDate || undefined);

  if (overview.isError || trends.isError) {
    return (
      <CustomerErrorState
        error={overview.error ?? trends.error}
        onRetry={() => {
          void overview.refetch();
          void trends.refetch();
        }}
      />
    );
  }

  const data = overview.data;
  const trendData = trends.data ?? [];

  const defaultTo = new Date();
  const defaultFrom = addDays(defaultTo, -(DEFAULT_RANGE_DAYS - 1));
  const minDate = addDays(defaultTo, -(MAX_RANGE_DAYS - 1));

  return (
    <div className="space-y-6">
      <PageHeading
        title="Analytics"
        description={
          "Platform-wide sales and platform revenue. POS revenue (Phase 11) is " +
          "commission-free and outside the marketplace ledger, so it is excluded " +
          "from all marketplace metrics."
        }
      />

      {/* Date range */}
      <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm flex flex-wrap items-center gap-4">
        <label className="text-xs font-medium text-slate-700">Date range</label>
        <div className="flex items-center gap-2">
          <input
            type="date"
            value={fromDate}
            onChange={(e) => setFromDate(e.target.value)}
            min={formatDateInput(minDate)}
            max={formatDateInput(defaultTo)}
            className="rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-brand-500"
          />
          <span className="text-slate-500">to</span>
          <input
            type="date"
            value={toDate}
            onChange={(e) => setToDate(e.target.value)}
            min={formatDateInput(minDate)}
            max={formatDateInput(defaultTo)}
            className="rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-brand-500"
          />
        </div>
        <button
          onClick={() => {
            setFromDate(formatDateInput(defaultFrom));
            setToDate(formatDateInput(defaultTo));
          }}
          className="text-xs font-medium rounded-md border border-slate-300 bg-white px-3 py-1.5 text-slate-700 hover:bg-slate-100"
        >
          Reset to 30 days
        </button>
        {fromDate || toDate ? (
          <button
            onClick={() => {
              setFromDate("");
              setToDate("");
            }}
            className="text-xs font-medium rounded-md border border-slate-300 bg-white px-3 py-1.5 text-slate-700 hover:bg-slate-100"
          >
            Clear range
          </button>
        ) : null}
      </div>

      {/* Overview KPI cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        <MetricCard
          label="Delivered orders"
          value={data?.totalDeliveredOrders ?? 0}
          sublabel="in selected range"
        />
        <MetricCard
          label="Marketplace sales"
          value={formatMoney(data?.marketplaceSales ?? 0)}
          sublabel="sum of order totals"
        />
        <MetricCard
          label="Platform revenue"
          value={formatMoney(data?.platformRevenue ?? 0)}
          sublabel="commission + platform fees"
        />
        <MetricCard
          label="Active vendors"
          value={data?.activeVendors ?? 0}
          sublabel="approved & accepting"
        />
        <MetricCard
          label="Active customers"
          value={data?.activeCustomers ?? 0}
          sublabel="≥1 delivered order"
        />
        <MetricCard
          label="Dispute rate"
          value={
            data?.disputeRate === null
              ? "—"
              : (Number(data?.disputeRate ?? 0) * 100).toFixed(1) + "%"
          }
          sublabel="disputed / delivered orders"
        />
      </div>

      {/* Top vendors */}
      <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
        <h2 className="text-base font-semibold text-slate-900 mb-4">
          Top vendors by marketplace sales
        </h2>
        {data?.topVendors?.length === 0 ? (
          <p className="text-sm text-slate-600">
            No vendors with delivered orders in range.
          </p>
        ) : (
          <table className="w-full text-left text-sm divide-y divide-slate-100">
            <thead className="text-xs uppercase tracking-wide text-slate-500">
              <tr>
                <th scope="col" className="py-3 pr-4 font-medium">
                  Vendor
                </th>
                <th scope="col" className="py-3 pr-4 font-medium text-right">
                  Sales
                </th>
                <th scope="col" className="py-3 font-medium text-right">
                  Orders
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-50">
              {data?.topVendors?.map((vendor) => (
                <tr key={vendor.vendorId} className="align-top">
                  <td className="py-3 pr-4 text-slate-900">
                    {vendor.businessName}
                  </td>
                  <td className="py-3 pr-4 text-right text-slate-900">
                    {formatMoney(vendor.marketplaceSales)}
                  </td>
                  <td className="py-3 font-medium text-right text-slate-900">
                    {vendor.deliveredOrders}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {/* Daily trends: chart + table */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <DailyTrendChart trends={trendData} />
        <DailyTrendTable trends={trendData} />
      </div>
    </div>
  );
}
