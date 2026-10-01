import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createMemoryRouter, RouterProvider, type RouteObject } from "react-router-dom";
import { QueryClientProvider } from "@tanstack/react-query";
import { createTestQueryClient } from "@/test/render";
import { makePage, makeServiceLocations, makeVendorProfile } from "@/test/factories";
// `vi.mock` is hoisted above these, so the router, guard and pages all bind to
// the mocked store and the mocked vendor API client.
import { router as appRouter } from "@/app/router";
import { ProtectedRoute } from "@/shared/components/ProtectedRoute";
import { VendorLayout } from "@/features/vendor/components/VendorLayout";
import { AdminLayout } from "@/features/admin/components/AdminLayout";
import type { UserResponse } from "@/features/auth/types";

const mockFetch = vi.hoisted(() => vi.fn());
const mockUpdate = vi.hoisted(() => vi.fn());
const mockFetchAdminVendors = vi.hoisted(() => vi.fn());
const mockFetchAdminUsers = vi.hoisted(() => vi.fn());
const mockFetchServiceLocations = vi.hoisted(() => vi.fn());
const mockRegisterVendor = vi.hoisted(() => vi.fn());
const mockState = vi.hoisted(() => ({
  isAuthenticated: true,
  hasLoadedInitial: true,
  user: { role: "FLORIST" } as UserResponse | null,
  logout: vi.fn(),
}));

vi.mock("@/features/vendor/api", () => ({
  fetchOwnProfile: mockFetch,
  updateOwnProfile: mockUpdate,
}));

vi.mock("@/features/vendor-registration/api", () => ({
  fetchServiceLocations: mockFetchServiceLocations,
  registerVendor: mockRegisterVendor,
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
    mockFetchAdminVendors.mockResolvedValue(makePage([]));
    mockFetchAdminUsers.mockResolvedValue(makePage([]));
  });

  it("keeps every pre-existing customer route", () => {
    const paths = appRouter.routes
      .flatMap((route) => route.children ?? [])
      .map((child) => child.path);

    expect(paths).toEqual(expect.arrayContaining(["browse", "cart", "orders", "login", "register"]));
  });

  it("adds the four vendor paths under a guarded /vendor namespace", () => {
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
    expect(vendorRoute?.children?.map((child) => child.path)).toEqual([
      undefined,
      "profile",
      "settings",
      "hours",
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
      expect(screen.getByRole("heading", { name: "Orders" })).toBeInTheDocument();
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
      expect(screen.getByRole("heading", { name: "Browse" })).toBeInTheDocument();
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
      expect(screen.getByRole("heading", { name: "FlowerConnect" })).toBeInTheDocument();
    });
    expect(screen.queryByRole("heading", { name: /register your flower shop/i })).not.toBeInTheDocument();
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
