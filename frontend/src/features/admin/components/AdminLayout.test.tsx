import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Route, Routes } from "react-router-dom";
import { AdminLayout } from "@/features/admin/components/AdminLayout";
import { adminKeys } from "@/features/admin/queries";
import { renderWithProviders } from "@/test/render";
import { makePage } from "@/test/factories";
import type { AdminUser } from "@/features/admin/types";
import type { UserResponse } from "@/features/auth/types";

const mockLogout = vi.hoisted(() => vi.fn());
const mockCurrentUser = vi.hoisted(() => ({ user: null as UserResponse | null }));

vi.mock("@/features/auth/stores/auth-store", () => ({
  useAuthStore: (selector: (state: { user: UserResponse | null; logout: () => void }) => unknown) =>
    selector({ user: mockCurrentUser.user, logout: mockLogout }),
}));

function adminUser(): UserResponse {
  return {
    id: 1,
    email: "admin@example.com",
    fullName: "Ada Admin",
    phone: null,
    role: "ADMIN",
    createdAt: "2026-01-01T00:00:00",
  };
}

function renderShell() {
  return renderWithProviders(
    <Routes>
      <Route element={<AdminLayout />}>
        <Route index element={<p>Admin outlet content</p>} />
      </Route>
    </Routes>,
  );
}

describe("AdminLayout", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockCurrentUser.user = adminUser();
  });

  it("links to both implemented admin areas and nothing else", () => {
    renderShell();

    const nav = screen.getByRole("navigation", { name: "Admin" });
    expect(nav).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Dashboard" })).toHaveAttribute("href", "/admin");
    expect(screen.getByRole("link", { name: "Vendors" })).toHaveAttribute("href", "/admin/vendors");
    expect(screen.getByRole("link", { name: "Users" })).toHaveAttribute("href", "/admin/users");

    // Catalog, orders and delivery have no admin API in Phase 2, so they are
    // absent rather than rendered as dead links.
    expect(screen.queryByRole("link", { name: /catalog|orders|delivery/i })).not.toBeInTheDocument();
  });

  it("renders the child route through the outlet", () => {
    renderShell();

    expect(screen.getByText("Admin outlet content")).toBeInTheDocument();
  });

  it("names the signed-in administrator", () => {
    renderShell();

    expect(screen.getByText(/admin@example.com \(ADMIN\)/)).toBeInTheDocument();
  });

  it("clears the admin query cache on logout so the next account sees nothing of this one", async () => {
    const { queryClient } = renderShell();
    queryClient.setQueryData(adminKeys.users.list({ role: null, status: null, page: 0 }), makePage<AdminUser>([]));

    await userEvent.click(screen.getByRole("button", { name: /log out/i }));

    expect(mockLogout).toHaveBeenCalledTimes(1);
    await waitFor(() => {
      expect(queryClient.getQueryState(adminKeys.users.all)).toBeUndefined();
    });
  });
});