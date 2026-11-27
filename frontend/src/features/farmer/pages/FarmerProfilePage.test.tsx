import { describe, expect, it, vi, beforeEach } from "vitest";
import { screen } from "@testing-library/react";
import { FarmerProfilePage } from "@/features/farmer/pages/FarmerProfilePage";
import * as farmerQueries from "@/features/farmer/queries";
import { renderWithProviders } from "@/test/render";
import type { FarmerProfile } from "@/features/farmer/types";
import type { UseQueryResult } from "@tanstack/react-query";

vi.mock("@/features/farmer/queries");

const mockUseFarmerProfile = vi.mocked(farmerQueries.useFarmerProfile);

describe("FarmerProfilePage", () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it("displays the farmer's name, mobile and address from the profile API", () => {
    const profile: FarmerProfile = {
      id: 7,
      email: "farmer@test.com",
      fullName: "Demo Farmer",
      phone: "+919999999999",
      address: "12, Flower Market Road, Koramangala",
      role: "FARMER",
    };
    mockUseFarmerProfile.mockReturnValue({
      data: profile,
      isPending: false,
      error: null,
      refetch: vi.fn(),
    } as unknown as UseQueryResult<FarmerProfile, Error>);

    renderWithProviders(<FarmerProfilePage />, { route: "/farmer/profile" });

    expect(screen.getByText("Demo Farmer")).toBeInTheDocument();
    expect(screen.getByText("+919999999999")).toBeInTheDocument();
    expect(screen.getByText("12, Flower Market Road, Koramangala")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /log out/i })).toBeInTheDocument();
  });

  it("shows Not provided when mobile and address are missing", () => {
    const profile: FarmerProfile = {
      id: 7,
      email: "farmer@test.com",
      fullName: "Demo Farmer",
      phone: null,
      address: null,
      role: "FARMER",
    };
    mockUseFarmerProfile.mockReturnValue({
      data: profile,
      isPending: false,
      error: null,
      refetch: vi.fn(),
    } as unknown as UseQueryResult<FarmerProfile, Error>);

    renderWithProviders(<FarmerProfilePage />, { route: "/farmer/profile" });

    expect(screen.getAllByText("Not provided")).toHaveLength(2);
  });

  it("shows a loading state while the profile is fetched", () => {
    mockUseFarmerProfile.mockReturnValue({
      data: undefined,
      isPending: true,
      error: null,
      refetch: vi.fn(),
    } as unknown as UseQueryResult<FarmerProfile, Error>);

    renderWithProviders(<FarmerProfilePage />, { route: "/farmer/profile" });

    expect(screen.getByText(/loading your profile/i)).toBeInTheDocument();
  });

  it("shows an error state with retry when the profile fetch fails", () => {
    const error = new Error("boom");
    mockUseFarmerProfile.mockReturnValue({
      data: undefined,
      isPending: false,
      error,
      refetch: vi.fn(),
    } as unknown as UseQueryResult<FarmerProfile, Error>);

    renderWithProviders(<FarmerProfilePage />, { route: "/farmer/profile" });

    expect(screen.getByText("Something went wrong")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /try again/i })).toBeInTheDocument();
  });
});
