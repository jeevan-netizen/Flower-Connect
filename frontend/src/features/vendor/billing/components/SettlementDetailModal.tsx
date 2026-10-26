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

import { useVendorSettlementDetail } from "../queries";

interface SettlementDetailModalProps {
  settlementId: number | null;
  onClose: () => void;
}

export function SettlementDetailModal({ settlementId, onClose }: SettlementDetailModalProps) {
  const { data, isLoading } = useVendorSettlementDetail(settlementId);

  if (settlementId == null) {
    return null;
  }

  return (
    <div
      data-testid="settlement-detail-modal"
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 backdrop-blur-sm p-4 animate-in fade-in"
    >
      <div className="relative w-full max-w-3xl rounded-2xl bg-white shadow-2xl overflow-hidden border border-slate-200">
        <div className="flex items-center justify-between border-b border-slate-100 px-6 py-4">
          <div>
            <h3 className="text-base font-bold text-slate-900">
              Settlement Cycle #{settlementId} Details
            </h3>
            {data && (
              <p className="text-xs text-slate-500">
                Period: {data.settlement.periodStart} → {data.settlement.periodEnd} • Status:{" "}
                <span className="font-semibold text-slate-700">{data.settlement.status}</span>
              </p>
            )}
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-600 focus:outline-none"
          >
            <span className="sr-only">Close</span>
            ✕
          </button>
        </div>

        <div className="max-h-[60vh] overflow-y-auto p-6">
          {isLoading ? (
            <div className="space-y-3">
              {[1, 2, 3, 4].map((i) => (
                <div key={i} className="h-10 w-full animate-pulse rounded-lg bg-slate-100" />
              ))}
            </div>
          ) : !data ? (
            <p className="text-sm text-slate-500">Unable to load settlement details.</p>
          ) : (
            <div className="space-y-6">
              {/* Summary Stats */}
              <div className="grid grid-cols-3 gap-4 rounded-xl bg-slate-50 p-4 text-center">
                <div>
                  <span className="text-xs text-slate-500">Gross Credits</span>
                  <p className="text-sm font-bold text-emerald-600">
                    +₹{data.settlement.totalCredits.toFixed(2)}
                  </p>
                </div>
                <div>
                  <span className="text-xs text-slate-500">Total Dues</span>
                  <p className="text-sm font-bold text-amber-600">
                    -₹{data.settlement.totalDues.toFixed(2)}
                  </p>
                </div>
                <div>
                  <span className="text-xs text-slate-500">Net Amount</span>
                  <p className="text-sm font-extrabold text-slate-900">
                    ₹{Math.abs(data.settlement.netAmount).toFixed(2)}
                  </p>
                </div>
              </div>

              {/* Entries list */}
              <div>
                <h4 className="text-xs font-semibold uppercase tracking-wider text-slate-500 mb-2">
                  Attached Transactions ({data.entries.length})
                </h4>
                {data.entries.length === 0 ? (
                  <p className="text-xs text-slate-400 italic">No entries attached.</p>
                ) : (
                  <table className="w-full text-left text-xs">
                    <thead className="bg-slate-100 text-slate-600">
                      <tr>
                        <th className="px-3 py-2 font-semibold">Date</th>
                        <th className="px-3 py-2 font-semibold">Type</th>
                        <th className="px-3 py-2 font-semibold">Reference</th>
                        <th className="px-3 py-2 font-semibold text-right">Amount</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100">
                      {data.entries.map((entry) => {
                        const isPositive = entry.amount >= 0;
                        return (
                          <tr key={entry.id}>
                            <td className="px-3 py-2 text-slate-600">
                              {new Date(entry.createdAt).toLocaleDateString("en-IN")}
                            </td>
                            <td className="px-3 py-2 font-medium text-slate-800">{entry.type}</td>
                            <td className="px-3 py-2 font-mono text-slate-500">
                              {entry.reference || `Entry #${entry.id}`}
                            </td>
                            <td
                              className={`px-3 py-2 text-right font-bold ${
                                isPositive ? "text-emerald-600" : "text-amber-600"
                              }`}
                            >
                              {isPositive ? "+" : "-"}₹{Math.abs(entry.amount).toFixed(2)}
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                )}
              </div>
            </div>
          )}
        </div>

        <div className="border-t border-slate-100 bg-slate-50 px-6 py-3 text-right">
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg bg-slate-200 px-4 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-300 focus:outline-none"
          >
            Close
          </button>
        </div>
      </div>
    </div>
  );
}
