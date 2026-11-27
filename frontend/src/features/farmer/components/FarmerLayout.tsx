import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { useQueryClient } from "@tanstack/react-query";
import { useAuthStore } from "@/features/auth/stores/auth-store";
import { clearFarmerCache } from "@/features/farmer/queries";
import { FOCUS_RING, PRESSABLE } from "@/motion/pressable";

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
  return [
    "block rounded-md px-3 py-2 text-sm font-medium transition-colors duration-micro ease-standard",
    FOCUS_RING,
    isActive ? "bg-brand-600 text-white" : "text-slate-700 hover:bg-slate-100 hover:text-slate-900",
  ].join(" ");
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
    <div className="min-h-screen bg-slate-50">
      <div className="mx-auto flex w-full max-w-7xl flex-col gap-6 px-4 py-6 md:flex-row">
        <aside className="md:w-60 md:shrink-0">
          <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
            <p className="text-lg font-bold text-brand-700">FlowerConnect</p>
            <p className="text-xs uppercase tracking-wide text-slate-500">Farmer area</p>
          </div>

          <nav className="mt-4 rounded-lg border border-slate-200 bg-white p-2 shadow-sm" aria-label="Farmer">
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
            className={`mt-4 w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-100 ${PRESSABLE} ${FOCUS_RING}`}
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
