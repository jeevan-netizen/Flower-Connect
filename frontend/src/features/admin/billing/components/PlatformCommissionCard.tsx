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
import { usePlatformCommissionRate, useUpdatePlatformCommissionRate } from "../queries";

export function PlatformCommissionCard() {
  const { data, isLoading } = usePlatformCommissionRate();
  const updateMutation = useUpdatePlatformCommissionRate();

  const [isEditing, setIsEditing] = useState(false);
  const [rateInput, setRateInput] = useState("");
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const handleStartEdit = () => {
    setRateInput(data ? data.rate.toFixed(2) : "10.00");
    setErrorMessage(null);
    setSuccessMessage(null);
    setIsEditing(true);
  };

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    const parsed = parseFloat(rateInput);
    if (isNaN(parsed) || parsed < 0 || parsed > 100) {
      setErrorMessage("Commission rate must be between 0.00% and 100.00%.");
      return;
    }

    try {
      setErrorMessage(null);
      await updateMutation.mutateAsync(parsed);
      setSuccessMessage(`Platform default rate updated to ${parsed.toFixed(2)}%.`);
      setIsEditing(false);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "Failed to update commission rate.";
      setErrorMessage(msg);
    }
  };

  return (
    <div
      data-testid="platform-commission-card"
      className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm"
    >
      <div className="flex items-center justify-between">
        <span className="text-xs font-semibold uppercase tracking-wider text-slate-500">
          Platform Global Settings
        </span>
        <span className="inline-flex items-center rounded-full bg-brand-100 px-2.5 py-0.5 text-xs font-medium text-brand-800">
          Default Rate
        </span>
      </div>

      <div className="mt-4 flex items-baseline gap-2">
        {isLoading ? (
          <div className="h-9 w-28 animate-pulse rounded bg-slate-200" />
        ) : (
          <>
            <span className="text-3xl font-extrabold tracking-tight text-slate-900">
              {data ? data.rate.toFixed(2) : "10.00"}%
            </span>
            <span className="text-xs font-medium text-slate-500">
              applied to subtotal when vendor has no custom rate
            </span>
          </>
        )}
      </div>

      <p className="mt-2 text-xs leading-relaxed text-slate-600">
        This platform default is snapshotted into commission records upon order delivery.
        Updates are audit-logged with before and after values.
      </p>

      {isEditing ? (
        <form onSubmit={handleSave} className="mt-4 flex flex-col gap-3 sm:flex-row sm:items-center">
          <div className="relative">
            <input
              type="number"
              step="0.01"
              min="0"
              max="100"
              value={rateInput}
              onChange={(e) => setRateInput(e.target.value)}
              className="w-32 rounded-lg border border-slate-300 px-3 py-1.5 text-sm font-semibold text-slate-900 focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
              required
            />
            <span className="absolute right-3 top-2 text-xs text-slate-400">%</span>
          </div>

          <div className="flex items-center gap-2">
            <button
              type="submit"
              disabled={updateMutation.isPending}
              className="rounded-lg bg-brand-600 px-3 py-1.5 text-xs font-semibold text-white shadow-sm hover:bg-brand-700 disabled:opacity-50"
            >
              {updateMutation.isPending ? "Saving..." : "Save Rate"}
            </button>
            <button
              type="button"
              onClick={() => setIsEditing(false)}
              className="rounded-lg border border-slate-300 bg-white px-3 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-50"
            >
              Cancel
            </button>
          </div>
        </form>
      ) : (
        <div className="mt-4">
          <button
            type="button"
            onClick={handleStartEdit}
            className="rounded-lg border border-slate-300 bg-white px-3 py-1.5 text-xs font-semibold text-slate-700 shadow-sm hover:bg-slate-50"
          >
            Change Default Rate
          </button>
        </div>
      )}

      {errorMessage && <p className="mt-2 text-xs font-medium text-red-600">{errorMessage}</p>}
      {successMessage && <p className="mt-2 text-xs font-medium text-emerald-600">{successMessage}</p>}
    </div>
  );
}
