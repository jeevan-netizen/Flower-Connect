/**
 * Presentation helpers for the customer catalogue.
 *
 * Money is INR and is only ever formatted for display — the wire format stays
 * a JSON number and no arithmetic is done in the browser. `formatMoney` is
 * re-exported from the vendor area so the money rule stays single-sourced
 * while the customer slice keeps one import path for its own formatting.
 */
export { formatMoney } from "@/features/vendor/format";

/**
 * The distance from the customer's area to a florist, as the server computed it.
 *
 * The browser never derives a distance: `GET /api/v1/discover` returns
 * `distanceKm`, because the haversine calculation belongs to one place.
 */
export function formatDistanceKm(distanceKm: number | null | undefined): string {
  if (distanceKm === null || distanceKm === undefined) {
    return "Distance unavailable";
  }
  if (distanceKm < 1) {
    return "Under 1 km away";
  }
  return `${distanceKm.toFixed(1)} km away`;
}

/**
 * A florist's rating.
 *
 * A florist with no reviews yet reads as "New" rather than as a zero or a
 * blank, which is what the plan asks for on the discovery card (task 4.6).
 */
export function formatCustomerRating(
  avgRating: number | null,
  reviewCount: number,
): string {
  if (avgRating === null || reviewCount === 0) {
    return "New";
  }
  return `${avgRating.toFixed(1)} (${reviewCount} review${reviewCount === 1 ? "" : "s"})`;
}

/** How long a florist needs to prepare an order. */
export function formatPrepTime(prepTimeMinutes: number | null | undefined): string {
  if (prepTimeMinutes === null || prepTimeMinutes === undefined) {
    return "Prep time unavailable";
  }
  if (prepTimeMinutes < 60) {
    return `Ready in ${prepTimeMinutes} min`;
  }
  const hours = Math.round((prepTimeMinutes / 60) * 10) / 10;
  return `Ready in ${hours} h`;
}

/**
 * What a customer can still buy.
 *
 * `available` is the server's `quantity - reservedQuantity`; the browser only
 * words it.
 */
export function formatAvailability(available: number | null): string {
  if (available === null) {
    return "Availability unknown";
  }
  if (available <= 0) {
    return "Out of stock";
  }
  if (available <= 3) {
    return `Only ${available} left`;
  }
  return "In stock";
}

/** The delivery fee a florist charges, before any free-delivery threshold. */
export function formatDeliveryFee(fee: number | null | undefined): string {
  if (fee === null || fee === undefined) {
    return "Delivery fee unavailable";
  }
  return `Delivery ${fee.toFixed(2)}`;
}

/**
 * A vendor's opening hours for one weekday, as a single readable line.
 *
 * The backend sends `HH:mm:ss`; this shortens it to `HH:mm`. A closed day
 * says so rather than showing a null time.
 */
export function formatOpeningHours(
  openTime: string | null,
  closeTime: string | null,
  closed: boolean,
): string {
  if (closed || !openTime || !closeTime) {
    return "Closed";
  }
  return `${openTime.slice(0, 5)} – ${closeTime.slice(0, 5)}`;
}

/** The `HH:mm:ss` weekday name the backend sends, as a title-case label. */
export function formatWeekday(weekday: string): string {
  const lower = weekday.toLowerCase();
  return lower.charAt(0).toUpperCase() + lower.slice(1);
}

/** A byte count as a short human-readable size, for image metadata. */
export function formatFileSize(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${Math.round(bytes / 1024)} KB`;
  }
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}
