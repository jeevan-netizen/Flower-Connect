/**
 * Vendor (florist) frontend types — plan tasks 2.9 and 3.9.
 *
 * Phase 2 mirrors these backend contracts exactly:
 *   `VendorProfileResponse` / `VendorProfileUpdateRequest` / `VendorHoursResponse`
 *   `VendorHoursRequest`
 * Phase 3 adds the catalog, image and inventory contracts:
 *   `ProductResponse` / `ProductPageResponse` / `ProductRequest`
 *   `ProductImageResponse` / `ProductImageOrderRequest`
 *   `InventorySummary` / `StockMovementResponse` / `LowStockProductResponse`
 *
 * Four deliberate constraints:
 *
 * 1. `VendorProfileEditableFields` is the union of fields `VendorProfileUpdateRequest`
 *    accepts. Everything outside it (status, commission-owned values, rating,
 *    coordinates, timestamps) is platform-controlled and is typed as read-only on
 *    `VendorProfile`, so a vendor form cannot construct a request for it even by
 *    accident.
 * 2. `hours` is a sub-resource on the backend: an absent list leaves the stored
 *    week untouched, a present list replaces the whole week. It therefore lives
 *    on the update request but *not* on the editable-fields contract.
 * 3. `ProductRequest` has **no `slug`**. The slug is generated server-side from
 *    the name with a collision-safe suffix (D-19), so there is nothing for the
 *    client to send; `Product.slug` is read-only.
 * 4. Every status and movement type is a closed union taken from the backend
 *    enum (native MySQL `ENUM`, D-20), so a request can never carry a value the
 *    column would truncate.
 */

import type { PageResponse } from "@/shared/types";

export const VENDOR_STATUSES = [
  "PENDING_APPROVAL",
  "APPROVED",
  "REJECTED",
  "SUSPENDED",
] as const;

export type VendorStatus = (typeof VENDOR_STATUSES)[number];

export const WEEKDAYS = [
  "MONDAY",
  "TUESDAY",
  "WEDNESDAY",
  "THURSDAY",
  "FRIDAY",
  "SATURDAY",
  "SUNDAY",
] as const;

export type Weekday = (typeof WEEKDAYS)[number];

export const WEEKDAY_LABELS: Record<Weekday, string> = {
  MONDAY: "Monday",
  TUESDAY: "Tuesday",
  WEDNESDAY: "Wednesday",
  THURSDAY: "Thursday",
  FRIDAY: "Friday",
  SATURDAY: "Saturday",
  SUNDAY: "Sunday",
};

/** One day of the operating week. Times are `LocalTime` on the wire (`HH:mm:ss`). */
export interface VendorHours {
  weekday: Weekday;
  openTime: string | null;
  closeTime: string | null;
  closed: boolean;
}

/**
 * Exactly the fields `PUT /api/v1/vendors/profile` accepts. `undefined` means
 * "not sent"; the backend then falls back to the schema default, so callers
 * build the payload from the current profile rather than from a sparse form.
 */
export interface VendorProfileEditableFields {
  businessName: string;
  description: string | null;
  addressLine1: string;
  addressLine2: string | null;
  serviceLocationId: number;
  deliveryRadiusKm: number;
  logoUrl: string | null;
  minOrderAmount: number;
  baseDeliveryFee: number;
  perKmFee: number;
  freeDeliveryAbove: number | null;
  prepTimeMinutes: number;
  slotDurationMinutes: number;
  maxOrdersPerSlot: number;
  acceptingOrders: boolean;
}

export interface VendorProfile extends VendorProfileEditableFields {
  id: number;
  ownerEmail: string;
  city: string;
  area: string;
  pincode: string;
  latitude: number;
  longitude: number;
  status: VendorStatus;
  avgRating: number | null;
  reviewCount: number;
  hours: VendorHours[];
  createdAt: string;
  updatedAt: string;
}

export interface VendorProfileUpdateRequest extends VendorProfileEditableFields {
  /** `null`/omitted leaves the stored week untouched; a list replaces the week. */
  hours?: VendorHours[] | null;
}

/**
 * Builds a complete PUT body from the profile currently on screen plus the
 * fields a page owns. Every editable field is resent on purpose: the backend
 * applies PUT semantics, so an omitted scalar silently resets to its default.
 * `hours` is only included when the caller explicitly owns it.
 */
