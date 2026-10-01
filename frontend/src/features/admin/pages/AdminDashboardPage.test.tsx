import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Route, Routes } from "react-router-dom";
import { AdminDashboardPage } from "@/features/admin/pages/AdminDashboardPage";
import { AdminVendorsPage } from "@/features/admin/pages/AdminVendorsPage";
import { AdminUsersPage } from "@/features/admin/pages/AdminUsersPage";
import { renderWithProviders } from "@/test/render";
import { makeAdminUser, makePage, makeVendorProfile } from "@/test/factories";
import { businessError } from "@/test/api-errors";
import type { AdminUser } from "@/features/admin/types";
import type { UserResponse } from "@/features/auth/types";
import type { VendorProfile } from "@/features/vendor/types";

const mockFetchVendors = vi.hoisted(() => vi.fn());
const mockFetchUsers = vi.hoisted(() => vi.fn());

vi.mock("@/features/admin/api", () => ({
  fetchAdminVendors: mockFetchVendors,
  fetchAdminUsers: mockFetchUsers,
  approveVendor: vi.fn(),
  rejectVendor: vi.fn(),
  suspendVendor: vi.fn(),
  reinstateVendor: vi.fn(),
  updateUserStatus: vi.fn(),
}));

// The users page needs the signed-in administrator's identity to decide which row
// is "you". Only that selector is needed from the store.
vi.mock("@/features/auth/stores/auth-store", () => ({
  useAuthStore: (selector: (state: { user: UserResponse | null }) => unknown) =>
    selector({ user: mockCurrentUser.user }),
}));

const mockCurrentUser = vi.hoisted(() => ({
  user: null as UserResponse | null,
}));

