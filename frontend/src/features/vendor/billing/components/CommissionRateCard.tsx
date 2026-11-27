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
      className="relative overflow-hidden rounded-2xl border bolder-line-control bg-gradient-to-br from-bolder-blush-light/60 via-white to-bolder-blush-light/20 p-6 shadow-sm"
    >
      <div className="flex items-center justify-between">
        <span className="text-xs font-semibold uppercase tracking-wider text-bolder-muted">
          Marketplace Commission
        </span>
        <span className="inline-flex items-center rounded-full bg-bolder-blush-light px-2.5 py-0.5 text-xs font-medium text-bolder-rose-deep">
          Effective Rate
        </span>
      </div>

      <div className="mt-4 flex items-baseline gap-2">
        {isLoading ? (
          <div className="h-9 w-28 animate-pulse rounded bg-bolder-bg" />
        ) : (
          <>
            <span className="text-3xl font-extrabold tracking-tight text-bolder-text">
              {rate.toFixed(2)}%
            </span>
            <span className="text-xs font-medium text-bolder-muted">per order subtotal</span>
          </>
        )}
      </div>

      <p className="mt-3 text-xs leading-relaxed text-bolder-muted">
        Applied exclusively to order subtotal when orders are delivered. Delivery fees and POS walk-in
        sales carry <span className="font-semibold text-bolder-text">0% commission</span>.
      </p>
    </div>
  );
}
