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
import type {
  VendorOrderResponse,
  VendorOrderPageResponse,
  VendorOrderSummaryResponse,
} from "@/features/vendor/orders/types";
import type { CartItem, CartResponse, CartShopView } from "@/features/cart/types";
import type {
  AddressResponse,
  AddressPageResponse,
  CheckoutQuoteResponse,
  CheckoutResponse,
  CheckoutShopQuote,
  CheckoutItemQuote,
  OrderSummary,
  OrderItemSummary,
  SlotResponse,
} from "@/features/checkout/types";
import type { ServiceLocationGroup } from "@/features/location/types";
import type { AdminRole, AdminUser, UserStatus } from "@/features/admin/types";
import type {
  DiscoveryVendor,
  PublicProduct,
  PublicProductImage,
  Storefront,
  StorefrontHour,
} from "@/features/customer/types";
import type {
  CustomerOrderItemResponse,
  CustomerOrderListItem,
  CustomerOrderPageResponse,
  CustomerOrderResponse,
  CustomerOrderTimelineEntry,
  OrderStatus,
  PaymentMethod,
  PaymentStatus,
} from "@/features/customer/orders/types";
import type {
  PaymentGroupOrderEntry,
  PaymentGroupStateResponse,
  PaymentResumeResponse,
  PaymentStateResponse,
} from "@/features/payment/types";
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

/* -------------------------------------------------------------------------- */
/* Cart fixtures (plan task 5.6)                                              */
/* -------------------------------------------------------------------------- */

export function makeCartItem(overrides: Partial<CartItem> = {}): CartItem {
  return {
    id: 1,
    productId: 101,
    productName: "Red Rose Bouquet",
    productSlug: "red-rose-bouquet",
    vendorId: 7,
    vendorName: "Petal & Stem",
    quantity: 2,
    priceSnapshot: 899,
    note: null,
    subtotal: 1798,
    currentPrice: 899,
    priceChanged: false,
    available: 12,
    purchasable: true,
    notPurchasableReason: null,
    createdAt: "2026-10-01T10:00:00",
    updatedAt: "2026-10-01T10:00:00",
    ...overrides,
  };
}

export function makeCartShopView(overrides: Partial<CartShopView> = {}): CartShopView {
  return {
    vendorId: 7,
    vendorName: "Petal & Stem",
    itemCount: 2,
    subtotal: 1798,
    purchasable: true,
    ...overrides,
  };
}

export function makeCartResponse(overrides: Partial<CartResponse> = {}): CartResponse {
  return {
    cartId: 1001,
    totalItems: 2,
    subtotal: 1798,
    shops: [makeCartShopView()],
    items: [makeCartItem()],
    ...overrides,
  };
}

/* -------------------------------------------------------------------------- */
/* Address fixtures (plan task 5.6)                                           */
/* -------------------------------------------------------------------------- */

export function makeAddressResponse(overrides: Partial<AddressResponse> = {}): AddressResponse {
  return {
    id: 42,
    label: "Home",
    line1: "12 MG Road",
    line2: "Unit 3",
    serviceLocationId: 3,
    city: "Bengaluru",
    area: "Indiranagar",
    pincode: "560038",
    latitude: 12.971199,
    longitude: 77.640586,
    defaultAddress: true,
    createdAt: "2026-09-01T10:00:00",
    updatedAt: "2026-09-20T08:30:00",
    ...overrides,
  };
}

export function makeAddressPageResponse(overrides: Partial<AddressPageResponse> = {}): AddressPageResponse {
  return {
    content: [makeAddressResponse()],
    page: 0,
    size: 20,
    totalElements: 1,
    totalPages: 1,
    first: true,
    last: true,
    empty: false,
    ...overrides,
  };
}

/* -------------------------------------------------------------------------- */
/* Checkout fixtures (plan task 5.6)                                          */
/* -------------------------------------------------------------------------- */

export function makeCheckoutItemQuote(overrides: Partial<CheckoutItemQuote> = {}): CheckoutItemQuote {
  return {
    productId: 101,
    productName: "Red Rose Bouquet",
    productSlug: "red-rose-bouquet",
    quantity: 2,
    priceSnapshot: 899,
    currentPrice: 899,
    priceChanged: false,
    note: null,
    subtotal: 1798,
    available: 12,
    purchasable: true,
    notPurchasableReason: null,
    ...overrides,
  };
}

