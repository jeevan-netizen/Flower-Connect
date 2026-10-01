import { describe, it, expect, vi, beforeEach } from "vitest";
import { fetchServiceLocations, registerVendor } from "@/features/vendor-registration/api";
import {
  buildVendorRegisterRequest,
  hasNoServiceLocations,
  toServiceLocationOptions,
  VENDOR_REGISTER_DEFAULTS,
  type VendorRegisterFormValues,
} from "@/features/vendor-registration/types";
import { isVendorRegisterField, vendorRegisterSchema } from "@/features/vendor-registration/form-schema";
import { makeServiceLocations, makeVendorProfile } from "@/test/factories";

const mockApi = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn() }));

vi.mock("@/shared/lib/api", () => ({ default: mockApi }));

/** A complete, valid form. Each test overrides one field at a time. */
function formValues(overrides: Partial<VendorRegisterFormValues> = {}): VendorRegisterFormValues {
  return {
    ...VENDOR_REGISTER_DEFAULTS,
    fullName: "Petal Owner",
    email: "petal@example.com",
    phone: "+919876543210",
    password: "correct-horse",
    confirmPassword: "correct-horse",
    businessName: "Petal & Stem",
    description: "Seasonal bouquets",
    addressLine1: "12 MG Road",
    addressLine2: "Unit 3",
    logoUrl: "https://cdn.example.com/logo.png",
    serviceLocationId: "3",
    ...overrides,
  };
}

describe("vendor registration api client", () => {
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

  it("posts the registration payload to /vendors/register", async () => {
    mockApi.post.mockResolvedValue({ data: makeVendorProfile({ status: "PENDING_APPROVAL" }) });

    await registerVendor(buildVendorRegisterRequest(formValues()));

    expect(mockApi.post).toHaveBeenCalledWith(
      "/vendors/register",
      expect.objectContaining({
        email: "petal@example.com",
        businessName: "Petal & Stem",
        serviceLocationId: 3,
      }),
    );
  });

  it("returns only the fields the success panel needs, never credentials", async () => {
    mockApi.post.mockResolvedValue({ data: makeVendorProfile({ status: "PENDING_APPROVAL" }) });

    const result = await registerVendor(buildVendorRegisterRequest(formValues()));

    expect(result).toEqual({
      businessName: "Petal & Stem",
      status: "PENDING_APPROVAL",
      city: "Bengaluru",
      area: "Indiranagar",
      pincode: "560038",
    });
    expect(result).not.toHaveProperty("ownerEmail");
    expect(result).not.toHaveProperty("password");
  });
});

describe("buildVendorRegisterRequest", () => {
  it("converts the picker's string id into the numeric serviceLocationId", () => {
    expect(buildVendorRegisterRequest(formValues({ serviceLocationId: "4" })).serviceLocationId).toBe(4);
  });

  it("sends blank optional text as null rather than an empty string", () => {
    const request = buildVendorRegisterRequest(
      formValues({ phone: "", description: "", addressLine2: "", logoUrl: "" }),
    );

    expect(request.phone).toBeNull();
    expect(request.description).toBeNull();
    expect(request.addressLine2).toBeNull();
    expect(request.logoUrl).toBeNull();
  });

  it("trims text fields and preserves the password verbatim", () => {
    const request = buildVendorRegisterRequest(
      formValues({ fullName: "  Petal Owner  ", businessName: "  Petal & Stem  ", password: "  spaced  " }),
    );

    expect(request.fullName).toBe("Petal Owner");
    expect(request.businessName).toBe("Petal & Stem");
    // Passwords are never trimmed: leading/trailing characters are significant.
    expect(request.password).toBe("  spaced  ");
  });

  it("drops the client-only confirmPassword field", () => {
    expect(buildVendorRegisterRequest(formValues())).not.toHaveProperty("confirmPassword");
  });

  it("omits every optional delivery setting so the backend schema defaults apply", () => {
    const request = buildVendorRegisterRequest(formValues());

    // Registration does not send the delivery block: those values are edited once on
    // /vendor/settings and /vendor/hours (docs/decisions.md, D-15).
    expect(request).not.toHaveProperty("deliveryRadiusKm");
    expect(request).not.toHaveProperty("minOrderAmount");
    expect(request).not.toHaveProperty("baseDeliveryFee");
    expect(request).not.toHaveProperty("perKmFee");
    expect(request).not.toHaveProperty("freeDeliveryAbove");
    expect(request).not.toHaveProperty("prepTimeMinutes");
    expect(request).not.toHaveProperty("slotDurationMinutes");
    expect(request).not.toHaveProperty("maxOrdersPerSlot");
    expect(request).not.toHaveProperty("acceptingOrders");
    expect(request).not.toHaveProperty("hours");
  });

  it("never sends a platform-owned field", () => {
    const request = buildVendorRegisterRequest(formValues());

    expect(request).not.toHaveProperty("status");
    expect(request).not.toHaveProperty("commissionRate");
    expect(request).not.toHaveProperty("avgRating");
    expect(request).not.toHaveProperty("reviewCount");
    expect(request).not.toHaveProperty("latitude");
    expect(request).not.toHaveProperty("longitude");
  });
});

