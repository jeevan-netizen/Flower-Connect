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
import { useRunAdminSettlement } from "../queries";

interface RunSettlementModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export function RunSettlementModal({ isOpen, onClose }: RunSettlementModalProps) {
  const today = new Date().toISOString().split("T")[0];
  const lastWeek = new Date(Date.now() - 7 * 86400000).toISOString().split("T")[0];

  const [periodStart, setPeriodStart] = useState(lastWeek);
  const [periodEnd, setPeriodEnd] = useState(today);
  const [vendorId, setVendorId] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [successCount, setSuccessCount] = useState<number | null>(null);

  const runMutation = useRunAdminSettlement();

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!periodStart || !periodEnd) {
      setError("Please specify both start and end dates.");
      return;
    }
    if (periodStart > periodEnd) {
      setError("Start date cannot be after end date.");
      return;
    }

    try {
      setError(null);
      setSuccessCount(null);
      const parsedVendorId = vendorId.trim() ? parseInt(vendorId.trim(), 10) : undefined;
      const result = await runMutation.mutateAsync({
        periodStart,
        periodEnd,
        vendorId: parsedVendorId,
      });
      setSuccessCount(result.length);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "Failed to run settlement cycle.";
      setError(msg);
    }
  };

  return (
    <div
      data-testid="run-settlement-modal"
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 backdrop-blur-sm p-4 animate-in fade-in"
    >
      <div className="relative w-full max-w-lg rounded-2xl bg-white p-6 shadow-2xl border border-slate-200">
        <div className="flex items-center justify-between border-b border-slate-100 pb-3">
          <h3 className="text-base font-bold text-slate-900">Run Weekly Settlement Cycle</h3>
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg p-1 text-slate-400 hover:bg-slate-100 hover:text-slate-600"
          >
            ✕
          </button>
        </div>

        <p className="mt-2 text-xs text-slate-500">
          Aggregates all unattached online credits, COD commission dues, and dispute adjustments for the
          selected period into a PENDING weekly settlement.
        </p>

        <form onSubmit={handleSubmit} className="mt-4 space-y-4">
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label htmlFor="periodStartInput" className="block text-xs font-medium text-slate-700">
                Period Start
              </label>
              <input
                id="periodStartInput"
                type="date"
                value={periodStart}
                onChange={(e) => setPeriodStart(e.target.value)}
                className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-1.5 text-xs text-slate-900 shadow-sm focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
                required
              />
            </div>

            <div>
              <label htmlFor="periodEndInput" className="block text-xs font-medium text-slate-700">
                Period End
              </label>
              <input
                id="periodEndInput"
                type="date"
                value={periodEnd}
                onChange={(e) => setPeriodEnd(e.target.value)}
                className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-1.5 text-xs text-slate-900 shadow-sm focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
                required
              />
            </div>
          </div>

          <div>
            <label htmlFor="vendorIdInput" className="block text-xs font-medium text-slate-700">
              Vendor ID (Optional)
            </label>
            <input
              id="vendorIdInput"
              type="number"
              placeholder="Leave blank to run for all vendors"
              value={vendorId}
              onChange={(e) => setVendorId(e.target.value)}
              className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-1.5 text-xs text-slate-900 shadow-sm focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
            />
            <span className="text-[11px] text-slate-400">
              If left blank, settlement is generated for every active vendor.
            </span>
          </div>

          {error && <p className="text-xs font-medium text-red-600">{error}</p>}
          {successCount !== null && (
            <p className="text-xs font-medium text-emerald-600">
              Settlement cycle completed: {successCount} settlement(s) generated/replayed.
            </p>
          )}

          <div className="flex items-center justify-end gap-2 pt-3 border-t border-slate-100">
            <button
              type="button"
              onClick={onClose}
              className="rounded-lg border border-slate-300 bg-white px-4 py-2 text-xs font-medium text-slate-700 hover:bg-slate-50"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={runMutation.isPending}
              className="rounded-lg bg-brand-600 px-4 py-2 text-xs font-semibold text-white shadow-sm hover:bg-brand-700 disabled:opacity-50"
            >
              {runMutation.isPending ? "Generating..." : "Generate Settlement"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
