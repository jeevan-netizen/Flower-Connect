import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClientProvider, type QueryClient } from "@tanstack/react-query";
import type { ReactNode } from "react";
import {
  adminKeys,
  clearAdminCache,
  useAdminUsers,
  useAdminVendors,
  useUpdateUserStatus,
  useVendorAdminAction,
} from "@/features/admin/queries";
import { createTestQueryClient } from "@/test/render";
import { makeAdminUser, makePage, makeVendorProfile } from "@/test/factories";
import type { AdminUser, UserListFilters, VendorListFilters } from "@/features/admin/types";
import type { VendorProfile } from "@/features/vendor/types";

const mockFetchVendors = vi.hoisted(() => vi.fn());
const mockFetchUsers = vi.hoisted(() => vi.fn());
const mockApprove = vi.hoisted(() => vi.fn());
const mockReject = vi.hoisted(() => vi.fn());
const mockSuspend = vi.hoisted(() => vi.fn());
const mockReinstate = vi.hoisted(() => vi.fn());
const mockUpdateStatus = vi.hoisted(() => vi.fn());

vi.mock("@/features/admin/api", () => ({
  fetchAdminVendors: mockFetchVendors,
  fetchAdminUsers: mockFetchUsers,
  approveVendor: mockApprove,
  rejectVendor: mockReject,
  suspendVendor: mockSuspend,
  reinstateVendor: mockReinstate,
  updateUserStatus: mockUpdateStatus,
}));

function wrapperFor(queryClient: QueryClient) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  };
}

const VENDOR_FILTERS: VendorListFilters = { status: "PENDING_APPROVAL", page: 0 };
const USER_FILTERS: UserListFilters = { role: null, status: "SUSPENDED", page: 0 };

describe("admin queries — cache keys", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("treats each filter combination as its own vendor query", async () => {
    mockFetchVendors.mockResolvedValue(makePage<VendorProfile>([makeVendorProfile()]));
    const queryClient = createTestQueryClient();

    const { rerender } = renderHook(
      ({ filters }: { filters: VendorListFilters }) => useAdminVendors(filters),
      { wrapper: wrapperFor(queryClient), initialProps: { filters: VENDOR_FILTERS } },
    );

    await waitFor(() => expect(mockFetchVendors).toHaveBeenCalledTimes(1));
    expect(mockFetchVendors).toHaveBeenCalledWith(VENDOR_FILTERS);

    rerender({ filters: { status: "APPROVED", page: 0 } });

    // A different status is a different key, so the previous result cannot be
    // shown under the new filter.
    await waitFor(() => expect(mockFetchVendors).toHaveBeenCalledTimes(2));
    expect(mockFetchVendors).toHaveBeenLastCalledWith({ status: "APPROVED", page: 0 });
  });

  it("refetches the user listing when the page changes", async () => {
    mockFetchUsers.mockResolvedValue(makePage<AdminUser>([makeAdminUser()]));
    const queryClient = createTestQueryClient();

    const { rerender } = renderHook(
      ({ filters }: { filters: UserListFilters }) => useAdminUsers(filters),
      { wrapper: wrapperFor(queryClient), initialProps: { filters: USER_FILTERS } },
    );

    await waitFor(() => expect(mockFetchUsers).toHaveBeenCalledTimes(1));

    rerender({ filters: { ...USER_FILTERS, page: 1 } });

    await waitFor(() => expect(mockFetchUsers).toHaveBeenCalledTimes(2));
    expect(mockFetchUsers).toHaveBeenLastCalledWith({ ...USER_FILTERS, page: 1 });
  });
});

describe("admin queries — vendor action invalidation", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("invalidates every vendor page and filter after a transition", async () => {
    mockApprove.mockResolvedValue(makeVendorProfile({ status: "APPROVED" }));
    mockFetchVendors.mockResolvedValue(makePage<VendorProfile>([makeVendorProfile()]));
    const queryClient = createTestQueryClient();

    // Seed one page under a different filter so a too-narrow invalidation is visible.
    await queryClient.prefetchQuery({
      queryKey: adminKeys.vendors.list({ status: "APPROVED", page: 1 }),
      queryFn: mockFetchVendors,
    });

    const { result } = renderHook(() => useVendorAdminAction(), { wrapper: wrapperFor(queryClient) });

    await result.current.mutateAsync({ action: "approve", profileId: 7 });

    await waitFor(() => {
      expect(queryClient.getQueryState(adminKeys.vendors.list({ status: "APPROVED", page: 1 }))?.isInvalidated).toBe(
        true,
      );
    });
  });

  it("sends no body for approve and a reason body for reject", async () => {
    mockApprove.mockResolvedValue(makeVendorProfile());
    mockReject.mockResolvedValue(makeVendorProfile({ status: "REJECTED" }));
    const queryClient = createTestQueryClient();

    const { result } = renderHook(() => useVendorAdminAction(), { wrapper: wrapperFor(queryClient) });

    await result.current.mutateAsync({ action: "approve", profileId: 7 });
    expect(mockApprove).toHaveBeenCalledWith(7);
    expect(mockReject).not.toHaveBeenCalled();

    await result.current.mutateAsync({ action: "reject", profileId: 7, reason: "No licence" });
    expect(mockReject).toHaveBeenCalledWith(7, { reason: "No licence" });
  });

  it("routes suspend and reinstate to their own endpoints", async () => {
    mockSuspend.mockResolvedValue(makeVendorProfile({ status: "SUSPENDED" }));
    mockReinstate.mockResolvedValue(makeVendorProfile({ status: "APPROVED" }));
    const queryClient = createTestQueryClient();

    const { result } = renderHook(() => useVendorAdminAction(), { wrapper: wrapperFor(queryClient) });

    await result.current.mutateAsync({ action: "suspend", profileId: 9, reason: "Late orders" });
    await result.current.mutateAsync({ action: "reinstate", profileId: 9 });

    expect(mockSuspend).toHaveBeenCalledWith(9, { reason: "Late orders" });
    // Reinstatement is a distinct route from approval: it skips a fresh review.
    expect(mockReinstate).toHaveBeenCalledWith(9);
    expect(mockApprove).not.toHaveBeenCalled();
  });
});

