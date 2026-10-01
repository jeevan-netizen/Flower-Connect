import type { VendorHours, VendorProfile, VendorStatus } from "@/features/vendor/types";
import type { ServiceLocationGroup } from "@/features/vendor-registration/types";
import type {
  AdminRole,
  AdminUser,
  PageResponse,
  UserStatus,
} from "@/features/admin/types";

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
