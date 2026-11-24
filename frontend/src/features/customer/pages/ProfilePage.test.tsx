import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen } from "@testing-library/react";
import { ProfilePage } from "@/features/customer/pages/ProfilePage";
import { renderWithProviders } from "@/test/render";
import { useAuthStore } from "@/features/auth/stores/auth-store";

const mockCustomer = vi.hoisted(() => vi.fn());
const mockVendor = vi.hoisted(() => vi.fn());
const mockAdmin = vi.hoisted(() => vi.fn());

vi.mock("./CustomerProfilePage", () => ({
  CustomerProfilePage: mockCustomer,
}));
vi.mock("@/features/vendor/pages/VendorProfilePage", () => ({
  VendorProfilePage: mockVendor,
}));
vi.mock("@/features/admin/pages/AdminProfilePage", () => ({
  AdminProfilePage: mockAdmin,
}));

function setup(role: "CUSTOMER" | "FLORIST" | "ADMIN") {
  useAuthStore.setState({
    user: { id: 1, email: "x@example.com", fullName: "X", phone: null, role, createdAt: "2026-01-01" },
  });
  return renderWithProviders(<ProfilePage />);
}

describe("ProfilePage role dispatch", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockCustomer.mockReturnValue(<p data-testid="customer-profile">Customer profile</p>);
    mockVendor.mockReturnValue(<p data-testid="vendor-profile">Vendor profile</p>);
    mockAdmin.mockReturnValue(<p data-testid="admin-profile">Admin profile</p>);
  });

  it("renders CustomerProfilePage for a CUSTOMER", () => {
    setup("CUSTOMER");
    expect(screen.getByTestId("customer-profile")).toBeInTheDocument();
    expect(screen.queryByTestId("vendor-profile")).not.toBeInTheDocument();
    expect(screen.queryByTestId("admin-profile")).not.toBeInTheDocument();
  });

  it("renders VendorProfilePage for a FLORIST", () => {
    setup("FLORIST");
    expect(screen.getByTestId("vendor-profile")).toBeInTheDocument();
    expect(screen.queryByTestId("customer-profile")).not.toBeInTheDocument();
    expect(screen.queryByTestId("admin-profile")).not.toBeInTheDocument();
  });

  it("renders AdminProfilePage for an ADMIN", () => {
    setup("ADMIN");
    expect(screen.getByTestId("admin-profile")).toBeInTheDocument();
    expect(screen.queryByTestId("customer-profile")).not.toBeInTheDocument();
    expect(screen.queryByTestId("vendor-profile")).not.toBeInTheDocument();
  });
});
