import { describe, it, expect, vi, beforeEach } from "vitest";
import {
  adjustStock,
  deleteProductImage,
  fetchCategories,
  fetchInventory,
  fetchLowStockProducts,
  fetchProduct,
  fetchProductImages,
  fetchProducts,
  fetchStockMovements,
  reorderProductImages,
  setPrimaryProductImage,
  stockIn,
  stockOut,
  updateExpiryDate,
  updateLowStockThreshold,
  writeOffStock,
  createProduct,
  deactivateProduct,
  updateProduct,
  uploadProductImage,
  fetchOwnProfile,
  updateOwnProfile,
} from "@/features/vendor/api";
import { buildProfileUpdateRequest, type VendorHours } from "@/features/vendor/types";
import {
  makeCategory,
  makeInventory,
  makeLowStockProduct,
  makePage,
  makeProduct,
  makeProductImage,
  makeStockMovement,
  makeVendorProfile,
} from "@/test/factories";

const mockApi = vi.hoisted(() => ({
  get: vi.fn(),
  put: vi.fn(),
  post: vi.fn(),
  patch: vi.fn(),
  delete: vi.fn(),
}));

vi.mock("@/shared/lib/api", () => ({ default: mockApi }));

describe("vendor api client", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("reads the vendor's own profile from the profile endpoint", async () => {
    const profile = makeVendorProfile();
    mockApi.get.mockResolvedValue({ data: profile });

    const result = await fetchOwnProfile();

    expect(mockApi.get).toHaveBeenCalledWith("/vendors/profile");
    expect(result).toEqual(profile);
  });

  it("sends the full update body to PUT /vendors/profile", async () => {
    mockApi.put.mockResolvedValue({ data: makeVendorProfile() });

    await updateOwnProfile({
      businessName: "Petal & Stem",
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
    });

    expect(mockApi.put).toHaveBeenCalledWith("/vendors/profile", expect.objectContaining({ businessName: "Petal & Stem" }));
  });
});

describe("catalog api client", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("reads the shared category reference list", async () => {
    mockApi.get.mockResolvedValue({ data: makePage([makeCategory()]) });

    await fetchCategories();

    expect(mockApi.get).toHaveBeenCalledWith("/categories");
  });

  it("omits absent filters rather than sending them as null", async () => {
    mockApi.get.mockResolvedValue({ data: makePage([]) });

    await fetchProducts({ status: null, categoryId: null, name: "", page: 0 });

    expect(mockApi.get).toHaveBeenCalledWith("/vendors/products", {
      params: { page: 0 },
      signal: undefined,
    });
  });

  it("drops a blank search term, because the backend reads it as no filter", async () => {
    mockApi.get.mockResolvedValue({ data: makePage([]) });

    await fetchProducts({ status: "ACTIVE", categoryId: 3, name: "   ", page: 2 });

    expect(mockApi.get).toHaveBeenCalledWith("/vendors/products", {
      params: { page: 2, status: "ACTIVE", categoryId: 3 },
      signal: undefined,
    });
  });

  it("sends a trimmed search term", async () => {
    mockApi.get.mockResolvedValue({ data: makePage([]) });

    await fetchProducts({ status: null, categoryId: null, name: "  tulip ", page: 0 });

    expect(mockApi.get).toHaveBeenCalledWith("/vendors/products", {
      params: { page: 0, name: "tulip" },
      signal: undefined,
    });
  });

  it("forwards an abort signal so a superseded page does not land late", async () => {
    mockApi.get.mockResolvedValue({ data: makePage([]) });
    const controller = new AbortController();

    await fetchProducts({ status: null, categoryId: null, name: "", page: 0 }, controller.signal);

    expect(mockApi.get).toHaveBeenCalledWith("/vendors/products", {
      params: { page: 0 },
      signal: controller.signal,
    });
  });

  it("reads, creates and updates one product by id", async () => {
    const product = makeProduct({ id: 101 });
    mockApi.get.mockResolvedValue({ data: product });
    mockApi.post.mockResolvedValue({ data: product });
    mockApi.put.mockResolvedValue({ data: product });

    await fetchProduct(101);
    await createProduct({
      name: product.name,
      categoryId: product.categoryId,
      description: null,
      basePrice: product.basePrice,
      status: null,
    });
    await updateProduct(101, {
      name: product.name,
      categoryId: product.categoryId,
      description: null,
      basePrice: product.basePrice,
      status: null,
    });

    expect(mockApi.get).toHaveBeenCalledWith("/vendors/products/101");
    expect(mockApi.post).toHaveBeenCalledWith(
      "/vendors/products",
      expect.objectContaining({ name: "Red Rose Bouquet" }),
    );
    expect(mockApi.put).toHaveBeenCalledWith(
      "/vendors/products/101",
      expect.objectContaining({ name: "Red Rose Bouquet" }),
    );
  });

  it("deactivates through PATCH, never through a delete", async () => {
    mockApi.patch.mockResolvedValue({ data: undefined });

    await deactivateProduct(101);

    expect(mockApi.patch).toHaveBeenCalledWith("/vendors/products/101/deactivate");
    expect(mockApi.delete).not.toHaveBeenCalled();
  });
});

