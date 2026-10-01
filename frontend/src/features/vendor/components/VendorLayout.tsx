import { useQueryClient } from "@tanstack/react-query";
import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { useAuthStore } from "@/features/auth/stores/auth-store";
import { clearVendorCache, useVendorProfile } from "@/features/vendor/queries";
import { VendorErrorState } from "@/features/vendor/components/VendorErrorState";
import { VendorStatusBanner } from "@/features/vendor/components/VendorStatusBanner";
import { countOpenDays } from "@/features/vendor/format";

interface VendorNavItem {
  to: string;
  label: string;
  end?: boolean;
}

/**
 * Phase 2 only. Catalog, inventory, orders, payments and delivery management are
 * Phase 3+ and have no API yet, so they are deliberately absent here rather
 * than rendered as dead links (plan tasks 2.9 / 3.9 / 6.x).
 */
const NAV_ITEMS: VendorNavItem[] = [
  { to: "/vendor", label: "Dashboard", end: true },
  { to: "/vendor/profile", label: "Profile" },
  { to: "/vendor/settings", label: "Delivery settings" },
  { to: "/vendor/hours", label: "Operating hours" },
];

function navClass({ isActive }: { isActive: boolean }): string {
  return [
    "block rounded-md px-3 py-2 text-sm font-medium transition-colors",
    isActive
      ? "bg-brand-600 text-white"
      : "text-slate-700 hover:bg-slate-100 hover:text-slate-900",
  ].join(" ");
}

/**
 * Vendor dashboard shell (plan task 2.9): sidebar navigation plus the shared
 * approval banner.
 *
 * The banner lives here rather than on each page so the vendor's state is
 * visible from every screen, and so it reads from the same cached profile query
 * the pages use — one `GET /api/v1/vendors/profile` serves the whole area.
 *
 * Logging out here clears the vendor query cache so the next account to sign in
 * on this tab cannot read the previous vendor's cached profile.
 */
export function VendorLayout() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const logout = useAuthStore((state) => state.logout);
  const { data: profile, isPending, error, refetch } = useVendorProfile();

  const handleLogout = () => {
    clearVendorCache(queryClient);
    logout();
    navigate("/", { replace: true });
  };

  return (
    <div className="min-h-screen bg-slate-50">
      <div className="mx-auto flex w-full max-w-7xl flex-col gap-6 px-4 py-6 md:flex-row">
        <aside className="md:w-60 md:shrink-0">
          <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
            <p className="text-lg font-bold text-brand-700">FlowerConnect</p>
            <p className="text-xs uppercase tracking-wide text-slate-500">Vendor area</p>
          </div>

          <nav className="mt-4 rounded-lg border border-slate-200 bg-white p-2 shadow-sm" aria-label="Vendor">
            <ul className="flex flex-col gap-1 md:flex-col">
              {NAV_ITEMS.map((item) => (
                <li key={item.to}>
                  <NavLink to={item.to} end={item.end} className={navClass}>
                    {item.label}
                  </NavLink>
                </li>
              ))}
            </ul>
          </nav>

          <button
            type="button"
            onClick={handleLogout}
            className="mt-4 w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-100"
          >
            Log out
          </button>
        </aside>

        <main className="min-w-0 flex-1 space-y-6">
          {isPending && (
            <div className="flex items-center gap-3 rounded-lg border border-slate-200 bg-white p-4 text-sm text-slate-600">
              <span className="animate-spin rounded-full h-4 w-4 border-b-2 border-brand-600" />
              <span>Loading your vendor profile...</span>
            </div>
          )}

          {!isPending && error && <VendorErrorState error={error} onRetry={() => void refetch()} />}

          {profile && (
            <>
              <VendorStatusBanner status={profile.status} />
              <p className="text-xs text-slate-500">
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
