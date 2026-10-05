/**
 * Admin frontend types — plan task 2.10.
 *
 * These mirror the Phase 2 admin backend contracts exactly:
 *   `VendorProfilePageResponse` / `VendorProfileResponse` / `VendorAdminReasonRequest`
 *   `AdminUserPageResponse`      / `AdminUserResponse`      / `UserStatusUpdateRequest`
 *
 * Three deliberate constraints:
 *
 * 1. Vendor read models are **re-exported** from `@/features/vendor/types` rather
 *    than re-declared. `GET /api/v1/admin/vendors` returns the same
 *    `VendorProfileResponse` the vendor themselves read from
 *    `GET /api/v1/vendors/profile`, and a second copy of that interface would be
 *    free to drift away from the one MapStruct mapper actually produces.
 *
 * 2. Statuses and roles are closed unions taken from the backend enums
 *    (`User.Status`, `VendorProfile.Status`, seeded `roles` rows). The admin UI
 *    can therefore never construct a request for a status the backend would
 *    reject with a 400.
 *
 * 3. Transitions are **derived from the backend rules**, not invented. See
 *    `availableVendorActions` / `vendorActionsFor` below: those encode exactly
 *    `VendorAdminService`'s `requireStatus` preconditions, so the buttons the UI
 *    offers can never produce a 409 that the backend would have refused anyway.
 */

/* -------------------------------------------------------------------------- */
/* Vendor administration (plan task 2.6)                                       */
/* -------------------------------------------------------------------------- */

import {
  VENDOR_STATUSES,
  type VendorHours,
  type VendorProfile,
  type VendorProfileEditableFields,
  type VendorStatus,
  type Weekday,
} from "@/features/vendor/types";
import type { PageResponse } from "@/shared/types";

export type {
  VendorHours,
  VendorProfile,
  VendorProfileEditableFields,
  VendorStatus,
  Weekday,
};
export { VENDOR_STATUSES } from "@/features/vendor/types";

/** Body of `POST /api/v1/admin/vendors/{id}/reject` and `.../suspend`. */
export interface VendorAdminReasonRequest {
  reason: string;
}

/**
 * The admin vendor actions the backend exposes, as four distinct routes rather
 * than one generic status update — see the `AdminVendorController` class comment.
 */
export type VendorAdminAction = "approve" | "reject" | "suspend" | "reinstate";

/**
 * `VendorAdminService` refuses any action whose `requireStatus` precondition does
 * not hold, with 409 `CONFLICT`. Mirroring those preconditions here keeps the
 * offered actions and the backend's legal transitions identical, so the UI never
 * offers a transition the backend would refuse.
 */
const VENDOR_ACTION_SOURCE: Record<VendorStatus, readonly VendorAdminAction[]> = {
  // PENDING_APPROVAL -> APPROVED (approve) or -> REJECTED (reject).
  PENDING_APPROVAL: ["approve", "reject"],
  // APPROVED -> SUSPENDED. Terminal: there is no "unreject" route.
  APPROVED: ["suspend"],
  // SUSPENDED -> APPROVED via reinstate, which deliberately skips a fresh approval.
  SUSPENDED: ["reinstate"],
  // Terminal: a rejected profile has no route back.
  REJECTED: [],
};

/** Actions the backend will accept for a profile currently in `status`. */
export function vendorActionsFor(status: VendorStatus): readonly VendorAdminAction[] {
  return VENDOR_ACTION_SOURCE[status];
}

/**
 * The subset of `VendorAdminAction` whose request body carries a reason.
 * Extracted so the type guard below and the mutation union below name the same
 * two actions instead of repeating the literal pair in three places.
 */
export type ReasonedVendorAction = Extract<VendorAdminAction, "reject" | "suspend">;

/**
 * Only `reject` and `suspend` carry a reason on the wire.
 *
 * A type predicate rather than a plain `boolean` so a caller can build the
 * `VendorAdminMutation` union from `action` without a cast: the narrowing this
 * performs is what lets the reason-bearing branch supply a `reason`.
 */
export function vendorActionRequiresReason(action: VendorAdminAction): action is ReasonedVendorAction {
  return action === "reject" || action === "suspend";
}

/**
 * Mutation variables for `useVendorAdminAction`.
 *
 * A discriminated union rather than one shape carrying `reason?: string`,
 * because the two shapes are genuinely different requests: `approve` and
 * `reinstate` take **no request body at all** (`AdminVendorController` declares
 * them without one), while `reject` and `suspend` require a non-blank
 * `VendorAdminReasonRequest.reason`. A single optional-reason shape would let
 * `{ action: "reject", profileId }` typecheck and then fail as a 400 from the
 * backend — the compiler now refuses the call instead.
 */
