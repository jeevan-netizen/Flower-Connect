import { describe, it, expect } from "vitest";
import {
  isMissingVendorProfile,
  isVendorNotApproved,
  toApiError,
} from "@/shared/lib/api-error";
import { apiError, businessError, vendorNotApprovedError, validationError } from "@/test/api-errors";

describe("toApiError", () => {
  it("reads status, code, message and validation from the backend envelope", () => {
    const info = toApiError(
      validationError({ businessName: "Business name is required", addressLine1: "Address line 1 is required" }),
    );

    expect(info.status).toBe(400);
    expect(info.code).toBe("VALIDATION_FAILED");
    expect(info.message).toBe("Validation failed");
    expect(info.validation).toEqual({
      businessName: "Business name is required",
      addressLine1: "Address line 1 is required",
    });
  });

  it("keeps a 409 CONFLICT distinguishable", () => {
    const info = toApiError(businessError(409, "CONFLICT", "Email already in use"));

    expect(info.status).toBe(409);
    expect(info.code).toBe("CONFLICT");
    expect(info.message).toBe("Email already in use");
  });

  it("falls back to FORBIDDEN when a 403 carries no code", () => {
    const info = toApiError(apiError(403, { message: "Forbidden" }));

    expect(info.code).toBe("FORBIDDEN");
  });

  it("never surfaces a raw stack trace for a network failure", () => {
    const error = new Error("Network Error");
    Object.assign(error, { isAxiosError: true, request: {}, response: undefined });

    const info = toApiError(error);

    expect(info.status).toBeNull();
    expect(info.code).toBeNull();
    expect(info.message).toMatch(/unable to reach the server/i);
    expect(info.message).not.toContain("Network Error");
  });

  it("returns a safe message for a non-axios throw", () => {
    const info = toApiError(new TypeError("cannot read x of undefined"));

    expect(info.message).toMatch(/something went wrong/i);
    expect(info.message).not.toContain("cannot read x of undefined");
  });
});

describe("error classification", () => {
  it("recognises the vendor approval refusal", () => {
    expect(isVendorNotApproved(vendorNotApprovedError())).toBe(true);
  });

  it("does not mistake a plain 403 for an approval refusal", () => {
    expect(isVendorNotApproved(businessError(403, "FORBIDDEN", "Forbidden"))).toBe(false);
  });

  it("does not mistake a validation failure for an approval refusal", () => {
    expect(isVendorNotApproved(validationError({ businessName: "required" }))).toBe(false);
  });

  it("recognises a missing vendor profile on 404", () => {
    expect(isMissingVendorProfile(businessError(404, "NOT_FOUND", "No vendor profile exists for this account"))).toBe(true);
  });

  it("does not treat a network failure as a missing profile", () => {
    expect(isMissingVendorProfile(new Error("boom"))).toBe(false);
  });
});
