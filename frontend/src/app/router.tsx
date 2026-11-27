import { useLocation, Outlet, RouterProvider, createBrowserRouter } from "react-router-dom";
import { AnimatePresence } from "framer-motion";
import { AnimatedPage } from "@/motion/AnimatedPage";
import { useInitAuth, RequireUnauth } from "@/features/auth/hooks/useAuth";
import { SiteHeader } from "@/app/components/SiteHeader";
import { HomePage } from "@/features/home/pages/HomePage";
import { LoginPage } from "@/features/auth/pages/LoginPage";
import { RegisterPage } from "@/features/auth/pages/RegisterPage";
import { ForgotPasswordPage } from "@/features/auth/pages/ForgotPasswordPage";
import { ResetPasswordPage } from "@/features/auth/pages/ResetPasswordPage";
import { VendorRegisterPage } from "@/features/vendor-registration/pages/VendorRegisterPage";
import { ProtectedRoute } from "@/shared/components/ProtectedRoute";
import { VendorLayout } from "@/features/vendor/components/VendorLayout";
import { ApprovedVendorGate } from "@/features/vendor/components/ApprovedVendorGate";
import { VendorDashboardPage } from "@/features/vendor/pages/VendorDashboardPage";
import { VendorSettingsPage } from "@/features/vendor/pages/VendorSettingsPage";
import { VendorHoursPage } from "@/features/vendor/pages/VendorHoursPage";
import { VendorCatalogPage } from "@/features/vendor/pages/VendorCatalogPage";
import { VendorProductFormPage } from "@/features/vendor/pages/VendorProductFormPage";
import { VendorInventoryPage } from "@/features/vendor/pages/VendorInventoryPage";
import { VendorOrderBoardPage } from "@/features/vendor/orders/pages/VendorOrderBoardPage";
import { VendorDisputesPage } from "@/features/vendor/disputes/pages/VendorDisputesPage";
import { VendorBillingPage } from "@/features/vendor/pages/VendorBillingPage";
import { VendorAnalyticsPage } from "@/features/vendor/pages/VendorAnalyticsPage";
import { VendorPosPage } from "@/features/vendor/pages/VendorPosPage";
import { VendorReviewsPage } from "@/features/vendor/pages/VendorReviewsPage";
import { CartPage } from "@/features/cart/pages/CartPage";
import { CheckoutPage } from "@/features/checkout/pages/CheckoutPage";
import { BrowsePage } from "@/features/customer/pages/BrowsePage";
import { SearchPage } from "@/features/customer/pages/SearchPage";
import { VendorStorefrontPage } from "@/features/customer/pages/VendorStorefrontPage";
import { ProductDetailPage } from "@/features/customer/pages/ProductDetailPage";
import { OrderHistoryPage } from "@/features/customer/orders/pages/OrderHistoryPage";
import { OrderDetailPage } from "@/features/customer/orders/pages/OrderDetailPage";
import { ProfilePage } from "@/features/customer/pages/ProfilePage";
import { ChangePasswordPage } from "@/features/auth/pages/ChangePasswordPage";
import { RaiseDisputePage, DisputeDetailPage } from "@/features/dispute/pages/DisputePages";
import { PRODUCT_ROUTE_IDS } from "@/features/customer/navigation";
import { PaymentStatusPage } from "@/features/payment/pages/PaymentStatusPage";
import RazorpayCheckoutPage from "@/features/payment/pages/RazorpayCheckoutPage";
import { AdminRefundsPage } from "@/features/admin/pages/AdminRefundsPage";
import { AdminOrderLookupPage } from "@/features/admin/pages/AdminOrderLookupPage";
import { AdminLocationProposalsPage } from "@/features/admin/pages/AdminLocationProposalsPage";
import { AdminDisputesPage } from "@/features/admin/pages/AdminDisputesPage";
import { AdminLayout } from "@/features/admin/components/AdminLayout";
import { AdminDashboardPage } from "@/features/admin/pages/AdminDashboardPage";
import { AdminVendorsPage } from "@/features/admin/pages/AdminVendorsPage";
import { AdminUsersPage } from "@/features/admin/pages/AdminUsersPage";
import { AdminBillingPage } from "@/features/admin/pages/AdminBillingPage";
import { FarmerDashboardPage } from "@/features/farmer/pages/FarmerDashboardPage";
import { FarmerVendorBrowsePage } from "@/features/farmer/pages/FarmerVendorBrowsePage";
import { FarmerPassbookPage } from "@/features/farmer/pages/FarmerPassbookPage";
import { FarmerLayout } from "@/features/farmer/components/FarmerLayout";
import { FarmerLedgerPage } from "@/features/farmer/pages/FarmerLedgerPage";
import { FarmerRegisterPage } from "@/features/farmer/pages/FarmerRegisterPage";
import { VendorFarmersListPage } from "@/features/vendor/pages/VendorFarmersListPage";
import { VendorFarmerPassbookPage } from "@/features/vendor/pages/VendorFarmerPassbookPage";
import { B2BPlaceOrderPage } from "@/features/b2b/pages/B2BPlaceOrderPage";
import { B2BOrdersPage } from "@/features/b2b/pages/B2BOrdersPage";
import { B2BOrderDetailPage } from "@/features/b2b/pages/B2BOrderDetailPage";
import { B2BStatementsPage } from "@/features/b2b/pages/B2BStatementsPage";
import { AdminReviewsPage } from "@/features/admin/pages/AdminReviewsPage";
import { AdminAnalyticsPage } from "@/features/admin/pages/AdminAnalyticsPage";
import { AdminFarmersPage } from "@/features/admin/pages/AdminFarmersPage";
import { AdminFarmerDetailPage } from "@/features/admin/pages/AdminFarmerDetailPage";

