import api from "@/shared/lib/api";
import type { PageResponse } from "@/shared/types";
import type {
  AdminReviewFilters,
  ReviewDetailResponse,
  ReviewRequest,
  ReviewResponse,
  VendorReplyRequest,
  VendorReviewSummary,
} from "@/features/review/types";

/**
 * Review API client (plan Phase 12, tasks 12.1-12.2).
 *
 * Customer submission and retrieval require the `CUSTOMER` role; vendor reply
 * and vendor listing require `FLORIST`; admin moderation requires `ADMIN`.
 * Public vendor reviews are `permitAll` for approved vendors. Ownership is
 * enforced server-side per request; the client never filters results itself.
 */

/**
 * `POST /api/v1/orders/{orderId}/reviews` — submit a review for a delivered
 * order. Requires `ROLE_CUSTOMER` and the caller must be the order's customer.
 * One review per order; a repeat submission is a 409.
 */
export async function submitReview(
  orderId: number,
  payload: ReviewRequest,
): Promise<ReviewDetailResponse> {
  const response = await api.post<ReviewDetailResponse>(
    `/orders/${orderId}/reviews`,
    payload,
  );
  return response.data;
}

/**
 * `GET /api/v1/reviews/{reviewId}` — the caller's own review, even if hidden.
 * Requires `ROLE_CUSTOMER` and the caller must be the review's customer.
 */
export async function fetchOwnReview(reviewId: number): Promise<ReviewDetailResponse> {
  const response = await api.get<ReviewDetailResponse>(`/reviews/${reviewId}`);
  return response.data;
}

/**
 * `GET /api/v1/vendors/{vendorId}/reviews` — public visible reviews for an
 * approved vendor, newest first, paginated. `permitAll`; a non-approved vendor
 * is a 404 (consistent with the storefront rule).
 */
export async function fetchPublicVendorReviews(
  vendorId: number,
  page: number,
  size: number,
): Promise<PageResponse<ReviewResponse>> {
  const response = await api.get<PageResponse<ReviewResponse>>(
    `/vendors/${vendorId}/reviews`,
    { params: { page, size } },
  );
  return response.data;
}

/**
 * `GET /api/v1/vendors/reviews` — the authenticated vendor's own reviews
 * (including hidden ones), newest first, paginated. Requires `ROLE_FLORIST`.
 */
export async function fetchVendorReviews(
  page: number,
  size: number,
): Promise<PageResponse<ReviewDetailResponse>> {
  const response = await api.get<PageResponse<ReviewDetailResponse>>("/vendors/reviews", {
    params: { page, size },
  });
  return response.data;
}

/**
 * `GET /api/v1/vendors/reviews/summary` — average rating, count and 1-5
 * distribution over the vendor's visible reviews. Requires `ROLE_FLORIST`.
 */
export async function fetchVendorReviewSummary(): Promise<VendorReviewSummary> {
  const response = await api.get<VendorReviewSummary>("/vendors/reviews/summary");
  return response.data;
}

/**
 * `PATCH /api/v1/vendors/reviews/{reviewId}/reply` — add or replace the
 * vendor's reply. One reply per review; a second attempt is 409. Requires
 * `ROLE_FLORIST` and the caller must be the review's vendor. Suspended vendors
 * may reply (D-6).
 */
export async function replyToReview(
  reviewId: number,
  payload: VendorReplyRequest,
): Promise<ReviewDetailResponse> {
  const response = await api.patch<ReviewDetailResponse>(
    `/vendors/reviews/${reviewId}/reply`,
    payload,
  );
  return response.data;
}

/**
 * `PATCH /api/v1/admin/reviews/{reviewId}/hide` — hide a review from public
 * storefronts and aggregates (requires `ROLE_ADMIN`, reason required).
 */
export async function hideReview(reviewId: number, reason: string): Promise<ReviewDetailResponse> {
  const response = await api.patch<ReviewDetailResponse>(
    `/admin/reviews/${reviewId}/hide`,
    null,
    { params: { reason } },
  );
  return response.data;
}

/**
 * `PATCH /api/v1/admin/reviews/{reviewId}/unhide` — restore a hidden review
 * (requires `ROLE_ADMIN`).
 */
export async function unhideReview(reviewId: number): Promise<ReviewDetailResponse> {
  const response = await api.patch<ReviewDetailResponse>(
    `/admin/reviews/${reviewId}/unhide`,
    null,
  );
  return response.data;
}

/**
 * `GET /api/v1/admin/reviews` — paginated admin review listing with optional
 * hidden-state filter, newest first. Requires `ROLE_ADMIN`.
 */
export async function fetchAdminReviews(
  filters: AdminReviewFilters,
): Promise<PageResponse<ReviewDetailResponse>> {
  const params: Record<string, string | number> = {
    page: String(filters.page),
    size: "20",
  };
  if (filters.hidden !== null) {
    params.hidden = filters.hidden ? "true" : "false";
  }
  const response = await api.get<PageResponse<ReviewDetailResponse>>("/admin/reviews", {
    params,
  });
  return response.data;
}
