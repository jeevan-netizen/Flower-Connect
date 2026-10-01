import { describe, it, expect, vi, beforeEach } from "vitest";
import {
  approveVendor,
  fetchAdminUsers,
  fetchAdminVendors,
  reinstateVendor,
  rejectVendor,
  suspendVendor,
  updateUserStatus,
} from "@/features/admin/api";
import {
  vendorActionRequiresReason,
  vendorActionsFor,
  type PageResponse,
} from "@/features/admin/types";
import { makeAdminUser, makePage, makeVendorProfile } from "@/test/factories";
import type { AdminUser } from "@/features/admin/types";
import type { VendorProfile } from "@/features/vendor/types";

const mockApi = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), patch: vi.fn() }));

vi.mock("@/shared/lib/api", () => ({ default: mockApi }));

describe("admin api client — vendor listing", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("omits the status filter entirely when none is set", async () => {
    mockApi.get.mockResolvedValue({ data: makePage<VendorProfile>([]) });

    await fetchAdminVendors({ status: null, page: 0 });

    // `status` is bound to the `VendorProfile.Status` enum server-side, so an
    // empty string would fail conversion. Omitting it is "no filter".
    expect(mockApi.get).toHaveBeenCalledWith("/admin/vendors", {
      params: { page: "0", size: "20" },
    });
  });

  it("sends the status filter and zero-based page when both are set", async () => {
    mockApi.get.mockResolvedValue({ data: makePage<VendorProfile>([]) });

    await fetchAdminVendors({ status: "PENDING_APPROVAL", page: 2 });

    expect(mockApi.get).toHaveBeenCalledWith("/admin/vendors", {
      params: { page: "2", size: "20", status: "PENDING_APPROVAL" },
    });
  });

  it("returns the page response as the backend sent it", async () => {
    const page = makePage<VendorProfile>([makeVendorProfile()]);
    mockApi.get.mockResolvedValue({ data: page });

    expect(await fetchAdminVendors({ status: null, page: 0 })).toEqual(page);
  });
});

describe("admin api client — vendor actions", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockApi.post.mockResolvedValue({ data: makeVendorProfile() });
  });

  it("approves with no request body", async () => {
    await approveVendor(7);

    expect(mockApi.post).toHaveBeenCalledWith("/admin/vendors/7/approve");
  });

  it("rejects with the reason as the request body", async () => {
    await rejectVendor(7, { reason: "Missing trade licence" });

    expect(mockApi.post).toHaveBeenCalledWith("/admin/vendors/7/reject", {
      reason: "Missing trade licence",
    });
  });

  it("suspends with the reason as the request body", async () => {
    await suspendVendor(7, { reason: "Repeated late deliveries" });

    expect(mockApi.post).toHaveBeenCalledWith("/admin/vendors/7/suspend", {
      reason: "Repeated late deliveries",
    });
  });

  it("reinstates with no request body", async () => {
    await reinstateVendor(7);

    expect(mockApi.post).toHaveBeenCalledWith("/admin/vendors/7/reinstate");
  });
});

describe("admin api client — user listing and status", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("omits both filters when neither is set", async () => {
    mockApi.get.mockResolvedValue({ data: makePage<AdminUser>([]) });

    await fetchAdminUsers({ role: null, status: null, page: 0 });

    expect(mockApi.get).toHaveBeenCalledWith("/admin/users", {
      params: { page: "0", size: "20" },
    });
  });

  it("sends both role and status filters, which the backend combines with AND", async () => {
    mockApi.get.mockResolvedValue({ data: makePage<AdminUser>([]) });

    await fetchAdminUsers({ role: "FLORIST", status: "SUSPENDED", page: 1 });

    expect(mockApi.get).toHaveBeenCalledWith("/admin/users", {
      params: { page: "1", size: "20", role: "FLORIST", status: "SUSPENDED" },
    });
  });

  it("PATCHes the status endpoint with status and reason", async () => {
    mockApi.patch.mockResolvedValue({ data: makeAdminUser({ status: "SUSPENDED" }) });

    await updateUserStatus(42, { status: "SUSPENDED", reason: "Chargeback fraud" });

    expect(mockApi.patch).toHaveBeenCalledWith("/admin/users/42/status", {
      status: "SUSPENDED",
      reason: "Chargeback fraud",
    });
  });
});

describe("vendorActionsFor", () => {
  /**
   * Mirrors `VendorAdminService.requireStatus` exactly. If this table ever offers
   * a transition the backend refuses, the UI would produce a 409 that the screen
   * chose to make possible.
   */
  it.each([
    ["PENDING_APPROVAL", ["approve", "reject"]],
    ["APPROVED", ["suspend"]],
    ["SUSPENDED", ["reinstate"]],
    ["REJECTED", []],
  ] as const)("offers only the legal transitions from %s", (status, expected) => {
    expect(vendorActionsFor(status)).toEqual(expected);
  });

  it("requires a reason for exactly the two audited-by-reason actions", () => {
    expect(vendorActionRequiresReason("reject")).toBe(true);
    expect(vendorActionRequiresReason("suspend")).toBe(true);
    // `approve` and `reinstate` routes take no request body at all.
    expect(vendorActionRequiresReason("approve")).toBe(false);
    expect(vendorActionRequiresReason("reinstate")).toBe(false);
  });
});

describe("page response shape", () => {
  it("exposes the zero-based page index the backend sends", () => {
    const page: PageResponse<VendorProfile> = makePage([makeVendorProfile()], { page: 2 });

    expect(page.page).toBe(2);
  });
});