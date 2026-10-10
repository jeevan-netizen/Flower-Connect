import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { QueryClientProvider } from "@tanstack/react-query";
import {
  categoryKeys,
  clearVendorCache,
  useCreateProduct,
  useDeactivateProduct,
  useSetPrimaryProductImage,
  useStockAction,
  useUpdateProduct,
  useUpdateVendorProfile,
  useVendorProfile,
  vendorKeys,
} from "@/features/vendor/queries";
import { createTestQueryClient } from "@/test/render";
import { makeInventory, makeProduct, makeVendorProfile } from "@/test/factories";
import type { VendorProfileUpdateRequest } from "@/features/vendor/types";

const mockFetch = vi.hoisted(() => vi.fn());
const mockUpdate = vi.hoisted(() => vi.fn());
const mockCreate = vi.hoisted(() => vi.fn());
const mockUpdateProduct = vi.hoisted(() => vi.fn());
const mockDeactivate = vi.hoisted(() => vi.fn());
const mockSetPrimary = vi.hoisted(() => vi.fn());
const mockStockIn = vi.hoisted(() => vi.fn());

vi.mock("@/features/vendor/api", () => ({
  fetchOwnProfile: mockFetch,
  updateOwnProfile: mockUpdate,
  createProduct: mockCreate,
  updateProduct: mockUpdateProduct,
  deactivateProduct: mockDeactivate,
  setPrimaryProductImage: mockSetPrimary,
  stockIn: mockStockIn,
  stockOut: vi.fn(),
  adjustStock: vi.fn(),
  writeOffStock: vi.fn(),
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

/*
 * Catalog and inventory invalidation.
 *
 * The rule under test is that a write must not leave the UI describing a state the
 * server has already left behind — a rename that regenerates a slug, a stock change
 * that moves a product out of the low-stock list, a deactivation that takes a product
 * off the storefront. Each case asserts what is written and what is re-read.
 */
describe("catalog and inventory cache invalidation", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("writes the created product into the detail cache and re-reads the listing", async () => {
    const queryClient = createTestQueryClient();
    const created = makeProduct({ id: 555, name: "Tulip Bunch" });
    mockCreate.mockResolvedValue(created);

    const { result } = renderHook(() => useCreateProduct(), { wrapper: wrapper(queryClient) });
    await result.current.mutateAsync({
      name: "Tulip Bunch",
      categoryId: 1,
      description: null,
      basePrice: 450,
      status: "DRAFT",
    });

    expect(mockCreate).toHaveBeenCalledWith({
      name: "Tulip Bunch",
      categoryId: 1,
      description: null,
      basePrice: 450,
      status: "DRAFT",
    });
    expect(queryClient.getQueryData(vendorKeys.products.detail(555))).toEqual(created);
  });

  it("stores the server's regenerated slug rather than patching the old one", async () => {
    const queryClient = createTestQueryClient();
    const renamed = makeProduct({ id: 101, name: "Renamed", slug: "renamed-2" });
    mockUpdateProduct.mockResolvedValue(renamed);

    const { result } = renderHook(() => useUpdateProduct(), { wrapper: wrapper(queryClient) });
    await result.current.mutateAsync({
      productId: 101,
      name: "Renamed",
      categoryId: 1,
      description: null,
      basePrice: 899,
      status: "DRAFT",
    });

    expect(queryClient.getQueryData<typeof renamed>(vendorKeys.products.detail(101))?.slug).toBe(
      "renamed-2",
    );
  });

  it("drops the deactivated product rather than leaving a cached ACTIVE row", async () => {
    const queryClient = createTestQueryClient();
    queryClient.setQueryData(
      vendorKeys.products.detail(101),
      makeProduct({ id: 101, status: "ACTIVE" }),
    );
    mockDeactivate.mockResolvedValue(undefined);

    const { result } = renderHook(() => useDeactivateProduct(), { wrapper: wrapper(queryClient) });
    await result.current.mutateAsync(101);

    expect(queryClient.getQueryData(vendorKeys.products.detail(101))).toBeUndefined();
  });

  it("replaces the whole image list from a cover change, because two rows changed", async () => {
    const queryClient = createTestQueryClient();
    const images = [makeProduct({ id: 101 }).images[0]!];
    mockSetPrimary.mockResolvedValue(images);

    const { result } = renderHook(() => useSetPrimaryProductImage(), { wrapper: wrapper(queryClient) });
    await result.current.mutateAsync({ productId: 101, imageId: 201 });

    expect(queryClient.getQueryData(vendorKeys.products.images(101))).toEqual(images);
  });

  it("writes the returned stock level into both the inventory cache and the product copy", async () => {
    const queryClient = createTestQueryClient();
    const summary = makeInventory({ productId: 101, quantity: 20, available: 20 });
    queryClient.setQueryData(
      vendorKeys.products.detail(101),
      makeProduct({ id: 101, inventory: makeInventory({ productId: 101, quantity: 12, available: 12 }) }),
    );
    mockStockIn.mockResolvedValue(summary);

    const { result } = renderHook(() => useStockAction(), { wrapper: wrapper(queryClient) });
    await result.current.mutateAsync({
      productId: 101,
      action: "stock-in",
      quantity: 8,
      reason: null,
    });

    expect(mockStockIn).toHaveBeenCalledWith(101, { quantity: 8, reason: null });
    expect(queryClient.getQueryData(vendorKeys.inventory.detail(101))).toEqual(summary);
    const cached = queryClient.getQueryData<ReturnType<typeof makeProduct>>(vendorKeys.products.detail(101));
    expect(cached?.inventory).toEqual(summary);
  });

  it("leaves the cached stock untouched when the write is refused", async () => {
    const queryClient = createTestQueryClient();
    const before = makeInventory({ productId: 101, quantity: 12, available: 12 });
    queryClient.setQueryData(vendorKeys.inventory.detail(101), before);
    mockStockIn.mockRejectedValue(new Error("insufficient"));

    const { result } = renderHook(() => useStockAction(), { wrapper: wrapper(queryClient) });

    await expect(
      result.current.mutateAsync({ productId: 101, action: "stock-in", quantity: 99, reason: null }),
    ).rejects.toThrow("insufficient");
    expect(queryClient.getQueryData(vendorKeys.inventory.detail(101))).toEqual(before);
  });

  it("clears the catalog and stock caches on logout, but keeps shared reference data", () => {
    const queryClient = createTestQueryClient();
    queryClient.setQueryData(vendorKeys.products.detail(101), makeProduct({ id: 101 }));
    queryClient.setQueryData(vendorKeys.inventory.detail(101), makeInventory({ productId: 101 }));
    queryClient.setQueryData(vendorKeys.inventory.lowStock(0), { content: [] });
    queryClient.setQueryData(categoryKeys.all, { content: [] });
    queryClient.setQueryData(vendorKeys.profile, makeVendorProfile());

    clearVendorCache(queryClient);

    expect(queryClient.getQueryData(vendorKeys.products.detail(101))).toBeUndefined();
    expect(queryClient.getQueryData(vendorKeys.inventory.detail(101))).toBeUndefined();
    expect(queryClient.getQueryData(vendorKeys.inventory.lowStock(0))).toBeUndefined();
    expect(queryClient.getQueryData(vendorKeys.profile)).toBeUndefined();
    // Categories are global reference data and cannot identify a vendor.
    expect(queryClient.getQueryData(categoryKeys.all)).toBeDefined();
  });
});
