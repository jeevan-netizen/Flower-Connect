import { describe, it, expect } from "vitest";
import {
  type DiscoveryResponse,
  type DiscoveryPageResponse,
  type VendorCardView,
  toVendorCardView,
} from "@/features/customer/types";

describe("customer types", () => {
  const mockDiscoveryResponse: DiscoveryResponse = {
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
  };

  it("DiscoveryResponse has all required fields", () => {
    expect(mockDiscoveryResponse.id).toBe(1);
    expect(mockDiscoveryResponse.businessName).toBe("Petal & Stem");
    expect(mockDiscoveryResponse.city).toBe("Bengaluru");
    expect(mockDiscoveryResponse.area).toBe("Indiranagar");
    expect(mockDiscoveryResponse.pincode).toBe("560038");
    expect(mockDiscoveryResponse.distanceKm).toBe(2.1);
    expect(mockDiscoveryResponse.estimatedDeliveryFee).toBe(35.5);
    expect(mockDiscoveryResponse.prepTimeMinutes).toBe(30);
    expect(mockDiscoveryResponse.minOrderAmount).toBe(300);
    expect(mockDiscoveryResponse.avgRating).toBe(4.5);
    expect(mockDiscoveryResponse.reviewCount).toBe(12);
    expect(mockDiscoveryResponse.acceptingOrders).toBe(true);
  });

  it("DiscoveryPageResponse uses the shared PageResponse envelope", () => {
    const pageResponse: DiscoveryPageResponse = {
      content: [mockDiscoveryResponse],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
      first: true,
      last: true,
      empty: false,
    };

    expect(pageResponse.content).toHaveLength(1);
    expect(pageResponse.page).toBe(0);
    expect(pageResponse.totalElements).toBe(1);
  });

  describe("toVendorCardView", () => {
    it("maps all fields correctly", () => {
      const view = toVendorCardView(mockDiscoveryResponse);

      expect(view.id).toBe(1);
      expect(view.businessName).toBe("Petal & Stem");
      expect(view.area).toBe("Indiranagar");
      expect(view.pincode).toBe("560038");
      expect(view.distanceKm).toBe(2.1);
      expect(view.estimatedDeliveryFee).toBe(35.5);
      expect(view.prepTimeMinutes).toBe(30);
      expect(view.minOrderAmount).toBe(300);
      expect(view.avgRating).toBe(4.5);
      expect(view.reviewCount).toBe(12);
      expect(view.acceptingOrders).toBe(true);
    });

    it("handles null avgRating", () => {
      const responseWithNullRating: DiscoveryResponse = {
        ...mockDiscoveryResponse,
        avgRating: null,
        reviewCount: 0,
      };

      const view = toVendorCardView(responseWithNullRating);

      expect(view.avgRating).toBeNull();
      expect(view.reviewCount).toBe(0);
    });

    it("defaults null optional numeric fields to 0", () => {
      const sparseResponse: DiscoveryResponse = {
        ...mockDiscoveryResponse,
        distanceKm: null,
        estimatedDeliveryFee: null,
        prepTimeMinutes: null,
        minOrderAmount: null,
      };

      const view = toVendorCardView(sparseResponse);

      expect(view.distanceKm).toBe(0);
      expect(view.estimatedDeliveryFee).toBe(0);
      expect(view.prepTimeMinutes).toBe(0);
      expect(view.minOrderAmount).toBe(0);
    });
  });

  describe("VendorCardView shape", () => {
    it("contains all UI-required fields", () => {
      const view: VendorCardView = {
        id: 1,
        businessName: "Test",
        area: "Area",
        pincode: "123456",
        distanceKm: 1.5,
        estimatedDeliveryFee: 25,
        prepTimeMinutes: 30,
        minOrderAmount: 200,
        avgRating: 4.0,
        reviewCount: 5,
        acceptingOrders: true,
      };

      expect(typeof view.id).toBe("number");
      expect(typeof view.businessName).toBe("string");
      expect(typeof view.area).toBe("string");
      expect(typeof view.pincode).toBe("string");
      expect(typeof view.distanceKm).toBe("number");
      expect(typeof view.estimatedDeliveryFee).toBe("number");
      expect(typeof view.prepTimeMinutes).toBe("number");
      expect(typeof view.minOrderAmount).toBe("number");
      expect(view.avgRating === null || typeof view.avgRating === "number").toBe(true);
      expect(typeof view.reviewCount).toBe("number");
      expect(typeof view.acceptingOrders).toBe("boolean");
    });
  });
});