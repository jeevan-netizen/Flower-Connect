import api from "@/shared/lib/api";
import type {
  FarmerCollection,
  FarmerCollectionRequest,
  FarmerDeposit,
  FarmerLink,
  FarmerProfile,
  FarmerVendor,
  Passbook,
} from "@/features/farmer/types";
import type { FarmerRegisterRequest, FarmerRegisterResponse } from "@/features/farmer/types";

/* -------------------------------------------------------------------------- */
/* Farmer self-service                                                        */
/* -------------------------------------------------------------------------- */

/**
 * `GET /api/v1/farmer/links` — only the authenticated farmer's own links.
 * Requires `ROLE_FARMER`.
 */
export async function fetchFarmerLinks(): Promise<FarmerLink[]> {
  const response = await api.get<FarmerLink[]>("/farmer/links");
  return response.data;
}

/**
 * `GET /api/v1/farmer/profile` — the authenticated farmer's own profile: name,
 * mobile number and address. Requires `ROLE_FARMER`.
 */
export async function fetchFarmerProfile(): Promise<FarmerProfile> {
  const response = await api.get<FarmerProfile>("/farmer/profile");
  return response.data;
}

/**
 * `GET /api/v1/farmer/vendors?query=` — approved vendors eligible to receive a
 * link request, each carrying the caller's link status when one exists.
 * Requires `ROLE_FARMER`.
 */
export async function fetchEligibleVendors(query?: string): Promise<FarmerVendor[]> {
  const response = await api.get<FarmerVendor[]>("/farmer/vendors", {
    params: query ? { query } : undefined,
  });
  return response.data;
}

/**
 * `POST /api/v1/farmer/links` — requests a `PENDING` link to a vendor.
 * Requires `ROLE_FARMER` and an `ACTIVE` link target.
 */
export async function requestLink(vendorId: number): Promise<FarmerLink> {
  const response = await api.post<FarmerLink>("/farmer/links", { vendorId });
  return response.data;
}

/**
 * `GET /api/v1/farmer/links/{linkId}/passbook` — the farmer's own link passbook:
 * link summary, outstanding balance, combined deposit/collection entries with
 * running balance. Requires `ROLE_FARMER` and ownership of the link.
 */
export async function fetchFarmerPassbook(linkId: number): Promise<Passbook> {
  const response = await api.get<Passbook>(`/farmer/links/${linkId}/passbook`);
  return response.data;
}

/**
 * `POST /api/v1/farmer/links/{linkId}/collections` — requests a `PENDING`
 * collection on the caller's own `ACTIVE` link, for an amount no greater than
 * the outstanding balance. Requires `ROLE_FARMER` and ownership of the link.
 */
export async function requestCollection(
  linkId: number,
  payload: FarmerCollectionRequest,
): Promise<FarmerCollection> {
  const response = await api.post<FarmerCollection>(`/farmer/links/${linkId}/collections`, payload);
  return response.data;
}

/**
 * `POST /api/v1/farmer/register` — registers a farmer account.
 * Public endpoint; returns the created farmer profile (no tokens).
 * The client then signs in through `POST /api/v1/auth/login`.
 */
export async function registerFarmer(
  data: FarmerRegisterRequest,
): Promise<FarmerRegisterResponse> {
  const response = await api.post<FarmerRegisterResponse>("/farmer/register", data);
  return response.data;
}

/* -------------------------------------------------------------------------- */
/* Vendor farmer management                                                   */
/* -------------------------------------------------------------------------- */

/**
 * `GET /api/v1/vendor/farmer-links?status=` — links on the caller's vendor's
 * links, optionally narrowed by status. Requires `ROLE_FLORIST`.
 */
export async function fetchVendorFarmerLinks(
  status?: "PENDING" | "ACTIVE" | "REJECTED" | "SUSPENDED",
): Promise<FarmerLink[]> {
  const response = await api.get<FarmerLink[]>("/vendor/farmer-links", {
    params: status ? { status } : undefined,
  });
  return response.data;
}

