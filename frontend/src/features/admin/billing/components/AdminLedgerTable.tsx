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
import { downloadAdminStatementCsv } from "../api";
import type { SettlementEntryResponse, SettlementEntryType } from "../types";

interface AdminLedgerTableProps {
  entries?: SettlementEntryResponse[];
  isLoading?: boolean;
  selectedType?: SettlementEntryType | "";
  onTypeChange?: (type: SettlementEntryType | "") => void;
  vendorIdFilter?: string;
  onVendorIdFilterChange?: (vendorId: string) => void;
  page?: number;
  totalPages?: number;
  totalElements?: number;
  onPageChange?: (newPage: number) => void;
}

const TYPE_CONFIG: Record<
  SettlementEntryType,
  { label: string; badgeClass: string; isCredit: boolean }
> = {
  ONLINE_ORDER_CREDIT: {
    label: "Online Order Credit",
    badgeClass: "bg-bolder-success-surface text-bolder-success",
    isCredit: true,
  },
  COD_DUES: {
    label: "COD Commission Due",
    badgeClass: "bg-bolder-warning-surface text-bolder-warning",
    isCredit: false,
  },
  DISPUTE_ADJUSTMENT: {
    label: "Dispute Adjustment",
    badgeClass: "bg-bolder-bg text-bolder-text",
    isCredit: false,
  },
  SETTLEMENT_PAYMENT: {
    label: "Settlement Payout",
    badgeClass: "bg-bolder-info-surface text-bolder-info",
    isCredit: true,
  },
};

