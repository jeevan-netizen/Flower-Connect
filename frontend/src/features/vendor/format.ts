import type {
  InventorySummary,
  MovementType,
  ProductStatus,
  StockMovement,
  VendorHours,
  VendorStatus,
  Weekday,
} from "@/features/vendor/types";
import { WEEKDAYS, WEEKDAY_LABELS } from "@/features/vendor/types";

/**
 * Presentation helpers for the vendor area. Money is INR (plan section 3) and is
 * only ever formatted for display — the wire format stays a JSON number and no
 * arithmetic is done in the browser.
 */
const currencyFormatter = new Intl.NumberFormat("en-IN", {
  style: "currency",
  currency: "INR",
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

export function formatMoney(value: number | null | undefined): string {
  if (value === null || value === undefined) {
    return "Not set";
  }
  return currencyFormatter.format(value);
}

export function formatDecimal(value: number | null | undefined, suffix = ""): string {
  if (value === null || value === undefined) {
    return "Not set";
  }
  return `${value}${suffix}`;
}

export function formatRating(avgRating: number | null, reviewCount: number): string {
  if (avgRating === null || reviewCount === 0) {
    return "No reviews yet";
  }
  return `${avgRating} from ${reviewCount} review${reviewCount === 1 ? "" : "s"}`;
}

export const VENDOR_STATUS_LABELS: Record<VendorStatus, string> = {
  PENDING_APPROVAL: "Awaiting approval",
  APPROVED: "Approved",
  REJECTED: "Rejected",
  SUSPENDED: "Suspended",
};

export function formatStatus(status: VendorStatus): string {
  return VENDOR_STATUS_LABELS[status];
}

/** `HH:mm:ss` (what the backend sends) to the `HH:mm` an `<input type="time">` needs. */
export function toTimeInputValue(value: string | null): string {
  return value ? value.slice(0, 5) : "";
}

/** `HH:mm` from a time input back to the backend's `LocalTime` representation. */
export function toApiTime(value: string): string | null {
  if (!value) {
    return null;
  }
  return value.length === 5 ? `${value}:00` : value;
}

export function formatTime(value: string | null): string {
  return value ? toTimeInputValue(value) : "—";
}

/**
 * Days that carry no stored row are treated as closed downstream (plan task
 * 2.4), so the editor always shows a full seven-day week.
 */
export function normalizeHours(hours: VendorHours[] | undefined): VendorHours[] {
  const stored = new Map<Weekday, VendorHours>();
  (hours ?? []).forEach((day) => stored.set(day.weekday, day));

  return WEEKDAYS.map((weekday) => {
    const existing = stored.get(weekday);
    return (
      existing ?? {
        weekday,
        openTime: null,
        closeTime: null,
        closed: true,
      }
    );
  });
}

export function weekdayLabel(weekday: Weekday): string {
  return WEEKDAY_LABELS[weekday];
}

export function countOpenDays(hours: VendorHours[] | undefined): number {
  return (hours ?? []).filter((day) => !day.closed).length;
}

/* -------------------------------------------------------------------------- */
/* Catalog and inventory (plan task 3.9)                                        */
/* -------------------------------------------------------------------------- */

/**
 * Product lifecycle labels.
 *
 * The wording states what the status *does*, because the backend enforces no
 * transition table (D-22): any of the four may be set from any other. A vendor
 * reading "Archived" should not assume it is a dead end.
 */
export const PRODUCT_STATUS_LABELS: Record<ProductStatus, string> = {
  DRAFT: "Draft",
  ACTIVE: "Active",
  INACTIVE: "Inactive",
  ARCHIVED: "Archived",
};

export function formatProductStatus(status: ProductStatus): string {
  return PRODUCT_STATUS_LABELS[status];
}

/** Pill tones, keyed the same way as the admin slice's tone maps. */
export const PRODUCT_STATUS_TONES: Record<ProductStatus, string> = {
  DRAFT: "bg-slate-200 text-slate-800",
  ACTIVE: "bg-brand-100 text-brand-900",
  INACTIVE: "bg-amber-100 text-amber-800",
  ARCHIVED: "bg-slate-300 text-slate-900",
};

/** What each status means for a storefront customer, for the form's hint text. */
export const PRODUCT_STATUS_HINTS: Record<ProductStatus, string> = {
  DRAFT: "Only visible to you. Nothing is listed to customers.",
  ACTIVE: "Listed to customers who can reach your delivery area.",
  INACTIVE: "Hidden from the storefront. You can switch it back at any time.",
  ARCHIVED: "Retired. Kept for your stock-movement history.",
};

/**
 * Movement labels.
 *
 * Only the four types a vendor can trigger are reachable from this UI; the other
 * five are written by checkout, the accept path, POS sales and the expiry sweep,
 * and the history still has to name them.
 */
export const MOVEMENT_TYPE_LABELS: Record<MovementType, string> = {
  STOCK_IN: "Stock in",
  STOCK_OUT: "Stock out",
  ADJUSTMENT: "Adjustment",
  RESERVE: "Reserved",
  RELEASE: "Reservation released",
  SALE_ONLINE: "Sale",
  SALE_POS: "POS sale",
  RESTOCK: "Restocked",
  WASTE: "Write-off",
};

export function formatMovementType(movementType: MovementType): string {
  return MOVEMENT_TYPE_LABELS[movementType];
}

/** Signed delta as `+5` / `-2`, so the direction is legible without the type. */
export function formatQuantityDelta(delta: number): string {
  return delta > 0 ? `+${delta}` : String(delta);
}

/** One-based page number for display; the wire format is zero-based. */
export function displayPageNumber(page: number): number {
  return page + 1;
}

const bytesFormatter = new Intl.NumberFormat("en-IN", { maximumFractionDigits: 1 });

/**
 * Uploaded file size, in the largest unit that keeps the number readable.
 *
 * A ladder rather than always-KB because the ceiling is 5 MB
 * (`app.image-upload.max-file-size-bytes`): reporting a rejected 5 MB file as
 * "5120 KB" makes the vendor compare two numbers instead of one, and the limit itself
 * is quoted in MB.
 */
export function formatFileSize(bytes: number): string {
  if (bytes < 1024) {
    return `${bytesFormatter.format(bytes)} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${bytesFormatter.format(bytes / 1024)} KB`;
  }
  return `${bytesFormatter.format(bytes / (1024 * 1024))} MB`;
}

/**
 * `LocalDate` (`yyyy-MM-dd`) rendered for display.
 *
 * Kept as the stored text rather than passed through `Date`: a bare date has no
 * timezone, and constructing a `Date` from `"2026-10-05"` in a browser west of
 * Greenwich shows the previous day. Reformatting the string cannot be wrong.
 */
export function formatLocalDate(value: string | null): string {
  if (!value) {
    return "—";
  }
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  if (!match) {
    return value;
  }
  const months = [
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
  ];
  const monthIndex = Number(match[2]) - 1;
  const month = months[monthIndex] ?? match[2];
  return `${Number(match[3])} ${month} ${match[1]}`;
}

/** `LocalDateTime` (`2026-10-05T09:15:00`) rendered in the browser's locale. */
export function formatDateTime(value: string | null | undefined): string {
  if (!value) {
    return "—";
  }
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? value : parsed.toLocaleString();
}

/**
 * Who caused a stock movement. A `null` actor is the scheduled expiry sweep,
 * which has no authenticated user (D-25) — naming it is more honest than
 * attributing it to whoever last touched the product.
 */
export function formatMovementActor(movement: StockMovement): string {
  return movement.actorEmail ?? "Scheduled sweep";
}

/**
 * Advisory "past its expiry date" marker.
 *
 * Uses the browser's clock and is explicitly *not* the authority: the sweep
 * decides what is written off and when (D-25). It exists so a vendor looking at a
 * dated row understands why it may vanish overnight.
 */
export function isPastExpiryDate(expiryDate: string | null, today: Date): boolean {
  if (!expiryDate) {
    return false;
  }
  const parsed = new Date(`${expiryDate}T00:00:00`);
  if (Number.isNaN(parsed.getTime())) {
    return false;
  }
  /*
   * The stored date is the *last usable day*, so it is expired only from the next
   * day — exactly the rule the scheduled sweep writes off on (`expiryDate < today`,
   * D-25). Comparing against today's midnight keeps the badge in step with the job
   * that will act on it; a badge that turned red a day early would promise a
   * write-off that has not happened.
   */
  const startOfToday = new Date(today.getFullYear(), today.getMonth(), today.getDate());
  return parsed.getTime() < startOfToday.getTime();
}

/**
 * The one-line stock summary shown in a catalog row and the inventory table:
 * available units, with the reserved count called out when there is one.
 */
export function formatStockSummary(inventory: InventorySummary | null): string {
  if (!inventory) {
    return "No inventory row";
  }
  if (inventory.reservedQuantity === 0) {
    return `${inventory.available} available`;
  }
  return `${inventory.available} available (${inventory.reservedQuantity} reserved)`;
}
