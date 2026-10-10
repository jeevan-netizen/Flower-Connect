import { describe, it, expect, vi, beforeEach } from "vitest";
import { fetchServiceLocations } from "@/features/location/api";
import { makeServiceLocations } from "@/test/factories";

const mockApi = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn() }));

vi.mock("@/shared/lib/api", () => ({ default: mockApi }));

describe("service location api client", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("reads the full service-area list from the unfiltered locations endpoint", async () => {
    const locations = makeServiceLocations();
    mockApi.get.mockResolvedValue({ data: locations });

    const result = await fetchServiceLocations();

    // No params: any `pincode`/`area`/`page`/`size` turns this into a paginated
    // search and a picker cannot enumerate the region.
    expect(mockApi.get).toHaveBeenCalledWith("/locations");
    expect(result).toEqual(locations);
  });

  it("adds no Authorization header of its own, because the endpoint is public", async () => {
    mockApi.get.mockResolvedValue({ data: [] });

    await fetchServiceLocations();

    // The shared axios instance's request interceptor attaches a bearer token
    // only when one exists in the auth store; the client itself must never
    // send credentials for public reference data, or the picker would fail for
    // a signed-out visitor — which is exactly who uses it.
    const config = mockApi.get.mock.calls[0]?.[1];
    expect(config?.headers?.Authorization).toBeUndefined();
  });
});
