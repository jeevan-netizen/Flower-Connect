import { VENDOR_STATUS_LABELS } from "@/features/vendor/format";
import type { VendorStatus } from "@/features/vendor/types";
import type { AdminRole, UserStatus, VendorAdminAction } from "@/features/admin/types";

/**
 * Presentation helpers for the admin area.
 *
 * Vendor status labels are **reused** from the vendor slice rather than
 * re-declared: an admin reading a vendor status and a vendor reading their own
 * must never see two different words for the same column value.
 */

export const USER_STATUS_LABELS: Record<UserStatus, string> = {
  ACTIVE: "Active",
  SUSPENDED: "Suspended",
  DISABLED: "Disabled",
};

export const USER_STATUS_TONES: Record<UserStatus, string> = {
  ACTIVE: "bg-brand-100 text-brand-900",
  SUSPENDED: "bg-amber-100 text-amber-800",
  DISABLED: "bg-red-100 text-red-800",
};

export const ROLE_LABELS: Record<AdminRole, string> = {
  CUSTOMER: "Customer",
  FLORIST: "Florist",
  ADMIN: "Admin",
};

export const VENDOR_ACTION_LABELS: Record<VendorAdminAction, string> = {
  approve: "Approve",
  reject: "Reject",
  suspend: "Suspend",
  reinstate: "Reinstate",
};

/** Heading question for the confirmation dialog. */
export const VENDOR_ACTION_HEADINGS: Record<VendorAdminAction, string> = {
  approve: "Approve this vendor?",
  reject: "Reject this vendor?",
  suspend: "Suspend this vendor?",
  reinstate: "Reinstate this vendor?",
};

/** Past tense, for the success confirmation after a transition. */
export const VENDOR_ACTION_PAST_TENSE: Record<VendorAdminAction, string> = {
  approve: "Approved",
  reject: "Rejected",
  suspend: "Suspended",
  reinstate: "Reinstated",
};

/**
 * `LocalDateTime` as the backend serialises it (`2026-09-01T10:00:00`). Rendered
 * in the browser's locale, for display only.
 */
export function formatDateTime(value: string | null | undefined): string {
  if (!value) {
    return "—";
  }
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? value : parsed.toLocaleString();
}

/**
 * One-based page number for display. The wire format is zero-based: both
 * `AdminVendorController` and `AdminUserController` default `page` to `0`.
 *
 * Moved to `@/shared/format` when the vendor catalog (plan task 3.9) needed the
 * same conversion; re-exported here so existing imports keep working.
 */
export { displayPageNumber } from "@/shared/format";

export function formatVendorStatus(status: VendorStatus): string {
  return VENDOR_STATUS_LABELS[status];
}

export function formatUserStatus(status: UserStatus): string {
  return USER_STATUS_LABELS[status];
}

export function formatRole(role: AdminRole): string {
  return ROLE_LABELS[role];
}