export function makeCheckoutShopQuote(overrides: Partial<CheckoutShopQuote> = {}): CheckoutShopQuote {
  return {
    vendorId: 7,
    vendorName: "Petal & Stem",
    items: [makeCheckoutItemQuote()],
    subtotal: 1798,
    deliveryFee: 25,
    platformFee: 0,
    minOrderAmount: 300,
    total: 1823,
    distanceKm: 3.5,
    withinRadius: true,
    minOrderMet: true,
    slotAvailable: true,
    warnings: [],
    errors: [],
    ...overrides,
  };
}

export function makeCheckoutQuoteResponse(overrides: Partial<CheckoutQuoteResponse> = {}): CheckoutQuoteResponse {
  return {
    shops: [makeCheckoutShopQuote()],
    total: 1823,
    hasErrors: false,
    hasPriceChanges: false,
    quoteHash: "abc123def456",
    ...overrides,
  };
}

export function makeOrderItemSummary(overrides: Partial<OrderItemSummary> = {}): OrderItemSummary {
  return {
    productId: 101,
    productName: "Red Rose Bouquet",
    quantity: 2,
    priceSnapshot: 899,
    subtotal: 1798,
    ...overrides,
  };
}

export function makeOrderSummary(overrides: Partial<OrderSummary> = {}): OrderSummary {
  return {
    orderId: 5001,
    vendorId: 7,
    vendorName: "Petal & Stem",
    status: "PLACED",
    paymentStatus: "PENDING",
    paymentMethod: "COD",
    subtotal: 1798,
    deliveryFee: 25,
    platformFee: 0,
    total: 1823,
    slotStart: "2026-10-03T10:00:00",
    slotEnd: "2026-10-03T11:00:00",
    items: [makeOrderItemSummary()],
    ...overrides,
  };
}

export function makeCheckoutResponse(overrides: Partial<CheckoutResponse> = {}): CheckoutResponse {
  return {
    clientOrderId: "550e8400-e29b-41d4-a716-446655440000",
    idempotentReplay: false,
    orders: [makeOrderSummary()],
    total: 1823,
    paymentMethod: "COD",
    paymentGroupId: null,
    intentReference: null,
    intentExpiresAt: null,
    ...overrides,
  };
}

/* -------------------------------------------------------------------------- */
/* Slot fixtures (plan task 5.6)                                              */
/* -------------------------------------------------------------------------- */

export function makeSlotResponse(overrides: Partial<SlotResponse> = {}): SlotResponse {
  return {
    slotStart: "2026-10-03T10:00:00",
    slotEnd: "2026-10-03T11:00:00",
    weekday: "SATURDAY",
    available: true,
    ...overrides,
  };
}

/* -------------------------------------------------------------------------- */
/* Customer catalogue fixtures (plan tasks 4.6, 4.7, 4.8, 4.9)                */
/* -------------------------------------------------------------------------- */

/*
 * Shaped exactly like the public backend DTOs.
 *
 * The public product carries no `status`, no audit timestamps and — the point
 * of the whole exercise — no image `storageKey`, so a fixture that included
 * one would be shaped unlike anything the server sends.
 */
export function makePublicProductImage(
  overrides: Partial<PublicProductImage> = {},
): PublicProductImage {
  return {
    id: 501,
    originalFilename: "bouquet.jpg",
    mimeType: "image/jpeg",
    fileSize: 84_231,
    sortOrder: 0,
    primary: true,
    ...overrides,
  };
}

export function makePublicProduct(overrides: Partial<PublicProduct> = {}): PublicProduct {
  return {
    id: 1001,
    vendorId: 7,
    vendorName: "Petal & Stem",
    categoryId: 1,
    categoryName: "Roses",
    name: "Red Rose Bouquet",
    slug: "red-rose-bouquet",
    description: "A dozen deep red roses.",
    basePrice: 899,
    available: 12,
    images: [makePublicProductImage()],
    ...overrides,
  };
}