/**
 * `POST /api/v1/vendor/farmer-links/{linkId}/accept` — accepts a pending link.
 * Requires `ROLE_FLORIST` and ownership of the link.
 */
export async function acceptLink(linkId: number): Promise<FarmerLink> {
  const response = await api.post<FarmerLink>(`/vendor/farmer-links/${linkId}/accept`);
  return response.data;
}

/**
 * `POST /api/v1/vendor/farmer-links/{linkId}/reject` — rejects a pending link.
 * Requires `ROLE_FLORIST` and ownership of the link.
 */
export async function rejectLink(linkId: number): Promise<FarmerLink> {
  const response = await api.post<FarmerLink>(`/vendor/farmer-links/${linkId}/reject`);
  return response.data;
}

/**
 * `POST /api/v1/vendor/farmer-links/{linkId}/suspend` — suspends an active link.
 * Requires `ROLE_FLORIST` and ownership of the link.
 */
export async function suspendLink(linkId: number): Promise<FarmerLink> {
  const response = await api.post<FarmerLink>(`/vendor/farmer-links/${linkId}/suspend`);
  return response.data;
}

/**
 * `POST /api/v1/vendor/farmer-links/{linkId}/reactivate` — reactivates a
 * suspended link. Requires `ROLE_FLORIST` and ownership of the link.
 */
export async function reactivateLink(linkId: number): Promise<FarmerLink> {
  const response = await api.post<FarmerLink>(`/vendor/farmer-links/${linkId}/reactivate`);
  return response.data;
}
export async function fetchVendorPassbook(linkId: number): Promise<Passbook> {
  const response = await api.get<Passbook>(`/vendor/farmer-links/${linkId}/passbook`);
  return response.data;
}

/**
 * `GET /api/v1/vendor/farmer-collections?status=` — collections on the caller's
 * vendor's links, narrowed by status (defaults to `PENDING`). Requires
 * `ROLE_FLORIST`.
 */
export async function fetchVendorCollections(
  status: "PENDING" | "CONFIRMED" | "REJECTED" = "PENDING",
): Promise<FarmerCollection[]> {
  const response = await api.get<FarmerCollection[]>("/vendor/farmer-collections", {
    params: { status },
  });
  return response.data;
}

/**
 * `POST /api/v1/vendor/farmer-collections/{paymentId}/confirm` — confirms a
 * pending collection belonging to the caller's vendor, recomputing the balance
 * under a lock. Requires `ROLE_FLORIST` and ownership of the link.
 */
export async function confirmCollection(paymentId: number): Promise<FarmerCollection> {
  const response = await api.post<FarmerCollection>(
    `/vendor/farmer-collections/${paymentId}/confirm`,
  );
  return response.data;
}

/**
 * `POST /api/v1/vendor/farmer-collections/{paymentId}/reject` — rejects a pending
 * collection belonging to the caller's vendor. Does not change the balance.
 * Requires `ROLE_FLORIST` and ownership of the link.
 */
export async function rejectCollection(paymentId: number): Promise<FarmerCollection> {
  const response = await api.post<FarmerCollection>(
    `/vendor/farmer-collections/${paymentId}/reject`,
  );
  return response.data;
}

/**
 * `POST /api/v1/vendor/farmer-links/{linkId}/deposits` — records a flower deposit
 * on an `ACTIVE` link owned by the caller's vendor. Requires `ROLE_FLORIST` and
 * ownership of the link.
 */
export async function addDeposit(
  linkId: number,
  payload: {
    flowerType: string;
    grossKg: number;
    pricePerKg: number;
    depositedAt?: string | null;
  },
): Promise<FarmerDeposit> {
  const response = await api.post<FarmerDeposit>(
    `/vendor/farmer-links/${linkId}/deposits`,
    payload,
  );
  return response.data;
}