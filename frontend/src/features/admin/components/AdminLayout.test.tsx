import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen } from "@testing-library/react";
import { Route, Routes } from "react-router-dom";
import { AdminLayout } from "@/features/admin/components/AdminLayout";
import { renderWithProviders } from "@/test/render";
import type { UserResponse } from "@/features/auth/types";

const mockCurrentUser = vi.hoisted(() => ({ user: null as UserResponse | null }));

vi.mock("@/features/auth/stores/auth-store", () => ({
  useAuthStore: (selector: (state: { user: UserResponse | null }) => unknown) =>
    selector({ user: mockCurrentUser.user }),
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

  it("links to the implemented admin areas and nothing else", () => {
    renderShell();

    const nav = screen.getByRole("navigation", { name: "Admin" });
    expect(nav).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Dashboard" })).toHaveAttribute("href", "/admin");
    expect(screen.getByRole("link", { name: "Vendors" })).toHaveAttribute("href", "/admin/vendors");
    expect(screen.getByRole("link", { name: "Users" })).toHaveAttribute("href", "/admin/users");

    // Catalog, orders and delivery have no admin API in Phase 2, so they are
    // absent rather than rendered as dead links.
    expect(screen.queryByRole("link", { name: /catalog|orders|delivery/i })).not.toBeInTheDocument();

    // Account actions (Change password, Log out) live on the shared /profile
    // page, not in the admin sidebar.
    expect(screen.queryByRole("link", { name: /change password/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /log out/i })).not.toBeInTheDocument();
  });

  it("renders the child route through the outlet", () => {
    renderShell();

    expect(screen.getByText("Admin outlet content")).toBeInTheDocument();
  });

  it("names the signed-in administrator", () => {
    renderShell();

    expect(screen.getByText(/admin@example.com \(ADMIN\)/)).toBeInTheDocument();
  });
});