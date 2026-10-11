import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider, UseQueryResult } from "@tanstack/react-query";
import { useDiscovery } from "@/features/customer/queries";
import type { DiscoveryPageResponse, DiscoveryResponse } from "@/features/customer/types";

const mockFetchDiscovery = vi.hoisted(() => vi.fn());

vi.mock("@/features/customer/api", () => ({
  fetchDiscovery: mockFetchDiscovery,
}));

function createTestQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: 0, staleTime: 0 },
      mutations: { retry: false },
    },
  });
}

function renderWithClient<T, P>(hook: (props: P) => T, queryClient: QueryClient, options?: { initialProps?: P }) {
  return renderHook(hook, {
    wrapper: ({ children }) => (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    ),
    initialProps: options?.initialProps,
  });
}

describe("useDiscovery query hook", () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    vi.clearAllMocks();
    queryClient = createTestQueryClient();
  });

  const mockDiscoveryResponse: DiscoveryPageResponse = {
    content: [
      {
        id: 1,
        businessName: "Petal & Stem",
        description: "Seasonal bouquets",
        city: "Bengaluru",
        area: "Indiranagar",
        pincode: "560038",
        deliveryRadiusKm: 5,
        minOrderAmount: 300,
        baseDeliveryFee: 25,
        perKmFee: 5,
        freeDeliveryAbove: 1000,
        prepTimeMinutes: 30,
        avgRating: 4.5,
        reviewCount: 12,
        acceptingOrders: true,
        distanceKm: 2.1,
        estimatedDeliveryFee: 35.5,
      },
      {
        id: 2,
        businessName: "Bloom Box",
        description: "Modern arrangements",
        city: "Bengaluru",
        area: "Koramangala",
        pincode: "560034",
        deliveryRadiusKm: 8,
        minOrderAmount: 500,
        baseDeliveryFee: 30,
        perKmFee: 4,
        freeDeliveryAbove: 1500,
        prepTimeMinutes: 45,
        avgRating: null,
        reviewCount: 0,
        acceptingOrders: true,
        distanceKm: 4.3,
        estimatedDeliveryFee: 47.2,
      },
    ] as DiscoveryResponse[],
    page: 0,
    size: 20,
    totalElements: 2,
    totalPages: 1,
    first: true,
    last: true,
    empty: false,
  };

  it("does not execute query when params is null", async () => {
    const { result } = renderWithClient<UseQueryResult<DiscoveryPageResponse, Error>, void>(() => useDiscovery(null), queryClient);

    // When params is null, the query is disabled (enabled: false) so the fetch function is never called
    await waitFor(() => expect(mockFetchDiscovery).not.toHaveBeenCalled());
    expect(result.current.data).toBeUndefined();
  });

  it("executes query when params is provided", async () => {
    mockFetchDiscovery.mockResolvedValue(mockDiscoveryResponse);

    const { result } = renderWithClient(
      () => useDiscovery({ locationId: 3, page: 0, size: 20 }),
      queryClient,
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockFetchDiscovery).toHaveBeenCalledWith({
      locationId: 3,
      page: 0,
      size: 20,
    });
    expect(result.current.data).toEqual(mockDiscoveryResponse);
  });

  it("includes locationId, page, and size in query key", async () => {
    mockFetchDiscovery.mockResolvedValue(mockDiscoveryResponse);

    const { result } = renderWithClient(
      () => useDiscovery({ locationId: 7, page: 1, size: 10 }),
      queryClient,
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    // The query key is internal but we can verify the query runs with correct params
    expect(mockFetchDiscovery).toHaveBeenCalledWith({
      locationId: 7,
      page: 1,
      size: 10,
    });
  });

  it("returns error when fetch fails", async () => {
    mockFetchDiscovery.mockRejectedValue(new Error("Network error"));

    const { result } = renderWithClient(
      () => useDiscovery({ locationId: 3, page: 0, size: 20 }),
      queryClient,
    );

    await waitFor(() => expect(result.current.isError).toBe(true));

    expect(result.current.error).toBeInstanceOf(Error);
    expect(result.current.error?.message).toBe("Network error");
  });

  it("refetches when locationId changes", async () => {
    mockFetchDiscovery.mockResolvedValue(mockDiscoveryResponse);

    const { result, rerender } = renderWithClient(
      ({ locationId }) => useDiscovery({ locationId, page: 0, size: 20 }),
      queryClient,
      { initialProps: { locationId: 3 } },
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockFetchDiscovery).toHaveBeenCalledTimes(1);

    // Change locationId
    rerender({ locationId: 5 });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockFetchDiscovery).toHaveBeenCalledTimes(2);
    expect(mockFetchDiscovery).toHaveBeenLastCalledWith({
      locationId: 5,
      page: 0,
      size: 20,
    });
  });

  it("refetches when page changes", async () => {
    mockFetchDiscovery.mockResolvedValue(mockDiscoveryResponse);

    const { result, rerender } = renderWithClient(
      ({ page }) => useDiscovery({ locationId: 3, page, size: 20 }),
      queryClient,
      { initialProps: { page: 0 } },
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockFetchDiscovery).toHaveBeenCalledTimes(1);

    rerender({ page: 1 });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockFetchDiscovery).toHaveBeenCalledTimes(2);
    expect(mockFetchDiscovery).toHaveBeenLastCalledWith({
      locationId: 3,
      page: 1,
      size: 20,
    });
  });
});