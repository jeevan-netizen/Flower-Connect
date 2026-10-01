import type { VendorStatus } from "@/features/vendor/types";
import { formatStatus } from "@/features/vendor/format";

interface VendorStatusBannerProps {
  status: VendorStatus;
}

/**
 * Per-status copy for the vendor area (plan tasks 2.5–2.7).
 *
 * The approval state lives only on `vendor_profiles.status` and is re-read on
 * every request, so this banner is always current — it is never cached in the
 * JWT or derived from a stale local copy. Admin-owned reasons (reject/suspend)
 * are stored in `audit_log`, which is admin-only, so the vendor sees the state
 * and not the reason.
 */
const STATUS_CONTENT: Record<
  VendorStatus,
  { tone: string; badgeTone: string; headline: string; detail: string }
> = {
  PENDING_APPROVAL: {
    tone: "bg-amber-50 border-amber-200 text-amber-900",
    badgeTone: "bg-amber-100 text-amber-800",
    headline: "Your application is awaiting approval",
    detail:
      "A FlowerConnect administrator has not reviewed your application yet. You can keep editing your profile, delivery settings and opening hours while you wait.",
  },
  APPROVED: {
    tone: "bg-brand-50 border-brand-100 text-brand-900",
    badgeTone: "bg-brand-100 text-brand-900",
    headline: "Your vendor account is approved",
    detail:
      "Customers can discover your shop. Keep your delivery settings and opening hours up to date so slots are accurate.",
  },
  REJECTED: {
    tone: "bg-red-50 border-red-200 text-red-900",
    badgeTone: "bg-red-100 text-red-800",
    headline: "Your application was rejected",
    detail:
      "Review your business details, address and service location below, then contact FlowerConnect support if you believe this is a mistake.",
  },
  SUSPENDED: {
    tone: "bg-slate-100 border-slate-300 text-slate-900",
    badgeTone: "bg-slate-200 text-slate-800",
    headline: "Your vendor account is suspended",
    detail:
      "Your shop is hidden from customer discovery and vendor features are unavailable. Contact FlowerConnect support to have the suspension reviewed.",
  },
};

export function VendorStatusBanner({ status }: VendorStatusBannerProps) {
  const content = STATUS_CONTENT[status];

  return (
    <div className={`rounded-lg border p-4 ${content.tone}`} role="status">
      <div className="flex flex-wrap items-center gap-2">
        <h2 className="text-sm font-semibold">{content.headline}</h2>
        <span
          className={`rounded-full px-2 py-0.5 text-xs font-medium ${content.badgeTone}`}
          data-testid="vendor-status-badge"
        >
          {formatStatus(status)}
        </span>
      </div>
      <p className="mt-1 text-sm">{content.detail}</p>
    </div>
  );
}

interface VendorStatusBadgeProps {
  status: VendorStatus;
}

/** Compact status pill for cards and summaries. */
export function VendorStatusBadge({ status }: VendorStatusBadgeProps) {
  return (
    <span
      className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_CONTENT[status].badgeTone}`}
    >
      {formatStatus(status)}
    </span>
  );
}
