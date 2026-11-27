import { NavLink, Outlet } from "react-router-dom";
import { useVendorProfile } from "@/features/vendor/queries";
import { VendorErrorState } from "@/features/vendor/components/VendorErrorState";
import { VendorStatusBanner } from "@/features/vendor/components/VendorStatusBanner";
import { countOpenDays } from "@/features/vendor/format";
import { FlowerLoader } from "@/motion/FlowerLoader";
import { navLinkClass } from "@/shared/ui/classes";

interface VendorNavItem {
  to: string;
  label: string;
  end?: boolean;
  /**
   * `true` for screens that need an approved vendor profile. The backend enforces
   * this per handler (`@RequiresApprovedVendor`, D-13); the link is simply not
   * offered to a vendor who would be refused, because a navigation item that always
   * fails is worse than an absent one.
   */
  requiresApproval?: boolean;
}

/**
 * Orders, payments and delivery tracking are still absent: those phases have no
 * API yet, so they are not rendered as dead links (plan tasks 6.x, 7.x).
 *
 * Catalog and inventory are present as of Phase 3g (tasks 3.5–3.9) and are gated on
 * the approval state rather than on the role — the nav follows the same rule the
 * routes do, and reads it from the same cached profile.
 */
const NAV_ITEMS: VendorNavItem[] = [
  { to: "/vendor", label: "Dashboard", end: true },
  { to: "/vendor/orders", label: "Orders" },
  { to: "/vendor/reviews", label: "Reviews" },
  { to: "/vendor/catalog", label: "Catalog", requiresApproval: true },
  { to: "/vendor/inventory", label: "Inventory", requiresApproval: true },
  { to: "/vendor/billing", label: "Billing & Payouts" },
  { to: "/vendor/b2b/place-order", label: "Place B2B order", requiresApproval: true },
  { to: "/vendor/b2b/orders", label: "B2B orders" },
  { to: "/vendor/b2b/statements", label: "Statements", requiresApproval: true },
  { to: "/vendor/farmers", label: "Farmers" },
  { to: "/vendor/analytics", label: "Analytics" },
  { to: "/vendor/settings", label: "Delivery settings" },
  { to: "/vendor/hours", label: "Operating hours" },
];

function navClass({ isActive }: { isActive: boolean }): string {
  return navLinkClass(isActive);
}

/**
 * Vendor dashboard shell (plan task 2.9): sidebar navigation plus the shared
 * approval banner.
 *
 * The banner lives here rather than on each page so the vendor's state is
 * visible from every screen, and so it reads from the same cached profile query
 * the pages use — one `GET /api/v1/vendors/profile` serves the whole area.
 *
 * Vendor account actions (Change password, Log out) live on the shared
 * `/profile` page rendered by `ProfilePage`, not in the vendor sidebar — so an
 * action belongs to exactly one place.
 */
export function VendorLayout() {
  const { data: profile, isPending, error, refetch } = useVendorProfile();

  return (
    <div className="min-h-[60vh] bg-bolder-bg">
      <div className="mx-auto flex w-full max-w-app flex-col gap-6 px-4 py-6 sm:px-6 md:flex-row lg:px-8">
        <aside className="md:w-60 md:shrink-0">
          <div className="rounded-card border border-bolder-line bg-bolder-surface p-4 shadow-card">
            <p className="font-display text-xl text-bolder-text">FlowerConnect</p>
            <p className="mt-1 text-xs text-bolder-muted">Vendor area</p>
          </div>

          <nav className="mt-4 rounded-card border border-bolder-line bg-bolder-surface p-2 shadow-card" aria-label="Vendor">
            <ul className="flex flex-col gap-1 md:flex-col">
              {NAV_ITEMS.filter((item) => !item.requiresApproval || profile?.status === "APPROVED").map(
                (item) => (
                  <li key={item.to}>
                    <NavLink to={item.to} end={item.end} className={navClass}>
                      {item.label}
                    </NavLink>
                  </li>
                ),
              )}
            </ul>
          </nav>
        </aside>

        <main className="min-w-0 flex-1 space-y-6">
          {isPending && (
            <FlowerLoader
              label="Loading your vendor profile..."
              showLabel
              className="flex items-center gap-3 rounded-card border border-bolder-line bg-bolder-surface p-4 text-sm text-bolder-muted"
            />
          )}

          {!isPending && error && <VendorErrorState error={error} onRetry={() => void refetch()} />}

          {profile && (
            <>
              <VendorStatusBanner status={profile.status} />
              <p className="text-xs text-bolder-muted">
                Signed in as {profile.ownerEmail} · {countOpenDays(profile.hours)} of 7 days open
              </p>
            </>
          )}

          <Outlet />
        </main>
      </div>
    </div>
  );
}
