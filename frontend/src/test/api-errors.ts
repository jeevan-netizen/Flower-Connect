import { AxiosError, type AxiosRequestHeaders } from "axios";
import type { ErrorResponse } from "@/features/auth/types";

/**
 * Builds a rejection shaped like the backend `ErrorResponse` envelope so the
 * error-handling tests exercise the real contract rather than a generic Error.
 */
export function apiError(
  status: number,
  body: Partial<ErrorResponse> = {},
): AxiosError<ErrorResponse> {
  return new AxiosError(
    "Request failed",
    "ERR_BAD_REQUEST",
    undefined,
    undefined,
    {
      status,
      statusText: String(status),
      headers: {},
      config: { headers: {} as AxiosRequestHeaders },
      data: {
        status,
        message: "Request failed",
        ...body,
      },
    },
  );
}

export function vendorNotApprovedError(message = "Vendor account is awaiting administrator approval") {
  return apiError(403, { code: "VENDOR_NOT_APPROVED", message, error: "Forbidden" });
}

export function validationError(validation: Record<string, string>) {
  return apiError(400, {
    code: "VALIDATION_FAILED",
    error: "Bad Request",
    message: "Validation failed",
    validation,
  });
}

export function businessError(status: number, code: string, message: string) {
  return apiError(status, { code, message });
}

/**
 * A rejection that remembers which endpoint failed.
 *
 * Needed wherever the *request* decides the message: `isMissingVendorProfile` treats a
 * `404` as "no vendor profile" only when the profile read is what failed, because a
 * missing product or category must not be reported as a missing account.
 */
export function apiErrorFor(
  url: string,
  status: number,
  body: Partial<ErrorResponse> = {},
): AxiosError<ErrorResponse> {
  const error = apiError(status, body);
  error.config = { ...error.config, url } as typeof error.config;
  return error;
}
