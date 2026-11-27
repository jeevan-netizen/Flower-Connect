import { describe, expect, it, vi, beforeEach } from "vitest";
import { screen } from "@testing-library/react";
import { FarmerLedgerPage } from "@/features/farmer/pages/FarmerLedgerPage";
import * as farmerQueries from "@/features/farmer/queries";
import { renderWithProviders } from "@/test/render";
import type { FarmerLink } from "@/features/farmer/types";
import type { UseQueryResult } from "@tanstack/react-query";

vi.mock("@/features/farmer/queries");

const mockUseFarmerLinks = vi.mocked(farmerQueries.useFarmerLinks);

describe("FarmerLedgerPage", () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it("displays each linked vendor as a separated ledger entry with its outstanding balance", () => {
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
      {
        id: 2,
        vendorId: 11,
        farmerId: 1,
        farmerName: "Demo Farmer",
        vendorName: "Blossom Haven",
        status: "PENDING",
        outstandingBalance: 0,
        createdAt: "2026-11-21T10:00:00Z",
        updatedAt: "2026-11-26T10:00:00Z",
      },
    ];
    mockUseFarmerLinks.mockReturnValue({
      data: links,
      isPending: false,
      error: null,
      refetch: vi.fn(),
    } as unknown as UseQueryResult<FarmerLink[], Error>);

    renderWithProviders(<FarmerLedgerPage />, { route: "/farmer/ledger" });

    // Each vendor is clearly separated and named.
    expect(screen.getByText("Green Acres Florist")).toBeInTheDocument();
    expect(screen.getByText("Blossom Haven")).toBeInTheDocument();
    // The current balance card sums across vendors using live API data.
    expect(screen.getByText("Current balance")).toBeInTheDocument();
    expect(screen.getByText("₹1,800.00")).toBeInTheDocument();
    // Each vendor links to its own passbook.
    expect(screen.getAllByRole("link", { name: /open passbook/i })).toHaveLength(2);
  });

  it("shows an empty state when the farmer has no linked vendors", () => {
    mockUseFarmerLinks.mockReturnValue({
      data: [],
      isPending: false,
      error: null,
      refetch: vi.fn(),
    } as unknown as UseQueryResult<FarmerLink[], Error>);

    renderWithProviders(<FarmerLedgerPage />, { route: "/farmer/ledger" });

    expect(screen.getByText("Your ledger is empty")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /open passbook/i })).not.toBeInTheDocument();
  });

  it("shows a loading state while the links are fetched", () => {
    mockUseFarmerLinks.mockReturnValue({
      data: undefined,
      isPending: true,
      error: null,
      refetch: vi.fn(),
    } as unknown as UseQueryResult<FarmerLink[], Error>);

    renderWithProviders(<FarmerLedgerPage />, { route: "/farmer/ledger" });

    expect(screen.getByText(/loading your ledger/i)).toBeInTheDocument();
  });
});