export function buildProfileUpdateRequest(
  profile: VendorProfile,
  changes: Partial<VendorProfileEditableFields>,
  hours?: VendorHours[] | null,
): VendorProfileUpdateRequest {
  const merged: VendorProfileEditableFields = { ...profile, ...changes };

  const request: VendorProfileUpdateRequest = {
    businessName: merged.businessName,
    description: merged.description,
    addressLine1: merged.addressLine1,
    addressLine2: merged.addressLine2,
    serviceLocationId: merged.serviceLocationId,
    deliveryRadiusKm: merged.deliveryRadiusKm,
    logoUrl: merged.logoUrl,
    minOrderAmount: merged.minOrderAmount,
    baseDeliveryFee: merged.baseDeliveryFee,
    perKmFee: merged.perKmFee,
    freeDeliveryAbove: merged.freeDeliveryAbove,
    prepTimeMinutes: merged.prepTimeMinutes,
    slotDurationMinutes: merged.slotDurationMinutes,
    maxOrdersPerSlot: merged.maxOrdersPerSlot,
    acceptingOrders: merged.acceptingOrders,
  };

  if (hours !== undefined) {
    request.hours = hours;
  }

  return request;
}

/* -------------------------------------------------------------------------- */
/* Catalog (plan tasks 3.1, 3.5, 3.8)                                           */
/* -------------------------------------------------------------------------- */

/** Mirrors `Product.ProductStatus`, a native MySQL `ENUM` column (D-20). */
export const PRODUCT_STATUSES = ["DRAFT", "ACTIVE", "INACTIVE", "ARCHIVED"] as const;
export type ProductStatus = (typeof PRODUCT_STATUSES)[number];

