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

import type { SettlementResponse } from "../types";

interface SettlementHistoryTableProps {
  settlements?: SettlementResponse[];
  isLoading?: boolean;
  onSelectSettlement?: (settlementId: number) => void;
}

export function SettlementHistoryTable({
  settlements = [],
  isLoading,
  onSelectSettlement,
}: SettlementHistoryTableProps) {
  if (isLoading) {
    return (
      <div className="rounded-2xl border bolder-line bg-bolder-surface p-6 shadow-sm">
        <h3 className="text-base font-bold text-bolder-text">Weekly Settlement Cycles</h3>
        <div className="mt-4 space-y-3">
          {[1, 2, 3].map((i) => (
            <div key={i} className="h-12 w-full animate-pulse rounded-lg bg-bolder-bg" />
          ))}
        </div>
      </div>
    );
  }

  return (
    <div
      data-testid="settlement-history-table"
      className="overflow-hidden rounded-2xl border bolder-line bg-bolder-surface shadow-sm"
    >
      <div className="border-b bolder-line px-6 py-4">
        <div className="flex items-center justify-between">
          <div>
            <h3 className="text-base font-bold text-bolder-text">Weekly Settlement Cycles</h3>
            <p className="mt-0.5 text-xs text-bolder-muted">
              Completed and pending weekly netting cycles. Credits and cash COD dues are aggregated and settled.
            </p>
          </div>
          <span className="rounded-full bg-bolder-bg px-2.5 py-0.5 text-xs font-semibold text-bolder-text">
            {settlements.length} Cycles
          </span>
        </div>
      </div>

      {settlements.length === 0 ? (
        <div className="px-6 py-12 text-center">
          <p className="text-sm font-medium text-bolder-muted">No settlement cycles recorded yet.</p>
          <p className="mt-1 text-xs text-bolder-muted">
            Weekly settlements are automatically generated at the end of each calendar week.
          </p>
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="bg-bolder-bg text-xs uppercase tracking-wider text-bolder-muted">
              <tr>
                <th scope="col" className="px-6 py-3 font-semibold">Period</th>
                <th scope="col" className="px-6 py-3 font-semibold">Credits</th>
                <th scope="col" className="px-6 py-3 font-semibold">Dues</th>
                <th scope="col" className="px-6 py-3 font-semibold">Net Payout</th>
                <th scope="col" className="px-6 py-3 font-semibold">Direction</th>
                <th scope="col" className="px-6 py-3 font-semibold">Status</th>
                <th scope="col" className="px-6 py-3 font-semibold">Reference Note</th>
                {onSelectSettlement && <th scope="col" className="px-6 py-3 font-semibold text-right">Actions</th>}
              </tr>
            </thead>
            <tbody className="divide-y divide-bolder-line text-bolder-text">
              {settlements.map((s) => {
                const isPlatformPays = s.direction === "PLATFORM_PAYS_VENDOR";
                const isVendorPays = s.direction === "VENDOR_PAYS_PLATFORM";
                const isSettled = s.status === "SETTLED";

                return (
                  <tr key={s.id} className="transition hover:bg-bolder-bg/80">
                    <td className="whitespace-nowrap px-6 py-4 font-medium text-bolder-text">
                      {s.periodStart} → {s.periodEnd}
                    </td>
                    <td className="whitespace-nowrap px-6 py-4 text-bolder-success font-medium">
                      +₹{s.totalCredits.toFixed(2)}
                    </td>
                    <td className="whitespace-nowrap px-6 py-4 text-bolder-warning font-medium">
                      -₹{s.totalDues.toFixed(2)}
                    </td>
                    <td className="whitespace-nowrap px-6 py-4 font-bold text-bolder-text">
                      ₹{Math.abs(s.netAmount).toFixed(2)}
                    </td>
                    <td className="whitespace-nowrap px-6 py-4">
                      <span
                        className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${
                          isPlatformPays
                            ? "bg-bolder-success-surface text-bolder-success"
                            : isVendorPays
                            ? "bg-bolder-warning-surface text-bolder-warning"
                            : "bg-bolder-bg text-bolder-text"
                        }`}
                      >
                        {isPlatformPays
                          ? "Platform Pays Vendor"
                          : isVendorPays
                          ? "Vendor Pays Platform"
                          : "Zero Balance"}
                      </span>
                    </td>
                    <td className="whitespace-nowrap px-6 py-4">
                      <span
                        className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold ${
                          isSettled
                            ? "bg-bolder-success-surface text-bolder-success"
                            : "bg-bolder-warning-surface text-bolder-warning"
                        }`}
                      >
                        {isSettled ? "SETTLED" : "PENDING"}
                      </span>
                    </td>
                    <td className="max-w-xs truncate px-6 py-4 text-xs text-bolder-muted">
                      {s.adminReferenceNote || "—"}
                    </td>
                    {onSelectSettlement && (
                      <td className="whitespace-nowrap px-6 py-4 text-right">
                        <button
                          type="button"
                          onClick={() => onSelectSettlement(s.id)}
                          className="font-medium text-bolder-rose-strong hover:text-bolder-rose-deep focus:outline-none"
                        >
                          View Entries
                        </button>
                      </td>
                    )}
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
