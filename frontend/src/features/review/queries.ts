import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  fetchAdminReviews,
  fetchOwnReview,
  fetchPublicVendorReviews,
  fetchVendorReviewSummary,
  fetchVendorReviews,
  hideReview,
  replyToReview,
  submitReview,
  unhideReview,
} from "@/features/review/api";
import {
  fetchAnalyticsDailyTrends,
  fetchAnalyticsOverview,
} from "@/features/admin/api";
import type {
  ReviewRequest,
  VendorReplyRequest,
} from "@/features/review/types";

export const reviewKeys = {
  all: ["review"] as const,
  own: (reviewId: number) => ["review", "own", reviewId] as const,
  vendor: {
    all: ["review", "vendor"] as const,
    list: (page: number) => ["review", "vendor", "list", page] as const,
    summary: ["review", "vendor", "summary"] as const,
  },
  public: (vendorId: number, page: number) =>
    ["review", "public", vendorId, page] as const,
  admin: {
    all: ["review", "admin"] as const,
    list: (hidden: boolean | null, page: number) =>
      ["review", "admin", "list", hidden, page] as const,
  },
};

/**
 * The authenticated customer's own review, even if hidden. Requires `ROLE_CUSTOMER`
 * and ownership (enforced server-side).
 */
export function useOwnReview(reviewId: number | null) {
  return useQuery({
    queryKey: reviewKeys.own(reviewId ?? 0),
    queryFn: () => fetchOwnReview(reviewId as number),
    enabled: reviewId !== null,
  });
}

/**
 * The authenticated vendor's own reviews, newest first, paginated. Requires
 * `ROLE_FLORIST`.
 */
export function useVendorReviews(page: number) {
  return useQuery({
    queryKey: reviewKeys.vendor.list(page),
    queryFn: () => fetchVendorReviews(page, 20),
  });
}

/** The vendor's review aggregate (average, count, distribution). Requires `ROLE_FLORIST`. */
export function useVendorReviewSummary() {
  return useQuery({
    queryKey: reviewKeys.vendor.summary,
    queryFn: fetchVendorReviewSummary,
  });
}

/**
 * Public visible reviews for a vendor, newest first, paginated. `permitAll`; a
 * non-approved vendor is a 404.
 */
export function usePublicVendorReviews(vendorId: number | null, page: number) {
  return useQuery({
    queryKey: reviewKeys.public(vendorId ?? 0, page),
    queryFn: () => fetchPublicVendorReviews(vendorId as number, page, 20),
    enabled: vendorId !== null,
  });
}

/**
 * Paginated admin review listing with optional hidden-state filter. Requires
 * `ROLE_ADMIN`.
 */
export function useAdminReviews(hidden: boolean | null, page: number) {
  return useQuery({
    queryKey: reviewKeys.admin.list(hidden, page),
    queryFn: () => fetchAdminReviews({ hidden, page }),
  });
}

/**
 * `GET /api/v1/admin/analytics/overview` — summary metrics for a date range.
 * Requires `ROLE_ADMIN`.
 */
export function useAnalyticsOverview(fromDate?: string, toDate?: string) {
  return useQuery({
    queryKey: [...reviewKeys.admin.all, "overview", fromDate, toDate] as const,
    queryFn: () => fetchAnalyticsOverview(fromDate, toDate),
    enabled: true,
  });
}

/**
 * `GET /api/v1/admin/analytics/daily-trends` — daily trend series. Requires
 * `ROLE_ADMIN`.
 */
export function useAnalyticsDailyTrends(fromDate?: string, toDate?: string) {
  return useQuery({
    queryKey: [...reviewKeys.admin.all, "daily-trends", fromDate, toDate] as const,
    queryFn: () => fetchAnalyticsDailyTrends(fromDate, toDate),
    enabled: true,
  });
}

/**
 * `POST /api/v1/orders/{orderId}/reviews`. On success the storefront review
 * listing and the vendor summary are invalidated so the new rating is reflected.
 */
export function useSubmitReview() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      orderId,
      payload,
    }: { orderId: number; payload: ReviewRequest }) => submitReview(orderId, payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: reviewKeys.vendor.summary });
    },
  });
}

/**
 * `PATCH /api/v1/vendors/reviews/{reviewId}/reply`. On success the reply is
 * written into the review's own cache and the vendor summary is invalidated.
 */
export function useReplyToReview() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      reviewId,
      payload,
    }: { reviewId: number; payload: VendorReplyRequest }) =>
      replyToReview(reviewId, payload),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: reviewKeys.vendor.summary });
    },
  });
}

/**
 * `PATCH /api/v1/admin/reviews/{reviewId}/hide|/unhide`. On success the public
 * listing, vendor summary and admin listing are invalidated.
 */
export function useModerateReview() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (variables: {
      reviewId: number;
      action: "hide" | "unhide";
      reason?: string;
    }) => {
      const { reviewId, action, reason } = variables;
      if (action === "hide") {
        if (!reason) {
          throw new Error("Hide reason is required");
        }
        return hideReview(reviewId, reason);
      }
      return unhideReview(reviewId);
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: reviewKeys.vendor.summary });
      void queryClient.invalidateQueries({ queryKey: reviewKeys.all });
    },
  });
}
