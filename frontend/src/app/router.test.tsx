import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createMemoryRouter, RouterProvider, type RouteObject } from "react-router-dom";
import { QueryClientProvider } from "@tanstack/react-query";
import { createTestQueryClient } from "@/test/render";
import {
  makeCartResponse,
  makeCustomerOrderPageResponse,
  makeCustomerOrderResponse,
  makeDiscoveryVendor,
  makeInventory,
  makePage,
  makeProduct,
  makePublicProduct,
  makeServiceLocations,
  makeStorefront,
  makeVendorProfile,
} from "@/test/factories";
// `vi.mock` is hoisted above these, so the router, guard and pages all bind to
// the mocked store and the mocked vendor API client.
import { router as appRouter } from "@/app/router";
import { ProtectedRoute } from "@/shared/components/ProtectedRoute";
import { useLocationStore } from "@/features/location/stores/location-store";
import { VendorLayout } from "@/features/vendor/components/VendorLayout";
import { AdminLayout } from "@/features/admin/components/AdminLayout";
import type { UserResponse } from "@/features/auth/types";

const mockFetch = vi.hoisted(() => vi.fn());
const mockUpdate = vi.hoisted(() => vi.fn());
const mockFetchCategories = vi.hoisted(() => vi.fn());
const mockFetchProducts = vi.hoisted(() => vi.fn());
const mockFetchProduct = vi.hoisted(() => vi.fn());
const mockFetchImages = vi.hoisted(() => vi.fn());
const mockFetchInventory = vi.hoisted(() => vi.fn());
const mockFetchMovements = vi.hoisted(() => vi.fn());
const mockFetchLowStock = vi.hoisted(() => vi.fn());
const mockFetchAdminVendors = vi.hoisted(() => vi.fn());
const mockFetchAdminUsers = vi.hoisted(() => vi.fn());
const mockFetchServiceLocations = vi.hoisted(() => vi.fn());
const mockRegisterVendor = vi.hoisted(() => vi.fn());
const mockFetchDiscovery = vi.hoisted(() => vi.fn());
const mockFetchSearch = vi.hoisted(() => vi.fn());
const mockFetchStorefront = vi.hoisted(() => vi.fn());
const mockFetchPublicProduct = vi.hoisted(() => vi.fn());
const mockFetchCustomerCategories = vi.hoisted(() => vi.fn());
const mockFetchCart = vi.hoisted(() => vi.fn());
const mockState = vi.hoisted(() => ({
  isAuthenticated: true,
  hasLoadedInitial: true,
  user: { role: "FLORIST" } as UserResponse | null,
  logout: vi.fn(),
}));

/**
 * The whole vendor API surface is stubbed, not just the profile read: the Phase 3g
 * pages bind every catalog and inventory client at module load, so a partial stub
 * would fail on the import rather than on the assertion under test.
 */
vi.mock("@/features/vendor/api", () => ({
  fetchOwnProfile: mockFetch,
  updateOwnProfile: mockUpdate,
  fetchCategories: mockFetchCategories,
  fetchProducts: mockFetchProducts,
  fetchProduct: mockFetchProduct,
  createProduct: vi.fn(),
  updateProduct: vi.fn(),
  deactivateProduct: vi.fn(),
  fetchProductImages: mockFetchImages,
  uploadProductImage: vi.fn(),
  setPrimaryProductImage: vi.fn(),
  reorderProductImages: vi.fn(),
  deleteProductImage: vi.fn(),
  fetchInventory: mockFetchInventory,
  fetchLowStockProducts: mockFetchLowStock,
  fetchStockMovements: mockFetchMovements,
  stockIn: vi.fn(),
  stockOut: vi.fn(),
  adjustStock: vi.fn(),
  writeOffStock: vi.fn(),
  updateLowStockThreshold: vi.fn(),
  updateExpiryDate: vi.fn(),
}));

