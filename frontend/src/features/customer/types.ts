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

/**
 * Product search request parameters.
 *
 * `locationId` is required by the backend (400 VALIDATION_FAILED when absent).
 * All other parameters are optional.
 */
export interface SearchRequest {
  locationId: number;
  q?: string;
  category?: number;
  priceMin?: number;
  priceMax?: number;
  sort?: "distance" | "price_asc" | "price_desc" | "name";
  vendorId?: number;
  page?: number;
  size?: number;
}

/**
 * Product search response (mirrors backend `SearchResponse`).
 *
 * Contains the product fields needed for the storefront product grid,
 * plus the vendor name and the computed distance + estimated delivery fee
 * from the search origin. Only products that are ACTIVE, have available
 * stock, and belong to an APPROVED vendor accepting orders within their
 * delivery radius are ever returned.
 */
export interface SearchResponse {
  id: number;
  vendorId: number;
  vendorName: string;
  categoryId: number;
  categoryName: string;
  name: string;
  slug: string;
  description: string | null;
  basePrice: number | null;
  distanceKm: number | null;
  estimatedDeliveryFee: number | null;
}

/** Search page response alias over the shared envelope. */
export type SearchPageResponse = PageResponse<SearchResponse>;

/**
 * Compact product card view model derived from `SearchResponse`.
 *
 * This exists so the card component receives a stable, UI-oriented shape and
 * does not need to know about `null` handling for optional backend fields.
 */
export interface ProductCardView {
  id: number;
  vendorId: number;
  vendorName: string;
  categoryId: number;
  categoryName: string;
  name: string;
  slug: string;
  description: string;
  basePrice: number;
  distanceKm: number;
  estimatedDeliveryFee: number;
  avgRating: number | null;
  reviewCount: number;
}

/** Converts a backend `SearchResponse` into the card view model. */
export function toProductCardView(product: SearchResponse): ProductCardView {
  return {
    id: product.id,
    vendorId: product.vendorId,
    vendorName: product.vendorName,
    categoryId: product.categoryId,
    categoryName: product.categoryName,
    name: product.name,
    slug: product.slug,
    description: product.description ?? "",
    basePrice: product.basePrice ?? 0,
    distanceKm: product.distanceKm ?? 0,
    estimatedDeliveryFee: product.estimatedDeliveryFee ?? 0,
    avgRating: null, // SearchResponse doesn't include rating yet
    reviewCount: 0,  // SearchResponse doesn't include review count yet
  };
}

/**
 * Storefront request parameters.
 *
 * `vendorId` is required by the backend (400 VALIDATION_FAILED when absent or non-positive).
 * `page` and `size` are optional; backend defaults to page=0, size=20, max 100.
 */
export interface StorefrontRequest {
  vendorId: number;
  page?: number;
  size?: number;
}

/**
 * Vendor hours response (mirrors backend `VendorHoursResponse`).
 */
export interface VendorHoursResponse {
  weekday: "MONDAY" | "TUESDAY" | "WEDNESDAY" | "THURSDAY" | "FRIDAY" | "SATURDAY" | "SUNDAY";
  openTime: string | null;
  closeTime: string | null;
  closed: boolean;
}

/**
 * Public vendor profile on a storefront (mirrors backend `StorefrontVendorResponse`).
 *
 * Contains only the storefront-relevant fields. Deliberately omitted: owner's
 * email and every other user field, commission rate, approval status, exact
 * latitude/longitude, street address lines, and audit timestamps.
 */
export interface StorefrontVendorResponse {
  id: number;
  businessName: string;
  description: string | null;
  logoUrl: string | null;
  city: string;
  area: string;
  pincode: string;
  deliveryRadiusKm: number | null;
  minOrderAmount: number | null;
  baseDeliveryFee: number | null;
  perKmFee: number | null;
  freeDeliveryAbove: number | null;
  prepTimeMinutes: number | null;
  slotDurationMinutes: number | null;
  maxOrdersPerSlot: number | null;
  acceptingOrders: boolean | null;
  avgRating: number | null;
  reviewCount: number | null;
  hours: VendorHoursResponse[];
}

/**
 * Public product row on a vendor storefront (mirrors backend `StorefrontProductResponse`).
 *
 * Availability is a single `inStock` flag rather than the raw quantity/reserved
 * quantity pair. The flag is derived from the availability the Inventory entity
 * itself computes (quantity - reservedQuantity > 0).
 */
export interface StorefrontProductResponse {
  id: number;
  categoryId: number;
  categoryName: string;
  name: string;
  slug: string;
  description: string | null;
  basePrice: number | null;
  inStock: boolean;
}

/**
 * Storefront response (mirrors backend `StorefrontResponse`).
 *
 * Two parts: `vendor` is the shop's public profile (not paginated), and
 * `products` is the shop's ACTIVE catalogue using the standard PageResponse
 * envelope.
 */
export interface StorefrontResponse {
  vendor: StorefrontVendorResponse;
  products: PageResponse<StorefrontProductResponse>;
}

/**
 * Compact product card view model derived from `StorefrontProductResponse`.
 *
 * This exists so the card component receives a stable, UI-oriented shape and
 * does not need to know about `null` handling for optional backend fields.
 */
export interface StorefrontProductCardView {
  id: number;
  categoryId: number;
  categoryName: string;
  name: string;
  slug: string;
  description: string;
  basePrice: number;
  inStock: boolean;
}

/** Converts a backend `StorefrontProductResponse` into the card view model. */
export function toStorefrontProductCardView(product: StorefrontProductResponse): StorefrontProductCardView {
  return {
    id: product.id,
    categoryId: product.categoryId,
    categoryName: product.categoryName,
    name: product.name,
    slug: product.slug,
    description: product.description ?? "",
    basePrice: product.basePrice ?? 0,
    inStock: product.inStock,
  };
}