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
      className="fixed inset-0 z-50 flex items-center justify-center bg-bolder-overlay backdrop-blur-sm p-4 animate-in fade-in"
    >
      <div className="relative w-full max-w-lg rounded-2xl bg-bolder-surface p-6 shadow-2xl border border-bolder-line">
        <div className="flex items-center justify-between border-b border-bolder-line pb-3">
          <h3 className="text-base font-bold text-bolder-text">Run Weekly Settlement Cycle</h3>
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg p-1 text-bolder-muted hover:bg-bolder-bg hover:text-bolder-text"
          >
            ✕
          </button>
        </div>

        <p className="mt-2 text-xs text-bolder-muted">
          Aggregates all unattached online credits, COD commission dues, and dispute adjustments for the
          selected period into a PENDING weekly settlement.
        </p>

        <form onSubmit={handleSubmit} className="mt-4 space-y-4">
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label htmlFor="periodStartInput" className="block text-xs font-medium text-bolder-text">
                Period Start
              </label>
              <input
                id="periodStartInput"
                type="date"
                value={periodStart}
                onChange={(e) => setPeriodStart(e.target.value)}
                className="mt-1 w-full rounded-lg border border-bolder-line-control px-3 py-1.5 text-xs text-bolder-text shadow-sm focus:border-bolder-rose focus:outline-none focus:ring-1 focus:ring-bolder-rose"
                required
              />
            </div>

            <div>
              <label htmlFor="periodEndInput" className="block text-xs font-medium text-bolder-text">
                Period End
              </label>
              <input
                id="periodEndInput"
                type="date"
                value={periodEnd}
                onChange={(e) => setPeriodEnd(e.target.value)}
                className="mt-1 w-full rounded-lg border border-bolder-line-control px-3 py-1.5 text-xs text-bolder-text shadow-sm focus:border-bolder-rose focus:outline-none focus:ring-1 focus:ring-bolder-rose"
                required
              />
            </div>
          </div>

          <div>
            <label htmlFor="vendorIdInput" className="block text-xs font-medium text-bolder-text">
              Vendor ID (Optional)
            </label>
            <input
              id="vendorIdInput"
              type="number"
              placeholder="Leave blank to run for all vendors"
              value={vendorId}
              onChange={(e) => setVendorId(e.target.value)}
              className="mt-1 w-full rounded-lg border border-bolder-line-control px-3 py-1.5 text-xs text-bolder-text shadow-sm focus:border-bolder-rose focus:outline-none focus:ring-1 focus:ring-bolder-rose"
            />
            <span className="text-[11px] text-bolder-muted">
              If left blank, settlement is generated for every active vendor.
            </span>
          </div>

          {error && <p className="mt-3 text-xs font-medium text-bolder-danger">{error}</p>}
          {successCount !== null && (
            <p className="mt-3 text-xs font-medium text-bolder-success">
              Settlement cycle completed: {successCount} settlement(s) generated/replayed.
            </p>
          )}

          <div className="flex items-center justify-end gap-2 pt-3 border-t border-bolder-line">
            <button
              type="button"
              onClick={onClose}
              className="rounded-lg border border-bolder-line-control bg-bolder-surface px-4 py-2 text-xs font-medium text-bolder-text hover:bg-bolder-bg"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={runMutation.isPending}
              className="rounded-lg bg-bolder-rose-action px-4 py-2 text-xs font-semibold text-white shadow-sm hover:bg-bolder-rose-deep disabled:opacity-50"
            >
              {runMutation.isPending ? "Generating..." : "Generate Settlement"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
