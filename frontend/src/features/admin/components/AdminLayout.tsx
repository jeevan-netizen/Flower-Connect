import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { useQueryClient } from "@tanstack/react-query";
import { useAuthStore } from "@/features/auth/stores/auth-store";
import { clearAdminCache } from "@/features/admin/queries";

interface AdminNavItem {
  to: string;
  label: string;
  end?: boolean;
}

/**
 * Admin dashboard shell (plan task 2.10).
 *
 * Only the two capabilities the Phase 2 backend actually exposes are listed:
 * vendor management (task 2.6) and user management (task 2.8). Catalog, orders,
 * payments and delivery have no admin API yet, so they are deliberately absent
 * rather than rendered as dead links — the same rule the vendor shell follows.
 */
const NAV_ITEMS: AdminNavItem[] = [
  { to: "/admin", label: "Dashboard", end: true },
  { to: "/admin/vendors", label: "Vendors" },
  { to: "/admin/users", label: "Users" },
];

function navClass({ isActive }: { isActive: boolean }): string {
  return [
    // Duration/easing come from the motion tokens via `tailwind.config.ts`;
    // `focus-visible` keeps a visible ring on the keyboard path.
    "block rounded-md px-3 py-2 text-sm font-medium transition-colors duration-micro ease-standard",
    "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:ring-offset-2",
    isActive
      ? "bg-brand-600 text-white"
      : "text-slate-700 hover:bg-slate-100 hover:text-slate-900",
  ].join(" ");
}

/**
 * Sidebar navigation plus the signed-in administrator's identity.
 *
 * Logging out clears the admin query cache so the next account to sign in on this
 * tab cannot read the previous admin's cached user/vendor listings — the same
 * reasoning as `clearVendorCache` in the vendor area.
 *
 * The route itself is already guarded by `ProtectedRoute roles={["ADMIN"]}`; this
 * component assumes an administrator is present and does not re-check.
 */
export function AdminLayout() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const logout = useAuthStore((state) => state.logout);
  const user = useAuthStore((state) => state.user);

  const handleLogout = () => {
    clearAdminCache(queryClient);
    logout();
    navigate("/", { replace: true });
  };

  return (
    <div className="min-h-screen bg-slate-50">
      <div className="mx-auto flex w-full max-w-7xl flex-col gap-6 px-4 py-6 md:flex-row">
        <aside className="md:w-60 md:shrink-0">
          <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
            <p className="text-lg font-bold text-brand-700">FlowerConnect</p>
            <p className="text-xs uppercase tracking-wide text-slate-500">Admin area</p>
          </div>

          <nav className="mt-4 rounded-lg border border-slate-200 bg-white p-2 shadow-sm" aria-label="Admin">
            <ul className="flex flex-col gap-1">
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
            className="mt-4 w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-100 transition-[background-color,transform] duration-micro ease-standard active:scale-press motion-reduce:active:scale-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:ring-offset-2"
          >
            Log out
          </button>
        </aside>

        <main className="min-w-0 flex-1 space-y-6">
          {user && (
            <p className="text-xs text-slate-500">
              Signed in as {user.email} ({user.role})
            </p>
          )}
          <Outlet />
        </main>
      </div>
    </div>
  );
}