/**
 * Vendor (florist) frontend types — plan task 2.9.
 *
 * These mirror the Phase 2 backend contracts exactly:
 *   `VendorProfileResponse` / `VendorProfileUpdateRequest` / `VendorHoursResponse`
 *   `VendorHoursRequest`
 *
 * Two deliberate constraints:
 *
 * 1. `VendorProfileEditableFields` is the union of fields `VendorProfileUpdateRequest`
 *    accepts. Everything outside it (status, commission-owned values, rating,
 *    coordinates, timestamps) is platform-controlled and is typed as read-only on
 *    `VendorProfile`, so a vendor form cannot construct a request for it even by
 *    accident.
 * 2. `hours` is a sub-resource on the backend: an absent list leaves the stored
 *    week untouched, a present list replaces the whole week. It therefore lives
 *    on the update request but *not* on the editable-fields contract.
 */

export const VENDOR_STATUSES = [
  "PENDING_APPROVAL",
  "APPROVED",
  "REJECTED",
  "SUSPENDED",
] as const;

export type VendorStatus = (typeof VENDOR_STATUSES)[number];

export const WEEKDAYS = [
  "MONDAY",
  "TUESDAY",
  "WEDNESDAY",
  "THURSDAY",
  "FRIDAY",
  "SATURDAY",
  "SUNDAY",
] as const;

export type Weekday = (typeof WEEKDAYS)[number];

export const WEEKDAY_LABELS: Record<Weekday, string> = {
  MONDAY: "Monday",
  TUESDAY: "Tuesday",
  WEDNESDAY: "Wednesday",
  THURSDAY: "Thursday",
  FRIDAY: "Friday",
  SATURDAY: "Saturday",
  SUNDAY: "Sunday",
};

/** One day of the operating week. Times are `LocalTime` on the wire (`HH:mm:ss`). */
export interface VendorHours {
  weekday: Weekday;
  openTime: string | null;
  closeTime: string | null;
  closed: boolean;
}

/**
 * Exactly the fields `PUT /api/v1/vendors/profile` accepts. `undefined` means
 * "not sent"; the backend then falls back to the schema default, so callers
 * build the payload from the current profile rather than from a sparse form.
 */
export interface VendorProfileEditableFields {
  businessName: string;
  description: string | null;
  addressLine1: string;
  addressLine2: string | null;
  serviceLocationId: number;
  deliveryRadiusKm: number;
  logoUrl: string | null;
  minOrderAmount: number;
  baseDeliveryFee: number;
  perKmFee: number;
  freeDeliveryAbove: number | null;
  prepTimeMinutes: number;
  slotDurationMinutes: number;
  maxOrdersPerSlot: number;
  acceptingOrders: boolean;
}

export interface VendorProfile extends VendorProfileEditableFields {
  id: number;
  ownerEmail: string;
  city: string;
  area: string;
  pincode: string;
  latitude: number;
  longitude: number;
  status: VendorStatus;
  avgRating: number | null;
  reviewCount: number;
  hours: VendorHours[];
  createdAt: string;
  updatedAt: string;
}

export interface VendorProfileUpdateRequest extends VendorProfileEditableFields {
  /** `null`/omitted leaves the stored week untouched; a list replaces the week. */
  hours?: VendorHours[] | null;
}

/**
 * Builds a complete PUT body from the profile currently on screen plus the
 * fields a page owns. Every editable field is resent on purpose: the backend
 * applies PUT semantics, so an omitted scalar silently resets to its default.
 * `hours` is only included when the caller explicitly owns it.
 */
export function buildProfileUpdateRequest(
  profile: VendorProfile,
  changes: Partial<VendorProfileEditableFields>,
  hours?: VendorHours[] | null,
): VendorProfileUpdateRequest {
  const merged: VendorProfileEditableFields = { ...profile, ...changes };

  const request: VendorProfileUpdateRequest = {
    businessName: merged.businessName,
    description: merged.description,
    addressLine1: merged.addressLine1,
    addressLine2: merged.addressLine2,
    serviceLocationId: merged.serviceLocationId,
    deliveryRadiusKm: merged.deliveryRadiusKm,
    logoUrl: merged.logoUrl,
    minOrderAmount: merged.minOrderAmount,
    baseDeliveryFee: merged.baseDeliveryFee,
    perKmFee: merged.perKmFee,
    freeDeliveryAbove: merged.freeDeliveryAbove,
    prepTimeMinutes: merged.prepTimeMinutes,
    slotDurationMinutes: merged.slotDurationMinutes,
    maxOrdersPerSlot: merged.maxOrdersPerSlot,
    acceptingOrders: merged.acceptingOrders,
  };

  if (hours !== undefined) {
    request.hours = hours;
  }

  return request;
}