describe("AdminDashboardPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockFetchVendors.mockResolvedValue(makePage<VendorProfile>([]));
    mockFetchUsers.mockResolvedValue(makePage<AdminUser>([]));
    mockCurrentUser.user = {
      id: 1,
      email: "admin@example.com",
      fullName: "Ada Admin",
      phone: null,
      role: "ADMIN",
      createdAt: "2026-01-01T00:00:00",
    };
  });

  it("offers navigation to both implemented admin capabilities", async () => {
    renderWithProviders(<AdminDashboardPage />);

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: "Admin dashboard" })).toBeInTheDocument();
    });
    expect(screen.getByRole("link", { name: /manage vendors/i })).toHaveAttribute(
      "href",
      "/admin/vendors",
    );
    expect(screen.getByRole("link", { name: /manage users/i })).toHaveAttribute("href", "/admin/users");
  });

  it("shows a heading for each area the operator is responsible for", async () => {
    renderWithProviders(<AdminDashboardPage />);

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: "Vendor management" })).toBeInTheDocument();
    });
    expect(screen.getByRole("heading", { name: "User management" })).toBeInTheDocument();
  });

  it("reads only the two queues an administrator produces", async () => {
    renderWithProviders(<AdminDashboardPage />);

    await waitFor(() => {
      expect(mockFetchVendors).toHaveBeenCalledWith({ status: "PENDING_APPROVAL", page: 0 });
    });
    expect(mockFetchUsers).toHaveBeenCalledWith({ role: null, status: "SUSPENDED", page: 0 });
  });

  it("previews pending vendor applications", async () => {
    mockFetchVendors.mockResolvedValue(
      makePage<VendorProfile>([makeVendorProfile({ businessName: "Orchid House", status: "PENDING_APPROVAL" })]),
    );
    renderWithProviders(<AdminDashboardPage />);

    await waitFor(() => {
      expect(screen.getByText("Orchid House")).toBeInTheDocument();
    });
    expect(screen.getByText("petal@example.com")).toBeInTheDocument();
  });

  it("previews suspended users with their status", async () => {
    mockFetchUsers.mockResolvedValue(
      makePage<AdminUser>([makeAdminUser({ fullName: "Blocked Buyer", status: "SUSPENDED" })]),
    );
    renderWithProviders(<AdminDashboardPage />);

    await waitFor(() => {
      expect(screen.getByText("Blocked Buyer")).toBeInTheDocument();
    });
    expect(screen.getByText("Suspended")).toBeInTheDocument();
  });

  it("shows empty states for both queues when there is nothing to review", async () => {
    renderWithProviders(<AdminDashboardPage />);

    await waitFor(() => {
      expect(
        screen.getByText(/no vendor applications are awaiting approval/i),
      ).toBeInTheDocument();
    });
    expect(screen.getByText(/no user accounts are suspended/i)).toBeInTheDocument();
  });

  it("reports a failed vendor listing", async () => {
    mockFetchVendors.mockRejectedValue(businessError(500, "VALIDATION_FAILED", "Vendor query failed"));
    renderWithProviders(<AdminDashboardPage />);

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent("Vendor query failed");
    });
  });

  it("reports a failed user listing independently of the vendor one", async () => {
    mockFetchUsers.mockRejectedValue(businessError(403, "FORBIDDEN", "Access Denied"));
    renderWithProviders(<AdminDashboardPage />);

    await waitFor(() => {
      expect(screen.getByText(/not permitted/i)).toBeInTheDocument();
    });
  });

  it("surfaces a 403 from the vendor listing as a permission problem", async () => {
    mockFetchVendors.mockRejectedValue(businessError(403, "FORBIDDEN", "Access Denied"));
    renderWithProviders(<AdminDashboardPage />);

    await waitFor(() => {
      expect(screen.getByText(/not permitted/i)).toBeInTheDocument();
    });
  });

  /**
   * The "View all" links are deep links into the two listings. Asserting only
   * that the anchor carries `?status=...` would pass while the destination still
   * ignored it, so these click through and assert the filter the destination
   * page actually applied.
   */
  describe("queue deep links", () => {
    function renderDashboardWithListings() {
      return renderWithProviders(
        <Routes>
          <Route path="/admin" element={<AdminDashboardPage />} />
          <Route path="/admin/vendors" element={<AdminVendorsPage />} />
          <Route path="/admin/users" element={<AdminUsersPage />} />
        </Routes>,
        { route: "/admin" },
      );
    }

    it("opens the vendor listing already filtered to pending applications", async () => {
      mockFetchVendors.mockResolvedValue(
        makePage<VendorProfile>(
          [
            makeVendorProfile({ id: 1, businessName: "Pending One" }),
            makeVendorProfile({ id: 2, businessName: "Pending Two" }),
          ],
          { totalElements: 25, totalPages: 2, first: true, last: false },
        ),
      );
      renderDashboardWithListings();

      await userEvent.click(
        await screen.findByRole("link", { name: /view all 25 pending applications/i }),
      );

      // The destination page, not the link, is what must carry the filter.
      await waitFor(() => {
        expect(screen.getByLabelText(/approval status/i)).toHaveValue("PENDING_APPROVAL");
      });
      expect(await screen.findByRole("heading", { name: "Vendors" })).toBeInTheDocument();
    });

    it("opens the user listing already filtered to suspended accounts", async () => {
      mockFetchUsers.mockResolvedValue(
        makePage<AdminUser>(
          [
            makeAdminUser({ id: 2, fullName: "Suspended One", status: "SUSPENDED" }),
            makeAdminUser({ id: 3, fullName: "Suspended Two", status: "SUSPENDED" }),
          ],
          { totalElements: 30, totalPages: 2, first: true, last: false },
        ),
      );
      renderDashboardWithListings();

      await userEvent.click(
        await screen.findByRole("link", { name: /view all 30 suspended accounts/i }),
      );

      await waitFor(() => {
        expect(screen.getByLabelText(/^status$/i)).toHaveValue("SUSPENDED");
      });
      expect(await screen.findByRole("heading", { name: "Users" })).toBeInTheDocument();
    });
  });
});