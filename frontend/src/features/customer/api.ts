import api from "@/shared/lib/api";
import type { PageResponse } from "@/shared/types";
import type { CategoryPageResponse } from "@/features/vendor/types";
import type {
  DiscoveryVendor,
  PublicProduct,
  SearchFilters,
  Storefront,
} from "@/features/customer/types";

/**
 * The customer's public catalogue API client (plan tasks 4.3, 4.4, 4.5, 4.9).
 *
 * Every endpoint here is `permitAll`: browsing is public, and the visibility
 * rules — active products, available stock, approved and accepting florists,
 * and the delivery radius — are enforced server-side per request. The client
 * therefore never filters results itself; it asks and renders the answer.
 */

/**
 * `GET /api/v1/discover` — florists that deliver to the selected location.
 *
 * `locationId` is required: without it the backend cannot compute a distance
 * and answers 400. The caller supplies it from the session store.
 */
export async function fetchDiscovery(
  locationId: number,
  page: number,
  size: number,
  signal?: AbortSignal,
): Promise<PageResponse<DiscoveryVendor>> {
  const response = await api.get<PageResponse<DiscoveryVendor>>("/discover", {
    params: { locationId, page, size },
    signal,
  });
  return response.data;
}

/**
 * `GET /api/v1/search` — products from florists that deliver to the location.
 *
 * Optional filters are omitted rather than sent as `null`, so the request
 * carries only what the customer actually narrowed. `priceMin`/`priceMax` are
 * only meaningful together, and the backend refuses `priceMin > priceMax` with
 * a 400 — the search page checks that first so the customer sees the problem
 * next to the fields rather than in an error banner.
 */
export async function fetchSearch(
  locationId: number,
  filters: SearchFilters,
  signal?: AbortSignal,
): Promise<PageResponse<PublicProduct>> {
  const params: Record<string, string | number> = { locationId };

  const q = filters.q.trim();
  if (q) {
    params.q = q;
  }
  if (filters.categoryId !== null) {
    params.category = filters.categoryId;
  }
  if (filters.priceMin !== null) {
    params.priceMin = filters.priceMin;
  }
  if (filters.priceMax !== null) {
    params.priceMax = filters.priceMax;
  }
  params.sort = filters.sort;
  if (filters.vendorId !== null) {
    params.vendorId = filters.vendorId;
  }
  params.page = filters.page;

  const response = await api.get<PageResponse<PublicProduct>>("/search", {
    params,
    signal,
  });
  return response.data;
}

/**
 * `GET /api/v1/vendors/{id}/storefront` — a florist's public storefront.
 *
 * No `locationId`: the storefront is not radius-filtered, because the customer
 * has already chosen this florist. An unknown, unapproved or non-accepting
 * florist is a 404, and the backend does not distinguish the three.
 */
export async function fetchStorefront(
  vendorId: number,
  page: number,
  size: number,
  signal?: AbortSignal,
): Promise<Storefront> {
  const response = await api.get<Storefront>(`/vendors/${vendorId}/storefront`, {
    params: { page, size },
    signal,
  });
  return response.data;
}

/**
 * `GET /api/v1/products/{id}` — one product's public detail.
 *
 * `locationId` is required, and every ineligibility — inactive, sold out, an
 * unapproved or non-accepting florist, or a florist outside its delivery
 * radius — is a 404 that does not say which. The client renders one message
 * for all of them.
 */
export async function fetchPublicProduct(
  productId: number,
  locationId: number,
  signal?: AbortSignal,
): Promise<PublicProduct> {
  const response = await api.get<PublicProduct>(`/products/${productId}`, {
    params: { locationId },
    signal,
  });
  return response.data;
}

/**
 * `GET /api/v1/categories` — the active category reference list.
 *
 * The public read returns the **active top-level** categories only (D-17), so
 * the search filter chips are one level deep. The search endpoint accepts any
 * active category id, including children, but a child id is not discoverable
 * from this list — that gap is known issue 025 and is not papered over here.
 *
 * This client is the customer slice's own rather than a shared one, so the
 * customer catalogue is served by its own cache key and does not depend on the
 * vendor area's module.
 */
export async function fetchCategories(signal?: AbortSignal): Promise<CategoryPageResponse> {
  const response = await api.get<CategoryPageResponse>("/categories", { signal });
  return response.data;
}
