import { useMutation, useQuery, useQueryClient, type QueryClient } from "@tanstack/react-query";
import { fetchOwnProfile, updateOwnProfile } from "@/features/vendor/api";
import type { VendorProfile, VendorProfileUpdateRequest } from "@/features/vendor/types";

export const vendorKeys = {
  all: ["vendor"] as const,
  profile: ["vendor", "profile"] as const,
};

/**
 * The single vendor read. Every vendor screen (dashboard, profile, delivery
 * settings, operating hours) and the status banner in the layout share this one
 * query key, so the whole area costs a single `GET /api/v1/vendors/profile`.
 *
 * No polling is configured: in Phase 2 the only thing that changes this data is
 * the vendor's own save or an admin transition, and both are handled by
 * invalidating/writing the cache on the next navigation or mutation.
 */
export function useVendorProfile() {
  return useQuery({
    queryKey: vendorKeys.profile,
    queryFn: fetchOwnProfile,
  });
}

/**
 * The single vendor write. All four vendor screens save through the same PUT, so
 * invalidation happens in one place.
 */
export function useUpdateVendorProfile() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: VendorProfileUpdateRequest) => updateOwnProfile(payload),
    onSuccess: (profile: VendorProfile) => {
      // The PUT response is the complete, server-normalised profile, so writing
      // it into the cache replaces the old value without a second round trip.
      // Nothing else in Phase 2 reads vendor data, so no other key is stale.
      queryClient.setQueryData(vendorKeys.profile, profile);
    },
  });
}

/**
 * Drops every cached vendor query. Called on logout so a different account
 * signing in on the same tab never sees the previous vendor's data before its
 * own query resolves.
 */
export function clearVendorCache(queryClient: QueryClient): void {
  queryClient.removeQueries({ queryKey: vendorKeys.all });
}