// The service-location API moved to the shared location slice in task 4.2: the
// header's location control and the vendor registration form share one fetch,
// so mocking the location module covers both.
vi.mock("@/features/location/api", () => ({
  fetchServiceLocations: mockFetchServiceLocations,
}));

vi.mock("@/features/vendor-registration/api", () => ({
  registerVendor: mockRegisterVendor,
}));

/**
 * The customer catalogue API is stubbed as a whole, for the same reason the
 * vendor one is: the catalogue pages bind every client at module load, so a
 * partial stub would fail on the import rather than on the assertion.
 */
vi.mock("@/features/customer/api", () => ({
  fetchDiscovery: mockFetchDiscovery,
  fetchSearch: mockFetchSearch,
  fetchStorefront: mockFetchStorefront,
  fetchPublicProduct: mockFetchPublicProduct,
  fetchCategories: mockFetchCustomerCategories,
}));

vi.mock("@/features/admin/api", () => ({
  fetchAdminVendors: mockFetchAdminVendors,
  fetchAdminUsers: mockFetchAdminUsers,
  approveVendor: vi.fn(),
  rejectVendor: vi.fn(),
  suspendVendor: vi.fn(),
  reinstateVendor: vi.fn(),
  updateUserStatus: vi.fn(),
}));

/**
 * The cart API is stubbed because the shell's cart badge reads it on every
 * page, and because the cart route itself has to render for the guard to be
 * observable.
 */
vi.mock("@/features/cart/api", () => ({
  fetchCart: mockFetchCart,
  addCartItem: vi.fn(),
  updateCartItem: vi.fn(),
  deleteCartItem: vi.fn(),
  clearCart: vi.fn(),
}));

/**
 * The orders API is stubbed because the customer orders page reads it on every
 * visit, and the route must render for the guard to be observable.
 */
vi.mock("@/features/customer/orders/api", () => ({
  fetchOrders: vi.fn().mockResolvedValue(makeCustomerOrderPageResponse()),
  fetchOrder: vi.fn().mockResolvedValue(makeCustomerOrderResponse()),
}));

vi.mock("@/features/auth/stores/auth-store", () => ({
  useAuthStore: (selector?: (s: typeof mockState) => unknown) => {
    const state = {
      isAuthenticated: mockState.isAuthenticated,
      hasLoadedInitial: mockState.hasLoadedInitial,
      user: mockState.user,
      logout: mockState.logout,
    };
    return selector ? selector(state) : state;
  },
}));

const VENDOR_ROLE = "FLORIST";
const ADMIN_ROLE = "ADMIN";

function florist(): UserResponse {
  return { id: 1, email: "petal@example.com", fullName: "Petal Owner", phone: null, role: "FLORIST", createdAt: "2026-09-01" };
}

function administrator(): UserResponse {
  return { id: 9, email: "admin@example.com", fullName: "Ada Admin", phone: null, role: "ADMIN", createdAt: "2026-01-01" };
}

function renderAt(path: string) {
  const router = createMemoryRouter(appRouter.routes, { initialEntries: [path] });
  return render(
    <QueryClientProvider client={createTestQueryClient()}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );
}

/**
 * The customer route table is asserted against the real exported router so a
 * change to it cannot silently drop or reorder a customer path.
 */
