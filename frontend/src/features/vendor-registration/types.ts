import type { VendorProfile } from "@/features/vendor/types";

/**
 * Vendor registration (plan task 1.7) — the vendor's entry point.
 *
 * These types mirror two backend contracts:
 *   `GET /api/v1/locations`      -> {@link ServiceLocationGroup} / {@link ServiceLocationArea}
 *   `POST /api/v1/vendors/register` -> {@link VendorRegisterRequest} in, `VendorProfile` out
 */

/**
 * One service area from `GET /api/v1/locations`.
 *
 * `id` is the `service_locations.id` primary key and is what the registration
 * request must send as `serviceLocationId`. The area text alone is never enough:
 * the backend resolves coordinates and validates the FK from the id, so guessing
 * one would be a data-integrity bug.
 */
export interface ServiceLocationArea {
  id: number;
  area: string;
  pincode: string;
  latitude: number;
  longitude: number;
}

/** The API groups service areas by city: one entry per city, in city order. */
export interface ServiceLocationGroup {
  city: string;
  areas: ServiceLocationArea[];
}

/** One flattened `<option>`: the id is the value, the label is human-readable. */
export interface ServiceLocationOption {
  id: string;
  city: string;
  label: string;
}

/**
 * Flattens the city-grouped response into option rows, dropping empty cities and
 * skipping areas with no usable id (a response shape the picker cannot submit).
 */
export function toServiceLocationOptions(
  groups: ServiceLocationGroup[] | undefined,
): ServiceLocationOption[] {
  if (!groups) return [];

  return groups.flatMap((group) =>
    group.areas
      .filter((area) => typeof area.id === "number" && area.id > 0)
      .map((area) => ({
        id: String(area.id),
        city: group.city,
        label: `${area.area} (${area.pincode})`,
      })),
  );
}

/** True when the response carried no selectable area at all. */
export function hasNoServiceLocations(options: ServiceLocationOption[]): boolean {
  return options.length === 0;
}

/**
 * Exactly the fields this page sends.
 *
 * `VendorRegisterRequest` also accepts the delivery-settings block
 * (`deliveryRadiusKm`, `minOrderAmount`, the fee fields, prep/slot/orders,
 * `acceptingOrders`) and the `hours` week. Every one of those is optional and
 * falls back to its schema default when omitted, so registration deliberately
 * does not send them: the vendor sets them once on `/vendor/settings` and
 * `/vendor/hours`, which are the same single profile resource (docs/decisions.md,
 * D-15). Sending partial guesses here would only create a second place for the
 * same values to be edited.
 *
 * Platform-owned fields — `status`, `commissionRate`, `avgRating`, `reviewCount`,
 * and the coordinates copied from the chosen location — are absent by design.
 */
export interface VendorRegisterRequest {
  email: string;
  password: string;
  fullName: string;
  phone: string | null;
  businessName: string;
  description: string | null;
  addressLine1: string;
  addressLine2: string | null;
  logoUrl: string | null;
  serviceLocationId: number;
}

/** Form-value shape; every field is a raw string as typed in the input. */
export interface VendorRegisterFormValues {
  fullName: string;
  email: string;
  phone: string;
  password: string;
  confirmPassword: string;
  businessName: string;
  description: string;
  addressLine1: string;
  addressLine2: string;
  logoUrl: string;
  serviceLocationId: string;
}

export const VENDOR_REGISTER_DEFAULTS: VendorRegisterFormValues = {
  fullName: "",
  email: "",
  phone: "",
  password: "",
  confirmPassword: "",
  businessName: "",
  description: "",
  addressLine1: "",
  addressLine2: "",
  logoUrl: "",
  serviceLocationId: "",
};

/**
 * Converts validated form strings into the registration payload.
 *
 * `confirmPassword` is client-only and is dropped; blank optional text becomes
 * `null` so it is not sent as an empty string; the picker's string id becomes the
 * numeric `serviceLocationId` the backend's `@Positive Long` expects.
 */
export function buildVendorRegisterRequest(
  values: VendorRegisterFormValues,
): VendorRegisterRequest {
  const text = (value: string) => (value.trim() === "" ? null : value.trim());

  return {
    email: values.email.trim(),
    password: values.password,
    fullName: values.fullName.trim(),
    phone: text(values.phone),
    businessName: values.businessName.trim(),
    description: text(values.description),
    addressLine1: values.addressLine1.trim(),
    addressLine2: text(values.addressLine2),
    logoUrl: text(values.logoUrl),
    serviceLocationId: Number(values.serviceLocationId),
  };
}

/** What the page shows after a successful `POST /api/v1/vendors/register`. */
export type VendorRegistrationResult = Pick<
  VendorProfile,
  "businessName" | "status" | "city" | "area" | "pincode"
>;