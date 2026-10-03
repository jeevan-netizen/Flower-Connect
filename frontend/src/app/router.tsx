import { useLocation, Outlet, RouterProvider, createBrowserRouter } from "react-router-dom";
import { AnimatePresence } from "framer-motion";
import { AnimatedPage } from "@/motion/AnimatedPage";
import { FadeIn } from "@/motion/FadeIn";
import { useInitAuth, RequireAuth, RequireUnauth } from "@/features/auth/hooks/useAuth";
import { SiteHeader } from "@/app/components/SiteHeader";
import { HomePage } from "@/features/home/pages/HomePage";
import { LoginPage } from "@/features/auth/pages/LoginPage";
import { RegisterPage } from "@/features/auth/pages/RegisterPage";
import { VendorRegisterPage } from "@/features/vendor-registration/pages/VendorRegisterPage";
import { ProtectedRoute } from "@/shared/components/ProtectedRoute";
import { VendorLayout } from "@/features/vendor/components/VendorLayout";
import { VendorDashboardPage } from "@/features/vendor/pages/VendorDashboardPage";
import { VendorProfilePage } from "@/features/vendor/pages/VendorProfilePage";
import { VendorSettingsPage } from "@/features/vendor/pages/VendorSettingsPage";
import { VendorHoursPage } from "@/features/vendor/pages/VendorHoursPage";
import { AdminLayout } from "@/features/admin/components/AdminLayout";
import { AdminDashboardPage } from "@/features/admin/pages/AdminDashboardPage";
import { AdminVendorsPage } from "@/features/admin/pages/AdminVendorsPage";
import { AdminUsersPage } from "@/features/admin/pages/AdminUsersPage";

/**
 * Application role for a vendor. The plan calls this role "VENDOR"; the seeded
 * role is FLORIST and is not renamed (docs/decisions.md, D-11).
 */
const VENDOR_ROLE = "FLORIST";

/** Seeded role name for administrators (`roles` row 3, V3__seed_roles.sql). */
const ADMIN_ROLE = "ADMIN";

function Layout() {
  const location = useLocation();

  return (
    <div className="flex min-h-screen flex-col">
      <SiteHeader />
      <main className="flex-1">
        {/*
          Route transition. `AnimatedPage` is keyed by pathname, so a navigation
          plays its exit on the outgoing page and its entry on the incoming one.

          Three deliberate choices:
          - `mode="wait"` so the two pages are never painted at the same time
            (no overlap flash, no double render of the routed page);
          - `initial={false}` so the first page of a session appears immediately
            instead of animating in behind a blank frame;
          - the wrapper sits *inside* the shell, below the route guards. A guard
            redirect therefore swaps the whole branch above it, and can never
            leave a half-exited page underneath the redirect target.
          Nothing here blocks navigation: the exit is `durations.ui` (240ms) and
          `AnimatedPage` skips both states entirely when reduced motion is set.
        */}
        <AnimatePresence mode="wait" initial={false}>
          <AnimatedPage key={location.pathname}>
            <Outlet />
          </AnimatedPage>
        </AnimatePresence>
      </main>
      <footer className="border-t border-glass-border-soft bg-bolder-bg py-4 text-center text-sm text-bolder-muted">
        &copy; 2026 FlowerConnect
      </footer>
    </div>
  );
}

function Placeholder({ title }: { title: string }) {
  return (
    <FadeIn className="max-w-7xl mx-auto px-4 py-12">
      <h2 className="text-2xl font-semibold">{title}</h2>
      <p className="mt-2 text-slate-600">Phase 0 placeholder page.</p>
    </FadeIn>
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
        // The vendor entry point (plan task 1.7). Public, because
        // `POST /api/v1/vendors/register` creates the account itself, and
        // `RequireUnauth` because it must not be reachable with an existing session:
        // submitting while signed in would create a second, orphaned account.
        path: "vendor/register",
        element: (
          <RequireUnauth>
            <VendorRegisterPage />
          </RequireUnauth>
        ),
      },
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