describe("image api client", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("uploads as multipart, overriding the instance default of application/json", async () => {
    mockApi.post.mockResolvedValue({ data: makeProductImage() });
    const file = new File(["bytes"], "bouquet.jpg", { type: "image/jpeg" });

    await uploadProductImage(101, file);

    const [url, body, config] = mockApi.post.mock.calls[0]!;
    expect(url).toBe("/vendors/products/101/images");
    expect(body).toBeInstanceOf(FormData);
    // Without this override the shared interceptor JSON-stringifies the FormData.
    expect(config).toEqual({ headers: { "Content-Type": "multipart/form-data" } });
  });

  it("sends the cover flag as a form field rather than a query parameter", async () => {
    mockApi.post.mockResolvedValue({ data: makeProductImage() });
    const file = new File(["bytes"], "bouquet.jpg", { type: "image/jpeg" });

    await uploadProductImage(101, file, true);

    const body = mockApi.post.mock.calls[0]![1] as FormData;
    expect(body.get("primary")).toBe("true");
    expect(body.get("file")).toBe(file);
  });

  it("reads the ordered list and replaces it wholesale on a cover change", async () => {
    const images = [makeProductImage({ id: 1, primary: true }), makeProductImage({ id: 2 })];
    mockApi.get.mockResolvedValue({ data: images });
    mockApi.put.mockResolvedValue({ data: images });

    await fetchProductImages(101);
    await setPrimaryProductImage(101, 2);

    expect(mockApi.get).toHaveBeenCalledWith("/vendors/products/101/images");
    expect(mockApi.put).toHaveBeenCalledWith("/vendors/products/101/images/2/primary");
  });

  it("sends the whole ordered id list for a reorder", async () => {
    mockApi.put.mockResolvedValue({ data: [] });

    await reorderProductImages(101, [3, 1, 2]);

    expect(mockApi.put).toHaveBeenCalledWith("/vendors/products/101/images/order", {
      imageIds: [3, 1, 2],
    });
  });

  it("deletes one image by id", async () => {
    mockApi.delete.mockResolvedValue({ data: undefined });

    await deleteProductImage(101, 7);

    expect(mockApi.delete).toHaveBeenCalledWith("/vendors/products/101/images/7");
  });
});