export function makeDiscoveryVendor(
  overrides: Partial<DiscoveryVendor> = {},
): DiscoveryVendor {
  return {
    id: 7,
    businessName: "Petal & Stem",
    description: "Seasonal bouquets",
    city: "Bengaluru",
    area: "Koramangala",
    pincode: "560034",
    deliveryRadiusKm: 5,
    minOrderAmount: 300,
    baseDeliveryFee: 25,
    perKmFee: 5,
    freeDeliveryAbove: 1000,
    prepTimeMinutes: 30,
    avgRating: 4.5,
    reviewCount: 12,
    acceptingOrders: true,
    distanceKm: 2.4,
    estimatedDeliveryFee: 37,
    ...overrides,
  };
}

export function makeStorefrontHour(overrides: Partial<StorefrontHour> = {}): StorefrontHour {
  return {
    weekday: "MONDAY",
    openTime: "09:00:00",
    closeTime: "18:00:00",
    closed: false,
    ...overrides,
  };
}

export function makeStorefront(overrides: Partial<Storefront> = {}): Storefront {
  return {
    id: 7,
    businessName: "Petal & Stem",
    description: "Seasonal bouquets",
    city: "Bengaluru",
    area: "Koramangala",
    pincode: "560034",
    deliveryRadiusKm: 5,
    minOrderAmount: 300,
    baseDeliveryFee: 25,
    perKmFee: 5,
    freeDeliveryAbove: 1000,
    prepTimeMinutes: 30,
    avgRating: 4.5,
    reviewCount: 12,
    acceptingOrders: true,
    hours: [makeStorefrontHour()],
    products: makePage([makePublicProduct()]),
    ...overrides,
  };
}

/* -------------------------------------------------------------------------- */
/* Customer order fixtures (plan Phase 6, task 6.6)                           */
/* -------------------------------------------------------------------------- */

export function makeCustomerOrderListItem(
  overrides: Partial<CustomerOrderListItem> = {},
): CustomerOrderListItem {
  return {
    id: 5001,
    status: "PLACED" as OrderStatus,
    paymentMethod: "COD" as PaymentMethod,
    paymentStatus: "PENDING" as PaymentStatus,
    vendorName: "Petal & Stem",
    total: 1823,
    createdAt: "2026-10-01T10:00:00",
    slotStart: "2026-10-03T10:00:00",
    slotEnd: "2026-10-03T11:00:00",
    ...overrides,
  };
}

export function makeCustomerOrderPageResponse(
  overrides: Partial<CustomerOrderPageResponse> = {},
): CustomerOrderPageResponse {
  return {
    content: [makeCustomerOrderListItem()],
    page: 0,
    size: 20,
    totalElements: 1,
    totalPages: 1,
    first: true,
    last: true,
    empty: false,
    ...overrides,
  };
}

export function makeCustomerOrderItemResponse(
  overrides: Partial<CustomerOrderItemResponse> = {},
): CustomerOrderItemResponse {
  return {
    productName: "Red Rose Bouquet",
    quantity: 2,
    priceSnapshot: 899,
    subtotal: 1798,
    note: null,
    ...overrides,
  };
}

export function makeCustomerOrderTimelineEntry(
  overrides: Partial<CustomerOrderTimelineEntry> = {},
): CustomerOrderTimelineEntry {
  return {
    fromStatus: null,
    toStatus: "PLACED" as OrderStatus,
    actorType: "SYSTEM",
    actorName: null,
    reason: null,
    createdAt: "2026-10-01T10:00:00",
    ...overrides,
  };
}

export function makeCustomerOrderResponse(
  overrides: Partial<CustomerOrderResponse> = {},
): CustomerOrderResponse {
  return {
    id: 5001,
    status: "PLACED" as OrderStatus,
    paymentMethod: "COD" as PaymentMethod,
    paymentStatus: "PENDING" as PaymentStatus,
    vendorName: "Petal & Stem",
    subtotal: 1798,
    deliveryFee: 25,
    platformFee: 0,
    total: 1823,
    createdAt: "2026-10-01T10:00:00",
    updatedAt: "2026-10-01T10:00:00",
    deliveryAddressLine1: "12 MG Road",
    deliveryAddressLine2: "Unit 3",
    deliveryLatitude: 12.971199,
    deliveryLongitude: 77.640586,
    slotStart: "2026-10-03T10:00:00",
    slotEnd: "2026-10-03T11:00:00",
    note: null,
    items: [makeCustomerOrderItemResponse()],
    timeline: [makeCustomerOrderTimelineEntry()],
    ...overrides,
  };
}

