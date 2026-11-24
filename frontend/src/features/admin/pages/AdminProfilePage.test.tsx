import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AdminProfilePage } from "@/features/admin/pages/AdminProfilePage";
import { renderWithProviders } from "@/test/render";
import { makeUserResponse } from "@/test/factories";
import { useAuthStore } from "@/features/auth/stores/auth-store";

const mockLogout = vi.hoisted(() => vi.fn());
const mockClearAdminCache = vi.hoisted(() => vi.fn());

vi.mock("@/features/auth/api", () => ({
  logout: mockLogout,
}));
vi.mock("@/features/admin/queries", () => ({
  clearAdminCache: mockClearAdminCache,
  adminKeys: {
    all: ["admin"],
    vendors: { all: ["admin", "vendors"], list: () => ["admin", "vendors", "list"] },
    users: { all: ["admin", "users"], list: () => ["admin", "users", "list"] },
    refunds: { all: ["admin", "refunds"], list: () => ["admin", "refunds", "list"] },
  },
}));

function setup(user = makeUserResponse({ role: "ADMIN" })) {
  useAuthStore.setState({ user });
  mockLogout.mockResolvedValue(undefined);
  return renderWithProviders(<AdminProfilePage />);
}

describe("AdminProfilePage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockClearAdminCache.mockClear();
  });

  it("renders the administrator's account details as read-only", async () => {
    setup(makeUserResponse({ id: 9, email: "ada@example.com", fullName: "Ada Admin", role: "ADMIN" }));

    await waitFor(() => {
      expect(screen.getByText(/ada admin/i)).toBeInTheDocument();
    });
    expect(screen.getByText(/ada@example.com/i)).toBeInTheDocument();
    expect(screen.getByText("ADMIN")).toBeInTheDocument();
    expect(screen.getByText("9")).toBeInTheDocument();
    // No credential material is displayed.
    expect(screen.queryByLabelText(/password/i)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/token/i)).not.toBeInTheDocument();
  });

  it("offers a Change password link to the existing password-change route", () => {
    setup();

    const link = screen.getByRole("link", { name: /change password/i });
    expect(link).toHaveAttribute("href", "/settings/password");
  });

  it("does not duplicate account-editing forms meant for customers", () => {
    setup();
    expect(screen.queryByLabelText(/full name/i)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/phone number/i)).not.toBeInTheDocument();
  });

  it("clears the admin query cache, ends the session and navigates home on Log out", async () => {
    const { queryClient } = setup();
    queryClient.setQueryData(["admin", "vendors", "list", null, 0], { content: [] });

    await waitFor(() => {
      expect(screen.getByRole("button", { name: /log out/i })).toBeInTheDocument();
    });
    await userEvent.click(screen.getByRole("button", { name: /log out/i }));

    await waitFor(() => {
      expect(mockClearAdminCache).toHaveBeenCalledTimes(1);
    });
    expect(mockLogout).toHaveBeenCalledTimes(1);
    expect(mockClearAdminCache).toHaveBeenCalledWith(queryClient);
  });
});
