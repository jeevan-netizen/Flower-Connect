import type { AxiosError } from "axios";
import type { ErrorResponse } from "@/features/auth/types";

/**
 * Error codes emitted by the backend `ErrorCode` enum. Only the ones a client
 * must *react* differently are named here; everything else is read off the
 * envelope's `code` field as a plain string.
 */
export const VENDOR_NOT_APPROVED_CODE = "VENDOR_NOT_APPROVED";
export const NOT_FOUND_CODE = "NOT_FOUND";

export const VENDOR_NOT_APPROVED_MESSAGE =
  "This part of the vendor area is available to approved vendors only.";

const NETWORK_ERROR_MESSAGE =
  "Unable to reach the server. Check your connection and try again.";
const FALLBACK_ERROR_MESSAGE = "Something went wrong. Please try again.";

export interface ApiErrorInfo {
  /** HTTP status, or `null` when the request never produced a response. */
  status: number | null;
  /** Backend `ErrorCode` value, or `null` when the body carried none. */
  code: string | null;
  /** User-safe message. Never a stack trace. */
  message: string;
  /** Per-field messages from a 400 bean-validation failure. */
  validation: Record<string, string>;
}

function isAxiosErrorLike(error: unknown): error is AxiosError<ErrorResponse> {
  return typeof error === "object" && error !== null && "isAxiosError" in error;
}

/**
 * Normalises anything thrown by the shared Axios client into the fields the UI
 * needs. This is deliberately the *only* place that reads the error envelope:
 * the Axios response interceptor stays focused on 401 refresh/retry, and pages
 * never dig into `error.response.data` themselves.
 */
export function toApiError(error: unknown): ApiErrorInfo {
  if (!isAxiosErrorLike(error)) {
    return { status: null, code: null, message: FALLBACK_ERROR_MESSAGE, validation: {} };
  }

  const response = error.response;
  const body = response?.data;

  if (!response || !body) {
    return { status: null, code: null, message: NETWORK_ERROR_MESSAGE, validation: {} };
  }

  return {
    status: response.status,
    code: body.code ?? (response.status === 403 ? "FORBIDDEN" : null),
    message: body.message || error.message || FALLBACK_ERROR_MESSAGE,
    validation: body.validation ?? {},
  };
}

/** True when the backend refused because the vendor profile is not `APPROVED`. */
export function isVendorNotApproved(error: unknown): boolean {
  return toApiError(error).code === VENDOR_NOT_APPROVED_CODE;
}

/** True when the authenticated account simply has no vendor profile. */
export function isMissingVendorProfile(error: unknown): boolean {
  const info = toApiError(error);
  return info.status === 404 && (info.code === NOT_FOUND_CODE || info.code === null);
}