/* -------------------------------------------------------------------------- */
/* Payment fixtures (plan task 7.6)                                           */
/* -------------------------------------------------------------------------- */

export function makePaymentGroupOrderEntry(
  overrides: Partial<PaymentGroupOrderEntry> = {},
): PaymentGroupOrderEntry {
  return {
    orderId: 5001,
    paymentStatus: "PENDING",
    orderStatus: "PENDING_PAYMENT",
    method: "ONLINE",
    amount: 1823,
    ...overrides,
  };
}

export function makePaymentGroupStateResponse(
  overrides: Partial<PaymentGroupStateResponse> = {},
): PaymentGroupStateResponse {
  return {
    paymentGroupId: "pg-1001",
    intentReference: "intent_sim_abc123",
    status: "CREATED",
    total: 1823,
    expiresAt: "2026-10-01T10:30:00",
    orders: [makePaymentGroupOrderEntry()],
    ...overrides,
  };
}

export function makePaymentStateResponse(
  overrides: Partial<PaymentStateResponse> = {},
): PaymentStateResponse {
  return {
    orderId: 5001,
    paymentGroupId: "pg-1001",
    intentReference: "intent_sim_abc123",
    method: "ONLINE",
    paymentStatus: "PENDING",
    orderStatus: "PENDING_PAYMENT",
    amount: 1823,
    expiresAt: "2026-10-01T10:30:00",
    createdAt: "2026-10-01T10:00:00",
    ...overrides,
  };
}

export function makePaymentResumeResponse(
  overrides: Partial<PaymentResumeResponse> = {},
): PaymentResumeResponse {
  return {
    orderId: 5001,
    orderStatus: "PENDING_PAYMENT",
    paymentStatus: "PENDING",
    paymentGroupId: "pg-1001",
    intentReference: "intent_sim_abc123",
    paidAt: null,
    ...overrides,
  };
}

/* -------------------------------------------------------------------------- */
/* Vendor order fixtures (plan Phase 6, tasks 6.2-6.4)                        */
/* -------------------------------------------------------------------------- */

export function makeVendorOrderResponse(
  overrides: Partial<{
    id: number;
    status: OrderStatus;
    paymentStatus: string;
    total: number;
    createdAt: string;
    slotStart: string | null;
    slotEnd: string | null;
    deliveryAddressLine1: string;
    deliveryAddressLine2: string | null;
    note: string | null;
  }> = {},
): VendorOrderResponse {
  return {
    id: 5001,
    status: "PLACED" as OrderStatus,
    paymentStatus: "PENDING",
    total: 1823,
    createdAt: "2026-10-01T10:00:00",
    slotStart: "2026-10-03T10:00:00",
    slotEnd: "2026-10-03T11:00:00",
    deliveryAddressLine1: "12 MG Road",
    deliveryAddressLine2: "Unit 3",
    note: null,
    ...overrides,
  };
}

export function makeVendorOrderPageResponse(
  overrides: Partial<VendorOrderPageResponse> = {},
): VendorOrderPageResponse {
  const content = overrides.content ?? [makeVendorOrderResponse()];
  return {
    content,
    page: 0,
    size: 20,
    totalElements: content.length,
    totalPages: 1,
    first: true,
    last: true,
    empty: content.length === 0,
    ...overrides,
  };
}

export function makeVendorOrderSummaryResponse(
  overrides: Partial<VendorOrderSummaryResponse> = {},
): VendorOrderSummaryResponse {
  return {
    today: "2026-10-02",
    ordersByStatus: { PLACED: 1 } as Record<OrderStatus, number>,
    todayOrderCount: 1,
    todayTotalValue: 1823,
    expectedCodCash: 1823,
    ...overrides,
  };
}
