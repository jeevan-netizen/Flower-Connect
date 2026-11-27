import {
  useMutation,
  useQuery,
  useQueryClient,
  type QueryClient,
} from "@tanstack/react-query";
import {
  approveVendor,
  adminCancelOrder,
  approveServiceLocationProposal,
  fetchAdminUsers,
  fetchAdminVendors,
  fetchRefundQueue,
  fetchServiceLocationProposals,
  markRefunded,
  rejectServiceLocationProposal,
  rejectVendor,
  reinstateVendor,
  suspendVendor,
  updateUserStatus,
} from "@/features/admin/api";
import type {
  AdminUser,
  UserStatusUpdateRequest,
  VendorAdminMutation,
  VendorListFilters,
  UserListFilters,
  RefundQueueFilters,
} from "@/features/admin/types";

/**
 * Query keys for the admin area (plan task 2.10).
 *
 * Both listings are *filtered and paged* queries, so their keys carry the filter
 * object rather than sitting under a single constant key. That means changing a
 * filter is a cache miss (a new key) instead of a stale render of the previous
 * filter's rows, and `invalidateQueries({ queryKey: adminKeys.vendors.all })`
 * still reaches every page/filter combination after a status change.
 */
export const adminKeys = {
  all: ["admin"] as const,
  vendors: {
    all: ["admin", "vendors"] as const,
    list: (filters: VendorListFilters) =>
      ["admin", "vendors", "list", filters.status, filters.page] as const,
  },
  users: {
    all: ["admin", "users"] as const,
    list: (filters: UserListFilters) =>
      ["admin", "users", "list", filters.role, filters.status, filters.page] as const,
  },
  refunds: {
    all: ["admin", "refunds"] as const,
    list: (filters: RefundQueueFilters) =>
      [
        "admin",
        "refunds",
        "list",
        filters.status ?? null,
        filters.vendorId ?? null,
        filters.fromDate ?? null,
        filters.toDate ?? null,
        filters.page,
      ] as const,
  },
  proposals: {
    all: ["admin", "proposals"] as const,
    list: ["admin", "proposals", "list"] as const,
  },
};

/**
 * `GET /api/v1/admin/vendors`. Paged, so `placeholderData` is left at its default
 * (no previous page retained) — a page change must not render stale rows under a
 * new page number.
 */
export function useAdminVendors(filters: VendorListFilters) {
  return useQuery({
    queryKey: adminKeys.vendors.list(filters),
    queryFn: () => fetchAdminVendors(filters),
  });
}

/** `GET /api/v1/admin/users`, filtered by role and/or status. */
export function useAdminUsers(filters: UserListFilters) {
  return useQuery({
    queryKey: adminKeys.users.list(filters),
    queryFn: () => fetchAdminUsers(filters),
  });
}

/**
 * One mutation for all four admin vendor transitions.
 *
 * They differ only in HTTP verb (all `POST`), path segment and whether a body is
 * sent, so they share a single mutation keyed by action. The variables are the
 * `VendorAdminMutation` union rather than one object with an optional `reason`:
 * that is what makes "reject with no reason" a compile error instead of a 400
 * discovered at runtime, and it keeps `approve`/`reinstate` from ever sending a
 * stray `{ reason }` body their routes do not accept.
 *
 * `onSuccess` invalidates the **whole** vendor listing rather than writing one
 * updated profile into a single page's cache: a transition can move a profile out
 * of the current status filter or off the current page entirely, so the list has
 * to be re-read to stay truthful about both.
 */
export function useVendorAdminAction() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (variables: VendorAdminMutation) => {
      const { action, profileId } = variables;

      switch (action) {
        case "approve":
          return approveVendor(profileId);
        case "reinstate":
          return reinstateVendor(profileId);
        // `action` is narrowed to `ReasonedVendorAction` here by the switch, so
        // `reason` is present and required — no cast and no optional handling.
        case "reject":
          return rejectVendor(profileId, { reason: variables.reason });
        case "suspend":
          return suspendVendor(profileId, { reason: variables.reason });
      }
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: adminKeys.vendors.all });
    },
  });
}