/** Seeded role name for customers (V3__seed_roles.sql). */
const CUSTOMER_ROLE = "CUSTOMER";

/** Seeded role name for vendors. The plan calls this role "VENDOR"; the seeded role is FLORIST and is not renamed (docs/decisions.md, D-11). */
const VENDOR_ROLE = "FLORIST";

/** Seeded role name for farmers (V35 migration). */
const FARMER_ROLE = "FARMER";

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
      <footer className="border-t border-bolder-line bg-bolder-bg py-8">
        <p className="mx-auto w-full max-w-landing px-5 text-center text-sm text-bolder-muted sm:px-8 lg:px-14">
          &copy; 2026 FlowerConnect
        </p>
      </footer>
    </div>
  );
}

/**
 * The catalog and inventory screens are wrapped in `ApprovedVendorGate`.
 *
 * This is UX, not authorization: `SecurityConfig`'s `hasRole("FLORIST")` on
 * `/api/v1/vendors/**` and `@RequiresApprovedVendor` on every catalog and inventory
 * handler are the authority (D-13). The gate exists so an unapproved vendor is not
 * offered buttons that would each come back `403 VENDOR_NOT_APPROVED`, and it reads
 * the same cached vendor profile the layout's banner does — there is no second copy
 * of the approval state anywhere in the frontend.
 */
function gated(element: React.ReactElement) {
  return <ApprovedVendorGate>{element}</ApprovedVendorGate>;
}

