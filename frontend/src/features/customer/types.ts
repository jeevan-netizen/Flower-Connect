import type { PageResponse } from "@/shared/types";

/**
 * Vendor discovery response (mirrors backend `DiscoveryResponse`).
 *
 * Only storefront-relevant fields are included. Private vendor/user fields
 * (owner email, commission rate, status, audit fields) are intentionally
 * omitted. Distance and estimated fee are computed server-side from the
 * search origin.
 */
export interface DiscoveryResponse {
  id: number;
  businessName: string;
  description: string | null;

  city: string;
  area: string;
  pincode: string;

  deliveryRadiusKm: number | null;
  minOrderAmount: number | null;
  baseDeliveryFee: number | null;
  perKmFee: number | null;
  freeDeliveryAbove: number | null;
  prepTimeMinutes: number | null;

  avgRating: number | null;
  reviewCount: number;
  acceptingOrders: boolean;

  distanceKm: number | null;
  estimatedDeliveryFee: number | null;
}

/** Discovery page response alias over the shared envelope. */
export type DiscoveryPageResponse = PageResponse<DiscoveryResponse>;

/**
 * Compact vendor card view model derived from `DiscoveryResponse`.
 *
 * This exists so the card component receives a stable, UI-oriented shape and
 * does not need to know about `null` handling for optional backend fields.
 */
export interface VendorCardView {
  id: number;
  businessName: string;
  area: string;
  pincode: string;
  distanceKm: number;
  estimatedDeliveryFee: number;
  prepTimeMinutes: number;
  minOrderAmount: number;
  avgRating: number | null;
  reviewCount: number;
  acceptingOrders: boolean;
}

/** Converts a backend `DiscoveryResponse` into the card view model. */
export function toVendorCardView(vendor: DiscoveryResponse): VendorCardView {
  return {
    id: vendor.id,
    businessName: vendor.businessName,
    area: vendor.area,
    pincode: vendor.pincode,
    distanceKm: vendor.distanceKm ?? 0,
    estimatedDeliveryFee: vendor.estimatedDeliveryFee ?? 0,
    prepTimeMinutes: vendor.prepTimeMinutes ?? 0,
    minOrderAmount: vendor.minOrderAmount ?? 0,
    avgRating: vendor.avgRating,
    reviewCount: vendor.reviewCount,
    acceptingOrders: vendor.acceptingOrders,
  };
}