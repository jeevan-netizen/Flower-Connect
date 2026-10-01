import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { QueryClientProvider } from "@tanstack/react-query";
import { clearVendorCache, useUpdateVendorProfile, useVendorProfile, vendorKeys } from "@/features/vendor/queries";
import { createTestQueryClient } from "@/test/render";
import { makeVendorProfile } from "@/test/factories";
import type { VendorProfileUpdateRequest } from "@/features/vendor/types";

const mockFetch = vi.hoisted(() => vi.fn());
const mockUpdate = vi.hoisted(() => vi.fn());

vi.mock("@/features/vendor/api", () => ({
  fetchOwnProfile: mockFetch,
  updateOwnProfile: mockUpdate,
}));

function wrapper(queryClient = createTestQueryClient()) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  };
}

const payload: VendorProfileUpdateRequest = {
  businessName: "Petal and Stem",
  description: null,
  addressLine1: "12 MG Road",
  addressLine2: null,
  serviceLocationId: 3,
  deliveryRadiusKm: 5,
  logoUrl: null,
  minOrderAmount: 0,
  baseDeliveryFee: 0,
  perKmFee: 0,
  freeDeliveryAbove: null,
  prepTimeMinutes: 30,
  slotDurationMinutes: 60,
  maxOrdersPerSlot: 10,
  acceptingOrders: true,
};

describe("vendor queries", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockFetch.mockResolvedValue(makeVendorProfile());
  });

  it("reads the profile through the single shared query key", async () => {
    const { result } = renderHook(() => useVendorProfile(), { wrapper: wrapper() });

    await waitFor(() => {
      expect(result.current.data?.businessName).toBe("Petal & Stem");
    });
    expect(mockFetch).toHaveBeenCalledTimes(1);
  });

  it("writes the PUT response straight into the cache instead of refetching", async () => {
    const queryClient = createTestQueryClient();
    const saved = makeVendorProfile({ businessName: "Petal and Stem", minOrderAmount: 450 });
    mockUpdate.mockResolvedValue(saved);

    const { result } = renderHook(() => useUpdateVendorProfile(), { wrapper: wrapper(queryClient) });

    await result.current.mutateAsync(payload);

    expect(mockUpdate).toHaveBeenCalledWith(payload);
    expect(queryClient.getQueryData(vendorKeys.profile)).toEqual(saved);
    // The PUT response is the whole profile, so no second GET is needed.
    expect(mockFetch).not.toHaveBeenCalled();
  });

  it("leaves the cached profile untouched when the write fails", async () => {
    const queryClient = createTestQueryClient();
    queryClient.setQueryData(vendorKeys.profile, makeVendorProfile());
    mockUpdate.mockRejectedValue(new Error("boom"));

    const { result } = renderHook(() => useUpdateVendorProfile(), { wrapper: wrapper(queryClient) });

    await expect(result.current.mutateAsync(payload)).rejects.toThrow("boom");
    expect(queryClient.getQueryData(vendorKeys.profile)).toEqual(makeVendorProfile());
  });

  it("clearVendorCache removes the cached profile", () => {
    const queryClient = createTestQueryClient();
    queryClient.setQueryData(vendorKeys.profile, makeVendorProfile());

    clearVendorCache(queryClient);

    expect(queryClient.getQueryData(vendorKeys.profile)).toBeUndefined();
  });

  it("shares one request between the layout banner and a page", async () => {
    const queryClient = createTestQueryClient();

    renderHook(
      () => {
        useVendorProfile();
        useVendorProfile();
      },
      { wrapper: wrapper(queryClient) },
    );

    await waitFor(() => {
      expect(mockFetch).toHaveBeenCalledTimes(1);
    });
  });
});
