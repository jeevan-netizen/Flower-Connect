import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { QueryClientProvider } from "@tanstack/react-query";
import { useEligibleVendors } from "@/features/farmer/queries";
import type { FarmerVendor } from "@/features/farmer/types";
import { createTestQueryClient } from "@/test/render";

const makeFarmerVendor = (overrides: Partial<FarmerVendor> = {}): FarmerVendor =>
  ({
    id: 1,
    businessName: "Green Acres Florist",
    vendorStatus: "APPROVED",
    linkStatus: null,
    ...overrides,
  });

const mockFetchEligibleVendors = vi.hoisted(() => vi.fn());

vi.mock("@/features/farmer/api", () => ({
  fetchEligibleVendors: mockFetchEligibleVendors,
}));

function wrapper(queryClient = createTestQueryClient()) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  };
}

describe("useEligibleVendors", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("fetches all vendors when no search query is provided (empty string)", async () => {
    const vendors = [makeFarmerVendor()];
    mockFetchEligibleVendors.mockResolvedValue(vendors);

    const { result } = renderHook(() => useEligibleVendors(""), { wrapper: wrapper() });

    expect(mockFetchEligibleVendors).toHaveBeenCalledWith("");
    await waitFor(() => {
      expect(result.current.data).toEqual(vendors);
    });
    expect(result.current.isPending).toBe(false);
  });

  it("fetches vendors filtered by a search query", async () => {
    const vendors = [makeFarmerVendor({ businessName: "Green Acres Florist" })];
    mockFetchEligibleVendors.mockResolvedValue(vendors);

    const { result } = renderHook(() => useEligibleVendors("Green"), { wrapper: wrapper() });

    expect(mockFetchEligibleVendors).toHaveBeenCalledWith("Green");
    await waitFor(() => {
      expect(result.current.data).toEqual(vendors);
    });
    expect(result.current.isPending).toBe(false);
  });

  it("starts in loading state while fetching", async () => {
    mockFetchEligibleVendors.mockImplementation(() => new Promise(() => {}));

    const { result } = renderHook(() => useEligibleVendors(""), { wrapper: wrapper() });

    expect(result.current.isPending).toBe(true);
    expect(result.current.data).toBeUndefined();
  });
});
