import api from "@/shared/lib/api";
import type { VendorProfile } from "@/features/vendor/types";
import type {
  ServiceLocationGroup,
  VendorRegisterRequest,
  VendorRegistrationResult,
} from "@/features/vendor-registration/types";

/**
 * `GET /api/v1/locations` — the seeded service areas, city-grouped.
 *
 * Called with no query parameters, which is the only form of the endpoint that
 * returns every area in one response (any `pincode`/`area`/`page`/`size` turns it
 * into a paginated search instead). Registration needs the complete list to offer
 * a picker, and the demo region is a single city, so this is a one-shot fetch
 * rather than a search-as-you-type flow.
 *
 * Public endpoint: no Authorization required, which is why this page works for a
 * signed-out visitor.
 */
export async function fetchServiceLocations(): Promise<ServiceLocationGroup[]> {
  const response = await api.get<ServiceLocationGroup[]>("/locations");
  return response.data;
}

/**
 * `POST /api/v1/vendors/register` — creates the FLORIST account and its
 * `PENDING_APPROVAL` profile in one transaction.
 *
 * The endpoint returns the created profile, **not** tokens, so this deliberately
 * does not touch the auth store: the vendor signs in through the existing
 * `/auth/login` page afterwards. That keeps a single path into an authenticated
 * session instead of minting a second one here.
 */
export async function registerVendor(
  payload: VendorRegisterRequest,
): Promise<VendorRegistrationResult> {
  const response = await api.post<VendorProfile>("/vendors/register", payload);
  const profile = response.data;
  return {
    businessName: profile.businessName,
    status: profile.status,
    city: profile.city,
    area: profile.area,
    pincode: profile.pincode,
  };
}