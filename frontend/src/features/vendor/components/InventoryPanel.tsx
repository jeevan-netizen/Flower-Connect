import { useEffect, useState } from "react";
import type { InventorySummary, StockActionKind } from "@/features/vendor/types";
import { formatLocalDate, isPastExpiryDate } from "@/features/vendor/format";
import { LowStockBadge } from "@/features/vendor/components/CatalogBadges";
import { STOCK_ACTION_LABELS } from "@/features/vendor/components/StockActionDialog";
import { ReadOnlyRow } from "@/features/vendor/components/FormFields";
import { FOCUS_RING, PRESSABLE } from "@/motion/pressable";

const ACTION_BUTTON_CLASS = `rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-50 ${PRESSABLE} ${FOCUS_RING}`;
const SETTING_BUTTON_CLASS = `rounded-md bg-brand-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-brand-700 disabled:cursor-not-allowed disabled:opacity-50 ${PRESSABLE} ${FOCUS_RING}`;

const STOCK_ACTIONS: StockActionKind[] = ["stock-in", "stock-out", "adjustment", "write-off"];

interface InventoryPanelProps {
  inventory: InventorySummary | null;
  isPending: boolean;
  isSettingThreshold: boolean;
  isSettingExpiryDate: boolean;
  /** Opens the shared stock dialog for one of the four actions. */
  onAction: (action: StockActionKind) => void;
  onSaveThreshold: (lowStockThreshold: number) => void;
  onSaveExpiryDate: (expiryDate: string | null) => void;
}

/**
 * Per-product inventory panel: the numbers, the four stock actions, and the two
 * alert settings (plan task 3.6 / 3.9).
 *
 * Shown both on the product editor and — for the whole shop at once — as the
 * low-stock listing, so the same numbers, the same four buttons and the same rules
 * appear wherever a vendor meets stock.
 *
 * Three things are read from the backend rather than decided here:
 *
 *  - **`available` and `lowStock`** come from `InventorySummary` unchanged. The
 *    server computes `available = quantity − reservedQuantity` and
 *    `lowStock = available ≤ lowStockThreshold` under the same row lock that
 *    guards the write, so a second browser-side answer could disagree with the
 *    `409 INSUFFICIENT_STOCK` that follows.
 *  - **Reserved units are shown, not editable.** They belong to placed orders and
 *    are released or committed by the order flow; a vendor cannot move them, so
 *    the panel offers no control that would pretend otherwise.
 *  - **The expiry date is advisory here.** The scheduled sweep is what writes off
 *    expired stock and delists the product (D-25); the "past its expiry date"
 *    marker uses the browser's clock purely so the row is not a mystery.
 */
export function InventoryPanel({
  inventory,
  isPending,
  isSettingThreshold,
  isSettingExpiryDate,
  onAction,
  onSaveThreshold,
  onSaveExpiryDate,
}: InventoryPanelProps) {
  const [threshold, setThreshold] = useState("0");
  const [expiryDate, setExpiryDate] = useState("");

  // Seeded from the server's values, and re-seeded whenever they change so a save
  // that the server normalises (or a concurrent change) is reflected.
  useEffect(() => {
    if (inventory) {
      setThreshold(String(inventory.lowStockThreshold));
      setExpiryDate(inventory.expiryDate ?? "");
    }
  }, [inventory]);

  if (isPending) {
    return <p className="text-sm text-slate-600">Loading stock levels...</p>;
  }

  if (!inventory) {
    return (
      <p className="rounded-md border border-dashed border-slate-300 p-4 text-sm text-slate-600">
        This product has no inventory row yet.
      </p>
    );
  }

  const thresholdValue = Number(threshold.trim());
  const thresholdValid = threshold.trim() !== "" && Number.isInteger(thresholdValue) && thresholdValue >= 0;

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-2">
        <h3 className="text-sm font-semibold text-slate-900">Stock</h3>
        <LowStockBadge inventory={inventory} />
        {inventory.expiryDate && isPastExpiryDate(inventory.expiryDate, new Date()) && (
          <span className="rounded-full bg-red-100 px-2 py-0.5 text-xs font-medium text-red-800">
            Past its expiry date
          </span>
        )}
      </div>

      <dl className="rounded-md border border-slate-200 bg-slate-50 p-3">
        <ReadOnlyRow label="On hand" value={String(inventory.quantity)} />
        <ReadOnlyRow label="Reserved for placed orders" value={String(inventory.reservedQuantity)} />
        <ReadOnlyRow label="Available to sell" value={String(inventory.available)} />
        <ReadOnlyRow label="Low stock threshold" value={String(inventory.lowStockThreshold)} />
        <ReadOnlyRow label="Expiry date" value={formatLocalDate(inventory.expiryDate)} />
      </dl>

      <div className="flex flex-wrap gap-2">
        {STOCK_ACTIONS.map((action) => (
          <button
            key={action}
            type="button"
            onClick={() => onAction(action)}
            className={ACTION_BUTTON_CLASS}
          >
            {STOCK_ACTION_LABELS[action]}
          </button>
        ))}
      </div>

      <p className="text-xs text-slate-500">
        Every change writes one movement record, visible in the history below. Stock reserved for a
        placed order cannot be removed by hand.
      </p>

      <div className="grid grid-cols-1 gap-4 border-t border-slate-200 pt-4 sm:grid-cols-2">
        <div>
          <label
            htmlFor="low-stock-threshold"
            className="block text-sm font-medium text-slate-700"
          >
            Low stock threshold
          </label>
          <p className="text-xs text-slate-500">
            The product is flagged low stock when available units reach this number.
          </p>
          <div className="mt-1 flex gap-2">
            <input
              id="low-stock-threshold"
              type="number"
              inputMode="numeric"
              min="0"
              step="1"
              value={threshold}
              onChange={(event) => setThreshold(event.target.value)}
              className="w-28 rounded-md border-slate-300 text-sm shadow-sm focus:border-brand-500 focus:ring-brand-500"
            />
            <button
              type="button"
              disabled={!thresholdValid || isSettingThreshold}
              onClick={() => onSaveThreshold(thresholdValue)}
              className={SETTING_BUTTON_CLASS}
            >
              {isSettingThreshold ? "Saving..." : "Save"}
            </button>
          </div>
        </div>

        <div>
          <label htmlFor="expiry-date" className="block text-sm font-medium text-slate-700">
            Expiry date
          </label>
          <p className="text-xs text-slate-500">
            The last day this stock may be used. The nightly sweep writes off anything past it and
            delists the product.
          </p>
          <div className="mt-1 flex gap-2">
            <input
              id="expiry-date"
              type="date"
              value={expiryDate}
              onChange={(event) => setExpiryDate(event.target.value)}
              className="rounded-md border-slate-300 text-sm shadow-sm focus:border-brand-500 focus:ring-brand-500"
            />
            <button
              type="button"
              disabled={isSettingExpiryDate}
              onClick={() => onSaveExpiryDate(expiryDate === "" ? null : expiryDate)}
              className={SETTING_BUTTON_CLASS}
            >
              {isSettingExpiryDate ? "Saving..." : expiryDate === "" ? "Clear" : "Save"}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}