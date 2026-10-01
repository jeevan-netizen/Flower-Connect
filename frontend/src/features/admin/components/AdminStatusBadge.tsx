import type { VendorStatus } from "@/features/vendor/types";
import { formatVendorStatus, USER_STATUS_TONES, formatUserStatus } from "@/features/admin/format";
import type { UserStatus } from "@/features/admin/types";

/**
 * Status pills for the admin listings.
 *
 * The vendor tone map is shared with the vendor area's status banner, so the same
 * status never renders in two different colours depending on which screen an
 * operator is looking at.
 */
const VENDOR_STATUS_TONES: Record<VendorStatus, string> = {
  PENDING_APPROVAL: "bg-amber-100 text-amber-800",
  APPROVED: "bg-brand-100 text-brand-900",
  REJECTED: "bg-red-100 text-red-800",
  SUSPENDED: "bg-slate-200 text-slate-800",
};

export function AdminVendorStatusBadge({ status }: { status: VendorStatus }) {
  return (
    <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${VENDOR_STATUS_TONES[status]}`}>
      {formatVendorStatus(status)}
    </span>
  );
}

export function AdminUserStatusBadge({ status }: { status: UserStatus }) {
  return (
    <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${USER_STATUS_TONES[status]}`}>
      {formatUserStatus(status)}
    </span>
  );
}