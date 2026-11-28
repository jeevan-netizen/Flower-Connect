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
      className="rounded-2xl border border-bolder-line bg-bolder-surface p-6 shadow-sm"
    >
      <div className="flex items-center justify-between">
        <span className="text-xs font-semibold uppercase tracking-wider text-bolder-muted">
          Platform Global Settings
        </span>
        <span className="inline-flex items-center rounded-full bg-bolder-blush-light px-2.5 py-0.5 text-xs font-medium text-bolder-rose-deep">
          Default Rate
        </span>
      </div>

      <div className="mt-4 flex items-baseline gap-2">
        {isLoading ? (
          <div className="h-9 w-28 animate-pulse rounded bg-bolder-bg" />
        ) : (
          <>
            <span className="text-3xl font-extrabold tracking-tight text-bolder-text">
              {data ? data.rate.toFixed(2) : "10.00"}%
            </span>
            <span className="text-xs font-medium text-bolder-muted">
              applied to subtotal when vendor has no custom rate
            </span>
          </>
        )}
      </div>

      <p className="mt-2 text-xs leading-relaxed text-bolder-muted">
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
                className="w-32 rounded-lg border border-bolder-line-control px-3 py-1.5 text-xs font-medium text-bolder-text shadow-sm focus:border-bolder-rose focus:outline-none focus:ring-1 focus:ring-bolder-rose"
              required
            />
            <span className="absolute right-3 top-2 text-xs text-bolder-muted">%</span>
          </div>

          <div className="flex items-center gap-2">
            <button
              type="submit"
              disabled={updateMutation.isPending}
              className="rounded-lg bg-bolder-rose-action px-3 py-1.5 text-xs font-semibold text-white shadow-sm hover:bg-bolder-rose-deep disabled:opacity-50"
            >
              {updateMutation.isPending ? "Saving..." : "Save Rate"}
            </button>
            <button
              type="button"
              onClick={() => setIsEditing(false)}
              className="rounded-lg border border-bolder-line-control bg-bolder-surface px-3 py-1.5 text-xs font-medium text-bolder-text hover:bg-bolder-bg"
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
            className="rounded-lg border border-bolder-line-control bg-bolder-surface px-3 py-1.5 text-xs font-medium text-bolder-text shadow-sm hover:bg-bolder-bg"
          >
            Change Default Rate
          </button>
        </div>
      )}

      {errorMessage && <p className="mt-2 text-xs font-medium text-bolder-danger">{errorMessage}</p>}
      {successMessage && <p className="mt-2 text-xs font-medium text-bolder-success">{successMessage}</p>}
    </div>
  );
}

