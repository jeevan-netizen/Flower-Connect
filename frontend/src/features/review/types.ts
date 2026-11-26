/**
 * Review frontend types (plan Phase 12, tasks 12.1-12.2).
 *
 * These mirror four backend contracts:
 *   `POST /api/v1/orders/{orderId}/reviews`   -> customer submits a review
 *   `GET /api/v1/reviews/{reviewId}`          -> customer's own review
 *   `GET /api/v1/vendors/{vendorId}/reviews`  -> public vendor reviews
 *   `PATCH /api/v1/vendors/reviews/{id}/reply`-> vendor reply
 *   `GET /api/v1/vendors/reviews/summary`     -> vendor review aggregate
 *   `GET|PATCH /api/v1/admin/reviews/...`     -> admin moderation
 *
 * Public responses mask customer identity (first name + last initial only) and
 * never return customer or user IDs, or order IDs (privacy rule, task 12.2).
 */

/** One review as the public sees it: masked customer name, no internal IDs. */
export interface ReviewResponse {
  id: number;
  rating: number;
  comment: string | null;
  customerName: string;
  vendorReply: string | null;
  vendorRepliedAt: string | null;
  createdAt: string;
}

/** One review as its owner or an administrator sees it (may carry order/customer IDs). */
export interface ReviewDetailResponse {
  id: number;
  orderId: number;
  customerId: number;
  rating: number;
  comment: string | null;
  customerName: string;
  hidden: boolean;
  hiddenReason: string | null;
  vendorReply: string | null;
  vendorRepliedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

/** Vendor review aggregate over visible (non-hidden) reviews. */
export interface VendorReviewSummary {
  avgRating: number | null;
  reviewCount: number;
  ratingDistribution: Record<number, number>;
}

/** Request body for submitting a review (rating 1-5, optional comment). */
export interface ReviewRequest {
  rating: number;
  comment: string | null;
}

/** Request body for a vendor reply (required, max 1000 chars). */
export interface VendorReplyRequest {
  reply: string;
}

/** Filter for the admin review listing. */
export interface AdminReviewFilters {
  hidden: boolean | null;
  page: number;
}

/** Admin analytics overview response. */
export interface AnalyticsOverviewResponse {
  totalDeliveredOrders: number;
  marketplaceSales: number;
  platformRevenue: number;
  activeVendors: number;
  activeCustomers: number;
  topVendors: AnalyticsTopVendor[];
  disputeRate: number | null;
}

/** One top-vendor entry in the analytics overview. */
export interface AnalyticsTopVendor {
  vendorId: number;
  businessName: string;
  marketplaceSales: number;
  deliveredOrders: number;
}

/** One daily trend data point. */
export interface AnalyticsDailyTrend {
  date: string;
  orderCount: number;
  revenue: number;
}