export function AdminLedgerTable({
  entries = [],
  isLoading,
  selectedType = "",
  onTypeChange,
  vendorIdFilter = "",
  onVendorIdFilterChange,
  page = 0,
  totalPages = 1,
  totalElements = 0,
  onPageChange,
}: AdminLedgerTableProps) {
  const [statementVendorId, setStatementVendorId] = useState("");
  const [statementStart, setStatementStart] = useState(
    new Date(Date.now() - 30 * 86400000).toISOString().split("T")[0],
  );
  const [statementEnd, setStatementEnd] = useState(new Date().toISOString().split("T")[0]);
  const [isDownloading, setIsDownloading] = useState(false);
  const [downloadError, setDownloadError] = useState<string | null>(null);

  const handleDownloadStatement = async (e: React.FormEvent) => {
    e.preventDefault();
    const vId = parseInt(statementVendorId.trim(), 10);
    if (isNaN(vId) || vId <= 0) {
      setDownloadError("Please specify a valid Vendor ID.");
      return;
    }
    if (!statementStart || !statementEnd) {
      setDownloadError("Please specify start and end dates.");
      return;
    }

    try {
      setIsDownloading(true);
      setDownloadError(null);
      await downloadAdminStatementCsv(vId, statementStart, statementEnd);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "Failed to download statement.";
      setDownloadError(msg);
    } finally {
      setIsDownloading(false);
    }
  };

  return (
    <div
      data-testid="admin-ledger-table"
      className="overflow-hidden rounded-2xl border border-bolder-line bg-bolder-surface shadow-sm"
    >
      <div className="border-b border-bolder-line px-6 py-4">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h3 className="text-base font-bold text-bolder-text">
              All-Vendor Ledger Transactions
            </h3>
            <p className="mt-0.5 text-xs text-bolder-muted">
              Audit log of double-entry ledger rows generated across all marketplace orders and
              disputes.
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-3">
            <input
              type="number"
              placeholder="Filter by Vendor ID"
              value={vendorIdFilter}
              onChange={(e) => onVendorIdFilterChange?.(e.target.value)}
              className="w-36 rounded-lg border border-bolder-line-control px-2.5 py-1.5 text-xs font-medium text-bolder-text shadow-sm focus:border-bolder-rose focus:outline-none focus:ring-1 focus:ring-bolder-rose"
            />

            <select
              value={selectedType}
              onChange={(e) => onTypeChange?.(e.target.value as SettlementEntryType | "")}
              className="rounded-lg border border-bolder-line-control bg-bolder-surface px-3 py-1.5 text-xs font-medium text-bolder-text shadow-sm focus:border-bolder-rose focus:outline-none focus:ring-1 focus:ring-bolder-rose"
            >
              <option value="">All Types</option>
              <option value="ONLINE_ORDER_CREDIT">Online Order Credit</option>
              <option value="COD_DUES">COD Commission Due</option>
              <option value="DISPUTE_ADJUSTMENT">Dispute Adjustment</option>
              <option value="SETTLEMENT_PAYMENT">Settlement Payout</option>
            </select>
          </div>
        </div>

        {/* Quick Statement Download Strip */}
        <form
          onSubmit={handleDownloadStatement}
          className="mt-3 flex flex-wrap items-center gap-2 rounded-xl bg-bolder-bg p-2.5 text-xs"
        >
          <span className="font-semibold text-bolder-muted">Export Statement CSV:</span>
          <input
            type="number"
            placeholder="Vendor ID"
            value={statementVendorId}
            onChange={(e) => setStatementVendorId(e.target.value)}
            className="w-24 rounded border border-bolder-line-control px-2 py-1 text-xs"
            required
          />
          <input
            type="date"
            value={statementStart}
            onChange={(e) => setStatementStart(e.target.value)}
            className="rounded border border-bolder-line-control px-2 py-1 text-xs"
            required
          />
          <span className="text-bolder-muted">→</span>
          <input
            type="date"
            value={statementEnd}
            onChange={(e) => setStatementEnd(e.target.value)}
            className="rounded border border-bolder-line-control px-2 py-1 text-xs"
            required
          />
          <button
            type="submit"
            disabled={isDownloading}
            className="rounded bg-bolder-rose-action px-3 py-1 text-xs font-semibold text-white shadow-sm hover:bg-bolder-rose-deep disabled:opacity-50"
          >
            {isDownloading ? "Exporting..." : "Download CSV"}
          </button>
          {downloadError && <span className="text-xs text-bolder-danger">{downloadError}</span>}
        </form>
      </div>

      {isLoading ? (
        <div className="p-6 space-y-3">
          {[1, 2, 3, 4, 5].map((i) => (
            <div key={i} className="h-10 w-full animate-pulse rounded-lg bg-bolder-bg" />
          ))}
        </div>
      ) : entries.length === 0 ? (
        <div className="px-6 py-12 text-center">
          <p className="text-sm font-medium text-bolder-muted">No ledger transactions found.</p>
          <p className="mt-1 text-xs text-bolder-muted">
            {selectedType || vendorIdFilter
              ? "No transactions match the selected filters."
              : "Ledger transactions are created upon order delivery."}
          </p>
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="bg-bolder-bg text-xs uppercase tracking-wide text-bolder-muted">
              <tr>
                <th scope="col" className="px-6 py-3 font-semibold">Entry ID</th>
                <th scope="col" className="px-6 py-3 font-semibold">Vendor ID</th>
                <th scope="col" className="px-6 py-3 font-semibold">Date & Time</th>
                <th scope="col" className="px-6 py-3 font-semibold">Type</th>
                <th scope="col" className="px-6 py-3 font-semibold">Reference</th>
                <th scope="col" className="px-6 py-3 font-semibold">Order / Dispute</th>
                <th scope="col" className="px-6 py-3 font-semibold">Cycle</th>
                <th scope="col" className="px-6 py-3 font-semibold text-right">Signed Amount</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-bolder-line text-bolder-text">
              {entries.map((entry) => {
                const config = TYPE_CONFIG[entry.type] || {
                  label: entry.type,
                  badgeClass: "bg-bolder-bg text-bolder-text",
                  isCredit: entry.amount >= 0,
                };
                const isPositive = entry.amount > 0;
                const formattedDate = new Date(entry.createdAt).toLocaleString("en-IN", {
                  dateStyle: "medium",
                  timeStyle: "short",
                });

                return (
                  <tr key={entry.id} className="transition hover:bg-bolder-surface/80">
                    <td className="whitespace-nowrap px-6 py-3.5 text-xs font-mono font-semibold text-bolder-text">
                      #{entry.id}
                    </td>
                    <td className="whitespace-nowrap px-6 py-3.5 text-xs font-mono text-bolder-text">
                      Vendor #{entry.vendorId}
                    </td>
                    <td className="whitespace-nowrap px-6 py-3.5 text-xs text-bolder-muted">
                      {formattedDate}
                    </td>
                    <td className="whitespace-nowrap px-6 py-3.5">
                      <span
                        className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${config.badgeClass}`}
                      >
                        {config.label}
                      </span>
                    </td>
                    <td className="max-w-xs truncate px-6 py-3.5 text-xs text-bolder-text">
                      {entry.reference || "—"}
                    </td>
                    <td className="whitespace-nowrap px-6 py-3.5 text-xs text-bolder-text font-mono">
                      {entry.orderId ? `Order #${entry.orderId}` : entry.disputeId ? `Dispute #${entry.disputeId}` : "—"}
                    </td>
                    <td className="whitespace-nowrap px-6 py-3.5 text-xs text-bolder-text">
                      {entry.settlementId ? (
                        <span className="text-bolder-muted">Cycle #{entry.settlementId}</span>
                      ) : (
                        <span className="inline-flex items-center rounded-full bg-bolder-bg px-2 py-0.5 text-[11px] font-medium text-bolder-muted">
                          Open
                        </span>
                      )}
                    </td>
                    <td
                      className={`whitespace-nowrap px-6 py-3.5 text-right font-bold ${
                        isPositive ? "text-bolder-success" : "text-bolder-warning"
                      }`}
                    >
                      {isPositive ? "+" : "-"}₹{Math.abs(entry.amount).toFixed(2)}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {/* Pagination Bar */}
      {totalPages > 1 && (
        <div className="flex items-center justify-between border-t border-bolder-line bg-bolder-bg/50 px-6 py-3">
          <span className="text-xs text-bolder-muted">
            Showing Page <span className="font-semibold text-bolder-text">{page + 1}</span> of{" "}
            <span className="font-semibold text-bolder-text">{totalPages}</span> ({
              totalElements
            } total entries)
          </span>

          <div className="flex items-center gap-2">
            <button
              type="button"
              disabled={page <= 0}
              onClick={() => onPageChange?.(page - 1)}
              className="rounded-lg border border-bolder-line-control bg-bolder-surface px-3 py-1 text-xs font-medium text-bolder-text shadow-sm hover:bg-bolder-bg disabled:cursor-not-allowed disabled:opacity-50"
            >
              Previous
            </button>
            <button
              type="button"
              disabled={page >= totalPages - 1}
              onClick={() => onPageChange?.(page + 1)}
              className="rounded-lg border border-bolder-line-control bg-bolder-surface px-3 py-1 text-xs font-medium text-bolder-text shadow-sm hover:bg-bolder-bg disabled:cursor-not-allowed disabled:opacity-50"
            >
              Next
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
