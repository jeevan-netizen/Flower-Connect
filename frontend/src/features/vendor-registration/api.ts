import api from "@/shared/lib/api";
import type { VendorProfile } from "@/features/vendor/types";
import type { VendorRegisterRequest } from "@/features/vendor-registration/types";

/**
 * `POST /api/v1/vendors/register` — creates the FLORIST account and its
 * `PENDING_APPROVAL` profile in one transaction.
 *
 * The endpoint returns the created profile, **not** tokens, so this deliberately
 * does not touch the auth store: the vendor signs in through the existing
 * `/auth/login` page afterwards. That keeps a single path into an authenticated
 * session instead of minting a second one here.
 *
 * The service-location list this form also needs (`GET /api/v1/locations`) lives
 * in `@/features/location/api` since task 4.2: the customer shell shares that
 * endpoint, and one client for one endpoint is one place for the contract to be
 * written down.
 */
export async function registerVendor(
  payload: VendorRegisterRequest,
): Promise<VendorProfile> {
  const response = await api.post<VendorProfile>("/vendors/register", payload);
  return response.data;
}
