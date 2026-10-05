import type { ReactNode } from "react";
import { useVendorProfile } from "@/features/vendor/queries";
import { VendorErrorState } from "@/features/vendor/components/VendorErrorState";
import { VendorStatusBanner } from "@/features/vendor/components/VendorStatusBanner";
import { VENDOR_NOT_APPROVED_MESSAGE } from "@/shared/lib/api-error";
import { Card } from "@/features/vendor/components/StatCard";
import { FlowerLoader } from "@/motion/FlowerLoader";
import { FadeIn } from "@/motion/FadeIn";

interface ApprovedVendorGateProps {
  children: ReactNode;
}

/**
 * Renders the catalog and inventory screens only for an approved vendor.
 *
 * **This is not a second approval-state system.** It reads the same
 * `["vendor","profile"]` query every other vendor screen already uses, so it
 * costs no extra request and it cannot disagree with the banner above it. The
 * authority stays exactly where D-13 put it: `@RequiresApprovedVendor` on the
 * backend, re-read from `vendor_profiles.status` on every request. This gate is
 * a convenience that keeps an unapproved vendor from being offered buttons that
 * would all come back `403 VENDOR_NOT_APPROVED`.
 *
 * The consequence that matters: because the status is read from the profile on
 * each request rather than cached in the token, an admin's approval takes effect
 * on the vendor's next visit to this screen with no re-login.
 *
 * Three states are handled explicitly — pending, failed, and not approved — and
 * only the last one withholds the children. Everything an unapproved vendor can
 * still do (edit their application, delivery settings, hours) stays reachable
 * through the other vendor routes, which carry no gate.
 */
export function ApprovedVendorGate({ children }: ApprovedVendorGateProps) {
  const { data: profile, isPending, error, refetch } = useVendorProfile();

  if (isPending) {
    return (
      <div className="rounded-lg border border-slate-200 bg-white p-6 text-sm text-slate-600">
        Loading your catalog...
      </div>
    );
  }

  if (error || !profile) {
    return <VendorErrorState error={error} onRetry={() => void refetch()} />;
  }

  if (profile.status !== "APPROVED") {
    return (
      <div className="space-y-4">
        {/*
          The same banner the layout already shows above. Re-rendering it here is
          intentional rather than duplicated: this panel is what the vendor reads
          when they navigated straight to a catalog URL, so it needs the state and
          its explanation in the same place. It is the same component reading the
          same cache entry, not a second banner with its own state.
        */}
        <VendorStatusBanner status={profile.status} />
        <FadeIn role="status">
          <Card>
            <p className="text-sm font-semibold text-slate-900">Catalog unavailable</p>
            <p className="mt-1 text-sm text-slate-600">{VENDOR_NOT_APPROVED_MESSAGE}</p>
            <p className="mt-2 text-sm text-slate-600">
              No products, stock or movement history is available until an administrator approves your
              account. You can keep editing your business details, delivery settings and opening hours
              while you wait.
            </p>
          </Card>
        </FadeIn>
      </div>
    );
  }

  return <>{children}</>;
}

/**
 * Small loader reused by the catalog screens' initial states. Kept here so the
 * gate and the pages it guards read as one surface.
 */
export function CatalogLoader({ label }: { label: string }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-6">
      <FlowerLoader label={label} showLabel className="flex items-center gap-3 text-sm text-slate-600" />
    </div>
  );
}