export const router = createBrowserRouter([
  {
    path: "/",
    element: <LayoutWithAuth />,
    children: [
      { index: true, element: <HomePage /> },
      {
        // Public customer discovery (plan tasks 4.6, 4.7). Browsing is open
        // to everyone: the catalogue endpoints are `permitAll`, and the
        // visibility rules — active products, available stock, approved and
        // accepting florists, delivery radius — are enforced server-side per
        // request. Signing in is only needed to add something to a cart.
        path: "browse",
        element: <BrowsePage />,
      },
      {
        // The search surface is also the layout for its product-detail child,
        // so a product opened from the results renders over them with the
        // results still behind it. The child's route id is what the search
        // page uses to recognise that its product route matched.
        path: "search",
        element: <SearchPage />,
        children: [
          // The search content is rendered by the layout itself, so the index
          // route contributes nothing.
          { index: true, element: null },
          { path: "products/:productId", id: PRODUCT_ROUTE_IDS.search, element: <ProductDetailPage /> },
        ],
      },
      {
        // A florist's public storefront (plan tasks 4.8, 4.9). The route is
        // also the layout for its product-detail child, so a product opened
        // from here renders over the storefront with the shop still behind it;
        // a product URL reached directly renders on its own.
        path: "vendors/:vendorId",
        element: <VendorStorefrontPage />,
        children: [
          // The storefront content is rendered by the layout itself, so the
          // index route contributes nothing.
          { index: true, element: null },
          { path: "products/:productId", id: PRODUCT_ROUTE_IDS.storefront, element: <ProductDetailPage /> },
        ],
      },
      {
        path: "cart",
        element: (
          <ProtectedRoute roles={[CUSTOMER_ROLE]}>
            <CartPage />
          </ProtectedRoute>
        ),
      },
      {
        path: "checkout",
        element: (
          <ProtectedRoute roles={[CUSTOMER_ROLE]}>
            <CheckoutPage />
          </ProtectedRoute>
        ),
      },
      {
        path: "payment/:paymentGroupId",
        element: (
          <ProtectedRoute roles={[CUSTOMER_ROLE]}>
            <PaymentStatusPage />
          </ProtectedRoute>
        ),
      },
      {
        path: "payment/razorpay/:orderId",
        element: (
          <ProtectedRoute roles={[CUSTOMER_ROLE]}>
            <RazorpayCheckoutPage />
          </ProtectedRoute>
        ),
      },
      {
        path: "orders",
        element: (
          <ProtectedRoute roles={[CUSTOMER_ROLE]}>
            <OrderHistoryPage />
          </ProtectedRoute>
        ),
      },
      {
        // The shared profile destination: customers see their account and
        // address profile, vendors see their vendor profile, farmers see their
        // farmer account, and administrators see their administrator account.
        // `ProfilePage` dispatches purely on
        // the authenticated role, so customer account data is never exposed to
        // vendors or admins, vendor profile management is never exposed to
        // customers or admins, and admin account data is never exposed to
        // customers or vendors. The route is guarded to CUSTOMER, VENDOR,
        // FARMER and ADMIN; other roles are refused by the guard, and unauthenticated
        // visitors are redirected to login.
        path: "profile",
        element: (
          <ProtectedRoute roles={[CUSTOMER_ROLE, VENDOR_ROLE, FARMER_ROLE, ADMIN_ROLE]}>
            <ProfilePage />
          </ProtectedRoute>
        ),
      },
      {
        path: "orders/:orderId",
        element: (
          <ProtectedRoute roles={[CUSTOMER_ROLE]}>
            <OrderDetailPage />
          </ProtectedRoute>
        ),
      },
      {
        path: "orders/:orderId/dispute",
        element: (
          <ProtectedRoute roles={[CUSTOMER_ROLE]}>
            <RaiseDisputePage />
          </ProtectedRoute>
        ),
      },
      {
        path: "disputes/:disputeId",
        element: (
          <ProtectedRoute roles={[CUSTOMER_ROLE]}>
            <DisputeDetailPage />
          </ProtectedRoute>
        ),
      },
      {
        // Change password is available to every authenticated role — customer,
        // florist and admin alike — because the endpoint verifies the caller's
        // own session and revokes their other refresh tokens on success.
        path: "settings/password",
        element: (
          <ProtectedRoute roles={[CUSTOMER_ROLE, VENDOR_ROLE, FARMER_ROLE, ADMIN_ROLE]}>
            <ChangePasswordPage />
          </ProtectedRoute>
        ),
      },
      { path: "login", element: <RequireUnauth><LoginPage /></RequireUnauth> },
      { path: "register", element: <RequireUnauth><RegisterPage /></RequireUnauth> },
      {
        // Password reset is public: no session is needed to request or complete one.
        path: "forgot-password",
        element: <RequireUnauth><ForgotPasswordPage /></RequireUnauth>,
      },
      {
        path: "reset-password",
        element: <RequireUnauth><ResetPasswordPage /></RequireUnauth>,
      },
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
        // Farmer registration entry point. Public, because
        // `POST /api/v1/farmer/register` creates the FARMER account itself, and
        // `RequireUnauth` because it must not be reachable with an existing session:
        // submitting while signed in would create a second, orphaned account.
        path: "farmer/register",
        element: (
          <RequireUnauth>
            <FarmerRegisterPage />
          </RequireUnauth>
        ),
      },
      {
        // Phase 11 farmer dashboard shell. Guarded by FARMER role; the backend
        // re-checks the role on every request.
        path: "farmer",
        element: (
          <ProtectedRoute roles={[FARMER_ROLE]}>
            <FarmerLayout />
          </ProtectedRoute>
        ),
        children: [
          { index: true, element: <FarmerDashboardPage /> },
          { path: "vendors", element: <FarmerVendorBrowsePage /> },
          { path: "ledger", element: <FarmerLedgerPage /> },
          { path: "links/:linkId/passbook", element: <FarmerPassbookPage /> },
        ],
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
          { path: "settings", element: <VendorSettingsPage /> },
          { path: "hours", element: <VendorHoursPage /> },
          // Phase 3g catalog and inventory (plan tasks 3.5–3.9). `new` is declared
          // before `:productId` so the literal segment is matched first; with the
          // dynamic one first it would win and "new" would be read as a product id.
          { path: "catalog", element: gated(<VendorCatalogPage />) },
          { path: "catalog/new", element: gated(<VendorProductFormPage />) },
          { path: "catalog/:productId", element: gated(<VendorProductFormPage />) },
          { path: "inventory", element: gated(<VendorInventoryPage />) },
          // Phase 6 vendor order board (task 6.7). Orders are the vendor's own
          // existing in-flight orders, so a suspended vendor can still reach it
          // — the backend enforces FLORIST role plus per-order ownership, but
          // deliberately not @RequiresApprovedVendor (D-6 / D-43).
          { path: "orders", element: <VendorOrderBoardPage /> },
          { path: "disputes", element: <VendorDisputesPage /> },
          { path: "billing", element: <VendorBillingPage /> },
          // Phase 3 Stage 3 B2B module (Stage 4 of the B2B module): place an
          // order, list and detail orders, and statements with payment
          // recording. Place-order and statements are vendor transactions, so
          // they are approval-gated at the UX layer; orders list/detail are
          // read-only over the caller's own orders like the vendor order board
          // (D-6).
          { path: "b2b/place-order", element: gated(<B2BPlaceOrderPage />) },
          { path: "b2b/orders", element: <B2BOrdersPage /> },
          { path: "b2b/orders/:orderId", element: <B2BOrderDetailPage /> },
          { path: "b2b/statements", element: gated(<B2BStatementsPage />) },
          // Phase 11 vendor farmer management (Stage 3 of the farmer module).
          { path: "farmers", element: <VendorFarmersListPage /> },
          { path: "farmer-links/:linkId/passbook", element: <VendorFarmerPassbookPage /> },
          { path: "pos", element: gated(<VendorPosPage />) },
          // Phase 12 vendor analytics (task 12.5): marketplace and POS metrics
          // for the vendor's own date range. FLORIST role only; suspended
          // vendors can read (D-6).
          { path: "analytics", element: <VendorAnalyticsPage /> },
          // Phase 12 vendor reviews (task 12.2): the vendor's own reviews and
          // replies. FLORIST role only; the backend enforces per-review
          // ownership, and a suspended vendor can still reply (D-6).
          { path: "reviews", element: <VendorReviewsPage /> },
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
          { path: "proposals", element: <AdminLocationProposalsPage /> },
          { path: "refunds", element: <AdminRefundsPage /> },
          { path: "orders/lookup", element: <AdminOrderLookupPage /> },
          { path: "disputes", element: <AdminDisputesPage /> },
          { path: "billing", element: <AdminBillingPage /> },
          // Phase 12 review moderation and analytics (task 12.4). ADMIN only;
          // `SecurityConfig` matches `/api/v1/admin/reviews/**` with
          // `hasRole("ADMIN")` on every request.
          { path: "reviews", element: <AdminReviewsPage /> },
          { path: "analytics", element: <AdminAnalyticsPage /> },
          // Admin farmer management: list and detail. Backend enforces
          // `hasRole("ADMIN")` on `/api/v1/admin/farmers/**`; the route guard is
          // routing UX only.
          { path: "farmers", element: <AdminFarmersPage /> },
          { path: "farmers/:farmerId", element: <AdminFarmerDetailPage /> },
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
