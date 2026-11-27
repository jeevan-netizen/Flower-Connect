/**
 * Today's order summary strip for the vendor order board (plan Phase 6, task 6.4).
 *
 * Reads `GET /api/v1/vendor/orders/summary` and shows today's order count,
 * total value and expected COD cash, with a per-status count breakdown.
 */

import type { VendorOrderSummaryResponse } from "@/features/vendor/orders/types";
import { formatMoney } from "@/features/vendor/format";
import { FadeIn } from "@/motion/FadeIn";

interface TodaySummaryStripProps {
  summary: VendorOrderSummaryResponse | null;
  isPending: boolean;
}

export function TodaySummaryStrip({ summary, isPending }: TodaySummaryStripProps) {
  if (isPending) {
    return (
      <FadeIn className="rounded-lg border bolder-line bg-bolder-surface p-4 text-sm text-bolder-muted">
        Loading today's summary…
      </FadeIn>
    );
  }

  if (!summary) {
    return null;
  }

    const statusRows = Object.entries(summary.ordersByStatus).map(
      ([status, count]) => (
        <div key={status} className="flex justify-between text-sm">
          <span className="text-bolder-muted">{status.replace(/_/g, " ")}</span>
          <span className="font-medium text-bolder-text">{count}</span>
        </div>
      ),
    );

  return (
      <div className="rounded-lg border bolder-line bg-bolder-surface p-4 shadow-sm">
        <h2 className="text-sm font-semibold text-bolder-text mb-2">
          Today's summary <span className="text-xs font-normal text-bolder-muted">{summary.today}</span>
        </h2>
        <div className="grid grid-cols-1 sm:grid-cols-4 gap-3 text-sm">
          <div className="border bolder-line rounded-lg p-3">
            <p className="text-xs text-bolder-muted uppercase tracking-wide">Orders today</p>
            <p className="text-base font-bold text-bolder-text">{summary.todayOrderCount}</p>
          </div>
          <div className="border bolder-line rounded-lg p-3">
            <p className="text-xs text-bolder-muted uppercase tracking-wide">Total value</p>
            <p className="text-base font-bold text-bolder-text">{formatMoney(summary.todayTotalValue)}</p>
          </div>
          <div className="border bolder-line rounded-lg p-3">
            <p className="text-xs text-bolder-muted uppercase tracking-wide">COD cash to collect</p>
            <p className="text-base font-bold text-bolder-text">{formatMoney(summary.expectedCodCash)}</p>
          </div>
          <div className="border bolder-line rounded-lg p-3">
            <p className="text-xs text-bolder-muted uppercase tracking-wide">Delivered</p>
            <p className="text-base font-bold text-bolder-text">
              {summary.ordersByStatus.DELIVERED ?? 0}
            </p>
          </div>
        </div>
        {statusRows.length > 0 && (
          <div className="mt-3 border-t bolder-line pt-3">
            <p className="text-xs font-medium text-bolder-text mb-1">By status</p>
            <div className="flex flex-wrap gap-4 text-sm">
              {statusRows}
            </div>
          </div>
        )}
      </div>
  );
}
