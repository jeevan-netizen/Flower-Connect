import { toApiError, type ApiErrorInfo } from "@/shared/lib/api-error";

interface AdminErrorStateProps {
  error: unknown;
  onRetry?: () => void;
}

/**
 * The single place an admin screen reports a failed read or write.
 *
 * The admin area can hit five genuinely different failures, and collapsing them
 * into one red box would misdirect the operator, so each is told apart:
 *
 *  - `403 FORBIDDEN` → the account is not an admin (or the role boundary was
 *    crossed some other way). Retrying will not help; the operator must sign in
 *    as an administrator.
 *  - `404 NOT_FOUND` → the target row was removed or never existed.
 *  - `409 CONFLICT` → an illegal status transition. This is the expected answer
 *    when someone else already moved the profile, so the copy points at a
 *    refresh rather than reading as a server fault.
 *  - `400 VALIDATION_FAILED` → the request was rejected on its shape; the
 *    backend's own message is shown.
 *  - anything else (network, 5xx) → the backend message, no stack trace.
 */
export function AdminErrorState({ error, onRetry }: AdminErrorStateProps) {
  const info: ApiErrorInfo = toApiError(error);

  if (info.status === 403) {
    return (
      <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-800" role="alert">
        <p className="font-semibold">Not permitted</p>
        <p className="mt-1">
          Your account does not have administrator access. Sign in as an administrator to manage
          vendors and users.
        </p>
      </div>
    );
  }

  if (info.status === 404) {
    return (
      <div className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900" role="alert">
        <p className="font-semibold">Not found</p>
        <p className="mt-1">{info.message}</p>
      </div>
    );
  }

  if (info.status === 409) {
    return (
      <div className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900" role="alert">
        <p className="font-semibold">Not allowed from the current status</p>
        <p className="mt-1">{info.message}</p>
        <p className="mt-1 text-amber-800">
          Someone may have changed this record already. Refresh the list and try again.
        </p>
        {onRetry && (
          <button
            type="button"
            onClick={onRetry}
            className="mt-3 rounded-md border border-amber-300 bg-white px-3 py-1.5 text-xs font-medium text-amber-900 hover:bg-amber-100"
          >
            Refresh the list
          </button>
        )}
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