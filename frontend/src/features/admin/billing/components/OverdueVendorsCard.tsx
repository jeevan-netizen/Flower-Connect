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

import type { OverdueVendorResponse } from "../types";

interface OverdueVendorsCardProps {
  overdueVendors?: OverdueVendorResponse[];
  isLoading?: boolean;
  onFilterVendor?: (vendorId: number) => void;
}

export function OverdueVendorsCard({
  overdueVendors = [],
  isLoading,
  onFilterVendor,
}: OverdueVendorsCardProps) {
  return (
    <div
      data-testid="overdue-vendors-card"
      className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm"
    >
      <div className="flex flex-wrap items-center justify-between gap-4 border-b border-slate-100 pb-4">
        <div>
          <div className="flex items-center gap-2">
            <h2 className="text-base font-semibold text-slate-900">Overdue Dues Notice</h2>
            {overdueVendors.length > 0 && (
              <span className="inline-flex items-center rounded-full bg-rose-100 px-2.5 py-0.5 text-xs font-bold text-rose-800">
                {overdueVendors.length} Overdue
              </span>
            )}
          </div>
          <p className="mt-0.5 text-xs text-slate-500">
            Vendors with unsettled negative balances outstanding past 30 days. Flag-and-review policy (v1).
          </p>
        </div>
      </div>

      {isLoading ? (
        <div className="py-8 text-center text-xs text-slate-400">Loading overdue accounts...</div>
      ) : overdueVendors.length === 0 ? (
        <div className="flex items-center gap-3 rounded-xl bg-emerald-50/70 p-4 mt-4 border border-emerald-100">
          <div className="flex h-8 w-8 items-center justify-center rounded-full bg-emerald-100 text-emerald-600 font-bold text-sm">
            ✓
          </div>
          <div>
            <div className="text-xs font-semibold text-emerald-900">All vendor accounts in good standing</div>
            <div className="text-[11px] text-emerald-700">
              No vendors currently have unattached or pending negative balances older than 30 days.
            </div>
          </div>
        </div>
      ) : (
        <div className="mt-4 space-y-3">
          <div className="rounded-lg bg-amber-50 border border-amber-200 p-3 text-xs text-amber-800">
            <strong>Advisory Notice:</strong> The following vendors have accumulated unpaid platform dues or clawbacks
            outstanding beyond the 30-day threshold. Review ledger activity before taking administrative action.
          </div>

          <div className="overflow-x-auto rounded-xl border border-slate-200">
            <table className="w-full text-left text-xs">
              <thead className="bg-slate-50 font-semibold text-slate-600 border-b border-slate-200">
                <tr>
                  <th scope="col" className="px-4 py-3">Vendor</th>
                  <th scope="col" className="px-4 py-3">Outstanding Dues</th>
                  <th scope="col" className="px-4 py-3">Net Balance</th>
                  <th scope="col" className="px-4 py-3">Days Overdue</th>
                  <th scope="col" className="px-4 py-3">Oldest Dues Date</th>
                  <th scope="col" className="px-4 py-3 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 text-slate-700">
                {overdueVendors.map((item) => (
                  <tr key={item.vendorId} className="hover:bg-slate-50/80 transition">
                    <td className="px-4 py-3">
                      <div className="font-semibold text-slate-900">{item.vendorBusinessName}</div>
                      <div className="text-[11px] font-mono text-slate-400">ID: {item.vendorId}</div>
                    </td>
                    <td className="px-4 py-3 font-semibold text-rose-600">
                      ₹{item.outstandingDues.toFixed(2)}
                    </td>
                    <td className="px-4 py-3 font-mono text-slate-700">
                      -₹{Math.abs(item.outstandingBalance).toFixed(2)}
                    </td>
                    <td className="px-4 py-3">
                      <span className="inline-flex items-center rounded-full bg-rose-50 px-2.5 py-0.5 text-xs font-semibold text-rose-700 ring-1 ring-inset ring-rose-600/20">
                        {item.daysOverdue} days
                      </span>
                    </td>
                    <td className="px-4 py-3 text-slate-500">
                      {new Date(item.oldestDuesDate).toLocaleDateString()}
                    </td>
                    <td className="px-4 py-3 text-right">
                      {onFilterVendor && (
                        <button
                          type="button"
                          onClick={() => onFilterVendor(item.vendorId)}
                          className="rounded-lg border border-slate-200 bg-white px-2.5 py-1 text-xs font-semibold text-slate-700 shadow-sm transition hover:bg-slate-50"
                        >
                          View Ledger
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}