describe("inventory api client", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("reads one product's stock", async () => {
    mockApi.get.mockResolvedValue({ data: makeInventory() });

    await fetchInventory(101);

    expect(mockApi.get).toHaveBeenCalledWith("/vendors/products/101/inventory");
  });

  it("pages the shop-wide low-stock list explicitly", async () => {
    mockApi.get.mockResolvedValue({ data: makePage([makeLowStockProduct()]) });

    await fetchLowStockProducts(2, 20);

    expect(mockApi.get).toHaveBeenCalledWith("/vendors/inventory/low-stock", {
      params: { page: 2, size: 20 },
      signal: undefined,
    });
  });

  it("pages the movement history with the page size it was given", async () => {
    mockApi.get.mockResolvedValue({ data: makePage([makeStockMovement()]) });

    await fetchStockMovements(101, 1, 20);

    expect(mockApi.get).toHaveBeenCalledWith("/vendors/products/101/inventory/movements", {
      params: { page: 1, size: 20 },
      signal: undefined,
    });
  });

  it("uses one route per stock action", async () => {
    mockApi.post.mockResolvedValue({ data: makeInventory() });

    await stockIn(101, { quantity: 5, reason: null });
    await stockOut(101, { quantity: 2, reason: "Damaged" });
    await adjustStock(101, { quantity: -2, reason: "Miscount" });
    await writeOffStock(101, { quantity: 3, reason: "Spoiled" });

    expect(mockApi.post.mock.calls.map((call) => call[0])).toEqual([
      "/vendors/products/101/inventory/stock-in",
      "/vendors/products/101/inventory/stock-out",
      "/vendors/products/101/inventory/adjustments",
      "/vendors/products/101/inventory/write-offs",
    ]);
  });

  it("carries the reason through the write-off body", async () => {
    mockApi.post.mockResolvedValue({ data: makeInventory() });

    await writeOffStock(101, { quantity: 3, reason: "Spoiled overnight" });

    expect(mockApi.post).toHaveBeenCalledWith(
      "/vendors/products/101/inventory/write-offs",
      { quantity: 3, reason: "Spoiled overnight" },
    );
  });

  it("writes the two alert settings through their own PUT routes", async () => {
    mockApi.put.mockResolvedValue({ data: makeInventory() });

    await updateLowStockThreshold(101, { lowStockThreshold: 6 });
    await updateExpiryDate(101, { expiryDate: null });

    expect(mockApi.put).toHaveBeenCalledWith(
      "/vendors/products/101/inventory/low-stock-threshold",
      { lowStockThreshold: 6 },
    );
    // `null`, not "": clearing the date is an explicit body, not an omission.
    expect(mockApi.put).toHaveBeenCalledWith(
      "/vendors/products/101/inventory/expiry-date",
      { expiryDate: null },
    );
  });
});

describe("buildProfileUpdateRequest", () => {
  const profile = makeVendorProfile();

  it("resends every editable field so PUT semantics cannot reset one to its default", () => {
    const request = buildProfileUpdateRequest(profile, { businessName: "Renamed" });

    expect(request.businessName).toBe("Renamed");
    expect(request).toEqual({
      businessName: "Renamed",
      description: "Seasonal bouquets",
      addressLine1: "12 MG Road",
      addressLine2: "Unit 3",
      serviceLocationId: 3,
      deliveryRadiusKm: 5,
      logoUrl: null,
      minOrderAmount: 300,
      baseDeliveryFee: 25,
      perKmFee: 5,
      freeDeliveryAbove: 1000,
      prepTimeMinutes: 30,
      slotDurationMinutes: 60,
      maxOrdersPerSlot: 10,
      acceptingOrders: true,
    });
  });

  it("omits hours entirely when the caller does not own the sub-resource", () => {
    const request = buildProfileUpdateRequest(profile, { businessName: "Renamed" });

    expect("hours" in request).toBe(false);
  });

  it("includes an explicit null hours list when the caller passes null", () => {
    const request = buildProfileUpdateRequest(profile, {}, null);

    expect(request.hours).toBeNull();
  });

  it("includes the supplied week when the caller owns hours", () => {
    const hours: VendorHours[] = [{ weekday: "MONDAY", openTime: "08:00:00", closeTime: "20:00:00", closed: false }];

    expect(buildProfileUpdateRequest(profile, {}, hours).hours).toEqual(hours);
  });

  it("never leaks platform-owned fields into the payload", () => {
    const request = buildProfileUpdateRequest(profile, {});

    expect(request).not.toHaveProperty("status");
    expect(request).not.toHaveProperty("avgRating");
    expect(request).not.toHaveProperty("commissionRate");
    expect(request).not.toHaveProperty("latitude");
    expect(request).not.toHaveProperty("longitude");
    expect(request).not.toHaveProperty("ownerEmail");
  });
});
