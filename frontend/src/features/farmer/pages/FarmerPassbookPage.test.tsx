import { describe, expect, it, vi, beforeEach } from "vitest";
import { screen } from "@testing-library/react";
import { FarmerPassbookPage } from "@/features/farmer/pages/FarmerPassbookPage";
import * as farmerQueries from "@/features/farmer/queries";
import { renderWithProviders } from "@/test/render";
import type { Passbook } from "@/features/farmer/types";
import type { UseQueryResult } from "@tanstack/react-query";

vi.mock("@/features/farmer/queries");

const mockUseFarmerPassbook = vi.mocked(farmerQueries.useFarmerPassbook);
const mockUseRequestCollection = vi.mocked(farmerQueries.useRequestCollection);

describe("FarmerPassbookPage", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    (mockUseRequestCollection as unknown as { mockReturnValue: (_: unknown) => void }).mockReturnValue({
      mutateAsync: vi.fn(),
      isPending: false,
    });
  });

  it("renders deposit breakdown columns and CollectionForm for an active link", () => {
    const passbook: Passbook = {
      linkId: 1,
      farmerId: 2,
      farmerName: "Demo Farmer",
      vendorId: 3,
      vendorName: "Demo Florist",
      linkStatus: "ACTIVE",
      outstandingBalance: 1800,
      entries: [
        {
          id: 10,
          type: "DEPOSIT",
          date: "2026-11-26T10:00:00Z",
          runningBalance: 1800,
          amount: 1800,
          mode: null,
          status: null,
          note: null,
          flowerType: "Lilies",
          grossKg: 20,
          deductionKg: 2,
          netKg: 18,
          pricePerKg: 100,
        },
      ],
    };

    mockUseFarmerPassbook.mockReturnValue({
      data: passbook,
      isPending: false,
      error: null,
      refetch: vi.fn(),
    } as unknown as UseQueryResult<Passbook, Error>);

    renderWithProviders(<FarmerPassbookPage />, { route: "/farmer/links/1/passbook" });

    expect(screen.getByText("20.00 kg")).toBeInTheDocument();
    expect(screen.getByText("2.00 kg")).toBeInTheDocument();
    expect(screen.getByText("18.00 kg")).toBeInTheDocument();
    expect(screen.getByText("₹100.00/kg")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Request collection/i })).toBeInTheDocument();
  });

  it("renders readable suspended message and disables collection requests when suspended", () => {
    const passbook: Passbook = {
      linkId: 1,
      farmerId: 2,
      farmerName: "Demo Farmer",
      vendorId: 3,
      vendorName: "Demo Florist",
      linkStatus: "SUSPENDED",
      outstandingBalance: 1800,
      entries: [],
    };

    mockUseFarmerPassbook.mockReturnValue({
      data: passbook,
      isPending: false,
      error: null,
      refetch: vi.fn(),
    } as unknown as UseQueryResult<Passbook, Error>);

    renderWithProviders(<FarmerPassbookPage />, { route: "/farmer/links/1/passbook" });

    expect(screen.getByText(/New collection requests are blocked/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Cannot request/i })).toBeDisabled();
  });
});
