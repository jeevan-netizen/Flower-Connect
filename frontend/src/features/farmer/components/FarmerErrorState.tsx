import { isVendorNotApproved, toApiError } from "@/shared/lib/api-error";
import { FadeIn } from "@/motion/FadeIn";
import { FOCUS_RING, PRESSABLE } from "@/motion/pressable";

interface FarmerErrorStateProps {
  error: unknown;
  onRetry?: () => void;
}

/**
 * The single place a farmer screen reports a failed read or write.
 *
 * The outcomes a farmer can hit are told apart explicitly:
 *
 *  - `VENDOR_NOT_APPROVED` (403) → the target vendor is not approvable yet;
 *  - `404 NOT_FOUND` → the link or vendor does not belong to this farmer;
 *  - `403 FORBIDDEN` → the role is wrong or the action is not permitted;
 *  - anything else → the backend's own message.
 */
export function FarmerErrorState({ error, onRetry }: FarmerErrorStateProps) {
  const info = toApiError(error);

  if (isVendorNotApproved(error)) {
    return (
      <FadeIn role="alert" className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">
        <p className="font-semibold">Vendor not approvable yet</p>
        <p className="mt-1">That florist cannot accept link requests at the moment.</p>
      </FadeIn>
    );
  }

  if (info.status === 404) {
    return (
      <FadeIn role="alert" className="rounded-lg border border-slate-300 bg-slate-50 p-4 text-sm text-slate-800">
        <p className="font-semibold">Not found</p>
        <p className="mt-1">That link or vendor does not belong to your account.</p>
      </FadeIn>
    );
  }

  if (info.status === 403) {
    return (
      <FadeIn role="alert" className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-800">
        <p className="font-semibold">Not permitted</p>
        <p className="mt-1">{info.message}</p>
      </FadeIn>
    );
  }

  return (
    <FadeIn role="alert" className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-800">
      <p className="font-semibold">Something went wrong</p>
      <p className="mt-1">{info.message}</p>
      {onRetry && (
        <button
          type="button"
          onClick={onRetry}
          className={`mt-3 rounded-md border border-red-300 bg-white px-3 py-1.5 text-xs font-medium text-red-800 hover:bg-red-100 ${PRESSABLE} ${FOCUS_RING}`}
        >
          Try again
        </button>
      )}
    </FadeIn>
  );
}