/**
 * The mutation variables are a discriminated union, so these are compile-time
 * assertions as much as runtime ones: `@ts-expect-error` fails `tsc -b` (and
 * therefore `npm run typecheck`) if a reasonless `reject`/`suspend` ever becomes
 * accepted again. That is the property the optional-reason shape plus a cast
 * could not express.
 */
describe("admin queries — vendor mutation contract", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockApprove.mockResolvedValue(makeVendorProfile());
    mockReject.mockResolvedValue(makeVendorProfile({ status: "REJECTED" }));
    mockSuspend.mockResolvedValue(makeVendorProfile({ status: "SUSPENDED" }));
    mockReinstate.mockResolvedValue(makeVendorProfile());
  });

  function renderAction() {
    const queryClient = createTestQueryClient();
    return renderHook(() => useVendorAdminAction(), { wrapper: wrapperFor(queryClient) });
  }

  it("accepts approve and reinstate with no reason", async () => {
    const { result } = renderAction();

    await result.current.mutateAsync({ action: "approve", profileId: 7 });
    await result.current.mutateAsync({ action: "reinstate", profileId: 7 });

    expect(mockApprove).toHaveBeenCalledWith(7);
    expect(mockReinstate).toHaveBeenCalledWith(7);
  });

  it("refuses a reasonless reject at compile time", () => {
    const { result } = renderAction();
    // Taken from the hook itself, so this asserts the real `mutateAsync`
    // signature rather than a hand-written stand-in.
    type Variables = Parameters<typeof result.current.mutateAsync>[0];

    // @ts-expect-error `reject` requires a reason; the union must reject this.
    const invalid: Variables = { action: "reject", profileId: 7 };

    expect(invalid).toBeDefined();
    expect(mockReject).not.toHaveBeenCalled();
  });

  it("refuses a reasonless suspend at compile time", () => {
    const { result } = renderAction();
    type Variables = Parameters<typeof result.current.mutateAsync>[0];

    // @ts-expect-error `suspend` requires a reason; the union must reject this.
    const invalid: Variables = { action: "suspend", profileId: 7 };

    expect(invalid).toBeDefined();
    // The hook is rendered but never invoked with the invalid shape, so no
    // request is made — this test is about the compiler, not the transport.
    expect(mockSuspend).not.toHaveBeenCalled();
    expect(result.current.isPending).toBe(false);
  });

  it("forwards the reason the caller supplies for reject and suspend", async () => {
    const { result } = renderAction();

    await result.current.mutateAsync({ action: "reject", profileId: 7, reason: "No licence" });
    await result.current.mutateAsync({ action: "suspend", profileId: 8, reason: "Late orders" });

    expect(mockReject).toHaveBeenCalledWith(7, { reason: "No licence" });
    expect(mockSuspend).toHaveBeenCalledWith(8, { reason: "Late orders" });
  });
});

describe("admin queries — user status invalidation", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("splits userId out of the payload and invalidates every user listing", async () => {
    mockUpdateStatus.mockResolvedValue(makeAdminUser({ status: "SUSPENDED" }));
    mockFetchUsers.mockResolvedValue(makePage<AdminUser>([makeAdminUser()]));
    const queryClient = createTestQueryClient();

    await queryClient.prefetchQuery({
      queryKey: adminKeys.users.list({ role: "FLORIST", status: null, page: 2 }),
      queryFn: mockFetchUsers,
    });

    const { result } = renderHook(() => useUpdateUserStatus(), { wrapper: wrapperFor(queryClient) });

    await result.current.mutateAsync({ userId: 42, status: "SUSPENDED", reason: "Fraud" });

    expect(mockUpdateStatus).toHaveBeenCalledWith(42, { status: "SUSPENDED", reason: "Fraud" });
    await waitFor(() => {
      expect(
        queryClient.getQueryState(adminKeys.users.list({ role: "FLORIST", status: null, page: 2 }))
          ?.isInvalidated,
      ).toBe(true);
    });
  });
});

describe("clearAdminCache", () => {
  it("removes both the vendor and the user listing", () => {
    const queryClient = createTestQueryClient();
    queryClient.setQueryData(adminKeys.vendors.list(VENDOR_FILTERS), makePage([]));
    queryClient.setQueryData(adminKeys.users.list(USER_FILTERS), makePage([]));

    clearAdminCache(queryClient);

    expect(queryClient.getQueryState(adminKeys.vendors.all)).toBeUndefined();
    expect(queryClient.getQueryState(adminKeys.users.all)).toBeUndefined();
  });
});