/**
 * Admin review moderation page (plan Phase 12, task 12.2).
 *
 * Lists all reviews with an optional hidden-state filter, newest first,
 * paginated. Admins can hide or unhide any review; each action writes an
 * audit-log row and recomputes the vendor aggregate.
 *
 * Requires `ROLE_ADMIN`; `SecurityConfig` matches `/api/v1/admin/reviews/**`
 * with `hasRole("ADMIN")` on every request.
 */
import { useState } from "react";
import { useAdminReviews, useModerateReview } from "@/features/review/queries";
import { StarRating } from "@/features/review/components/StarRating";
import { CustomerErrorState } from "@/features/customer/components/CustomerErrorState";
import { PageHeading } from "@/features/vendor/components/StatCard";
import { Pagination } from "@/shared/components/Pagination";
import { FadeIn } from "@/motion/FadeIn";
import { FOCUS_RING, PRESSABLE } from "@/motion/pressable";
export function AdminReviewsPage() {
  const [page, setPage] = useState(0);
  const [hiddenFilter, setHiddenFilter] = useState<boolean | null>(null);
  const [moderateTarget, setModerateTarget] = useState<number | null>(null);

  const adminReviews = useAdminReviews(hiddenFilter, page);
  const moderateReview = useModerateReview();

  const isModerating = moderateReview.isPending;

  if (adminReviews.isError) {
    return (
      <CustomerErrorState
        error={adminReviews.error}
        onRetry={() => void adminReviews.refetch()}
      />
    );
  }

  const reviewData = adminReviews.data?.content ?? [];
  const hasData = adminReviews.isSuccess;

  const handleHide = (reviewId: number, reason: string) => {
    setModerateTarget(reviewId);
    void moderateReview.mutateAsync({ reviewId, action: "hide", reason }).finally(() => {
      setModerateTarget(null);
      void adminReviews.refetch();
    });
  };

  const handleUnhide = (reviewId: number) => {
    setModerateTarget(reviewId);
    void moderateReview.mutateAsync({ reviewId, action: "unhide" }).finally(() => {
      setModerateTarget(null);
      void adminReviews.refetch();
    });
  };

  return (
    <div className="space-y-6">
      <PageHeading
        title="Review moderation"
        description="Hide or unhide reviews across the platform."
      />

      {/* Filter */}
      <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm flex flex-wrap items-center gap-3">
        <label className="text-xs font-medium text-slate-700">Filter</label>
        <button
          onClick={() => setHiddenFilter(null)}
          className={`px-3 py-1 rounded-md text-sm font-medium transition-colors ${
            hiddenFilter === null
              ? "bg-brand-600 text-white"
              : "bg-slate-100 text-slate-700 hover:bg-slate-200"
          } ${FOCUS_RING} ${PRESSABLE}`}
        >
          All
        </button>
        <button
          onClick={() => setHiddenFilter(false)}
          className={`px-3 py-1 rounded-md text-sm font-medium transition-colors ${
            hiddenFilter === false
              ? "bg-brand-600 text-white"
              : "bg-slate-100 text-slate-700 hover:bg-slate-200"
          } ${FOCUS_RING} ${PRESSABLE}`}
        >
          Visible
        </button>
        <button
          onClick={() => setHiddenFilter(true)}
          className={`px-3 py-1 rounded-md text-sm font-medium transition-colors ${
            hiddenFilter === true
              ? "bg-brand-600 text-white"
              : "bg-slate-100 text-slate-700 hover:bg-slate-200"
          } ${FOCUS_RING} ${PRESSABLE}`}
        >
          Hidden
        </button>
      </div>

      {/* Review list */}
      <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
        {adminReviews.isPending ? (
          <FadeIn role="status" aria-busy="true" className="text-sm text-slate-600">
            Loading reviews…
          </FadeIn>
        ) : hasData && reviewData.length === 0 ? (
          <p className="text-sm text-slate-600">
            No reviews match this filter.
          </p>
        ) : (
          <ul className="space-y-4">
            {reviewData.map((review) => (
              <li
                key={review.id}
                className="flex flex-col gap-2 pt-4 border-t border-slate-100 first:pt-0 first:border-0"
              >
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div className="flex items-center gap-2">
                    <StarRating
                      rating={review.rating}
                      reviewCount={1}
                      className="text-sm"
                    />
                    <span className="text-xs font-medium text-slate-900">
                      {review.customerName}
                    </span>
                    {review.hidden && (
                      <span className="text-[10px] font-medium text-amber-600 bg-amber-50 rounded px-2 py-0.5 border border-amber-100">
                        Hidden
                      </span>
                    )}
                  </div>
                  <p className="text-xs text-slate-500">
                    Order #{review.orderId} ·
                    {new Date(review.createdAt).toLocaleDateString(undefined, {
                      year: "numeric",
                      month: "short",
                      day: "numeric",
                    })}
                  </p>
                </div>

                {review.comment && (
                  <p className="text-sm text-slate-700">{review.comment}</p>
                )}

                {review.vendorReply && (
                  <div className="mt-1 rounded-lg border border-brand-100 bg-brand-50 p-2 text-sm">
                    <p className="text-xs font-semibold text-brand-900">
                      Vendor reply:
                    </p>
                    <p className="mt-0.5 text-brand-700">{review.vendorReply}</p>
                  </div>
                )}

                <div className="mt-2 flex items-center gap-2">
                  {!review.hidden && (
                    <button
                      onClick={() => {
                        const reason = window.prompt(
                          "Reason for hiding this review:",
                        );
                        if (reason) {
                          handleHide(review.id, reason.trim());
                        }
                      }}
                      disabled={isModerating && moderateTarget !== null}
                      className={`text-xs font-medium rounded-md border border-transparent px-2.5 py-1.5 ${
                        isModerating && moderateTarget !== null
                          ? "bg-slate-200 text-slate-500 cursor-not-allowed"
                          : "bg-red-600 text-white hover:bg-red-700 ${PRESSABLE} ${FOCUS_RING}"
                      }`}
                    >
                      {isModerating && moderateTarget === review.id
                        ? "Hiding…"
                        : "Hide"}
                    </button>
                  )}
                  {review.hidden && (
                    <button
                      onClick={() => handleUnhide(review.id)}
                      disabled={isModerating && moderateTarget !== null}
                      className={`text-xs font-medium rounded-md border border-transparent px-2.5 py-1.5 ${
                        isModerating && moderateTarget !== null
                          ? "bg-slate-200 text-slate-500 cursor-not-allowed"
                          : "bg-brand-600 text-white hover:bg-brand-700 ${PRESSABLE} ${FOCUS_RING}"
                      }`}
                    >
                      {isModerating && moderateTarget === review.id
                        ? "Unhiding…"
                        : "Unhide"}
                    </button>
                  )}
                </div>
              </li>
            ))}
          </ul>
        )}

        {adminReviews.data && !adminReviews.isPending && !adminReviews.error && (
          <div className="mt-4">
            <Pagination page={adminReviews.data} onPageChange={setPage} />
          </div>
        )}
      </div>
    </div>
  );
}
