import api from "@/shared/lib/api";
import type { VendorProfile } from "@/features/vendor/types";
import type {
  AdminRole,
  AdminUser,
  AdminUserPageResponse,
  UserStatus,
  UserStatusUpdateRequest,
  VendorAdminReasonRequest,
  VendorListFilters,
  VendorProfilePageResponse,
  MarkRefundRequest,
  RefundMarkedResponse,
  RefundQueuePageResponse,
  RefundQueueFilters,
} from "@/features/admin/types";
import { ADMIN_PAGE_SIZE } from "@/features/admin/types";

/**
 * The admin API client — plan task 2.10.
 *
 * Every call here sits under `/api/v1/admin/**`, which `SecurityConfig` matches
 * with `hasRole("ADMIN")`. That backend check is the authorization authority; the
 * frontend `ProtectedRoute roles={["ADMIN"]}` is routing UX only.
 */

/* -------------------------------------------------------------------------- */
/* Vendor administration (plan task 2.6)                                       */
/* -------------------------------------------------------------------------- */

/**
 * `GET /api/v1/admin/vendors` — page of vendor profiles, optionally narrowed to
 * one status. Sorted by `businessName` then `id`, so paging is stable.
 *
 * The backend clamps `size` to `1..100` and defaults `page` to `0`; the caller
 * always sends an explicit size so the page controls know what they asked for.
 */
export async function fetchAdminVendors(
  filters: VendorListFilters,
): Promise<VendorProfilePageResponse> {
  const params: Record<string, string> = {
    page: String(filters.page),
    size: String(ADMIN_PAGE_SIZE),
  };
  if (filters.status) {
    params.status = filters.status;
  }

  const response = await api.get<VendorProfilePageResponse>("/admin/vendors", { params });
  return response.data;
}

/**
 * `POST /api/v1/admin/vendors/{id}/approve` — `PENDING_APPROVAL -> APPROVED`.
 * No request body: the reason field belongs only to reject and suspend.
 */
export async function approveVendor(profileId: number): Promise<VendorProfile> {
  const response = await api.post<VendorProfile>(`/admin/vendors/${profileId}/approve`);
  return response.data;
}

/**
 * `POST /api/v1/admin/vendors/{id}/reject` — `PENDING_APPROVAL -> REJECTED`.
 * The reason is `@NotBlank` and is stored on the `audit_log` row.
 */
export async function rejectVendor(
  profileId: number,
  payload: VendorAdminReasonRequest,
): Promise<VendorProfile> {
  const response = await api.post<VendorProfile>(`/admin/vendors/${profileId}/reject`, payload);
  return response.data;
}

/**
 * `POST /api/v1/admin/vendors/{id}/suspend` — `APPROVED -> SUSPENDED`.
 * In-flight orders are deliberately unaffected (D-6).
 */
export async function suspendVendor(
  profileId: number,
  payload: VendorAdminReasonRequest,
): Promise<VendorProfile> {
  const response = await api.post<VendorProfile>(`/admin/vendors/${profileId}/suspend`, payload);
  return response.data;
}

/**
 * `POST /api/v1/admin/vendors/{id}/reinstate` — `SUSPENDED -> APPROVED`.
 * Deliberately distinct from `approve` because reinstatement skips a fresh
 * approval review. No request body.
 */
export async function reinstateVendor(profileId: number): Promise<VendorProfile> {
  const response = await api.post<VendorProfile>(`/admin/vendors/${profileId}/reinstate`);
  return response.data;
}

/* -------------------------------------------------------------------------- */
/* User administration (plan task 2.8)                                         */
/* -------------------------------------------------------------------------- */

/**
 * `GET /api/v1/admin/users` — page of users, optionally narrowed to one role
 * and/or one status. `AdminUserService` combines the two filters with `AND` and
 * rejects an unseeded role name with 400 `VALIDATION_FAILED`.
 */
export async function fetchAdminUsers(filters: {
  role: AdminRole | null;
  status: UserStatus | null;
  page: number;
}): Promise<AdminUserPageResponse> {
  const params: Record<string, string> = {
    page: String(filters.page),
    size: String(ADMIN_PAGE_SIZE),
  };
  if (filters.role) {
    params.role = filters.role;
  }
  if (filters.status) {
    params.status = filters.status;
  }

  const response = await api.get<AdminUserPageResponse>("/admin/users", { params });
  return response.data;
}

/**
 * `PATCH /api/v1/admin/users/{id}/status`.
 *
 * The backend refuses a self-targeted change with 403 `FORBIDDEN` (`D-14`) — see
 * `UserStatusService.changeStatus`. The admin UI disables the control for the
 * signed-in admin's own row, but that is usability only; this call is still made
 * the same way and any 403 is reported from the error envelope.
 */
export async function updateUserStatus(
  userId: number,
  payload: UserStatusUpdateRequest,
): Promise<AdminUser> {
  const response = await api.patch<AdminUser>(`/admin/users/${userId}/status`, payload);
  return response.data;
}

/* -------------------------------------------------------------------------- */
/* Refund queue (plan Phase 8, task 8.3)                                       */
/* -------------------------------------------------------------------------- */

/**
 * `GET /api/v1/admin/refunds` — paginated page of `REFUND_PENDING` payments,
 * optionally narrowed to one vendor and/or a date range.
 *
 * The backend clamps `size` to `1..100` and defaults `page` to `0`; the caller
 * always sends an explicit size so the page controls know what they asked for.
 * Null filters are omitted entirely rather than sent as empty strings.
 */
export async function fetchRefundQueue(
  filters: RefundQueueFilters,
): Promise<RefundQueuePageResponse> {
  const params: Record<string, string> = {
    page: String(filters.page),
    size: String(ADMIN_PAGE_SIZE),
  };
  if (filters.status) {
    params.status = filters.status;
  }
  if (filters.vendorId) {
    params.vendorId = String(filters.vendorId);
  }
  if (filters.fromDate) {
    params.fromDate = filters.fromDate;
  }
  if (filters.toDate) {
    params.toDate = filters.toDate;
  }

  const response = await api.get<RefundQueuePageResponse>("/admin/refunds", { params });
  return response.data;
}

/**
 * `POST /api/v1/admin/refunds/{orderId}/mark` — marks a `REFUND_PENDING`
 * payment as `REFUNDED`. Requires a non-blank reference note of at most 500
 * characters.
 */
export async function markRefunded(
  orderId: number,
  payload: MarkRefundRequest,
): Promise<RefundMarkedResponse> {
  const response = await api.post<RefundMarkedResponse>(
    `/admin/refunds/${orderId}/mark`,
    payload,
  );
  return response.data;
}

/**
 * `POST /api/v1/orders/{orderId}/cancel` — admin cancels any order.
 * A reason is mandatory for an admin cancellation.
 */
export async function adminCancelOrder(
  orderId: number,
  reason: string,
): Promise<{ id: number; status: string; paymentStatus: string; refundPending: boolean }> {
  const response = await api.post<{
    id: number;
    status: string;
    paymentStatus: string;
    refundPending: boolean;
  }>(`/orders/${orderId}/cancel`, { reason });
  return response.data;
}