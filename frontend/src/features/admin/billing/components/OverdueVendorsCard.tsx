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
      className="rounded-2xl border border-bolder-line bg-bolder-surface p-6 shadow-sm"
    >
      <div className="flex flex-wrap items-center justify-between gap-4 border-b border-bolder-line pb-4">
        <div>
          <div className="flex items-center gap-2">
            <h2 className="text-base font-semibold text-bolder-text">Overdue Dues Notice</h2>
            {overdueVendors.length > 0 && (
              <span className="inline-flex items-center rounded-full bg-bolder-warning-surface px-2.5 py-0.5 text-xs font-bold text-bolder-warning">
                {overdueVendors.length} Overdue
              </span>
            )}
          </div>
          <p className="mt-0.5 text-xs text-bolder-muted">
            Vendors with unsettled negative balances outstanding past 30 days. Flag-and-review policy (v1).
          </p>
        </div>
      </div>

      {isLoading ? (
        <div className="py-8 text-center text-xs text-bolder-muted">Loading overdue accounts...</div>
      ) : overdueVendors.length === 0 ? (
        <div className="flex items-center gap-3 rounded-xl bg-bolder-success-surface p-4 mt-4 border border-bolder-success-border">
          <div className="flex h-8 w-8 items-center justify-center rounded-full bg-bolder-success-surface text-bolder-success font-bold text-sm">
            ✓
          </div>
          <div>
            <div className="text-xs font-semibold text-bolder-success">All vendor accounts in good standing</div>
            <div className="text-[11px] text-bolder-success">
              No vendors currently have unattached or pending negative balances older than 30 days.
            </div>
          </div>
        </div>
      ) : (
        <div className="mt-4 space-y-3">
          <div className="rounded-lg bg-bolder-warning-surface border border-bolder-warning-border p-3 text-xs text-bolder-warning">
            <strong>Advisory Notice:</strong> The following vendors have accumulated unpaid platform dues or clawbacks
            outstanding beyond the 30-day threshold. Review ledger activity before taking administrative action.
          </div>

          <div className="overflow-x-auto rounded-xl border border-bolder-line">
            <table className="w-full text-left text-xs">
              <thead className="bg-bolder-bg font-semibold text-bolder-muted border-b border-bolder-line">
                <tr>
                  <th scope="col" className="px-4 py-3">Vendor</th>
                  <th scope="col" className="px-4 py-3">Outstanding Dues</th>
                  <th scope="col" className="px-4 py-3">Net Balance</th>
                  <th scope="col" className="px-4 py-3">Days Overdue</th>
                  <th scope="col" className="px-4 py-3">Oldest Dues Date</th>
                  <th scope="col" className="px-4 py-3 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-bolder-line text-bolder-text">
                {overdueVendors.map((item) => (
                  <tr key={item.vendorId} className="hover:bg-bolder-surface/80 transition">
                    <td className="px-4 py-3">
                      <div className="font-semibold text-bolder-text">{item.vendorBusinessName}</div>
                      <div className="text-[11px] font-mono text-bolder-muted">ID: {item.vendorId}</div>
                    </td>
                    <td className="px-4 py-3 font-semibold text-bolder-text">
                      ₹{item.outstandingDues.toFixed(2)}
                    </td>
                    <td className="px-4 py-3 font-mono text-bolder-text">
                      -₹{Math.abs(item.outstandingBalance).toFixed(2)}
                    </td>
                    <td className="px-4 py-3">
                      <span className="inline-flex items-center rounded-full bg-bolder-warning-surface px-2.5 py-0.5 text-xs font-semibold text-bolder-warning ring-1 ring-inset ring-bolder-warning/20">
                        {item.daysOverdue} days
                      </span>
                    </td>
                    <td className="px-4 py-3 text-bolder-text">
                      {new Date(item.oldestDuesDate).toLocaleDateString()}
                    </td>
                    <td className="px-4 py-3 text-right">
                      {onFilterVendor && (
                        <button
                          type="button"
                          onClick={() => onFilterVendor(item.vendorId)}
                          className="rounded-lg border border-bolder-line-control bg-bolder-surface px-2.5 py-1 text-xs font-semibold text-bolder-text shadow-sm transition hover:bg-bolder-bg"
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
