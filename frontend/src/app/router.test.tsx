import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { createMemoryRouter, RouterProvider, type RouteObject } from "react-router-dom";
import { QueryClientProvider } from "@tanstack/react-query";
import { createTestQueryClient } from "@/test/render";
import { makeVendorProfile } from "@/test/factories";
// `vi.mock` is hoisted above these, so the router, guard and pages all bind to
// the mocked store and the mocked vendor API client.
import { router as appRouter } from "@/app/router";
import { ProtectedRoute } from "@/shared/components/ProtectedRoute";
import { VendorLayout } from "@/features/vendor/components/VendorLayout";
import type { UserResponse } from "@/features/auth/types";

const mockFetch = vi.hoisted(() => vi.fn());
const mockUpdate = vi.hoisted(() => vi.fn());
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

function florist(): UserResponse {
  return { id: 1, email: "petal@example.com", fullName: "Petal Owner", phone: null, role: "FLORIST", createdAt: "2026-09-01" };
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
