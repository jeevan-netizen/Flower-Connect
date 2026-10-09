import type { VendorProfile } from "@/features/vendor/types";

/**
 * Vendor registration (plan task 1.7) — the vendor's entry point.
 *
 * These types mirror one backend contract:
 *   `POST /api/v1/vendors/register` -> {@link VendorRegisterRequest} in,
 *   `VendorProfile` out
 *
 * The service-location types (`ServiceLocationGroup` and friends) used to live
 * here because this page was the only consumer of `GET /api/v1/locations`. Task
 * 4.2's customer location picker shares that endpoint, so they now live in
 * `@/features/location/types` — the customer shell must not import from a
 * vendor feature slice, and one definition of the response shape is better than
 * two.
 */

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
