import { z } from "zod";
import {
  logoUrlField,
  optionalTextField,
  requiredTextField,
} from "@/features/vendor/form-schema";
import type { VendorRegisterFormValues } from "@/features/vendor-registration/types";

/**
 * Form-value schema for vendor registration (plan task 1.7).
 *
 * Every bound mirrors the annotation of the same name on the backend
 * `VendorRegisterRequest`, and the shared text/logo helpers are reused from the
 * vendor slice rather than restated here. Client validation is a fast convenience
 * only: the backend re-checks all of it and stays authoritative.
 *
 * `confirmPassword` has no backend counterpart — it is a client-only guard so the
 * vendor is not locked out by a typo before the account exists.
 */
export const vendorRegisterSchema = z
  .object({
    fullName: requiredTextField("Full name", 128),
    email: z
      .string()
      .trim()
      .min(1, "Email is required")
      .email("Enter a valid email address")
      .max(255, "Email must not exceed 255 characters"),
    // Matches the backend `@Pattern` on `phone`: optional, digits only, optional
    // leading `+`. Blank is legal and is sent as `null`.
    phone: z
      .string()
      .trim()
      .refine(
        (value) => value === "" || /^\+?[0-9]{7,15}$/.test(value),
        "Enter a valid phone number (7-15 digits, optional + prefix)",
      ),
    password: z
      .string()
      .min(8, "Password must be at least 8 characters")
      .max(128, "Password must not exceed 128 characters"),
    confirmPassword: z.string().min(1, "Please confirm your password"),
    businessName: requiredTextField("Business name", 160),
    description: optionalTextField("Description", 1000),
    addressLine1: requiredTextField("Address line 1", 255),
    addressLine2: optionalTextField("Address line 2", 255),
    logoUrl: logoUrlField,
    // Kept as the raw `<select>` string and converted by
    // `buildVendorRegisterRequest`, consistent with the string-typed form state
    // the vendor area uses (docs/decisions.md, D-15).
    serviceLocationId: z
      .string()
      .trim()
      .min(1, "Select the service area your shop operates in"),
  })
  .refine((data) => data.password === data.confirmPassword, {
    message: "Passwords do not match",
    path: ["confirmPassword"],
  });

export type VendorRegisterForm = z.infer<typeof vendorRegisterSchema>;

/**
 * Form field names, used to decide whether a backend validation key can be
 * attached to a real input. Unknown keys stay in the summary banner instead.
 */
export const VENDOR_REGISTER_FIELDS = [
  "fullName",
  "email",
  "phone",
  "password",
  "confirmPassword",
  "businessName",
  "description",
  "addressLine1",
  "addressLine2",
  "logoUrl",
  "serviceLocationId",
] as const satisfies readonly (keyof VendorRegisterFormValues)[];

/** Narrows a backend validation key so it can be attached to a real input. */
export function isVendorRegisterField(field: string): field is keyof VendorRegisterFormValues {
  return (VENDOR_REGISTER_FIELDS as readonly string[]).includes(field);
}