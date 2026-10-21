import { describe, it, expect, vi, beforeEach } from "vitest";
import {
  fetchCategories,
  fetchDiscovery,
  fetchPublicProduct,
  fetchSearch,
  fetchStorefront,
} from "@/features/customer/api";
import { DEFAULT_SEARCH_FILTERS } from "@/features/customer/types";
import {
  makeDiscoveryVendor,
  makePage,
  makePublicProduct,
  makeStorefront,
} from "@/test/factories";

const mockApi = vi.hoisted(() => ({
  get: vi.fn(),
}));

vi.mock("@/shared/lib/api", () => ({ default: mockApi }));

/**
 * The customer API client.
 *
 * The contract that matters here is the *request*: which path, which query
 * parameters, and — just as important — which parameters are **absent** when
 * the customer has not narrowed anything. A client that sends `category: null`
 * or `q: ""` is asking the server to apply a filter the customer never chose.
 */
describe("customer api client", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("asks discovery for the florists near a location", async () => {
    const page = makePage([makeDiscoveryVendor()]);
    mockApi.get.mockResolvedValue({ data: page });

    const result = await fetchDiscovery(3, 0, 20);

    expect(mockApi.get).toHaveBeenCalledWith("/discover", {
      params: { locationId: 3, page: 0, size: 20 },
      signal: undefined,
    });
    expect(result).toEqual(page);
  });

  it("omits every filter the customer has not set", async () => {
    mockApi.get.mockResolvedValue({ data: makePage([]) });

    await fetchSearch(3, DEFAULT_SEARCH_FILTERS);

    const [, config] = mockApi.get.mock.calls[0];
    expect(mockApi.get).toHaveBeenCalledWith("/search", {
      params: { locationId: 3, sort: "distance", page: 0 },
      signal: undefined,
    });
    expect(config.params).not.toHaveProperty("q");
    expect(config.params).not.toHaveProperty("category");
    expect(config.params).not.toHaveProperty("priceMin");
    expect(config.params).not.toHaveProperty("priceMax");
    expect(config.params).not.toHaveProperty("vendorId");
  });

  it("sends every filter the customer has set", async () => {
    mockApi.get.mockResolvedValue({ data: makePage([]) });

    await fetchSearch(3, {
      q: "  roses  ",
      categoryId: 5,
      priceMin: 100,
      priceMax: 900,
      sort: "price_asc",
      vendorId: 7,
      page: 2,
    });

    expect(mockApi.get).toHaveBeenCalledWith("/search", {
      params: {
        locationId: 3,
        q: "roses",
        category: 5,
        priceMin: 100,
        priceMax: 900,
        sort: "price_asc",
        vendorId: 7,
        page: 2,
      },
      signal: undefined,
    });
  });

  it("reads a storefront without a location, because the endpoint takes none", async () => {
    const storefront = makeStorefront();
    mockApi.get.mockResolvedValue({ data: storefront });

    const result = await fetchStorefront(7, 0, 20);

    expect(mockApi.get).toHaveBeenCalledWith("/vendors/7/storefront", {
      params: { page: 0, size: 20 },
      signal: undefined,
    });
    expect(result).toEqual(storefront);
  });

  it("reads a product with the location that decides its deliverability", async () => {
    const product = makePublicProduct();
    mockApi.get.mockResolvedValue({ data: product });

    const result = await fetchPublicProduct(1001, 3);

    expect(mockApi.get).toHaveBeenCalledWith("/products/1001", {
      params: { locationId: 3 },
      signal: undefined,
    });
    expect(result).toEqual(product);
  });

  it("reads the public category list", async () => {
    mockApi.get.mockResolvedValue({ data: makePage([]) });

    await fetchCategories();

    expect(mockApi.get).toHaveBeenCalledWith("/categories", { signal: undefined });
  });
});
