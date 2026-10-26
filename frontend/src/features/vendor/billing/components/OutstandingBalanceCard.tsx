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

interface OutstandingBalanceCardProps {
  balance?: number;
  isLoading?: boolean;
}

export function OutstandingBalanceCard({ balance = 0, isLoading }: OutstandingBalanceCardProps) {
  const isPositive = balance > 0;
  const isNegative = balance < 0;

  const formattedAmount = Math.abs(balance).toLocaleString("en-IN", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });

  return (
    <div
      data-testid="outstanding-balance-card"
      className={`relative overflow-hidden rounded-2xl border p-6 shadow-sm transition-all duration-300 ${
        isPositive
          ? "border-emerald-200 bg-gradient-to-br from-emerald-50/80 via-white to-emerald-50/30 text-emerald-950"
          : isNegative
          ? "border-amber-200 bg-gradient-to-br from-amber-50/80 via-white to-amber-50/30 text-amber-950"
          : "border-slate-200 bg-gradient-to-br from-slate-50 via-white to-slate-50/30 text-slate-800"
      }`}
    >
      <div className="flex items-center justify-between">
        <span className="text-xs font-semibold uppercase tracking-wider text-slate-500">
          Unattached Balance
        </span>
        <span
          className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${
            isPositive
              ? "bg-emerald-100 text-emerald-800"
              : isNegative
              ? "bg-amber-100 text-amber-800"
              : "bg-slate-100 text-slate-700"
          }`}
        >
          {isPositive ? "Platform Owes You" : isNegative ? "Remittance Due" : "Settled"}
        </span>
      </div>

      <div className="mt-4 flex items-baseline gap-2">
        {isLoading ? (
          <div className="h-9 w-36 animate-pulse rounded bg-slate-200" />
        ) : (
          <>
            <span className="text-3xl font-extrabold tracking-tight">
              {isNegative ? "-" : isPositive ? "+" : ""}₹{formattedAmount}
            </span>
            <span className="text-xs font-medium text-slate-500">INR</span>
          </>
        )}
      </div>

      <p className="mt-3 text-xs leading-relaxed text-slate-600">
        {isPositive
          ? "Accumulated online order credits minus COD commissions. Netted and paid out weekly."
          : isNegative
          ? "Cash collected on COD orders exceeds your platform credits. Due in the upcoming settlement run."
          : "All deliveries and adjustments are accounted for with no open dues."}
      </p>
    </div>
  );
}
