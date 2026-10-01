import { isMissingVendorProfile, isVendorNotApproved, toApiError, VENDOR_NOT_APPROVED_MESSAGE } from "@/shared/lib/api-error";

interface VendorErrorStateProps {
  error: unknown;
  onRetry?: () => void;
}

/**
 * The single place a vendor screen reports a failed read or write.
 *
 * The four outcomes a vendor can actually hit are told apart explicitly, because
 * collapsing them into one red box would send a legitimate user to the wrong
 * place:
 *
 *  - `VENDOR_NOT_APPROVED` (403) → approval guidance, *not* a login redirect;
 *  - `404 NOT_FOUND` → the account holds the florist role but has no profile;
 *  - `403 FORBIDDEN` → the role is wrong, so the account cannot use vendor routes;
 *  - anything else (validation, conflict, network, 5xx) → the backend's own
 *    message, with no stack trace or internal detail exposed.
 */
export function VendorErrorState({ error, onRetry }: VendorErrorStateProps) {
  const info = toApiError(error);

  if (isVendorNotApproved(error)) {
    return (
      <div className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900" role="alert">
        <p className="font-semibold">Approval required</p>
        <p className="mt-1">{VENDOR_NOT_APPROVED_MESSAGE}</p>
      </div>
    );
  }

  if (isMissingVendorProfile(error)) {
    return (
      <div className="rounded-lg border border-slate-300 bg-slate-50 p-4 text-sm text-slate-800" role="alert">
        <p className="font-semibold">No vendor profile</p>
        <p className="mt-1">
          This account does not have a vendor profile yet. Contact FlowerConnect support to have one
          created.
        </p>
      </div>
    );
  }

  if (info.status === 403) {
    return (
      <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-800" role="alert">
        <p className="font-semibold">Not permitted</p>
        <p className="mt-1">Your account does not have permission to use the vendor area.</p>
      </div>
    );
  }

  return (
    <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-800" role="alert">
      <p className="font-semibold">Something went wrong</p>
      <p className="mt-1">{info.message}</p>
      {onRetry && (
        <button
          type="button"
          onClick={onRetry}
          className="mt-3 rounded-md border border-red-300 bg-white px-3 py-1.5 text-xs font-medium text-red-800 hover:bg-red-100"
        >
          Try again
        </button>
      )}
    </div>
  );
}
