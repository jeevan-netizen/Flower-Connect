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

import type { SettlementEntryResponse, SettlementEntryType } from "../types";

interface LedgerTransactionTableProps {
  entries?: SettlementEntryResponse[];
  isLoading?: boolean;
  page?: number;
  totalPages?: number;
  totalElements?: number;
  selectedType?: SettlementEntryType | "";
  onTypeChange?: (type: SettlementEntryType | "") => void;
  onPageChange?: (newPage: number) => void;
}

const TYPE_CONFIG: Record<
  SettlementEntryType,
  { label: string; badgeClass: string; isCredit: boolean }
> = {
  ONLINE_ORDER_CREDIT: {
    label: "Online Order Credit",
    badgeClass: "bg-emerald-100 text-emerald-800",
    isCredit: true,
  },
  COD_DUES: {
    label: "COD Commission Due",
    badgeClass: "bg-amber-100 text-amber-800",
    isCredit: false,
  },
  DISPUTE_ADJUSTMENT: {
    label: "Dispute Adjustment",
    badgeClass: "bg-purple-100 text-purple-800",
    isCredit: false,
  },
  SETTLEMENT_PAYMENT: {
    label: "Settlement Payout",
    badgeClass: "bg-blue-100 text-blue-800",
    isCredit: true,
  },
};

export function LedgerTransactionTable({
  entries = [],
  isLoading,
  page = 0,
  totalPages = 1,
  totalElements = 0,
  selectedType = "",
  onTypeChange,
  onPageChange,
}: LedgerTransactionTableProps) {
  return (
    <div
      data-testid="ledger-transaction-table"
      className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm"
    >
      <div className="border-b border-slate-100 px-6 py-4">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h3 className="text-base font-bold text-slate-900">Ledger Transactions</h3>
            <p className="mt-0.5 text-xs text-slate-500">
              Immutable double-entry log of all delivery credits, commission dues, and adjustments.
            </p>
          </div>

          <div className="flex items-center gap-3">
            <label htmlFor="filter-entry-type" className="text-xs font-medium text-slate-600">
              Filter by type:
            </label>
            <select
              id="filter-entry-type"
              value={selectedType}
              onChange={(e) => onTypeChange?.(e.target.value as SettlementEntryType | "")}
              className="rounded-lg border border-slate-300 bg-white px-3 py-1.5 text-xs font-medium text-slate-700 shadow-sm focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
            >
              <option value="">All Transactions</option>
              <option value="ONLINE_ORDER_CREDIT">Online Order Credit</option>
              <option value="COD_DUES">COD Commission Due</option>
              <option value="DISPUTE_ADJUSTMENT">Dispute Adjustment</option>
              <option value="SETTLEMENT_PAYMENT">Settlement Payout</option>
            </select>
          </div>
        </div>
      </div>

      {isLoading ? (
        <div className="p-6 space-y-3">
          {[1, 2, 3, 4, 5].map((i) => (
            <div key={i} className="h-10 w-full animate-pulse rounded-lg bg-slate-100" />
          ))}
        </div>
      ) : entries.length === 0 ? (
        <div className="px-6 py-12 text-center">
          <p className="text-sm font-medium text-slate-600">No ledger transactions found.</p>
          <p className="mt-1 text-xs text-slate-400">
            {selectedType
              ? "No entries match the selected filter."
              : "Transactions appear automatically as orders are delivered and completed."}
          </p>
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="bg-slate-50 text-xs uppercase tracking-wider text-slate-500">
              <tr>
                <th scope="col" className="px-6 py-3 font-semibold">Date & Time</th>
                <th scope="col" className="px-6 py-3 font-semibold">Type</th>
                <th scope="col" className="px-6 py-3 font-semibold">Reference</th>
                <th scope="col" className="px-6 py-3 font-semibold">Order / Dispute</th>
                <th scope="col" className="px-6 py-3 font-semibold">Cycle</th>
                <th scope="col" className="px-6 py-3 font-semibold text-right">Signed Amount</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 text-slate-700">
              {entries.map((entry) => {
                const config = TYPE_CONFIG[entry.type] || {
                  label: entry.type,
                  badgeClass: "bg-slate-100 text-slate-700",
                  isCredit: entry.amount >= 0,
                };
                const isPositive = entry.amount > 0;
                const formattedAmount = Math.abs(entry.amount).toFixed(2);
                const formattedDate = new Date(entry.createdAt).toLocaleString("en-IN", {
                  dateStyle: "medium",
                  timeStyle: "short",
                });

                return (
                  <tr key={entry.id} className="transition hover:bg-slate-50/80">
                    <td className="whitespace-nowrap px-6 py-3.5 text-xs text-slate-600">
                      {formattedDate}
                    </td>
                    <td className="whitespace-nowrap px-6 py-3.5">
                      <span
                        className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${config.badgeClass}`}
                      >
                        {config.label}
                      </span>
                    </td>
                    <td className="max-w-xs truncate px-6 py-3.5 text-xs font-mono text-slate-700">
                      {entry.reference || "—"}
                    </td>
                    <td className="whitespace-nowrap px-6 py-3.5 text-xs text-slate-600">
                      {entry.orderId ? (
                        <span className="font-mono">Order #{entry.orderId}</span>
                      ) : entry.disputeId ? (
                        <span className="font-mono">Dispute #{entry.disputeId}</span>
                      ) : (
                        "—"
                      )}
                    </td>
                    <td className="whitespace-nowrap px-6 py-3.5 text-xs">
                      {entry.settlementId ? (
                        <span className="font-mono text-slate-600">Cycle #{entry.settlementId}</span>
                      ) : (
                        <span className="inline-flex items-center rounded-full bg-slate-100 px-2 py-0.5 text-[11px] font-medium text-slate-600">
                          Open
                        </span>
                      )}
                    </td>
                    <td
                      className={`whitespace-nowrap px-6 py-3.5 text-right font-bold ${
                        isPositive ? "text-emerald-600" : "text-amber-600"
                      }`}
                    >
                      {isPositive ? "+" : "-"}₹{formattedAmount}
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
        <div className="flex items-center justify-between border-t border-slate-100 bg-slate-50/50 px-6 py-3">
          <span className="text-xs text-slate-500">
            Showing Page <span className="font-semibold text-slate-800">{page + 1}</span> of{" "}
            <span className="font-semibold text-slate-800">{totalPages}</span> ({totalElements} total entries)
          </span>

          <div className="flex items-center gap-2">
            <button
              type="button"
              disabled={page <= 0}
              onClick={() => onPageChange?.(page - 1)}
              className="rounded-lg border border-slate-300 bg-white px-3 py-1 text-xs font-medium text-slate-700 shadow-sm hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50"
            >
              Previous
            </button>
            <button
              type="button"
              disabled={page >= totalPages - 1}
              onClick={() => onPageChange?.(page + 1)}
              className="rounded-lg border border-slate-300 bg-white px-3 py-1 text-xs font-medium text-slate-700 shadow-sm hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50"
            >
              Next
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
