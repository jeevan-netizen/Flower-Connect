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
import { useAdminVendorOutstanding, useUpdateVendorCommissionRate } from "../queries";

export function VendorRateOverrideCard() {
  const [vendorIdInput, setVendorIdInput] = useState("");
  const [activeVendorId, setActiveVendorId] = useState<number | null>(null);
  const [rateOverrideInput, setRateOverrideInput] = useState("");
  const [statusMessage, setStatusMessage] = useState<{ text: string; isError: boolean } | null>(null);

  const { data: balanceData, isLoading: balanceLoading } = useAdminVendorOutstanding(activeVendorId);
  const updateRateMutation = useUpdateVendorCommissionRate();

  const handleLookup = (e: React.FormEvent) => {
    e.preventDefault();
    const id = parseInt(vendorIdInput, 10);
    if (!isNaN(id) && id > 0) {
      setActiveVendorId(id);
      setStatusMessage(null);
    }
  };

  const handleSetRate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (activeVendorId == null) return;

    const rate = parseFloat(rateOverrideInput);
    if (isNaN(rate) || rate < 0 || rate > 100) {
      setStatusMessage({ text: "Rate must be between 0.00% and 100.00%.", isError: true });
      return;
    }

    try {
      await updateRateMutation.mutateAsync({ vendorId: activeVendorId, rate });
      setStatusMessage({
        text: `Vendor #${activeVendorId} commission rate override set to ${rate.toFixed(2)}%.`,
        isError: false,
      });
      setRateOverrideInput("");
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "Failed to update vendor commission rate.";
      setStatusMessage({ text: msg, isError: true });
    }
  };

  return (
    <div
      data-testid="vendor-rate-override-card"
      className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm"
    >
      <div className="flex items-center justify-between">
        <span className="text-xs font-semibold uppercase tracking-wider text-slate-500">
          Vendor Rate & Balance Control
        </span>
        <span className="rounded-full bg-slate-100 px-2.5 py-0.5 text-xs font-medium text-slate-600">
          Per-Vendor Override
        </span>
      </div>

      <p className="mt-1 text-xs text-slate-500">
        Lookup specific vendor to inspect unattached balance and configure custom commission rates.
      </p>

      {/* Lookup Form */}
      <form onSubmit={handleLookup} className="mt-4 flex gap-2">
        <input
          type="number"
          placeholder="Enter Vendor ID"
          value={vendorIdInput}
          onChange={(e) => setVendorIdInput(e.target.value)}
          className="w-40 rounded-lg border border-slate-300 px-3 py-1.5 text-xs font-medium text-slate-900 focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
          required
        />
        <button
          type="submit"
          className="rounded-lg bg-slate-800 px-3 py-1.5 text-xs font-semibold text-white shadow-sm hover:bg-slate-900"
        >
          Inspect Vendor
        </button>
      </form>

      {activeVendorId !== null && (
        <div className="mt-5 rounded-xl border border-slate-100 bg-slate-50/70 p-4 space-y-4">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold text-slate-800">{`Vendor #${activeVendorId}`}</span>
            <div className="text-right">
              <span className="text-[11px] text-slate-500 block">Unattached Balance</span>
              {balanceLoading ? (
                <div className="h-5 w-20 animate-pulse rounded bg-slate-200" />
              ) : (
                <span
                  className={`text-sm font-extrabold ${
                    (balanceData?.outstandingBalance ?? 0) >= 0
                      ? "text-emerald-600"
                      : "text-amber-600"
                  }`}
                >
                  {`${(balanceData?.outstandingBalance ?? 0) >= 0 ? "+" : "-"}₹${Math.abs(
                    balanceData?.outstandingBalance ?? 0,
                  ).toFixed(2)}`}
                </span>
              )}
            </div>
          </div>

          {/* Rate Override Form */}
          <form onSubmit={handleSetRate} className="flex items-center gap-2 pt-2 border-t border-slate-200/60">
            <label htmlFor="vendorRate" className="text-xs text-slate-600 whitespace-nowrap">
              Override Rate:
            </label>
            <div className="relative w-28">
              <input
                id="vendorRate"
                type="number"
                step="0.01"
                min="0"
                max="100"
                placeholder="10.00"
                value={rateOverrideInput}
                onChange={(e) => setRateOverrideInput(e.target.value)}
                className="w-full rounded-lg border border-slate-300 px-2.5 py-1 text-xs font-medium text-slate-900 focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
                required
              />
              <span className="absolute right-2.5 top-1.5 text-xs text-slate-400">%</span>
            </div>
            <button
              type="submit"
              disabled={updateRateMutation.isPending}
              className="rounded-lg bg-brand-600 px-3 py-1 text-xs font-semibold text-white shadow-sm hover:bg-brand-700 disabled:opacity-50"
            >
              {updateRateMutation.isPending ? "Applying..." : "Set Rate"}
            </button>
          </form>
        </div>
      )}

      {statusMessage && (
        <p
          className={`mt-3 text-xs font-medium ${
            statusMessage.isError ? "text-red-600" : "text-emerald-600"
          }`}
        >
          {statusMessage.text}
        </p>
      )}
    </div>
  );
}
