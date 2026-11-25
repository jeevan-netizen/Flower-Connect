import { useMutation, useQuery, useQueryClient, type QueryClient } from "@tanstack/react-query";
import {
  acceptLink,
  addDeposit,
  confirmCollection,
  fetchEligibleVendors,
  fetchFarmerLinks,
  fetchFarmerPassbook,
  fetchVendorCollections,
  fetchVendorFarmerLinks,
  fetchVendorPassbook,
  rejectCollection,
  rejectLink,
  requestCollection,
  requestLink,
  suspendLink,
  reactivateLink,
} from "@/features/farmer/api";
import type { FarmerCollection, FarmerCollectionRequest, FarmerLink, FarmerVendor, Passbook } from "@/features/farmer/types";

export const farmerKeys = {
  all: ["farmer"] as const,
  links: ["farmer", "links"] as const,
  vendorLinks: ["vendor", "farmer-links"] as const,
  eligibleVendors: ["farmer", "vendors"] as const,
  passbook: (linkId: number) => ["farmer", "passbook", linkId] as const,
  vendorCollections: (status: string) => ["vendor", "collections", status] as const,
};

/**
 * Drops every cached farmer query on logout so a different account signing in on
 * the same tab never sees the previous farmer's links or passbook.
 */
export function clearFarmerCache(queryClient: QueryClient): void {
  queryClient.removeQueries({ queryKey: farmerKeys.all });
}

/* -------------------------------------------------------------------------- */
/* Farmer links                                                               */
/* -------------------------------------------------------------------------- */

/** `GET /api/v1/farmer/links` — the authenticated farmer's own links. */
export function useFarmerLinks() {
  return useQuery({
    queryKey: farmerKeys.links,
    queryFn: fetchFarmerLinks,
  });
}

/**
 * `GET /api/v1/farmer/vendors?query=` — vendors eligible to receive a link
 * request, keyed by the search query.
 */
export function useEligibleVendors(query?: string) {
  return useQuery<FarmerVendor[]>({
    queryKey: [...farmerKeys.eligibleVendors, query],
    queryFn: () => fetchEligibleVendors(query),
    enabled: query !== undefined,
  });
}

/** `POST /api/v1/farmer/links`. */
export function useRequestLink() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (vendorId: number) => requestLink(vendorId),
    onSuccess: (_link, _vendorId) => {
      void queryClient.invalidateQueries({ queryKey: farmerKeys.links });
      void queryClient.invalidateQueries({
        queryKey: [...farmerKeys.eligibleVendors, undefined],
      });
    },
  });
}

/* -------------------------------------------------------------------------- */
/* Vendor farmer links                                                        */
/* -------------------------------------------------------------------------- */

/** `GET /api/v1/vendor/farmer-links?status=` — links on the caller's vendor. */
export function useVendorFarmerLinks(
  status?: "PENDING" | "ACTIVE" | "REJECTED" | "SUSPENDED",
) {
  return useQuery<FarmerLink[]>({
    queryKey: [...farmerKeys.vendorLinks, status],
    queryFn: () => fetchVendorFarmerLinks(status),
  });
}

/** `POST /api/v1/vendor/farmer-links/{linkId}/accept`. */
export function useAcceptLink() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (linkId: number) => acceptLink(linkId),
    onSuccess: (_link, linkId) => {
      void queryClient.invalidateQueries({ queryKey: farmerKeys.vendorLinks });
      void queryClient.invalidateQueries({ queryKey: farmerKeys.passbook(linkId) });
    },
  });
}

/** `POST /api/v1/vendor/farmer-links/{linkId}/reject`. */
export function useRejectLink() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (linkId: number) => rejectLink(linkId),
    onSuccess: (_link, linkId) => {
      void queryClient.invalidateQueries({ queryKey: farmerKeys.vendorLinks });
      void queryClient.invalidateQueries({ queryKey: farmerKeys.passbook(linkId) });
    },
  });
}

