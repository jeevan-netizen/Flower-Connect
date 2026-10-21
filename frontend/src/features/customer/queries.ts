import { useQuery } from "@tanstack/react-query";
import {
  fetchCategories,
  fetchDiscovery,
  fetchPublicProduct,
  fetchSearch,
  fetchStorefront,
} from "@/features/customer/api";
import type { SearchFilters } from "@/features/customer/types";

/**
 * Query keys for the customer catalogue.
 *
 * Every key is positional and carries the values that change the answer, so a
 * different location, a different filter or a different page is a different
 * cache entry rather than a re-render of the previous one. The location is
 * part of every key because it changes which florists and products are
 * eligible — it is not a display filter.
 */
export const customerKeys = {
  all: ["customer"] as const,
  discovery: (locationId: number, page: number) =>
    ["customer", "discovery", locationId, page] as const,
  search: (
    locationId: number,
    q: string,
    categoryId: number | null,
    priceMin: number | null,
    priceMax: number | null,
    sort: string,
    vendorId: number | null,
    page: number,
  ) =>
    [
      "customer",
      "search",
      locationId,
      q,
      categoryId,
      priceMin,
      priceMax,
      sort,
      vendorId,
      page,
    ] as const,
  storefront: (vendorId: number, page: number) =>
    ["customer", "storefront", vendorId, page] as const,
  product: (productId: number, locationId: number) =>
    ["customer", "product", productId, locationId] as const,
  categories: ["customer", "categories"] as const,
};

/** Reference data changes rarely, so it may be reused for a while. */
const REFERENCE_STALE_TIME = 5 * 60 * 1000;

/**
 * The florists that deliver to the selected location, nearest first.
 *
 * Disabled until a location exists: the endpoint requires one, and asking
 * without it would be a guaranteed 400.
 */
export function useDiscovery(locationId: number | null, page: number) {
  return useQuery({
    queryKey: customerKeys.discovery(locationId ?? 0, page),
    queryFn: ({ signal }) => fetchDiscovery(locationId as number, page, 20, signal),
    enabled: locationId !== null,
    staleTime: 60_000,
  });
}

/**
 * Products matching the customer's filters, for the selected location.
 *
 * Disabled until a location exists, for the same reason as discovery. A
 * caller can switch it off as well — the search surface is also the layout
 * for its product-detail child, so a product opened directly has no results
 * behind it and no results to fetch.
 */
export function useSearch(
  locationId: number | null,
  filters: SearchFilters,
  enabled = true,
) {
  return useQuery({
    queryKey: customerKeys.search(
      locationId ?? 0,
      filters.q,
      filters.categoryId,
      filters.priceMin,
      filters.priceMax,
      filters.sort,
      filters.vendorId,
      filters.page,
    ),
    queryFn: ({ signal }) => fetchSearch(locationId as number, filters, signal),
    enabled: enabled && locationId !== null,
    staleTime: 60_000,
  });
}

/**
 * A florist's storefront.
 *
 * Enabled by default: the endpoint takes no location, so the storefront is
 * readable without one. A caller can switch it off — the storefront route is
 * also the layout for its product-detail child, so a product opened directly
 * has no shop behind it and no shop to fetch.
 */
export function useStorefront(vendorId: number, page: number, enabled = true) {
  return useQuery({
    queryKey: customerKeys.storefront(vendorId, page),
    queryFn: ({ signal }) => fetchStorefront(vendorId, page, 20, signal),
    enabled,
    staleTime: 60_000,
  });
}

/**
 * One product's public detail.
 *
 * Disabled until a location exists: the endpoint requires one to decide
 * whether the product is deliverable to the customer. A caller can switch it
 * off as well, which is how a malformed product id is answered without
 * issuing a request at all.
 */
export function usePublicProduct(
  productId: number,
  locationId: number | null,
  enabled = true,
) {
  return useQuery({
    queryKey: customerKeys.product(productId, locationId ?? 0),
    queryFn: ({ signal }) => fetchPublicProduct(productId, locationId as number, signal),
    enabled: enabled && locationId !== null,
    staleTime: 60_000,
  });
}

/** The active top-level categories, for the search filter chips. */
export function useCategories() {
  return useQuery({
    queryKey: customerKeys.categories,
    queryFn: ({ signal }) => fetchCategories(signal),
    staleTime: REFERENCE_STALE_TIME,
  });
}
