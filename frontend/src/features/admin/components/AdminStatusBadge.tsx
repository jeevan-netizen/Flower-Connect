import type { VendorStatus } from "@/features/vendor/types";
import { formatVendorStatus, USER_STATUS_TONES, formatUserStatus } from "@/features/admin/format";
import type { UserStatus } from "@/features/admin/types";
import { StatusPill } from "@/motion/StatusPill";

/**
 * Status pills for the admin listings.
 *
 * The vendor tone map is shared with the vendor area's status banner, so the same
 * status never renders in two different colours depending on which screen an
 * operator is looking at. Both badges render `StatusPill`, which is what makes a
 * status that changes after an action cross-fade instead of snapping — and what
 * keeps a page of twenty rows from animating twenty pills on first paint.
 */
const VENDOR_STATUS_TONES: Record<VendorStatus, string> = {
  PENDING_APPROVAL: "bg-amber-100 text-amber-800",
  APPROVED: "bg-brand-100 text-brand-900",
  REJECTED: "bg-red-100 text-red-800",
  SUSPENDED: "bg-slate-200 text-slate-800",
};

export function AdminVendorStatusBadge({ status }: { status: VendorStatus }) {
  return <StatusPill tone={VENDOR_STATUS_TONES[status]} label={formatVendorStatus(status)} />;
}

export function AdminUserStatusBadge({ status }: { status: UserStatus }) {
  return <StatusPill tone={USER_STATUS_TONES[status]} label={formatUserStatus(status)} />;
}