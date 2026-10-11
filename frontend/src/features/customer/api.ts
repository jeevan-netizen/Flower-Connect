import api from "@/shared/lib/api";
import type { DiscoveryPageResponse } from "@/features/customer/types";

/**
 * Discovery request parameters.
 *
 * `locationId` is required by the backend (400 VALIDATION_FAILED when absent).
 * `page` and `size` are optional; backend defaults to page=0, size=20, max 100.
 */
export interface DiscoverRequest {
  locationId: number;
  page?: number;
  size?: number;
}

/**
 * Calls `GET /api/v1/discover` to fetch approved, order-accepting vendors
 * within their delivery radius of the given location.
 *
 * Public endpoint: no Authorization header is sent. The backend validates
 * `locationId` against `service_locations` and returns 400 for unknown ids.
 */
export async function fetchDiscovery(
  params: DiscoverRequest,
): Promise<DiscoveryPageResponse> {
  const response = await api.get<DiscoveryPageResponse>("/discover", {
    params,
  });
  return response.data;
}