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
import { downloadVendorStatementCsv } from "../api";

export function StatementDownloadCard() {
  const today = new Date().toISOString().split("T")[0];
  const lastWeek = new Date(Date.now() - 7 * 86400000).toISOString().split("T")[0];

  const [periodStart, setPeriodStart] = useState(lastWeek);
  const [periodEnd, setPeriodEnd] = useState(today);
  const [isDownloading, setIsDownloading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleDownload = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!periodStart || !periodEnd) {
      setError("Please select both start and end dates.");
      return;
    }
    if (periodStart > periodEnd) {
      setError("Start date must be before or equal to end date.");
      return;
    }

    try {
      setIsDownloading(true);
      setError(null);
      await downloadVendorStatementCsv(periodStart, periodEnd);
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : "Failed to download statement.";
      setError(message);
    } finally {
      setIsDownloading(false);
    }
  };

  return (
    <div
      data-testid="statement-download-card"
      className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm"
    >
      <div className="flex items-center justify-between">
        <h3 className="text-base font-bold text-slate-900">Download Account Statement</h3>
        <span className="rounded-full bg-slate-100 px-2.5 py-0.5 text-xs font-medium text-slate-600">
          CSV Export
        </span>
      </div>

      <p className="mt-1 text-xs text-slate-500">
        Generate an official ledger statement for your accounting and tax records.
      </p>

      <form onSubmit={handleDownload} className="mt-4 flex flex-col gap-3 sm:flex-row sm:items-end">
        <div className="flex-1">
          <label htmlFor="periodStart" className="block text-xs font-medium text-slate-700">
            From Date
          </label>
          <input
            id="periodStart"
            type="date"
            value={periodStart}
            onChange={(e) => setPeriodStart(e.target.value)}
            className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm shadow-sm focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
            required
          />
        </div>

        <div className="flex-1">
          <label htmlFor="periodEnd" className="block text-xs font-medium text-slate-700">
            To Date
          </label>
          <input
            id="periodEnd"
            type="date"
            value={periodEnd}
            onChange={(e) => setPeriodEnd(e.target.value)}
            className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm shadow-sm focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
            required
          />
        </div>

        <button
          type="submit"
          disabled={isDownloading}
          className="inline-flex items-center justify-center rounded-lg bg-brand-600 px-5 py-2.5 text-sm font-semibold text-white shadow-sm transition hover:bg-brand-700 focus:outline-none focus:ring-2 focus:ring-brand-500 focus:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-60"
        >
          {isDownloading ? (
            <span className="flex items-center gap-2">
              <span className="h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent" />
              Exporting...
            </span>
          ) : (
            "Export CSV"
          )}
        </button>
      </form>

      {error && <p className="mt-2 text-xs font-medium text-red-600">{error}</p>}

      <div className="mt-4 flex items-center gap-2 text-[11px] text-slate-400">
        <svg className="h-3.5 w-3.5 shrink-0 text-slate-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
        </svg>
        <span>Protected against formula injection attacks according to OWASP guidelines.</span>
      </div>
    </div>
  );
}