describe("application router", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockState.isAuthenticated = true;
    mockState.hasLoadedInitial = true;
    mockState.user = florist();
    mockFetch.mockResolvedValue(makeVendorProfile());
    mockFetchCategories.mockResolvedValue(makePage([]));
    mockFetchProducts.mockResolvedValue(makePage([]));
    mockFetchProduct.mockResolvedValue(makeProduct());
    mockFetchImages.mockResolvedValue([]);
    mockFetchInventory.mockResolvedValue(makeInventory());
    mockFetchMovements.mockResolvedValue(makePage([]));
    mockFetchLowStock.mockResolvedValue(makePage([]));
    mockFetchAdminVendors.mockResolvedValue(makePage([]));
    mockFetchAdminUsers.mockResolvedValue(makePage([]));
    // The shell's location control mounts on every page, so the shared
    // locations fetch must resolve wherever the router renders.
    mockFetchServiceLocations.mockResolvedValue(makeServiceLocations());
    mockFetchDiscovery.mockResolvedValue(makePage([makeDiscoveryVendor()]));
    mockFetchSearch.mockResolvedValue(makePage([makePublicProduct()]));
    mockFetchStorefront.mockResolvedValue(makeStorefront());
    mockFetchPublicProduct.mockResolvedValue(makePublicProduct());
    mockFetchCustomerCategories.mockResolvedValue(makePage([]));
  });

  it("keeps every pre-existing customer route", () => {
    const paths = appRouter.routes
      .flatMap((route) => route.children ?? [])
      .map((child) => child.path);

    expect(paths).toEqual(expect.arrayContaining(["browse", "cart", "orders", "login", "register"]));
  });

  it("adds the vendor paths under a guarded /vendor namespace", () => {
    // `RouteObject` is the widened shape: the router's discriminated union of
    // index and path routes does not expose `element` on every member.
    const vendorRoute = appRouter.routes
      .flatMap((route) => route.children ?? [])
      .find((child) => child.path === "vendor") as RouteObject | undefined;

    expect(vendorRoute).toBeDefined();
    expect(vendorRoute?.element).toEqual(
      <ProtectedRoute roles={[VENDOR_ROLE]}>
        <VendorLayout />
      </ProtectedRoute>,
    );
    // `catalog/new` is declared before `catalog/:productId` so the literal segment
    // is matched first; with the dynamic one first it would win and "new" would be
    // read as a product id.
    expect(vendorRoute?.children?.map((child) => child.path)).toEqual([
      undefined,
      "profile",
      "settings",
      "hours",
      "catalog",
      "catalog/new",
      "catalog/:productId",
      "inventory",
      "orders",
    ]);
  });

  it("renders the vendor dashboard for a florist", async () => {
    renderAt("/vendor");

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: "Petal & Stem" })).toBeInTheDocument();
    });
    expect(screen.getByRole("link", { name: "Operating hours" })).toBeInTheDocument();
  });

  it("renders the profile editor for a florist", async () => {
    renderAt("/vendor/profile");

    await waitFor(() => {
      expect(screen.getByLabelText(/business name/i)).toBeInTheDocument();
    });
  });

  it("renders the catalog for an approved florist", async () => {
    renderAt("/vendor/catalog");

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: "Catalog" })).toBeInTheDocument();
    });
    expect(mockFetchProducts).toHaveBeenCalledWith(
      { status: null, categoryId: null, name: "", page: 0 },
      expect.anything(),
    );
  });

  it("renders the create-product form at /vendor/catalog/new", async () => {
    renderAt("/vendor/catalog/new");

    await waitFor(() => {
      expect(screen.getByLabelText(/product name/i)).toBeInTheDocument();
    });
    // The create form has no product, so nothing keyed by an id is fetched.
    expect(mockFetchProduct).not.toHaveBeenCalled();
  });

  it("renders the product editor at /vendor/catalog/:productId", async () => {
    renderAt("/vendor/catalog/101");

    await waitFor(() => {
      expect(mockFetchProduct).toHaveBeenCalledWith(101);
    });
    expect(await screen.findByRole("heading", { name: /edit red rose bouquet/i })).toBeInTheDocument();
  });

  it("renders the inventory screen for an approved florist", async () => {
    renderAt("/vendor/inventory");

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: "Inventory" })).toBeInTheDocument();
    });
    expect(mockFetchLowStock).toHaveBeenCalledWith(0, 20, expect.anything());
  });

  it("refuses the catalog to a florist who is not approved, without calling the catalog API", async () => {
    mockFetch.mockResolvedValue(makeVendorProfile({ status: "PENDING_APPROVAL" }));

    renderAt("/vendor/catalog");

    expect(await screen.findByText(/catalog unavailable/i)).toBeInTheDocument();
    // The gate reads the cached profile only: nothing gated is ever requested.
    expect(mockFetchProducts).not.toHaveBeenCalled();
    expect(mockFetchLowStock).not.toHaveBeenCalled();
  });

  it("leaves the ungated vendor areas reachable while the catalog is withheld", async () => {
    mockFetch.mockResolvedValue(makeVendorProfile({ status: "PENDING_APPROVAL" }));

    renderAt("/vendor/settings");

    await waitFor(() => {
      expect(screen.getByLabelText(/base delivery fee/i)).toBeInTheDocument();
    });
  });

  it("renders the delivery-settings editor for a florist", async () => {
    renderAt("/vendor/settings");

    await waitFor(() => {
      expect(screen.getByLabelText(/delivery radius/i)).toBeInTheDocument();
    });
  });

  it("renders the operating-hours editor for a florist", async () => {
    renderAt("/vendor/hours");

    await waitFor(() => {
      expect(screen.getByText("Monday")).toBeInTheDocument();
    });
  });

  it("keeps a customer out of every vendor route", async () => {
    mockState.user = { ...florist(), role: "CUSTOMER" };

    renderAt("/vendor/profile");

    await waitFor(() => {
      expect(screen.getByText(/does not have access to this area/i)).toBeInTheDocument();
    });
    expect(screen.queryByLabelText(/business name/i)).not.toBeInTheDocument();
  });

  it("keeps an anonymous visitor out of the vendor area and shows the login page", async () => {
    mockState.isAuthenticated = false;
    mockState.user = null;

    renderAt("/vendor");

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: /welcome back/i })).toBeInTheDocument();
    });
    expect(mockFetch).not.toHaveBeenCalled();
  });

  it("keeps customer routes working for a signed-in customer", async () => {
    mockState.user = { ...florist(), role: "CUSTOMER" };

    renderAt("/orders");

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: "Your orders" })).toBeInTheDocument();
    });
    // A customer never sees the vendor entry point in the customer navigation.
    expect(screen.queryByRole("link", { name: "Vendor" })).not.toBeInTheDocument();
  });

  it("shows the vendor entry point in the header for a florist", async () => {
    renderAt("/");

    await waitFor(() => {
      expect(screen.getByRole("link", { name: "Vendor" })).toBeInTheDocument();
    });
    expect(screen.getByRole("link", { name: "Vendor" })).toHaveAttribute("href", "/vendor");
  });

  it("keeps the customer routes available to a florist", async () => {
    renderAt("/browse");

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: "Browse florists" })).toBeInTheDocument();
    });
  });
});

