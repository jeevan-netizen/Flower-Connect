import { useQuery } from "@tanstack/react-query";
import { fetchDiscovery, fetchSearch, type DiscoverRequest, type SearchRequest } from "@/features/customer/api";
import type { DiscoveryPageResponse, SearchPageResponse } from "@/features/customer/types";

/**
 * TanStack Query keys for vendor discovery.
 *
 * The key includes all request-defining values (locationId, page, size) so
 * a location change automatically produces a new cache entry and the old
 * results are never shown for the new location.
 */
export const discoveryKeys = {
  all: ["discovery"] as const,
  list: (params: DiscoverRequest) => ["discovery", "list", params] as const,
};

/**
 * Fetches the discovery page for the given parameters.
 *
 * Returns `undefined` data when `locationId` is not provided, allowing the
 * caller to render the "no location selected" state without issuing a request.
 */
export function useDiscovery(params: DiscoverRequest | null) {
  return useQuery<DiscoveryPageResponse, Error>({
    queryKey: params ? discoveryKeys.list(params) : discoveryKeys.all,
    queryFn: () => fetchDiscovery(params!),
    enabled: params !== null,
    staleTime: 30_000,
    gcTime: 5 * 60_000,
    placeholderData: (previousData) => previousData,
  });
}

/**
 * Invalidates all discovery queries (e.g., after a location change that
 * should refresh the current page). Currently unused but available for
 * future mutations that might affect vendor visibility.
 */
export function invalidateDiscovery() {
  // No-op for now — discovery is read-only in Phase 4.
  // If a future phase adds vendor approval/suspension that affects
  // public discovery, this would call queryClient.invalidateQueries({ queryKey: discoveryKeys.all });
}

/**
 * TanStack Query keys for product search.
 *
 * The key includes all request-defining values so a filter change
 * automatically produces a new cache entry and the old results are never
 * shown for the new query.
 */
export const searchKeys = {
  all: ["search"] as const,
  list: (params: SearchRequest) => ["search", "list", params] as const,
};

/**
 * Fetches the search page for the given parameters.
 *
 * Returns `undefined` data when `locationId` is not provided (via enabled: false),
 * allowing the caller to render the "no location selected" state without
 * issuing a request.
 */
export function useSearch(params: SearchRequest | null) {
  return useQuery<SearchPageResponse, Error>({
    queryKey: params ? searchKeys.list(params) : searchKeys.all,
    queryFn: () => fetchSearch(params!),
    enabled: params !== null && params.locationId !== undefined,
    staleTime: 30_000,
    gcTime: 5 * 60_000,
    placeholderData: (previousData) => previousData,
  });
}

/**
 * Invalidates all search queries (e.g., after a location change that
 * should refresh the current page). Currently unused but available for
 * future mutations that might affect product visibility.
 */
export function invalidateSearch() {
  // No-op for now — search is read-only in Phase 4.
}