/** Mirrors `CategoryResponse` from `GET /api/v1/categories`. */
export interface Category {
  id: number;
  parentId: number | null;
  parentName: string | null;
  name: string;
  slug: string;
  displayOrder: number;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

/**
 * Mirrors `InventorySummary`.
 *
 * `available` is the backend's own `quantity − reservedQuantity` and is read
 * from the response rather than recomputed here: the row also carries `lowStock`,
 * which is that same comparison against the vendor's threshold, and recomputing
 * either in the browser would give a second answer to a question the backend
 * already answered.
 */
export interface InventorySummary {
  productId: number;
  quantity: number;
  reservedQuantity: number;
  available: number;
  lowStockThreshold: number;
  /** `LocalDate` (`yyyy-MM-dd`) or `null`. Last day the stock may be used. */
  expiryDate: string | null;
  lowStock: boolean;
}

/**
 * Mirrors `ProductImageResponse`.
 *
 * `storageKey` is the opaque backend key. The catalog API deliberately does not
 * serve the bytes (see `docs/known-issues.md`), so this client never builds a URL
 * from it — that is why it is typed but unused beyond display.
 */
export interface ProductImage {
  id: number;
  storageKey: string;
  originalFilename: string | null;
  mimeType: string;
  fileSize: number;
  sortOrder: number;
  primary: boolean;
  createdAt: string;
}

/** Mirrors `ProductResponse`. */
export interface Product {
  id: number;
  vendorId: number;
  categoryId: number;
  categoryName: string | null;
  name: string;
  /** Server-generated; never sent by a client request. */
  slug: string;
  description: string | null;
  basePrice: number;
  status: ProductStatus;
  /** `null` only for a product read before its inventory row was created. */
  inventory: InventorySummary | null;
  images: ProductImage[];
  createdAt: string;
  updatedAt: string;
}

/**
 * Body of `POST /api/v1/vendors/products` and `PUT /api/v1/vendors/products/{id}`.
 *
 * `status` is optional on both verbs but means different things: on create it
 * defaults to `DRAFT` server-side, and on update `null` leaves the stored status
 * untouched. The forms always send a value, because both screens own the status.
 */
export interface ProductRequest {
  name: string;
  categoryId: number;
  description: string | null;
  basePrice: number;
  status: ProductStatus | null;
}

export type ProductPageResponse = PageResponse<Product>;
export type CategoryPageResponse = PageResponse<Category>;

/** The query parameters `GET /api/v1/vendors/products` accepts. */
export interface ProductListFilters {
  /** `null` = no status filter. */
  status: ProductStatus | null;
  /** `null` = every category. */
  categoryId: number | null;
  /** Case-insensitive partial match; `""` narrows nothing. */
  name: string;
  page: number;
}

/** `PUT /api/v1/vendors/products/{productId}/images/order`. */
export interface ProductImageOrderRequest {
  /** Must be an exact permutation of the product's own image ids. */
  imageIds: number[];
}

/**
 * Reads a `?status=` search parameter as a product-status filter.
 *
 * The listing binds `status` to the `Product.ProductStatus` enum, so an
 * unrecognised deep link would come back as a 400 from the server. Dropping the
 * value shows the unfiltered list instead, which is what a mistyped URL deserves.
 */
export function parseProductStatusParam(value: string | null): ProductStatus | null {
  return PRODUCT_STATUSES.find((status) => status === value) ?? null;
}

/** The `VendorProductController` page-size bounds. */
export const PRODUCT_PAGE_SIZE = 20;
export const PRODUCT_MIN_PAGE_SIZE = 1;
export const PRODUCT_MAX_PAGE_SIZE = 100;

/**
 * `app.image-upload.max-images-per-product` in `application.yml`. Shown as the
 * remaining budget in the media section; the server enforces the same number
 * under the row lock (D-28), so this is a courtesy, not the guard.
 */
export const MAX_IMAGES_PER_PRODUCT = 8;

/* -------------------------------------------------------------------------- */
/* Inventory (plan tasks 3.6, 3.7)                                             */
/* -------------------------------------------------------------------------- */

/** Mirrors `StockMovement.MovementType`. */
export const MOVEMENT_TYPES = [
  "STOCK_IN",
  "STOCK_OUT",
  "ADJUSTMENT",
  "RESERVE",
  "RELEASE",
  "SALE_ONLINE",
  "SALE_POS",
  "RESTOCK",
  "WASTE",
] as const;
export type MovementType = (typeof MOVEMENT_TYPES)[number];

/** Mirrors `StockMovementResponse`. */
export interface StockMovement {
  id: number;
  productId: number;
  movementType: MovementType;
  /** Signed: negative for anything that removes units. */
  quantityDelta: number;
  reason: string | null;
  referenceId: number | null;
  referenceType: string | null;
  /** `null` for a scheduled sweep, which has no authenticated actor. */
  actorUserId: number | null;
  actorEmail: string | null;
  createdAt: string;
}

export type StockMovementPageResponse = PageResponse<StockMovement>;

/** Mirrors `LowStockProductResponse` — the shop-wide low-stock listing. */
export interface LowStockProduct {
  productId: number;
  productName: string;
  quantity: number;
  reservedQuantity: number;
  available: number;
  lowStockThreshold: number;
  expiryDate: string | null;
  lowStock: boolean;
}

export type LowStockPageResponse = PageResponse<LowStockProduct>;

/**
 * The four stock mutations of `VendorInventoryController`, as one discriminated
 * union.
 *
 * `stock-in` and `stock-out` take a **positive** quantity and an optional
 * reason; `adjustment` takes a **signed, non-zero** quantity and a mandatory
 * reason; `write-off` takes a positive quantity and a mandatory reason. The
 * shapes genuinely differ, so a union makes "write off with no reason" a compile
 * error rather than a 400 discovered in the browser.
 */
export type StockActionKind = "stock-in" | "stock-out" | "adjustment" | "write-off";

export interface StockInRequest {
  quantity: number;
  reason: string | null;
}

export interface StockOutRequest {
  quantity: number;
  reason: string | null;
}

export interface StockAdjustmentRequest {
  /** Signed and non-zero. */
  quantity: number;
  reason: string;
}

export interface StockWriteOffRequest {
  quantity: number;
  reason: string;
}

export type StockActionRequest =
  | StockInRequest
  | StockOutRequest
  | StockAdjustmentRequest
  | StockWriteOffRequest;

/** `PUT /api/v1/vendors/products/{productId}/inventory/low-stock-threshold`. */
export interface LowStockThresholdRequest {
  lowStockThreshold: number;
}

/** `PUT /api/v1/vendors/products/{productId}/inventory/expiry-date`. */
export interface ExpiryDateRequest {
  /** `null` clears the stored date. */
  expiryDate: string | null;
}

/** `VendorInventoryController` page-size bounds. */
export const INVENTORY_PAGE_SIZE = 20;
export const INVENTORY_MIN_PAGE_SIZE = 1;
export const INVENTORY_MAX_PAGE_SIZE = 100;

/**
 * The `@Size(max = 500)` bound shared by every `reason` field on
 * `StockInRequest`, `StockOutRequest`, `StockAdjustmentRequest` and
 * `StockWriteOffRequest`. One source so the four dialogs cannot drift apart.
 */
export const STOCK_REASON_MAX_LENGTH = 500;