/**
 * The customer catalogue routes (plan tasks 4.6, 4.7, 4.8, 4.9).
 *
 * Browsing is public: the catalogue endpoints are `permitAll` and the
 * visibility rules are enforced server-side. Only the cart needs an account,
 * so these routes must not send an anonymous visitor to the login page — and
 * the cart and checkout must still send one.
 */
describe("customer catalogue routes", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // Sibling suite: set up every piece of shared state this one relies on.
    mockState.isAuthenticated = true;
    mockState.hasLoadedInitial = true;
    mockState.user = { ...florist(), role: "CUSTOMER" };
    sessionStorage.clear();
    useLocationStore.setState({ selected: null });
    mockFetchServiceLocations.mockResolvedValue(makeServiceLocations());
    mockFetchDiscovery.mockResolvedValue(makePage([makeDiscoveryVendor()]));
    mockFetchSearch.mockResolvedValue(makePage([makePublicProduct()]));
    mockFetchStorefront.mockResolvedValue(makeStorefront());
    mockFetchPublicProduct.mockResolvedValue(makePublicProduct());
    mockFetchCustomerCategories.mockResolvedValue(makePage([]));
    mockFetchCart.mockResolvedValue(makeCartResponse());
  });

  it("registers the four customer catalogue paths", () => {
    const paths = appRouter.routes
      .flatMap((route) => route.children ?? [])
      .map((child) => child.path);

    expect(paths).toEqual(
      expect.arrayContaining(["browse", "search", "vendors/:vendorId"]),
    );
  });

  it("nests the product detail under its florist's storefront", () => {
    const storefrontRoute = appRouter.routes
      .flatMap((route) => route.children ?? [])
      .find((child) => child.path === "vendors/:vendorId") as RouteObject | undefined;

    expect(storefrontRoute?.children?.map((child) => child.path)).toEqual([
      undefined,
      "products/:productId",
    ]);
  });

  it("nests the product detail under the search surface too", () => {
    const searchRoute = appRouter.routes
      .flatMap((route) => route.children ?? [])
      .find((child) => child.path === "search") as RouteObject | undefined;

    expect(searchRoute?.children?.map((child) => child.path)).toEqual([
      undefined,
      "products/:productId",
    ]);
  });

  it("gives the two product routes distinct ids", () => {
    // React Router requires route ids to be globally unique within a data
    // router, and throws on a collision when the router is created. Distinct
    // ids are also how each surface recognises that its product child matched.
    const children = appRouter.routes.flatMap((route) => route.children ?? []);
    const storefrontRoute = children.find(
      (child) => child.path === "vendors/:vendorId",
    ) as RouteObject | undefined;
    const searchRoute = children.find(
      (child) => child.path === "search",
    ) as RouteObject | undefined;

    const storefrontProduct = storefrontRoute?.children?.find(
      (child) => child.path === "products/:productId",
    );
    const searchProduct = searchRoute?.children?.find(
      (child) => child.path === "products/:productId",
    );

    expect(storefrontProduct?.id).toBe("storefront-product");
    expect(searchProduct?.id).toBe("search-product");
    expect(storefrontProduct?.id).not.toBe(searchProduct?.id);
  });

  it("serves the browse page to an anonymous visitor", async () => {
    mockState.isAuthenticated = false;
    mockState.user = null;

    renderAt("/browse");

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: "Browse florists" })).toBeInTheDocument();
    });
    // Not the login page: browsing does not need an account.
    expect(screen.queryByRole("heading", { name: /welcome back/i })).not.toBeInTheDocument();
  });

  it("serves the search page to an anonymous visitor", async () => {
    mockState.isAuthenticated = false;
    mockState.user = null;

    renderAt("/search");

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: "Search products" })).toBeInTheDocument();
    });
    expect(screen.queryByRole("heading", { name: /welcome back/i })).not.toBeInTheDocument();
  });

  it("serves a florist's storefront to an anonymous visitor", async () => {
    mockState.isAuthenticated = false;
    mockState.user = null;

    renderAt("/vendors/7");

    await waitFor(() => {
      expect(
        screen.getByRole("heading", { name: "Petal & Stem", level: 1 }),
      ).toBeInTheDocument();
    });
    expect(mockFetchStorefront).toHaveBeenCalledWith(7, 0, 20, expect.anything());
  });

  it("serves a product to an anonymous visitor", async () => {
    mockState.isAuthenticated = false;
    mockState.user = null;
    // The product endpoint requires a delivery area, so one is chosen here.
    useLocationStore.setState({
      selected: {
        id: 4,
        city: "Bengaluru",
        area: "Koramangala",
        pincode: "560034",
        latitude: 12.9352,
        longitude: 77.6245,
      },
    });

    renderAt("/vendors/7/products/1001");

    await waitFor(() => {
      expect(
        screen.getByRole("heading", { name: "Red Rose Bouquet", level: 1 }),
      ).toBeInTheDocument();
    });
  });

  it("still sends an anonymous visitor away from the cart", async () => {
    mockState.isAuthenticated = false;
    mockState.user = null;

    renderAt("/cart");

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: /welcome back/i })).toBeInTheDocument();
    });
  });

  it("still sends an anonymous visitor away from checkout", async () => {
    mockState.isAuthenticated = false;
    mockState.user = null;

    renderAt("/checkout");

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: /welcome back/i })).toBeInTheDocument();
    });
  });

  it("keeps the cart reachable for a signed-in customer", async () => {
    renderAt("/cart");

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: /your cart/i })).toBeInTheDocument();
    });
  });
});

