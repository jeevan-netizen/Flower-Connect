import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { useQueryClient } from "@tanstack/react-query";
import { useAuthStore } from "@/features/auth/stores/auth-store";
import { clearFarmerCache } from "@/features/farmer/queries";
import { BUTTON_SECONDARY, navLinkClass } from "@/shared/ui/classes";

interface FarmerNavItem {
  to: string;
  label: string;
  end?: boolean;
}

/**
 * Farmer dashboard shell (Phase 14 farmer portal): sidebar navigation plus a Log
 * out button that clears the farmer cache so the next account to sign in on this
 * tab never sees the previous farmer's profile, links or passbook.
 *
 * The two navigation items are Overview and My Ledger. Account profile actions
 * live on the shared home `/profile` destination. Navigation renders on
 * every viewport: as a horizontal bar on desktop and a stacked list on mobile.
 */
const NAV_ITEMS: FarmerNavItem[] = [
  { to: "/farmer", label: "Overview", end: true },
  { to: "/farmer/ledger", label: "My Ledger" },
];

function navClass({ isActive }: { isActive: boolean }): string {
  return navLinkClass(isActive);
}

export function FarmerLayout() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const logout = useAuthStore((state) => state.logout);

  const handleLogout = () => {
    clearFarmerCache(queryClient);
    logout();
    navigate("/", { replace: true });
  };

  return (
    <div className="min-h-[60vh] bg-bolder-bg">
      <div className="mx-auto flex w-full max-w-app flex-col gap-6 px-4 py-6 sm:px-6 md:flex-row lg:px-8">
        <aside className="md:w-60 md:shrink-0">
          <div className="rounded-card border border-bolder-line bg-bolder-surface p-4 shadow-card">
            <p className="font-display text-xl text-bolder-text">FlowerConnect</p>
            <p className="mt-1 text-xs text-bolder-muted">Farmer area</p>
          </div>

          <nav className="mt-4 rounded-card border border-bolder-line bg-bolder-surface p-2 shadow-card" aria-label="Farmer">
            <ul className="flex flex-col gap-1 md:flex-col">
              {NAV_ITEMS.map((item) => (
                <li key={item.to}>
                  <NavLink to={item.to} end={item.end} className={({ isActive }) => navClass({ isActive })}>
                    {item.label}
                  </NavLink>
                </li>
              ))}
            </ul>
          </nav>

          <button
            type="button"
            onClick={handleLogout}
            className={`mt-4 w-full ${BUTTON_SECONDARY}`}
          >
            Log out
          </button>
        </aside>

        <main className="min-w-0 flex-1 space-y-6">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
