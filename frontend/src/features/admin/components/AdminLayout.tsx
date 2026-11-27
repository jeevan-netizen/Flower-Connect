import { NavLink, Outlet } from "react-router-dom";
import { useAuthStore } from "@/features/auth/stores/auth-store";
import { navLinkClass } from "@/shared/ui/classes";

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
  return navLinkClass(isActive);
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
    <div className="min-h-[60vh] bg-bolder-bg">
      <div className="mx-auto flex w-full max-w-app flex-col gap-6 px-4 py-6 sm:px-6 md:flex-row lg:px-8">
        <aside className="md:w-60 md:shrink-0">
          <div className="rounded-card border border-bolder-line bg-bolder-surface p-4 shadow-card">
            <p className="font-display text-xl text-bolder-text">FlowerConnect</p>
            <p className="mt-1 text-xs text-bolder-muted">Admin area</p>
          </div>

          <nav className="mt-4 rounded-card border border-bolder-line bg-bolder-surface p-2 shadow-card" aria-label="Admin">
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
            <p className="text-xs text-bolder-muted">
              Signed in as {user.email} ({user.role})
            </p>
          )}
          <Outlet />
        </main>
      </div>
    </div>
  );
}
