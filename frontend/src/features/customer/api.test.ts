import { describe, it, expect, vi, beforeEach } from "vitest";
import { fetchDiscovery } from "@/features/customer/api";
import type { DiscoveryPageResponse } from "@/features/customer/types";

const mockGet = vi.hoisted(() => vi.fn());

vi.mock("@/shared/lib/api", () => ({
  default: {
    get: mockGet,
  },
}));

describe("customer discovery API", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("calls GET /discover with locationId, page, and size", async () => {
    const mockResponse: DiscoveryPageResponse = {
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
      first: true,
      last: true,
      empty: true,
    };
    mockGet.mockResolvedValue({ data: mockResponse });

    const result = await fetchDiscovery({ locationId: 3, page: 0, size: 20 });

    expect(mockGet).toHaveBeenCalledWith("/discover", {
      params: { locationId: 3, page: 0, size: 20 },
    });
    expect(result).toEqual(mockResponse);
  });

  it("omits page and size when not provided", async () => {
    const mockResponse: DiscoveryPageResponse = {
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
      first: true,
      last: true,
      empty: true,
    };
    mockGet.mockResolvedValue({ data: mockResponse });

    await fetchDiscovery({ locationId: 5 });

    expect(mockGet).toHaveBeenCalledWith("/discover", {
      params: { locationId: 5 },
    });
  });

  it("includes page=0 when explicitly provided", async () => {
    const mockResponse: DiscoveryPageResponse = {
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
      first: true,
      last: true,
      empty: true,
    };
    mockGet.mockResolvedValue({ data: mockResponse });

    await fetchDiscovery({ locationId: 1, page: 0 });

    expect(mockGet).toHaveBeenCalledWith("/discover", {
      params: { locationId: 1, page: 0 },
    });
  });
});