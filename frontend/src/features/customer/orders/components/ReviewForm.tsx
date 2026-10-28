/**
 * Customer review form for a delivered order (plan Phase 12, task 12.3).
 *
 * Renders when an order is DELIVERED and the customer has not yet reviewed it.
 * Submits through `POST /api/v1/orders/{orderId}/reviews`; one review per
 * order, and a repeat submission is a 409 handled by the backend.
 */
import { useState } from "react";
import { useSubmitReview } from "@/features/review/queries";
import { StarRating } from "@/features/review/components/StarRating";
import { FOCUS_RING, PRESSABLE } from "@/motion/pressable";

interface ReviewFormProps {
  orderId: number;
  onSuccess?: () => void;
}

export function ReviewForm({ orderId, onSuccess }: ReviewFormProps) {
  const [comment, setComment] = useState("");
  const [rating, setRating] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [submitted, setSubmitted] = useState(false);

  const submitReview = useSubmitReview();

  const isSubmitting = submitReview.isPending;

  const canSubmit = rating >= 1 && rating <= 5 && !isSubmitting && !submitted;

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    setError(null);
    try {
      await submitReview.mutateAsync({
        orderId,
        payload: { rating, comment: comment.trim() || null },
      });
      setSubmitted(true);
      onSuccess?.();
    } catch (mutationError) {
      setError(
        mutationError instanceof Error
          ? mutationError.message
          : "Could not submit review. Please try again.",
      );
    }
  };

  if (submitted) {
    return (
      <div className="rounded-lg border border-green-100 bg-green-50 p-4 text-sm text-green-800">
        <p className="font-semibold">Thank you for your review!</p>
        <p className="mt-1 text-sm">
          Your review has been submitted and will appear on the shop's storefront.
        </p>
      </div>
    );
  }

  return (
    <form
      onSubmit={handleSubmit}
      className="space-y-4 rounded-lg border border-slate-200 bg-white p-5 shadow-sm"
    >
      <div>
        <h3 className="text-sm font-semibold text-slate-900 mb-2">
          Rate your experience
        </h3>
        <StarRating
          rating={rating}
          reviewCount={0}
          interactive
          value={rating}
          onSelect={setRating}
          emptyLabel="Select a rating"
        />
        {!rating && !isSubmitting && (
          <p className="mt-1 text-xs text-red-600">Please select a rating.</p>
        )}
      </div>

      <div>
        <label
          htmlFor="review-comment"
          className="block text-xs font-medium text-slate-700"
        >
          Comment (optional)
        </label>
        <textarea
          id="review-comment"
          value={comment}
          onChange={(e) => setComment(e.target.value)}
          maxLength={1000}
          rows={3}
          className={`mt-1 block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-brand-500 ${FOCUS_RING}`}
          placeholder="Share your thoughts about this order..."
        />
        <p className="mt-1 text-xs text-slate-500">
          {comment.length}/1000 characters
        </p>
      </div>

      {error && (
        <p className="text-sm text-red-600 bg-red-50 rounded-lg p-2 border border-red-100">
          {error}
        </p>
      )}

      <div className="flex items-center justify-between gap-3">
        <button
          type="submit"
          disabled={!canSubmit}
          className={`inline-flex items-center rounded-md border border-transparent bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-50 disabled:cursor-not-allowed ${PRESSABLE} ${FOCUS_RING}`}
        >
          {isSubmitting ? "Submitting…" : "Submit review"}
        </button>
      </div>
    </form>
  );
}