/**
 * `GET /api/v1/locations/proposals` — all PENDING proposals, oldest first.
 * No filters or pagination: every pending proposal fits on one screen.
 */
export function useServiceLocationProposals() {
  return useQuery({
    queryKey: adminKeys.proposals.list,
    queryFn: () => fetchServiceLocationProposals(),
  });
}

/**
 * Approves a PENDING proposal. Requires administrator-supplied coordinates and
 * timezone — data the registration form cannot provide. On success the whole
 * proposal listing is invalidated so the approved proposal drops out of view.
 */
export function useApproveServiceLocationProposal() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (variables: {
      proposalId: number;
      latitude: number;
      longitude: number;
      timezone: string;
    }) =>
      approveServiceLocationProposal(variables.proposalId, {
        latitude: variables.latitude,
        longitude: variables.longitude,
        timezone: variables.timezone,
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: adminKeys.proposals.all });
    },
  });
}

/**
 * Rejects a PENDING proposal with a mandatory reason. On success the whole
 * proposal listing is invalidated so the rejected proposal drops out of view.
 */
export function useRejectServiceLocationProposal() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (variables: { proposalId: number; reason: string }) =>
      rejectServiceLocationProposal(variables.proposalId, {
        reason: variables.reason,
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: adminKeys.proposals.all });
    },
  });
}

/**
 * `PATCH /api/v1/admin/users/{id}/status`.
 *
 * Same reasoning as the vendor action: the target may leave the active status
 * filter or move to another page, so every filtered listing is re-read rather
 * than patched in place.
 */
export function useUpdateUserStatus() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ userId, ...payload }: UserStatusUpdateRequest & { userId: number }) =>
      updateUserStatus(userId, payload),
    onSuccess: (_user: AdminUser) => {
      void queryClient.invalidateQueries({ queryKey: adminKeys.users.all });
    },
  });
}

/**
 * Drops every cached admin query. Called on logout so the next account to sign in
 * on the same tab cannot see the previous admin's user/vendor listing before its
 * own queries resolve.
 *
 * Mirrors `clearVendorCache` for the admin area.
 */
export function clearAdminCache(queryClient: QueryClient): void {
  queryClient.removeQueries({ queryKey: adminKeys.all });
}

/**
 * One page of `REFUND_PENDING` payments for the admin refund queue, keyed by
 * every filter and page so a filter change is a cache miss rather than a stale
 * render. On success of a mark-refunded mutation the whole listing is
 * invalidated: a refunded payment leaves the queue entirely.
 */
export function useRefundQueue(filters: RefundQueueFilters) {
  return useQuery({
    queryKey: adminKeys.refunds.list(filters),
    queryFn: () => fetchRefundQueue(filters),
  });
}

/**
 * Marks a `REFUND_PENDING` payment as `REFUNDED`. Requires a reference note;
 * the backend enforces the state transition (`REFUND_PENDING -> REFUNDED`)
 * and returns 409 for any other state. On success the whole refund queue is
 * invalidated so the marked payment drops out of the listing.
 */
export function useMarkRefunded() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (variables: { orderId: number; referenceNote: string }) =>
      markRefunded(variables.orderId, { referenceNote: variables.referenceNote }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: adminKeys.refunds.all });
    },
  });
}

/**
 * Admin cancels any order. A reason is mandatory. On success the refund queue
 * (if the order had a pending refund) and any other cached admin listings are
 * invalidated.
 */
export function useAdminCancelOrder() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (variables: { orderId: number; reason: string }) =>
      adminCancelOrder(variables.orderId, variables.reason),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: adminKeys.refunds.all });
      void queryClient.invalidateQueries({ queryKey: adminKeys.all });
    },
  });
}