/** `POST /api/v1/vendor/farmer-links/{linkId}/suspend`. */
export function useSuspendLink() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (linkId: number) => suspendLink(linkId),
    onSuccess: (_link, linkId) => {
      void queryClient.invalidateQueries({ queryKey: farmerKeys.vendorLinks });
      void queryClient.invalidateQueries({ queryKey: farmerKeys.passbook(linkId) });
    },
  });
}

/** `POST /api/v1/vendor/farmer-links/{linkId}/reactivate`. */
export function useReactivateLink() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (linkId: number) => reactivateLink(linkId),
    onSuccess: (_link, linkId) => {
      void queryClient.invalidateQueries({ queryKey: farmerKeys.vendorLinks });
      void queryClient.invalidateQueries({ queryKey: farmerKeys.passbook(linkId) });
    },
  });
}

/* -------------------------------------------------------------------------- */
/* Passbook                                                                   */
/* -------------------------------------------------------------------------- */

/** `GET /api/v1/farmer/links/{linkId}/passbook`. */
export function useFarmerPassbook(linkId: number | null) {
  return useQuery<Passbook>({
    queryKey: farmerKeys.passbook(linkId ?? 0),
    queryFn: () => fetchFarmerPassbook(linkId as number),
    enabled: linkId !== null,
  });
}

/** `GET /api/v1/vendor/farmer-links/{linkId}/passbook`. */
export function useVendorPassbook(linkId: number | null) {
  return useQuery<Passbook>({
    queryKey: farmerKeys.passbook(linkId ?? 0),
    queryFn: () => fetchVendorPassbook(linkId as number),
    enabled: linkId !== null,
  });
}

/**
 * `POST /api/v1/farmer/links/{linkId}/collections`.
 *
 * On success the link's passbook is invalidated so the outstanding balance and
 * running balances reflect the new `PENDING` request.
 */
export function useRequestCollection() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ linkId, payload }: { linkId: number; payload: FarmerCollectionRequest }) =>
      requestCollection(linkId, payload),
    onSuccess: (_data, { linkId }) => {
      void queryClient.invalidateQueries({ queryKey: farmerKeys.passbook(linkId) });
    },
  });
}

/* -------------------------------------------------------------------------- */
/* Vendor collection queue                                                    */
/* -------------------------------------------------------------------------- */

/** `GET /api/v1/vendor/farmer-collections?status=`. */
export function useVendorCollections(
  status: "PENDING" | "CONFIRMED" | "REJECTED" = "PENDING",
) {
  return useQuery<FarmerCollection[]>({
    queryKey: farmerKeys.vendorCollections(status),
    queryFn: () => fetchVendorCollections(status),
  });
}

/** `POST /api/v1/vendor/farmer-collections/{paymentId}/confirm`. */
export function useConfirmCollection() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (paymentId: number) => confirmCollection(paymentId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: farmerKeys.vendorCollections("PENDING") });
      // The collection's passbook row is replaced by the server's response.
      void queryClient.invalidateQueries({ queryKey: farmerKeys.vendorCollections("CONFIRMED") });
    },
  });
}

/** `POST /api/v1/vendor/farmer-collections/{paymentId}/reject`. */
export function useRejectCollection() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (paymentId: number) => rejectCollection(paymentId),
    onSuccess: (_collection, _paymentId) => {
      void queryClient.invalidateQueries({ queryKey: farmerKeys.vendorCollections("PENDING") });
      void queryClient.invalidateQueries({ queryKey: farmerKeys.vendorCollections("REJECTED") });
    },
  });
}

/* -------------------------------------------------------------------------- */
/* Vendor deposits                                                            */
/* -------------------------------------------------------------------------- */

/** `POST /api/v1/vendor/farmer-links/{linkId}/deposits`. */
export function useAddDeposit() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      linkId,
      payload,
    }: {
      linkId: number;
      payload: {
        flowerType: string;
        grossKg: number;
        pricePerKg: number;
        depositedAt?: string | null;
      };
    }) => addDeposit(linkId, payload),
    onSuccess: (_deposit, { linkId }) => {
      void queryClient.invalidateQueries({ queryKey: farmerKeys.passbook(linkId) });
    },
  });
}