/**
 * Admin access (plan task 2.10). `ProtectedRoute` is the same component the vendor
 * namespace uses; the three outcomes asserted here — anonymous, wrong role,
 * ADMIN — are the ones an admin screen has to keep distinct.
 */
describe("admin route protection", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // This suite is a sibling of "application router", so it sets up the shared
    // auth state itself rather than inheriting that suite's `beforeEach`.
    mockState.isAuthenticated = true;
    mockState.hasLoadedInitial = true;
    mockState.user = administrator();
    mockFetch.mockResolvedValue(makeVendorProfile());
    mockFetchAdminVendors.mockResolvedValue(makePage([]));
    mockFetchAdminUsers.mockResolvedValue(makePage([]));
    mockFetchServiceLocations.mockResolvedValue(makeServiceLocations());
  });

  it("adds the three admin paths under a guarded /admin namespace", () => {
    const adminRoute = appRouter.routes
      .flatMap((route) => route.children ?? [])
      .find((child) => child.path === "admin") as RouteObject | undefined;

    expect(adminRoute).toBeDefined();
    expect(adminRoute?.element).toEqual(
      <ProtectedRoute roles={[ADMIN_ROLE]}>
        <AdminLayout />
      </ProtectedRoute>,
    );
    expect(adminRoute?.children?.map((child) => child.path)).toEqual([
      undefined,
      "vendors",
      "users",
      "refunds",
      "orders/lookup",
    ]);
  });

  it("renders the admin dashboard for an ADMIN", async () => {
    mockState.user = administrator();

    renderAt("/admin");

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: "Admin dashboard" })).toBeInTheDocument();
    });
    expect(screen.getByRole("link", { name: /manage vendors/i })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /manage users/i })).toBeInTheDocument();
  });

  it("renders vendor management for an ADMIN", async () => {
    mockState.user = administrator();

    renderAt("/admin/vendors");

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: "Vendors" })).toBeInTheDocument();
    });
    expect(mockFetchAdminVendors).toHaveBeenCalledWith({ status: null, page: 0 });
  });

  it("renders user management for an ADMIN", async () => {
    mockState.user = administrator();

    renderAt("/admin/users");

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: "Users" })).toBeInTheDocument();
    });
    expect(mockFetchAdminUsers).toHaveBeenCalledWith({ role: null, status: null, page: 0 });
  });

  it("keeps a non-admin out of every admin route without calling the admin API", async () => {
    mockState.user = florist();

    renderAt("/admin/vendors");

    await waitFor(() => {
      expect(screen.getByText(/does not have access to this area/i)).toBeInTheDocument();
    });
    expect(screen.queryByRole("heading", { name: "Vendors" })).not.toBeInTheDocument();
    expect(mockFetchAdminVendors).not.toHaveBeenCalled();
    expect(mockFetchAdminUsers).not.toHaveBeenCalled();
  });

  it("keeps a CUSTOMER out of the admin area too", async () => {
    mockState.user = { ...florist(), role: "CUSTOMER" };

    renderAt("/admin/users");

    await waitFor(() => {
      expect(screen.getByText(/does not have access to this area/i)).toBeInTheDocument();
    });
    expect(mockFetchAdminUsers).not.toHaveBeenCalled();
  });

  it("keeps an anonymous visitor out of the admin area and shows the login page", async () => {
    mockState.isAuthenticated = false;
    mockState.user = null;

    renderAt("/admin");

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: /welcome back/i })).toBeInTheDocument();
    });
    expect(mockFetchAdminVendors).not.toHaveBeenCalled();
    expect(mockFetchAdminUsers).not.toHaveBeenCalled();
  });

  it("shows the admin entry point in the header only for an ADMIN", async () => {
    mockState.user = administrator();

    renderAt("/");

    await waitFor(() => {
      expect(screen.getByRole("link", { name: "Admin" })).toBeInTheDocument();
    });
    expect(screen.getByRole("link", { name: "Admin" })).toHaveAttribute("href", "/admin");
  });

  it("hides the admin entry point from a florist", async () => {
    mockState.user = florist();

    renderAt("/");

    await waitFor(() => {
      expect(screen.getByRole("link", { name: "Vendor" })).toBeInTheDocument();
    });
    expect(screen.queryByRole("link", { name: "Admin" })).not.toBeInTheDocument();
  });

  it("leaves the vendor routes working for a florist after the admin routes were added", async () => {
    mockState.user = florist();

    renderAt("/vendor/profile");

    await waitFor(() => {
      expect(screen.getByLabelText(/business name/i)).toBeInTheDocument();
    });
  });
});