export type VendorAdminMutation =
  | { action: "approve" | "reinstate"; profileId: number }
  | { action: ReasonedVendorAction; profileId: number; reason: string };

/* -------------------------------------------------------------------------- */
/* User administration (plan task 2.8)                                         */
/* -------------------------------------------------------------------------- */

/** Mirrors the seeded `roles` rows (V3__seed_roles.sql). */
export const ADMIN_ROLES = ["CUSTOMER", "FLORIST", "ADMIN"] as const;
export type AdminRole = (typeof ADMIN_ROLES)[number];

/** Mirrors `User.Status`. */
export const USER_STATUSES = ["ACTIVE", "SUSPENDED", "DISABLED"] as const;
export type UserStatus = (typeof USER_STATUSES)[number];

/** Mirrors `AdminUserResponse`. Carries no credential material. */
export interface AdminUser {
  id: number;
  email: string;
  fullName: string;
  phone: string | null;
  role: AdminRole;
  status: UserStatus;
  createdAt: string;
}

/** Mirrors `UserStatusUpdateRequest`. `reason` is mandatory for every transition. */
export interface UserStatusUpdateRequest {
  status: UserStatus;
  reason: string;
}

/* -------------------------------------------------------------------------- */
/* Pagination (identical shape for both admin listings)                        */
/* -------------------------------------------------------------------------- */

/**
 * Mirrors `VendorProfilePageResponse` and `AdminUserPageResponse`, which are
 * deliberately identical and both mirror the Phase 2a location page response.
 *
 * `page` is **zero-based** — `AdminVendorController` and `AdminUserController`
 * both default it to `0` and clamp it with `Math.max(0, page)`.
 *
 * The interface itself moved to `@/shared/types` when the vendor catalog (plan
 * task 3.9) started paginating against the same envelope; it is re-exported
 * here so every admin import path keeps working unchanged.
 */
export type { PageResponse };

export type VendorProfilePageResponse = PageResponse<VendorProfile>;
export type AdminUserPageResponse = PageResponse<AdminUser>;

/* -------------------------------------------------------------------------- */
/* List filters (the query parameters the two listing routes accept)           */
/* -------------------------------------------------------------------------- */

export interface VendorListFilters {
  /** `null` = no status filter. */
  status: VendorStatus | null;
  page: number;
}

export interface UserListFilters {
  /** `null` = no role filter. `AdminUserService` combines role and status with AND. */
  role: AdminRole | null;
  status: UserStatus | null;
  page: number;
}

/* -------------------------------------------------------------------------- */
/* URL query-parameter parsing (dashboard deep links)                          */
/* -------------------------------------------------------------------------- */

/**
 * Reads a `?status=` search parameter as a vendor status filter.
 *
 * The admin dashboard links into `/admin/vendors?status=PENDING_APPROVAL`, so a
 * value that reaches this function is untrusted input from the address bar. A
 * value that is not one of `VENDOR_STATUSES` is discarded (`null` = no filter)
 * rather than forwarded, because `AdminVendorController` binds `status` to the
 * `VendorProfile.Status` enum and answers anything else with a 400 — a bad deep
 * link should show the unfiltered list, not an error.
 */
export function parseVendorStatusParam(value: string | null): VendorStatus | null {
  return VENDOR_STATUSES.find((status) => status === value) ?? null;
}

/**
 * Reads a `?status=` search parameter as a user status filter.
 *
 * Same reasoning as `parseVendorStatusParam`: `AdminUserController` binds the
 * parameter to `User.Status`, so an unknown value must be dropped rather than
 * sent.
 */
export function parseUserStatusParam(value: string | null): UserStatus | null {
  return USER_STATUSES.find((status) => status === value) ?? null;
}

/** The `AdminVendorController` / `AdminUserController` page size bounds. */
export const ADMIN_PAGE_SIZE = 20;
export const ADMIN_MIN_PAGE_SIZE = 1;
export const ADMIN_MAX_PAGE_SIZE = 100;

/**
 * `VendorAdminReasonRequest.reason` and `UserStatusUpdateRequest.reason` both
 * carry `@NotBlank` + `@Size(max = 500)`. Single source for both forms.
 */
export const ADMIN_REASON_MAX_LENGTH = 500;