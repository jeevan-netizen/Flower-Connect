import { useAuthStore } from "@/features/auth/stores/auth-store";
import { CustomerProfilePage } from "./CustomerProfilePage";
import { VendorProfilePage } from "@/features/vendor/pages/VendorProfilePage";
import { AdminProfilePage } from "@/features/admin/pages/AdminProfilePage";

/**
 * The shared `/profile` destination. It dispatches to the role-appropriate
 * profile page so a single navigation entry serves every authenticated role:
 *
 * - CUSTOMER → account details and saved addresses (`CustomerProfilePage`)
 * - VENDOR   → the vendor business profile (`VendorProfilePage`)
 * - ADMIN    → the administrator account (`AdminProfilePage`)
 *
 * Unauthenticated users and unsupported roles never reach here: the route is
 * guarded by `ProtectedRoute`, which redirects unauthenticated visitors to login
 * and refuses other roles. Customer account/address data is never exposed to
 * vendors and vendor profile management is never exposed to customers, because
 * the component branches purely on the authenticated role read from the JWT
 * subject on every render.
 */
export function ProfilePage() {
  const { user } = useAuthStore();
  if (user?.role === "FLORIST") {
    return <VendorProfilePage />;
  }
  if (user?.role === "ADMIN") {
    return <AdminProfilePage />;
  }
  return <CustomerProfilePage />;
}
