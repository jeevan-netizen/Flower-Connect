import { createBrowserRouter, Link, Outlet, RouterProvider, useNavigate } from "react-router-dom";
import { useQueryClient } from "@tanstack/react-query";
import { useInitAuth, RequireAuth, RequireUnauth } from "@/features/auth/hooks/useAuth";
import { useAuthStore } from "@/features/auth/stores/auth-store";
import { LoginPage } from "@/features/auth/pages/LoginPage";
import { RegisterPage } from "@/features/auth/pages/RegisterPage";
import { ProtectedRoute } from "@/shared/components/ProtectedRoute";
import { clearVendorCache } from "@/features/vendor/queries";
import { VendorLayout } from "@/features/vendor/components/VendorLayout";
import { VendorDashboardPage } from "@/features/vendor/pages/VendorDashboardPage";
import { VendorProfilePage } from "@/features/vendor/pages/VendorProfilePage";
import { VendorSettingsPage } from "@/features/vendor/pages/VendorSettingsPage";
import { VendorHoursPage } from "@/features/vendor/pages/VendorHoursPage";
import { AdminLayout } from "@/features/admin/components/AdminLayout";
import { AdminDashboardPage } from "@/features/admin/pages/AdminDashboardPage";
import { AdminVendorsPage } from "@/features/admin/pages/AdminVendorsPage";
import { AdminUsersPage } from "@/features/admin/pages/AdminUsersPage";
import { clearAdminCache } from "@/features/admin/queries";

/**
 * Application role for a vendor. The plan calls this role "VENDOR"; the seeded
 * role is FLORIST and is not renamed (docs/decisions.md, D-11).
 */
const VENDOR_ROLE = "FLORIST";

/** Seeded role name for administrators (`roles` row 3, V3__seed_roles.sql). */
const ADMIN_ROLE = "ADMIN";

function Layout() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { isAuthenticated, user, logout } = useAuthStore();

  const handleLogout = () => {
    // Vendor and admin data are each cached under their own query key; drop both
    // so the next account to sign in on this tab never sees the previous user's
    // profile, user list or vendor list.
    clearVendorCache(queryClient);
    clearAdminCache(queryClient);
    logout();
    navigate("/", { replace: true });
  };

  return (
    <div className="min-h-screen flex flex-col">
      <header className="bg-brand-600 text-white">
        <nav className="max-w-7xl mx-auto px-4 py-3 flex items-center justify-between">
          <Link to="/" className="text-xl font-bold">
            FlowerConnect
          </Link>
          <div className="flex gap-4 text-sm">
            {isAuthenticated ? (
              <>
                <Link to="/browse">Browse</Link>
                <Link to="/cart">Cart</Link>
                <Link to="/orders">Orders</Link>
                {user?.role === VENDOR_ROLE && <Link to="/vendor">Vendor</Link>}
                {user?.role === ADMIN_ROLE && <Link to="/admin">Admin</Link>}
                <button onClick={handleLogout} className="hover:underline">
                  Log out
                </button>
              </>
            ) : (
              <>
                <Link to="/login">Login</Link>
                <Link to="/register">Register</Link>
              </>
            )}
          </div>
        </nav>
      </header>
      <main className="flex-1">
        <Outlet />
      </main>
      <footer className="bg-slate-100 text-center text-sm py-4">
        &copy; 2026 FlowerConnect
      </footer>
    </div>
  );
}

function HomePage() {
  return (
    <div className="max-w-7xl mx-auto px-4 py-12 text-center">
      <h1 className="text-4xl font-bold text-brand-700">FlowerConnect</h1>
      <p className="mt-3 text-slate-600">Hyperlocal flower marketplace</p>
    </div>
  );
}

function Placeholder({ title }: { title: string }) {
  return (
    <div className="max-w-7xl mx-auto px-4 py-12">
      <h2 className="text-2xl font-semibold">{title}</h2>
      <p className="mt-2 text-slate-600">Phase 0 placeholder page.</p>
    </div>
  );
}

export const router = createBrowserRouter([
  {
    path: "/",
    element: <LayoutWithAuth />,
    children: [
      { index: true, element: <HomePage /> },
      {
        path: "browse",
        element: (
          <RequireAuth>
            <Placeholder title="Browse" />
          </RequireAuth>
        ),
      },
      {
        path: "cart",
        element: (
          <RequireAuth>
            <Placeholder title="Cart" />
          </RequireAuth>
        ),
      },
      {
        path: "orders",
        element: (
          <RequireAuth>
            <Placeholder title="Orders" />
          </RequireAuth>
        ),
      },
      { path: "login", element: <RequireUnauth><LoginPage /></RequireUnauth> },
      { path: "register", element: <RequireUnauth><RegisterPage /></RequireUnauth> },
      {
        // Phase 2 vendor dashboard shell. The guard is UX gating only — the
        // backend re-checks ROLE_FLORIST (and, for approved-only vendor
        // features, the approval state) on every request.
        path: "vendor",
        element: (
          <ProtectedRoute roles={[VENDOR_ROLE]}>
            <VendorLayout />
          </ProtectedRoute>
        ),
        children: [
          { index: true, element: <VendorDashboardPage /> },
          { path: "profile", element: <VendorProfilePage /> },
          { path: "settings", element: <VendorSettingsPage /> },
          { path: "hours", element: <VendorHoursPage /> },
        ],
      },
      {
        // Phase 2 admin dashboard shell. Same reasoning as the vendor namespace:
        // the guard is routing UX only, and `SecurityConfig` matches
        // `/api/v1/admin/**` with `hasRole("ADMIN")` on every request, so the
        // backend stays the authorization authority.
        path: "admin",
        element: (
          <ProtectedRoute roles={[ADMIN_ROLE]}>
            <AdminLayout />
          </ProtectedRoute>
        ),
        children: [
          { index: true, element: <AdminDashboardPage /> },
          { path: "vendors", element: <AdminVendorsPage /> },
          { path: "users", element: <AdminUsersPage /> },
        ],
      },
    ],
  },
]);

function LayoutWithAuth() {
  useInitAuth();
  return <Layout />;
}

export function AppRouter() {
  return <RouterProvider router={router} />;
}
