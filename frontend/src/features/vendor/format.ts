import type { VendorHours, VendorStatus, Weekday } from "@/features/vendor/types";
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
