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

import type { SettlementResponse, SettlementStatus } from "../types";

interface AdminSettlementTableProps {
  settlements?: SettlementResponse[];
  isLoading?: boolean;
  selectedStatus?: SettlementStatus | "";
  onStatusChange?: (status: SettlementStatus | "") => void;
  vendorIdFilter?: string;
  onVendorIdFilterChange?: (vendorId: string) => void;
  page?: number;
  totalPages?: number;
  totalElements?: number;
  onPageChange?: (newPage: number) => void;
  onOpenRunModal: () => void;
  onOpenFinalizeModal: (settlement: SettlementResponse) => void;
  onSelectSettlement: (settlementId: number) => void;
}

export function AdminSettlementTable({
  settlements = [],
  isLoading,
  selectedStatus = "",
  onStatusChange,
  vendorIdFilter = "",
  onVendorIdFilterChange,
  page = 0,
  totalPages = 1,
  totalElements = 0,
  onPageChange,
  onOpenRunModal,
  onOpenFinalizeModal,
  onSelectSettlement,
}: AdminSettlementTableProps) {
  return (
    <div
      data-testid="admin-settlement-table"
      className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm"
    >
      <div className="border-b border-slate-100 px-6 py-4">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h3 className="text-base font-bold text-slate-900">Weekly Settlement Cycles</h3>
            <p className="mt-0.5 text-xs text-slate-500">
              Manage weekly netting settlements across all marketplace vendors.
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-3">
            <input
              type="number"
              placeholder="Filter by Vendor ID"
              value={vendorIdFilter}
              onChange={(e) => onVendorIdFilterChange?.(e.target.value)}
              className="w-36 rounded-lg border border-slate-300 px-2.5 py-1.5 text-xs font-medium text-slate-700 shadow-sm focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
            />

            <select
              value={selectedStatus}
              onChange={(e) => onStatusChange?.(e.target.value as SettlementStatus | "")}
              className="rounded-lg border border-slate-300 bg-white px-3 py-1.5 text-xs font-medium text-slate-700 shadow-sm focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
            >
              <option value="">All Statuses</option>
              <option value="PENDING">PENDING</option>
              <option value="SETTLED">SETTLED</option>
            </select>

            <button
              type="button"
              onClick={onOpenRunModal}
              className="rounded-lg bg-brand-600 px-3.5 py-1.5 text-xs font-semibold text-white shadow-sm hover:bg-brand-700 focus:outline-none focus:ring-2 focus:ring-brand-500"
            >
              + Run Settlement
            </button>
          </div>
        </div>
      </div>

      {isLoading ? (
        <div className="p-6 space-y-3">
          {[1, 2, 3, 4].map((i) => (
            <div key={i} className="h-10 w-full animate-pulse rounded-lg bg-slate-100" />
          ))}
        </div>
      ) : settlements.length === 0 ? (
        <div className="px-6 py-12 text-center">
          <p className="text-sm font-medium text-slate-600">No settlement cycles found.</p>
          <p className="mt-1 text-xs text-slate-400">
            Use the "Run Settlement" button to generate a cycle for a specific vendor or across all vendors.
          </p>
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="bg-slate-50 text-xs uppercase tracking-wider text-slate-500">
              <tr>
                <th scope="col" className="px-6 py-3 font-semibold">Cycle</th>
                <th scope="col" className="px-6 py-3 font-semibold">Vendor</th>
                <th scope="col" className="px-6 py-3 font-semibold">Period</th>
                <th scope="col" className="px-6 py-3 font-semibold">Credits</th>
                <th scope="col" className="px-6 py-3 font-semibold">Dues</th>
                <th scope="col" className="px-6 py-3 font-semibold">Net Payout</th>
                <th scope="col" className="px-6 py-3 font-semibold">Status</th>
                <th scope="col" className="px-6 py-3 font-semibold">Reference Note</th>
                <th scope="col" className="px-6 py-3 font-semibold text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 text-slate-700">
              {settlements.map((s) => {
                const isSettled = s.status === "SETTLED";
                return (
                  <tr key={s.id} className="transition hover:bg-slate-50/80">
                    <td className="whitespace-nowrap px-6 py-4 font-mono text-xs font-semibold text-slate-900">
                      #{s.id}
                    </td>
                    <td className="whitespace-nowrap px-6 py-4">
                      <div className="text-xs font-semibold text-slate-900">
                        {s.vendorBusinessName || `Vendor #${s.vendorId}`}
                      </div>
                      <div className="text-[11px] text-slate-400 font-mono">ID: {s.vendorId}</div>
                    </td>
                    <td className="whitespace-nowrap px-6 py-4 text-xs font-medium text-slate-700">
                      {s.periodStart} → {s.periodEnd}
                    </td>
                    <td className="whitespace-nowrap px-6 py-4 text-xs text-emerald-600 font-medium">
                      +₹{s.totalCredits.toFixed(2)}
                    </td>
                    <td className="whitespace-nowrap px-6 py-4 text-xs text-amber-600 font-medium">
                      -₹{s.totalDues.toFixed(2)}
                    </td>
                    <td className="whitespace-nowrap px-6 py-4 text-xs font-bold text-slate-900">
                      ₹{Math.abs(s.netAmount).toFixed(2)}
                    </td>
                    <td className="whitespace-nowrap px-6 py-4">
                      <span
                        className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold ${
                          isSettled
                            ? "bg-emerald-100 text-emerald-800"
                            : "bg-amber-100 text-amber-800"
                        }`}
                      >
                        {s.status}
                      </span>
                    </td>
                    <td className="max-w-xs truncate px-6 py-4 text-xs text-slate-500 font-mono">
                      {s.adminReferenceNote || "—"}
                    </td>
                    <td className="whitespace-nowrap px-6 py-4 text-right text-xs">
                      <div className="flex items-center justify-end gap-3">
                        <button
                          type="button"
                          onClick={() => onSelectSettlement(s.id)}
                          className="font-medium text-brand-600 hover:text-brand-800 focus:outline-none"
                        >
                          Entries
                        </button>
                        {!isSettled && (
                          <button
                            type="button"
                            onClick={() => onOpenFinalizeModal(s)}
                            className="rounded bg-emerald-600 px-2.5 py-1 text-xs font-semibold text-white shadow-sm hover:bg-emerald-700 focus:outline-none"
                          >
                            Finalize
                          </button>
                        )}
                      </div>
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
            <span className="font-semibold text-slate-800">{totalPages}</span> ({totalElements} total settlements)
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
