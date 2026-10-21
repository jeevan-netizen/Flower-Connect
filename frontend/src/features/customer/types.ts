import type { PageResponse } from "@/shared/types";
import type { Category } from "@/features/vendor/types";

/**
 * Customer discovery and catalogue types (plan tasks 4.3, 4.4, 4.5, 4.6, 4.8, 4.9).
 *
 * These mirror four backend contracts:
 *   `GET /api/v1/discover`                  -> {@link DiscoveryVendor} in a {@link PageResponse}
 *   `GET /api/v1/search`                   -> {@link PublicProduct} in a {@link PageResponse}
 *   `GET /api/v1/vendors/{id}/storefront`  -> {@link Storefront}
 *   `GET /api/v1/products/{id}`            -> {@link PublicProduct}
 *
 * The public product shape is deliberately narrower than the vendor area's
 * `ProductResponse`: it carries no `status`, no audit timestamps and — most
 * importantly — no image `storageKey`, which is an internal storage-backend key
 * that must not reach a customer (D-31, known issue 020).
 */

/**
 * One image of a public product.
 *
 * `originalFilename` is nullable because the backend column is nullable; the
 * image *bytes* are not served by any route, so the customer UI shows a
 * placeholder and only this metadata is ever displayed.
 */
export interface PublicProductImage {
  id: number;
  originalFilename: string | null;
  mimeType: string;
  fileSize: number;
  sortOrder: number;
  primary: boolean;
}

/** A product as a customer sees it. */
export interface PublicProduct {
  id: number;
  vendorId: number;
  vendorName: string;
  categoryId: number;
  categoryName: string;
  name: string;
  slug: string;
  description: string | null;
  basePrice: number;
  /** Available units (`quantity - reservedQuantity`); null when unknown. */
  available: number | null;
  images: PublicProductImage[];
}

/**
 * A florist as discovery returns them.
 *
 * `distanceKm` and `estimatedDeliveryFee` are computed server-side from the
 * selected service location; the browser never derives a distance.
 */
export interface DiscoveryVendor {
  id: number;
  businessName: string;
  description: string | null;
  city: string;
  area: string;
  pincode: string;
  deliveryRadiusKm: number;
  minOrderAmount: number;
  baseDeliveryFee: number;
  perKmFee: number;
  freeDeliveryAbove: number | null;
  prepTimeMinutes: number;
  avgRating: number | null;
  reviewCount: number;
  acceptingOrders: boolean;
  distanceKm: number;
  estimatedDeliveryFee: number;
}

/** One day of a vendor's opening week, as the storefront returns it. */
export interface StorefrontHour {
  weekday: string;
  openTime: string | null;
  closeTime: string | null;
  closed: boolean;
}

/**
 * A vendor's public storefront.
 *
 * `products` is a `PageResponse<PublicProduct>` — field for field the same
 * envelope as every other paginated read in the project, which is why the
 * shared `Pagination` component can drive it.
 */
export interface Storefront {
  id: number;
  businessName: string;
  description: string | null;
  city: string;
  area: string;
  pincode: string;
  deliveryRadiusKm: number;
  minOrderAmount: number;
  baseDeliveryFee: number;
  perKmFee: number;
  freeDeliveryAbove: number | null;
  prepTimeMinutes: number;
  avgRating: number | null;
  reviewCount: number;
  acceptingOrders: boolean;
  hours: StorefrontHour[];
  products: PageResponse<PublicProduct>;
}

/**
 * The sort keys `GET /api/v1/search` accepts.
 *
 * A closed list, mirroring the backend's whitelist: an unknown value is a 400
 * server-side, so the client never sends one.
 */
export const SORT_OPTIONS = ["distance", "price_asc", "price_desc", "name"] as const;

export type SortOption = (typeof SORT_OPTIONS)[number];

/** What each sort key is called in the dropdown. */
export const SORT_LABELS: Record<SortOption, string> = {
  distance: "Nearest first",
  price_asc: "Price: low to high",
  price_desc: "Price: high to low",
  name: "Name: A to Z",
};

/**
 * The search filter state, held by the page rather than in the URL.
 *
 * `locationId` is deliberately absent: the delivery location lives in the
 * session store (D-34) and is supplied by the query hook, so there is exactly
 * one copy of it and no URL parameter that could disagree with it.
 */
export interface SearchFilters {
  /** Free-text match on the product name; blank narrows nothing. */
  q: string;
  /** An exact category id; null means every category. */
  categoryId: number | null;
  priceMin: number | null;
  priceMax: number | null;
  sort: SortOption;
  /** Restrict to one florist; null means every florist. */
  vendorId: number | null;
  /** Zero-based, as on the wire. */
  page: number;
}

/** The filters a search starts from. */
export const DEFAULT_SEARCH_FILTERS: SearchFilters = {
  q: "",
  categoryId: null,
  priceMin: null,
  priceMax: null,
  sort: "distance",
  vendorId: null,
  page: 0,
};

/** The category reference list, as `GET /api/v1/categories` returns it. */
export type CustomerCategory = Category;
