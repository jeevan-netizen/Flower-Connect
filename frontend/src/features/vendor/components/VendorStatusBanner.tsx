import type { VendorStatus } from "@/features/vendor/types";
import { formatStatus } from "@/features/vendor/format";
import { FadeIn } from "@/motion/FadeIn";
import { StatusPill } from "@/motion/StatusPill";

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
    tone: "bg-bolder-warning-surface border-bolder-warning-border text-bolder-warning",
    badgeTone: "bg-bolder-warning-surface text-bolder-warning",
    headline: "Your application is awaiting approval",
    detail:
      "A FlowerConnect administrator has not reviewed your application yet. You can keep editing your profile, delivery settings and opening hours while you wait.",
  },
  APPROVED: {
    tone: "bg-bolder-blush-light border-bolder-line-control text-bolder-rose-deep",
    badgeTone: "bg-bolder-blush-light text-bolder-rose-deep",
    headline: "Your vendor account is approved",
    detail:
      "Customers can discover your shop. Keep your delivery settings and opening hours up to date so slots are accurate.",
  },
  REJECTED: {
    tone: "bg-bolder-danger-surface border-bolder-danger-border text-bolder-danger",
    badgeTone: "bg-bolder-danger-surface text-bolder-danger",
    headline: "Your application was rejected",
    detail:
      "Review your business details, address and service location below, then contact FlowerConnect support if you believe this is a mistake.",
  },
  SUSPENDED: {
    tone: "bg-bolder-bg border-bolder-line text-bolder-text",
    badgeTone: "bg-bolder-bg text-bolder-text",
    headline: "Your vendor account is suspended",
    detail:
      "Your shop is hidden from customer discovery and vendor features are unavailable. Contact FlowerConnect support to have the suspension reviewed.",
  },
};

export function VendorStatusBanner({ status }: VendorStatusBannerProps) {
  const content = STATUS_CONTENT[status];

  return (
    <FadeIn role="status" className={`rounded-lg border p-4 ${content.tone}`}>
      <div className="flex flex-wrap items-center gap-2">
        <h2 className="text-sm font-semibold">{content.headline}</h2>
        <StatusPill
          tone={content.badgeTone}
          label={formatStatus(status)}
          data-testid="vendor-status-badge"
        />
      </div>
      <p className="mt-1 text-sm">{content.detail}</p>
    </FadeIn>
  );
}

interface VendorStatusBadgeProps {
  status: VendorStatus;
}

/** Compact status pill for cards and summaries. */
export function VendorStatusBadge({ status }: VendorStatusBadgeProps) {
  return (
    <StatusPill tone={STATUS_CONTENT[status].badgeTone} label={formatStatus(status)} />
  );
}
