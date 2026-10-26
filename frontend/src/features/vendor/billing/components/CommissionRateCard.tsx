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

interface CommissionRateCardProps {
  rate?: number;
  isLoading?: boolean;
}

export function CommissionRateCard({ rate = 0, isLoading }: CommissionRateCardProps) {
  return (
    <div
      data-testid="commission-rate-card"
      className="relative overflow-hidden rounded-2xl border border-brand-200 bg-gradient-to-br from-brand-50/60 via-white to-brand-50/20 p-6 shadow-sm"
    >
      <div className="flex items-center justify-between">
        <span className="text-xs font-semibold uppercase tracking-wider text-slate-500">
          Marketplace Commission
        </span>
        <span className="inline-flex items-center rounded-full bg-brand-100 px-2.5 py-0.5 text-xs font-medium text-brand-800">
          Effective Rate
        </span>
      </div>

      <div className="mt-4 flex items-baseline gap-2">
        {isLoading ? (
          <div className="h-9 w-28 animate-pulse rounded bg-slate-200" />
        ) : (
          <>
            <span className="text-3xl font-extrabold tracking-tight text-slate-900">
              {rate.toFixed(2)}%
            </span>
            <span className="text-xs font-medium text-slate-500">per order subtotal</span>
          </>
        )}
      </div>

      <p className="mt-3 text-xs leading-relaxed text-slate-600">
        Applied exclusively to order subtotal when orders are delivered. Delivery fees and POS walk-in
        sales carry <span className="font-semibold text-slate-800">0% commission</span>.
      </p>
    </div>
  );
}