describe("toServiceLocationOptions", () => {
  it("flattens the city-grouped response into id/label rows", () => {
    const options = toServiceLocationOptions(makeServiceLocations());

    expect(options).toEqual([
      { id: "3", city: "Bengaluru", label: "Indiranagar (560038)" },
      { id: "4", city: "Bengaluru", label: "Koramangala (560034)" },
    ]);
  });

  it("keeps the id from the response instead of deriving one from the label", () => {
    const [option] = toServiceLocationOptions(makeServiceLocations());

    expect(option?.id).toBe("3");
  });

  it("returns an empty list when the query has not resolved", () => {
    expect(toServiceLocationOptions(undefined)).toEqual([]);
  });

  it("drops an area with no usable id, because it could not be submitted", () => {
    const options = toServiceLocationOptions(
      makeServiceLocations({
        areas: [
          { id: 3, area: "Indiranagar", pincode: "560038", latitude: 12.97, longitude: 77.64 },
          { id: 0, area: "Broken Row", pincode: "000000", latitude: 0, longitude: 0 },
        ],
      }),
    );

    expect(options).toHaveLength(1);
    expect(options[0]?.label).toBe("Indiranagar (560038)");
  });

  it("reports the empty case rather than offering an unusable picker", () => {
    expect(hasNoServiceLocations(toServiceLocationOptions([]))).toBe(true);
    expect(hasNoServiceLocations(toServiceLocationOptions(makeServiceLocations()))).toBe(false);
  });
});

describe("vendorRegisterSchema", () => {
  const valid = formValues();

  it("accepts a complete form", () => {
    expect(vendorRegisterSchema.safeParse(valid).success).toBe(true);
  });

  it("requires the service area, because the backend requires serviceLocationId", () => {
    const result = vendorRegisterSchema.safeParse({ ...valid, serviceLocationId: "" });

    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.issues[0]?.message).toMatch(/select the service area/i);
    }
  });

  it("requires the identity, business and address fields the DTO marks NotBlank", () => {
    const result = vendorRegisterSchema.safeParse({
      ...valid,
      fullName: "",
      email: "",
      businessName: "",
      addressLine1: "",
    });

    expect(result.success).toBe(false);
    if (!result.success) {
      const fields = result.error.issues.map((issue) => issue.path[0]);
      expect(fields).toEqual(expect.arrayContaining(["fullName", "email", "businessName", "addressLine1"]));
    }
  });

  it("enforces the DTO password bounds", () => {
    expect(vendorRegisterSchema.safeParse({ ...valid, password: "short" }).success).toBe(false);
    expect(vendorRegisterSchema.safeParse({ ...valid, password: "x".repeat(129) }).success).toBe(false);
  });

  it("rejects mismatched passwords on the confirmPassword field", () => {
    const result = vendorRegisterSchema.safeParse({ ...valid, confirmPassword: "different" });

    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.issues[0]).toMatchObject({
        path: ["confirmPassword"],
        message: "Passwords do not match",
      });
    }
  });

  it("applies the same phone pattern as the DTO, and treats blank as optional", () => {
    expect(vendorRegisterSchema.safeParse({ ...valid, phone: "" }).success).toBe(true);
    expect(vendorRegisterSchema.safeParse({ ...valid, phone: "+919876543210" }).success).toBe(true);
    expect(vendorRegisterSchema.safeParse({ ...valid, phone: "12345" }).success).toBe(false);
    expect(vendorRegisterSchema.safeParse({ ...valid, phone: "not-a-number" }).success).toBe(false);
  });

  it("mirrors the DTO max-lengths", () => {
    expect(vendorRegisterSchema.safeParse({ ...valid, businessName: "b".repeat(161) }).success).toBe(false);
    expect(vendorRegisterSchema.safeParse({ ...valid, addressLine1: "a".repeat(256) }).success).toBe(false);
    expect(vendorRegisterSchema.safeParse({ ...valid, description: "d".repeat(1001) }).success).toBe(false);
  });

  it("accepts only backend validation keys that map to a real input", () => {
    expect(isVendorRegisterField("serviceLocationId")).toBe(true);
    expect(isVendorRegisterField("hours")).toBe(false);
  });
});