import { NOT_FOUND_CODE, toApiError } from "@/shared/lib/api-error";
import { FadeIn } from "@/motion/FadeIn";
import { PRESSABLE } from "@/motion/pressable";

interface CustomerErrorStateProps {
  error: unknown;
  /** Re-runs the failed request. Omitted when there is nothing to retry. */
  onRetry?: () => void;
  /**
   * The 404 wording for this surface.
   *
   * The backend answers 404 for a product or a florist that is unknown *and*
   * for one that is merely ineligible — inactive, sold out, unapproved, or
   * outside the delivery radius — and it does not say which. The customer UI
   * therefore shows one message for all of them, because distinguishing them
   * would leak the state of a florist the customer is not entitled to know.
   */
  notFoundTitle?: string;
  notFoundDescription?: string;
}

const DEFAULT_NOT_FOUND_TITLE = "Not available";
const DEFAULT_NOT_FOUND_DESCRIPTION =
  "This is not available for your delivery area right now.";

/**
 * The customer catalogue's error state.
 *
 * It reads the backend's own error envelope through `toApiError` — the single
 * place in the app that does — and says something different for the three
 * cases a customer can act on: the resource is not available, the request was
 * rejected, and everything else.
 */
export function CustomerErrorState({
  error,
  onRetry,
  notFoundTitle = DEFAULT_NOT_FOUND_TITLE,
  notFoundDescription = DEFAULT_NOT_FOUND_DESCRIPTION,
}: CustomerErrorStateProps) {
  const info = toApiError(error);
  const isNotFound = info.status === 404 || info.code === NOT_FOUND_CODE;

  const title = isNotFound ? notFoundTitle : "Something went wrong";
  const description = isNotFound
    ? notFoundDescription
    : info.message || "Please try again.";

  return (
    <FadeIn
      role="alert"
      className="rounded-lg border border-slate-200 bg-white p-6 text-center shadow-sm"
    >
      <h2 className="text-base font-semibold text-slate-900">{title}</h2>
      <p className="mt-2 text-sm text-slate-600">{description}</p>

      {onRetry && (
        <button
          type="button"
          onClick={onRetry}
          className={`mt-4 inline-flex items-center rounded-md border border-transparent bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-700 focus:outline-none focus:ring-2 focus:ring-brand-500 focus:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 ${PRESSABLE}`}
        >
          Try again
        </button>
      )}
    </FadeIn>
  );
}
