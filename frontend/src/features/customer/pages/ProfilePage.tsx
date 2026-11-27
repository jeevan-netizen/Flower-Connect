import { useAuthStore } from "@/features/auth/stores/auth-store";
import { CustomerProfilePage } from "./CustomerProfilePage";
import { VendorProfilePage } from "@/features/vendor/pages/VendorProfilePage";
import { AdminProfilePage } from "@/features/admin/pages/AdminProfilePage";
import { FarmerProfilePage } from "@/features/farmer/pages/FarmerProfilePage";

/**
 * The shared `/profile` destination. It dispatches to the role-appropriate
 * profile page so a single navigation entry serves every authenticated role:
 *
 * - CUSTOMER → account details and saved addresses (`CustomerProfilePage`)
 * - FLORIST  → the vendor business profile (`VendorProfilePage`)
 * - ADMIN    → the administrator account (`AdminProfilePage`)
 * - FARMER   → the farmer account (`FarmerProfilePage`)
 *
 * The route guard and role dispatch keep each account's data scoped to its owner.
 */
export function ProfilePage() {
  const { user } = useAuthStore();
  if (user?.role === "FLORIST") {
    return <VendorProfilePage />;
  }
  if (user?.role === "ADMIN") {
    return <AdminProfilePage />;
  }
  if (user?.role === "FARMER") {
    return <FarmerProfilePage />;
  }
  return <CustomerProfilePage />;
}
