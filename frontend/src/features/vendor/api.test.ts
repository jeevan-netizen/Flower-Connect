import { describe, it, expect, vi, beforeEach } from "vitest";
import { fetchOwnProfile, updateOwnProfile } from "@/features/vendor/api";
import { buildProfileUpdateRequest, type VendorHours } from "@/features/vendor/types";
import { makeVendorProfile } from "@/test/factories";

const mockApi = vi.hoisted(() => ({ get: vi.fn(), put: vi.fn(), post: vi.fn() }));

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
