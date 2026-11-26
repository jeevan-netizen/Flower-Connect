/**
 * Vendor reviews page (plan Phase 12, task 12.2).
 *
 * Shows the vendor's own reviews (including hidden ones, newest first,
 * paginated) and lets the vendor reply to each review. One reply per review;
 * a second attempt is a 409. A suspended vendor may still reply (D-6).
 *
 * Requires `ROLE_FLORIST`; the backend enforces per-review ownership.
 */
import { useState } from "react";
import { useVendorReviewSummary, useVendorReviews, useReplyToReview } from "@/features/review/queries";
import { StarRating } from "@/features/review/components/StarRating";
import { CustomerErrorState } from "@/features/customer/components/CustomerErrorState";
import { PageHeading } from "@/features/vendor/components/StatCard";
import { Pagination } from "@/shared/components/Pagination";
import { FadeIn } from "@/motion/FadeIn";
export function VendorReviewsPage() {
  const [page, setPage] = useState(0);
  const [replyTarget, setReplyTarget] = useState<number | null>(null);
  const [replyText, setReplyText] = useState("");
  const [replyError, setReplyError] = useState<string | null>(null);

  const summary = useVendorReviewSummary();
  const reviews = useVendorReviews(page);
  const replyToReview = useReplyToReview();

  const isReplying = replyToReview.isPending;

  if (summary.isError || reviews.isError) {
    return (
      <CustomerErrorState
        error={summary.error ?? reviews.error}
        onRetry={() => {
          void summary.refetch();
          void reviews.refetch();
        }}
      />
    );
  }

  const summaryData = summary.data;
  const reviewData = reviews.data?.content ?? [];
  const hasData = summary.isSuccess && reviews.isSuccess;

  const handleReply = async (event: React.FormEvent, reviewId: number) => {
    event.preventDefault();
    setReplyError(null);
    if (!replyText.trim()) return;
    try {
      await replyToReview.mutateAsync({
        reviewId,
        payload: { reply: replyText.trim() },
      });
      setReplyTarget(null);
      setReplyText("");
      void reviews.refetch();
      void summary.refetch();
    } catch (mutationError) {
      setReplyError(
        mutationError instanceof Error
          ? mutationError.message
          : "Could not post reply. Please try again.",
      );
    }
  };

  return (
    <div className="space-y-6">
      <PageHeading title="Reviews" description="What customers say about your shop." />

      {/* Summary */}
      {hasData && (
        <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
          <h2 className="text-base font-semibold text-slate-900 mb-4">Your rating</h2>
          <div className="flex items-start gap-4">
            <div className="flex-1">
              <StarRating
                rating={summaryData?.avgRating ?? null}
                reviewCount={summaryData?.reviewCount ?? 0}
              />
            </div>
            <div className="text-right">
              <p className="text-2xl font-bold text-slate-900">
                {summaryData?.avgRating?.toFixed(1) ?? "—"}
              </p>
              <p className="text-xs text-slate-500">
                {summaryData?.reviewCount ?? 0} review
                {summaryData?.reviewCount !== 1 ? "s" : ""}
              </p>
            </div>
          </div>
          <div className="mt-4 grid grid-cols-5 gap-1">
            {[1, 2, 3, 4, 5].map((star) => {
              const count = summaryData?.ratingDistribution?.[star] ?? 0;
              const total = summaryData?.reviewCount ?? 0;
              const pct = total > 0 ? (count / total) * 100 : 0;
              return (
                <div key={star} className="flex flex-col items-center gap-1">
                  <div className="text-[10px] text-slate-500">{star}</div>
                  <div
                    className="w-full bg-slate-100 rounded-full h-1.5"
                    style={{ width: `${pct}%` }}
                  >
                    <div className="bg-amber-500 rounded-full h-1.5" style={{ width: `${pct}%` }} />
                  </div>
                  <span className="text-[10px] font-medium text-slate-700">{count}</span>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* Review list */}
      <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
        <h2 className="text-base font-semibold text-slate-900 mb-4">
          Your reviews
        </h2>

        {reviews.isPending ? (
          <FadeIn role="status" aria-busy="true" className="text-sm text-slate-600">
            Loading reviews…
          </FadeIn>
        ) : hasData && reviewData.length === 0 ? (
          <p className="text-sm text-slate-600">
            No reviews yet. Reviews appear here after customers receive a
            delivered order.
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
                      Your reply:
                    </p>
                    <p className="mt-0.5 text-brand-700">{review.vendorReply}</p>
                    <p className="mt-0.5 text-[10px] text-brand-500">
                      {new Date(review.vendorRepliedAt!).toLocaleDateString(undefined, {
                        year: "numeric",
                        month: "short",
                        day: "numeric",
                      })}
                    </p>
                  </div>
                )}

                {!review.vendorReply && (
                  <form
                    onSubmit={(e) => handleReply(e, review.id)}
                    className="mt-3 space-y-2"
                  >
                    <div className="relative">
                      <StarRating
                        rating={0}
                        reviewCount={0}
                        interactive
                        value={0}
                        onSelect={() => {}}
                        className="absolute inset-y-0 left-0 flex items-center pl-1 pointer-events-none"
                      />
                      <textarea
                        value={replyTarget === review.id ? replyText : ""}
                        onChange={(e) => {
                          if (replyTarget !== review.id) {
                            setReplyTarget(review.id);
                            setReplyText("");
                          }
                          setReplyText(e.target.value);
                        }}
                        maxLength={1000}
                        rows={2}
                        placeholder="Write a reply…"
                        className="w-full rounded-md border border-slate-300 bg-white pl-8 pr-3 py-2 text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-brand-500"
                      />
                    </div>
                    <div className="flex items-center justify-between gap-2">
                      <button
                        type="submit"
                        disabled={
                          isReplying ||
                          replyTarget !== review.id ||
                          !replyText.trim()
                        }
                        className={`text-xs font-medium rounded-md border border-transparent px-2.5 py-1.5 ${
                          isReplying
                            ? "bg-slate-200 text-slate-500 cursor-not-allowed"
                            : "bg-brand-600 text-white hover:bg-brand-700 ${PRESSABLE} ${FOCUS_RING}"
                        }`}
                      >
                        {isReplying ? "Posting…" : "Post reply"}
                      </button>
                      <button
                        type="button"
                        onClick={() => {
                          setReplyTarget(null);
                          setReplyText("");
                          setReplyError(null);
                        }}
                        className="text-xs text-slate-500 hover:text-slate-700"
                      >
                        Cancel
                      </button>
                    </div>
                    {replyError && (
                      <p className="text-xs text-red-600 bg-red-50 rounded-lg p-1.5 border border-red-100">
                        {replyError}
                      </p>
                    )}
                  </form>
                )}
              </li>
            ))}
          </ul>
        )}

        {reviews.data && !reviews.isPending && !reviews.error && (
          <div className="mt-4">
            <Pagination page={reviews.data} onPageChange={setPage} />
          </div>
        )}
      </div>
    </div>
  );
}
