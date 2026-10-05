import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Outlet } from "react-router-dom";
import { VendorLayout } from "@/features/vendor/components/VendorLayout";
import { vendorKeys } from "@/features/vendor/queries";
import { renderWithProviders, createTestQueryClient } from "@/test/render";
import { makeVendorProfile } from "@/test/factories";
import { businessError, vendorNotApprovedError } from "@/test/api-errors";

const mockFetch = vi.hoisted(() => vi.fn());
const mockUpdate = vi.hoisted(() => vi.fn());
const mockLogout = vi.hoisted(() => vi.fn());

vi.mock("@/features/vendor/api", () => ({
  fetchOwnProfile: mockFetch,
  updateOwnProfile: mockUpdate,
}));

vi.mock("@/features/auth/stores/auth-store", () => ({
  useAuthStore: (selector?: (s: { logout: () => void }) => unknown) => {
    const state = { logout: mockLogout };
    return selector ? selector(state) : state;
  },
}));

function renderLayout(queryClient = createTestQueryClient()) {
  return renderWithProviders(
    <>
      <VendorLayout />
      <Outlet />
    </>,
    { queryClient },
  );
}

describe("VendorLayout", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockFetch.mockResolvedValue(makeVendorProfile());
  });

  it("navigates to the four Phase 2 vendor areas", async () => {
    renderLayout();

    const nav = await screen.findByRole("navigation", { name: /vendor/i });
    expect(nav).toBeInTheDocument();

    const dashboard = screen.getByRole("link", { name: "Dashboard" });
    expect(dashboard).toHaveAttribute("href", "/vendor");
    expect(screen.getByRole("link", { name: "Profile" })).toHaveAttribute("href", "/vendor/profile");
    expect(screen.getByRole("link", { name: "Delivery settings" })).toHaveAttribute("href", "/vendor/settings");
    expect(screen.getByRole("link", { name: "Operating hours" })).toHaveAttribute("href", "/vendor/hours");
  });

  it("offers the Phase 3g catalog areas to an approved vendor", async () => {
    renderLayout();

    await screen.findByRole("navigation", { name: /vendor/i });
    // The navigation renders in every state, so wait for the profile it filters on:
    // the banner row only appears once the query has settled.
    await waitFor(() => {
      expect(screen.getByText(/2 of 7 days open/i)).toBeInTheDocument();
    });

    expect(screen.getByRole("link", { name: "Catalog" })).toHaveAttribute("href", "/vendor/catalog");
    expect(screen.getByRole("link", { name: "Inventory" })).toHaveAttribute("href", "/vendor/inventory");
  });

  it("withholds the catalog areas from a vendor who is not approved", async () => {
    mockFetch.mockResolvedValue(makeVendorProfile({ status: "PENDING_APPROVAL" }));

    renderLayout();

    await waitFor(() => {
      expect(screen.getByText(/2 of 7 days open/i)).toBeInTheDocument();
    });

    ["Catalog", "Inventory"].forEach((label) => {
      expect(screen.queryByRole("link", { name: new RegExp(label, "i") })).not.toBeInTheDocument();
    });
    // The areas a pending vendor still needs stay reachable.
    expect(screen.getByRole("link", { name: "Profile" })).toBeInTheDocument();
  });

  it("does not link to the Phase 5/6 areas that have no API yet", async () => {
    renderLayout();

    await screen.findByRole("navigation", { name: /vendor/i });

    ["Orders", "Payments", "Delivery tracking"].forEach((label) => {
      expect(screen.queryByRole("link", { name: new RegExp(label, "i") })).not.toBeInTheDocument();
    });
  });

  it("shows the approval banner and the signed-in vendor on every screen", async () => {
    renderLayout();

    await waitFor(() => {
      expect(screen.getByText(/signed in as petal@example\.com/i)).toBeInTheDocument();
    });
    expect(screen.getByText(/2 of 7 days open/i)).toBeInTheDocument();
  });

  it("shows a loading state while the profile is in flight", async () => {
    mockFetch.mockImplementation(() => new Promise(() => {}));

    renderLayout();

    expect(await screen.findByText(/loading your vendor profile/i)).toBeInTheDocument();
  });

  it("surfaces an approval refusal as guidance, not as a login redirect", async () => {
    mockFetch.mockRejectedValue(vendorNotApprovedError());

    renderLayout();

    expect(await screen.findByText(/approval required/i)).toBeInTheDocument();
  });

  it("surfaces a missing vendor profile with its own message", async () => {
    mockFetch.mockRejectedValue(businessError(404, "NOT_FOUND", "No vendor profile exists for this account"));

    renderLayout();

    expect(await screen.findByText(/no vendor profile/i)).toBeInTheDocument();
  });

  it("drops cached vendor data on logout so the next account starts clean", async () => {
    const queryClient = createTestQueryClient();
    renderLayout(queryClient);

    await waitFor(() => {
      expect(queryClient.getQueryData(vendorKeys.profile)).toBeDefined();
    });

    const removeSpy = vi.spyOn(queryClient, "removeQueries");
    await userEvent.click(screen.getByRole("button", { name: /log out/i }));

    expect(mockLogout).toHaveBeenCalledTimes(1);
    expect(removeSpy).toHaveBeenCalledWith({ queryKey: vendorKeys.all });
  });
});
