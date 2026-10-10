import type { InventorySummary, ProductStatus } from "@/features/vendor/types";
import { formatProductStatus, formatStockSummary, PRODUCT_STATUS_TONES } from "@/features/vendor/format";
import { StatusPill } from "@/motion/StatusPill";

/** The product lifecycle pill. Tone map lives in `format.ts` beside the labels. */
export function ProductStatusPill({ status }: { status: ProductStatus }) {
  return <StatusPill tone={PRODUCT_STATUS_TONES[status]} label={formatProductStatus(status)} />;
}

/**
 * Low-stock badge.
 *
 * Driven by the backend's own `InventorySummary.lowStock`, which is
 * `available <= lowStockThreshold` — not by a comparison recomputed here. That
 * matters because the same boolean decides whether a product appears in
 * `GET /api/v1/vendors/inventory/low-stock`; two answers to one question would let
 * a row show "in stock" while sitting in the low-stock list.
 *
 * The zero case is separated from the threshold case because they need different
 * actions: nothing available means nothing is sellable right now, while merely
 * reaching the threshold means "reorder soon".
 */
export function LowStockBadge({ inventory }: { inventory: InventorySummary | null }) {
  if (!inventory || !inventory.lowStock) {
    return null;
  }

  const tone =
    inventory.available === 0
      ? "bg-red-100 text-red-800"
      : "bg-amber-100 text-amber-800";

  return (
    <StatusPill
      tone={tone}
      label={inventory.available === 0 ? "Out of stock" : "Low stock"}
      data-testid="low-stock-badge"
    />
  );
}

/** Available units, with the reserved count named when there is one. */
export function StockSummary({ inventory }: { inventory: InventorySummary | null }) {
  return <span className="text-sm text-slate-700">{formatStockSummary(inventory)}</span>;
}