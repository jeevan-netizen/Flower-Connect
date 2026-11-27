import { describe, expect, it, vi, beforeEach } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import { FarmerDashboardPage } from "@/features/farmer/pages/FarmerDashboardPage";
import * as farmerQueries from "@/features/farmer/queries";
import { fetchFarmerPassbook } from "@/features/farmer/api";
import { renderWithProviders } from "@/test/render";
import type { FarmerLink } from "@/features/farmer/types";
import type { Passbook } from "@/features/farmer/types";
import type { UseQueryResult } from "@tanstack/react-query";

vi.mock("@/features/farmer/queries");
vi.mock("@/features/farmer/api");

const mockUseFarmerLinks = vi.mocked(farmerQueries.useFarmerLinks);
const mockFetchFarmerPassbook = vi.mocked(fetchFarmerPassbook);

describe("FarmerDashboardPage", () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it("shows current balance and pending collections from live API data", async () => {
    const links: FarmerLink[] = [
      {
        id: 1,
        vendorId: 10,
        farmerId: 1,
        farmerName: "Demo Farmer",
        vendorName: "Green Acres Florist",
        status: "ACTIVE",
        outstandingBalance: 1800,
        createdAt: "2026-11-20T10:00:00Z",
        updatedAt: "2026-11-26T10:00:00Z",
      },
    ];
    mockUseFarmerLinks.mockReturnValue({
      data: links,
      isPending: false,
      error: null,
      refetch: vi.fn(),
    } as unknown as UseQueryResult<FarmerLink[], Error>);

    const passbook: Passbook = {
      linkId: 1,
      farmerId: 1,
      farmerName: "Demo Farmer",
      vendorId: 10,
      vendorName: "Green Acres Florist",
      linkStatus: "ACTIVE",
      outstandingBalance: 1800,
      entries: [
        {
          id: 10,
          type: "COLLECTION",
          date: "2026-11-25T10:00:00Z",
          runningBalance: 1300,
          amount: 500,
          mode: "UPI",
          status: "PENDING",
          note: null,
          flowerType: null,
          grossKg: null,
          deductionKg: null,
          netKg: null,
          pricePerKg: null,
        },
      ],
    };
    mockFetchFarmerPassbook.mockResolvedValue(passbook);

    renderWithProviders(<FarmerDashboardPage />, { route: "/farmer" });

    expect(screen.getByText("Green Acres Florist")).toBeInTheDocument();
    expect(screen.getByText("Current balance")).toBeInTheDocument();
    expect(screen.getByText("₹1,800.00")).toBeInTheDocument();
    expect(screen.getByText("Pending collections")).toBeInTheDocument();
    // Pending collections come from an async passbook query; wait for it.
    await waitFor(() => {
      expect(screen.getByText("₹500.00")).toBeInTheDocument();
    });
  });

  it("shows an empty state when the farmer has no linked vendors", () => {
    mockUseFarmerLinks.mockReturnValue({
      data: [],
      isPending: false,
      error: null,
      refetch: vi.fn(),
    } as unknown as UseQueryResult<FarmerLink[], Error>);
    mockFetchFarmerPassbook.mockResolvedValue({
      linkId: 1, farmerId: 1, farmerName: "Demo Farmer", vendorId: 10,
      vendorName: "Green Acres Florist", linkStatus: "ACTIVE",
      outstandingBalance: 0, entries: [],
    } as Passbook);

    renderWithProviders(<FarmerDashboardPage />, { route: "/farmer" });

    expect(screen.getByText(/You have no linked florists yet/)).toBeInTheDocument();
  });
});
