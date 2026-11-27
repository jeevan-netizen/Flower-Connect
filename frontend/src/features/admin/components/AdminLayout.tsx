import { NavLink, Outlet } from "react-router-dom";
import { useAuthStore } from "@/features/auth/stores/auth-store";
import { FOCUS_RING } from "@/motion/pressable";

interface AdminNavItem {
  to: string;
  label: string;
  end?: boolean;
}

/**
 * Admin dashboard shell (plan task 2.10).
 *
 * Only the capabilities the admin backend actually exposes are listed: vendor
 * management (task 2.6), user management (task 2.8), billing (task 10.x),
 * review moderation (task 12.x) and analytics (task 12.x). Catalog, orders,
 * payments and delivery have no admin API yet, so they are deliberately absent
 * rather than rendered as dead links — the same rule the vendor shell follows.
 *
 * Admin account actions (Change password, Log out) live on the shared
 * `/profile` page rendered by `ProfilePage`, not in the admin sidebar — so an
 * action belongs to exactly one place.
 */
const NAV_ITEMS: AdminNavItem[] = [
  { to: "/admin", label: "Dashboard", end: true },
  { to: "/admin/vendors", label: "Vendors" },
  { to: "/admin/users", label: "Users" },
  { to: "/admin/farmers", label: "Farmers" },
  { to: "/admin/proposals", label: "Service Area Proposals" },
  { to: "/admin/billing", label: "Billing & Settlements" },
  { to: "/admin/reviews", label: "Review moderation" },
  { to: "/admin/analytics", label: "Analytics" },
];

function navClass({ isActive }: { isActive: boolean }): string {
  return [
    // Duration/easing come from the motion tokens via `tailwind.config.ts`;
    // `focus-visible` keeps a visible ring on the keyboard path.
    "block rounded-md px-3 py-2 text-sm font-medium transition-colors duration-micro ease-standard",
    FOCUS_RING,
    isActive
      ? "bg-brand-600 text-white"
      : "text-slate-700 hover:bg-slate-100 hover:text-slate-900",
  ].join(" ");
}

/**
 * Sidebar navigation plus the signed-in administrator's identity.
 *
 * The route itself is already guarded by `ProtectedRoute roles={["ADMIN"]}`;
 * this component assumes an administrator is present and does not re-check.
 */
export function AdminLayout() {
  const user = useAuthStore((state) => state.user);

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
