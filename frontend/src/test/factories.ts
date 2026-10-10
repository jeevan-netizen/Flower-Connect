import type {
  Category,
  InventorySummary,
  LowStockProduct,
  MovementType,
  Product,
  ProductImage,
  ProductStatus,
  StockMovement,
  VendorHours,
  VendorProfile,
  VendorStatus,
} from "@/features/vendor/types";
import type { ServiceLocationGroup } from "@/features/location/types";
import type { AdminRole, AdminUser, UserStatus } from "@/features/admin/types";
import type { PageResponse } from "@/shared/types";

/**
 * A profile fixture shaped exactly like `VendorProfileResponse`, so tests exercise
 * the real field names the backend sends rather than a convenient subset.
 */
export function makeVendorProfile(overrides: Partial<VendorProfile> = {}): VendorProfile {
  return {
    id: 7,
    ownerEmail: "petal@example.com",
    businessName: "Petal & Stem",
    description: "Seasonal bouquets",
    addressLine1: "12 MG Road",
    addressLine2: "Unit 3",
    serviceLocationId: 3,
    city: "Bengaluru",
    area: "Indiranagar",
    pincode: "560038",
    latitude: 12.971199,
    longitude: 77.640586,
    deliveryRadiusKm: 5,
    logoUrl: null,
    status: "APPROVED" as VendorStatus,
    minOrderAmount: 300,
    baseDeliveryFee: 25,
    perKmFee: 5,
    freeDeliveryAbove: 1000,
    prepTimeMinutes: 30,
    slotDurationMinutes: 60,
    maxOrdersPerSlot: 10,
    acceptingOrders: true,
    avgRating: null,
    reviewCount: 0,
    hours: [
      { weekday: "MONDAY", openTime: "09:00:00", closeTime: "18:00:00", closed: false },
      { weekday: "SUNDAY", openTime: "10:00:00", closeTime: "14:00:00", closed: false },
    ],
    createdAt: "2026-09-01T10:00:00",
    updatedAt: "2026-09-20T08:30:00",
    ...overrides,
  };
}

export function makeVendorHours(overrides: Partial<VendorHours> = {}): VendorHours {
  return {
    weekday: "MONDAY",
    openTime: "09:00:00",
    closeTime: "18:00:00",
    closed: false,
    ...overrides,
  };
}

/**
 * A user fixture shaped exactly like `AdminUserResponse`: id, email, fullName,
 * phone, role, status, createdAt — and deliberately no credential material,
 * because the backend sends none.
 */
export function makeAdminUser(overrides: Partial<AdminUser> = {}): AdminUser {
  return {
    id: 42,
    email: "buyer@example.com",
    fullName: "Bea Buyer",
    phone: "+919876543210",
    role: "CUSTOMER" as AdminRole,
    status: "ACTIVE" as UserStatus,
    createdAt: "2026-09-02T09:15:00",
    ...overrides,
  };
}

/**
 * A page fixture shaped exactly like `VendorProfilePageResponse` /
 * `AdminUserPageResponse`, which are the same shape. Defaults are derived from
 * `content` so `first`/`last`/`totalPages` stay consistent unless a test
 * deliberately overrides them.
 */
/**
 * The city-grouped payload of `GET /api/v1/locations`, exactly as the backend sends
 * it — including `id`, which is what `POST /api/v1/vendors/register` requires as
 * `serviceLocationId`.
 */
export function makeServiceLocations(
  overrides: Partial<ServiceLocationGroup> = {},
): ServiceLocationGroup[] {
  return [
    {
      city: "Bengaluru",
      areas: [
        {
          id: 3,
          area: "Indiranagar",
          pincode: "560038",
          latitude: 12.971199,
          longitude: 77.640586,
        },
        {
          id: 4,
          area: "Koramangala",
          pincode: "560034",
          latitude: 12.9352,
          longitude: 77.6245,
        },
      ],
      ...overrides,
    },
  ];
}

export function makePage<T>(
  content: T[],
  overrides: Partial<PageResponse<T>> = {},
): PageResponse<T> {
  const totalElements = overrides.totalElements ?? content.length;
  const totalPages = overrides.totalPages ?? (totalElements === 0 ? 0 : 1);

  return {
    content,
    page: 0,
    size: 20,
    totalElements,
    totalPages,
    first: true,
    last: true,
    empty: content.length === 0,
    ...overrides,
  };
}

/* -------------------------------------------------------------------------- */
/* Catalog and inventory fixtures (plan task 3.9)                               */
/*                                                                             */
/* Shaped exactly like the backend DTOs — including the fields the UI does not   */
/* render (`storageKey`, `referenceId`, `vendorId`) — so a test cannot pass      */
/* against a response shape the server never sends.                             */
/* -------------------------------------------------------------------------- */

/** One seeded catalog category, as `GET /api/v1/categories` returns it. */
export function makeCategory(overrides: Partial<Category> = {}): Category {
  return {
    id: 1,
    parentId: null,
    parentName: null,
    name: "Roses",
    slug: "roses",
    displayOrder: 1,
    active: true,
    createdAt: "2026-09-01T10:00:00",
    updatedAt: "2026-09-01T10:00:00",
    ...overrides,
  };
}

export function makeInventory(overrides: Partial<InventorySummary> = {}): InventorySummary {
  return {
    productId: 101,
    quantity: 12,
    reservedQuantity: 0,
    available: 12,
    lowStockThreshold: 4,
    expiryDate: null,
    lowStock: false,
    ...overrides,
  };
}

export function makeProductImage(overrides: Partial<ProductImage> = {}): ProductImage {
  return {
    id: 201,
    storageKey: "product-images/101/2f1c8a90-1f2b-4c3d-9e8a-77b1c0d4e5f6.jpg",
    originalFilename: "bouquet.jpg",
    mimeType: "image/jpeg",
    fileSize: 84_231,
    sortOrder: 0,
    primary: true,
    createdAt: "2026-09-05T09:00:00",
    ...overrides,
  };
}

export function makeProduct(overrides: Partial<Product> = {}): Product {
  const { inventory, images, ...rest } = {
    id: 101,
    vendorId: 7,
    categoryId: 1,
    categoryName: "Roses",
    name: "Red Rose Bouquet",
    slug: "red-rose-bouquet",
    description: "A dozen deep red roses.",
    basePrice: 899,
    status: "DRAFT" as ProductStatus,
    inventory: makeInventory({ productId: 101 }),
    images: [makeProductImage()],
    createdAt: "2026-09-05T09:00:00",
    updatedAt: "2026-09-05T09:00:00",
    ...overrides,
  };

  return { ...rest, inventory, images };
}

export function makeStockMovement(overrides: Partial<StockMovement> = {}): StockMovement {
  return {
    id: 301,
    productId: 101,
    movementType: "STOCK_IN" as MovementType,
    quantityDelta: 12,
    reason: "Weekly delivery",
    referenceId: null,
    referenceType: null,
    actorUserId: 7,
    actorEmail: "petal@example.com",
    createdAt: "2026-09-05T09:05:00",
    ...overrides,
  };
}

export function makeLowStockProduct(overrides: Partial<LowStockProduct> = {}): LowStockProduct {
  return {
    productId: 101,
    productName: "Red Rose Bouquet",
    quantity: 3,
    reservedQuantity: 0,
    available: 3,
    lowStockThreshold: 4,
    expiryDate: null,
    lowStock: true,
    ...overrides,
  };
}
