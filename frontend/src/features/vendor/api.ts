import api from "@/shared/lib/api";
import type { VendorProfile, VendorProfileUpdateRequest } from "@/features/vendor/types";

/**
 * `GET /api/v1/vendors/profile` — the authenticated vendor's own profile.
 *
 * There is no vendor id in the path: the backend resolves the profile from the
 * JWT subject, so a vendor cannot read another vendor's row (plan task 2.5).
 * Requires `ROLE_FLORIST`.
 */
export async function fetchOwnProfile(): Promise<VendorProfile> {
  const response = await api.get<VendorProfile>("/vendors/profile");
  return response.data;
}

/**
 * `PUT /api/v1/vendors/profile` — full replacement of the vendor's own profile
 * and delivery settings. `hours` is a sub-resource: send it to replace the whole
 * week, omit it to leave the stored week untouched.
 */
export async function updateOwnProfile(
  payload: VendorProfileUpdateRequest,
): Promise<VendorProfile> {
  const response = await api.put<VendorProfile>("/vendors/profile", payload);
  return response.data;
}
