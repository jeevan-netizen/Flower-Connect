import api from "@/shared/lib/api";
import type { DiscoveryPageResponse, SearchPageResponse, StorefrontResponse } from "@/features/customer/types";

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
 * Search request parameters.
 *
 * `locationId` is required by the backend (400 VALIDATION_FAILED when absent).
 * All other parameters are optional.
 */
export interface SearchRequest {
  locationId: number;
  q?: string;
  category?: number;
  priceMin?: number;
  priceMax?: number;
  sort?: "distance" | "price_asc" | "price_desc" | "name";
  vendorId?: number;
  page?: number;
  size?: number;
}

/**
 * Storefront request parameters.
 *
 * `vendorId` is required by the backend (400 VALIDATION_FAILED when absent or non-positive).
 * `page` and `size` are optional; backend defaults to page=0, size=20, max 100.
 */
export interface StorefrontRequest {
  vendorId: number;
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

/**
 * Calls `GET /api/v1/search` to fetch products matching the search criteria.
 *
 * Public endpoint: no Authorization header is sent. The backend validates
 * `locationId` against `service_locations` and returns 400 for unknown ids.
 * Unknown/inactive category returns 400. Invalid sort value returns 400.
 * `priceMin > priceMax` returns 400.
 * Zero results returns an empty page (not an error).
 */
export async function fetchSearch(
  params: SearchRequest,
): Promise<SearchPageResponse> {
  const response = await api.get<SearchPageResponse>("/search", {
    params,
  });
  return response.data;
}

/**
 * Calls `GET /api/v1/vendors/{vendorId}/storefront` to fetch a vendor's public storefront.
 *
 * Public endpoint: no Authorization header is sent. The backend validates
 * `vendorId` (must be positive) and returns 404 for unknown or non-approved vendors.
 * The 404 response does not distinguish between unknown and non-approved vendors.
 * Zero results returns an empty products page (not an error).
 */
export async function fetchStorefront(
  params: StorefrontRequest,
): Promise<StorefrontResponse> {
  const response = await api.get<StorefrontResponse>(`/vendors/${params.vendorId}/storefront`, {
    params: {
      page: params.page,
      size: params.size,
    },
  });
  return response.data;
}