/**
 * The vendor entry point (plan task 1.7). Reachable while signed out from two
 * places — the login page and the header's florist link — and closed to a signed-in
 * account, because `POST /api/v1/vendors/register` creates a *new* account.
 */
describe("vendor entry point", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // Sibling suite: set up every piece of shared state this one relies on.
    mockState.isAuthenticated = false;
    mockState.hasLoadedInitial = true;
    mockState.user = null;
    mockFetchServiceLocations.mockResolvedValue(makeServiceLocations());
  });

  it("serves the registration page to an anonymous visitor", async () => {
    renderAt("/vendor/register");

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: /register your flower shop/i })).toBeInTheDocument();
    });
    expect(await screen.findByLabelText(/service area/i)).toBeInTheDocument();
    // The vendor namespace stays closed to the same visitor.
    expect(mockFetch).not.toHaveBeenCalled();
  });

  it("is reachable from the login page", async () => {
    renderAt("/login");

    const entryLink = await screen.findByRole("link", { name: /register it on flowerconnect/i });
    expect(entryLink).toHaveAttribute("href", "/vendor/register");

    await userEvent.click(entryLink);

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: /register your flower shop/i })).toBeInTheDocument();
    });
  });

  it("is reachable from the Phase 1 florist entry point in the header", async () => {
    renderAt("/");

    const entryLink = await screen.findByRole("link", { name: /for florists/i });
    expect(entryLink).toHaveAttribute("href", "/vendor/register");
  });

  it("sends a signed-in account away, because registering would create a second one", async () => {
    mockState.isAuthenticated = true;
    mockState.user = florist();

    renderAt("/vendor/register");

    await waitFor(() => {
      expect(
        screen.getByRole("heading", { name: /flowers, beautifully delivered/i, level: 1 }),
      ).toBeInTheDocument();
    });
    expect(mockRegisterVendor).not.toHaveBeenCalled();
  });

  it("exposes no admin entry point from the registration flow", async () => {
    renderAt("/vendor/register");

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: /register your flower shop/i })).toBeInTheDocument();
    });
    expect(screen.queryByRole("link", { name: "Admin" })).not.toBeInTheDocument();
  });

  it("keeps the existing vendor dashboard reachable for a florist", async () => {
    mockState.isAuthenticated = true;
    mockState.user = florist();
    mockFetch.mockResolvedValue(makeVendorProfile());

    renderAt("/vendor");

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: "Petal & Stem" })).toBeInTheDocument();
    });
  });
});
