import { useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { useAuthStore } from "@/features/auth/stores/auth-store";
import { useAdminUsers, useUpdateUserStatus } from "@/features/admin/queries";
import {
  ADMIN_ROLES,
  USER_STATUSES,
  parseUserStatusParam,
  type AdminRole,
  type AdminUser,
  type UserListFilters,
  type UserStatus,
} from "@/features/admin/types";
import { formatDateTime, formatRole, formatUserStatus } from "@/features/admin/format";
import { PageHeading } from "@/features/vendor/components/StatCard";
import { SuccessMessage } from "@/features/vendor/components/FormFields";
import { AdminErrorState } from "@/features/admin/components/AdminErrorState";
import { AdminUserStatusBadge } from "@/features/admin/components/AdminStatusBadge";
import { Pagination } from "@/shared/components/Pagination";
import { ReasonDialog } from "@/features/admin/components/ReasonDialog";
import { AnimatePresence } from "framer-motion";
import { FadeIn } from "@/motion/FadeIn";
import { FIELD_TRANSITION, FOCUS_RING, PRESSABLE } from "@/motion/pressable";
import { toApiError, type ApiErrorInfo } from "@/shared/lib/api-error";

const ROLE_OPTIONS: { value: AdminRole | null; label: string }[] = [
  { value: null, label: "All roles" },
  ...ADMIN_ROLES.map((role) => ({ value: role, label: formatRole(role) })),
];

const STATUS_OPTIONS: { value: UserStatus | null; label: string }[] = [
  { value: null, label: "All statuses" },
  ...USER_STATUSES.map((status) => ({ value: status, label: formatUserStatus(status) })),
];

/** The status change awaiting confirmation, and the account it targets. */
interface PendingStatusChange {
  status: UserStatus;
  user: AdminUser;
}

/**
 * Admin user management (plan task 2.10, backend task 2.8).
 *
 * **No transition matrix is implemented.** `UserStatusService` enforces no
 * transition rules at all — `ACTIVE`, `SUSPENDED` and `DISABLED` are mutually
 * reachable, and the plan defines none — so this screen offers every status other
 * than the one the account currently holds and lets the backend decide. A
 * transition that is refused therefore arrives as a real 409/400 and is reported
 * from the error envelope.
 *
 * **Self-targeting is disabled but not trusted.** `UserStatusService.changeStatus`
 * compares the target id against the JWT subject and refuses a self-change with
 * 403 `FORBIDDEN` (D-14). The control is disabled for the signed-in admin's own row
 * purely so the attempt is not offered in the first place; the backend check is
 * unaffected and a 403 is still handled if one somehow arrives.
 *
 * Every transition requires a reason — including reactivation — because
 * `UserStatusUpdateRequest.reason` is `@NotBlank` and is the only record of why an
 * account was locked out.
 *
 * **The status filter is seeded from the query string.** The admin dashboard's
 * "View all N suspended accounts" link is a deep link into this page, so
 * `?status=SUSPENDED` has to actually select that filter. The URL is read once on
 * mount and again whenever it changes — the page stays mounted when only the
 * query string changes, so initialisation alone would miss a later navigation.
 * Choosing a filter in the select deliberately does *not* rewrite the URL: the
 * query string is an entry point, not the source of truth.
 */
export function AdminUsersPage() {
  const [searchParams] = useSearchParams();
  const statusFromUrl = parseUserStatusParam(searchParams.get("status"));

  const [filters, setFilters] = useState<UserListFilters>({
    role: null,
    status: statusFromUrl,
    page: 0,
  });
  const [pending, setPending] = useState<PendingStatusChange | null>(null);
  const [actionError, setActionError] = useState<ApiErrorInfo | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  // Re-seed from the URL when it changes under a mounted page. `filters` is not a
  // dependency on purpose: this must react to the query string, not to the
  // operator picking a filter, which would otherwise immediately be undone.
  useEffect(() => {
    setFilters({ role: null, status: statusFromUrl, page: 0 });
  }, [statusFromUrl]);

  const currentUserId = useAuthStore((state) => state.user?.id ?? null);

  const { data, isPending, error, refetch } = useAdminUsers(filters);
  const updateStatus = useUpdateUserStatus();

  /**
   * When the table replays its entry animation: both filters, the page, and the
   * rows themselves. Identical rows from a refetch produce an identical key, so
   * invalidation or polling that changed nothing does not replay it.
   */
  const listingKey = data
    ? `${filters.role ?? "all"}:${filters.status ?? "all"}:${data.page}:${data.content
        .map((user) => user.id)
        .join(",")}`
    : "";

  const openDialog = (status: UserStatus, user: AdminUser) => {
    setActionError(null);
    setSuccess(null);
    setPending({ status, user });
  };

  const closeDialog = () => {
    setPending(null);
    setActionError(null);
  };

  const confirmChange = async (reason?: string) => {
    if (!pending) {
      return;
    }
    setActionError(null);
    try {
      await updateStatus.mutateAsync({
        userId: pending.user.id,
        status: pending.status,
        // `reason` is mandatory on the wire; the dialog's required-reason mode
        // guarantees it is present, and `?? ""` only satisfies the type system.
        reason: reason ?? "",
      });
      setSuccess(`${pending.user.email} is now ${formatUserStatus(pending.status).toLowerCase()}.`);
      setPending(null);
    } catch (mutationError) {
      setActionError(toApiError(mutationError));
    }
  };

  const applyFilter = (patch: Partial<UserListFilters>) => {
    setFilters((current) => ({ ...current, ...patch, page: 0 }));
  };

  return (
    <div className="space-y-6">
      <PageHeading
        title="Users"
        description="List accounts by role and status, and change an account's status. Every change revokes that account's sessions."
      />

      <div className="flex flex-wrap items-end gap-3 rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
        <div>
          <label htmlFor="user-role-filter" className="block text-sm font-medium text-slate-700">
            Role
          </label>
          <select
            id="user-role-filter"
            value={filters.role ?? ""}
            onChange={(event) =>
              applyFilter({ role: event.target.value === "" ? null : (event.target.value as AdminRole) })
            }
            className={`mt-1 block rounded-md border-slate-300 text-sm shadow-sm ${FIELD_TRANSITION} focus:border-brand-500 focus:ring-brand-500`}
          >
            {ROLE_OPTIONS.map((option) => (
              <option key={option.label} value={option.value ?? ""}>
                {option.label}
              </option>
            ))}
          </select>
        </div>

        <div>
          <label htmlFor="user-status-filter" className="block text-sm font-medium text-slate-700">
            Status
          </label>
          <select
            id="user-status-filter"
            value={filters.status ?? ""}
            onChange={(event) =>
              applyFilter({
                status: event.target.value === "" ? null : (event.target.value as UserStatus),
              })
            }
            className={`mt-1 block rounded-md border-slate-300 text-sm shadow-sm ${FIELD_TRANSITION} focus:border-brand-500 focus:ring-brand-500`}
          >
            {STATUS_OPTIONS.map((option) => (
              <option key={option.label} value={option.value ?? ""}>
                {option.label}
              </option>
            ))}
          </select>
        </div>
      </div>

      <SuccessMessage message={success} />

      {isPending && (
        <div className="rounded-lg border border-slate-200 bg-white p-5 text-sm text-slate-600">
          Loading users...
        </div>
      )}

      {!isPending && error && (
        <AdminErrorState error={error} onRetry={() => void refetch()} />
      )}

      {!isPending && !error && data && data.empty && (
        <div className="rounded-lg border border-slate-200 bg-white p-5 text-sm text-slate-600">
          No user accounts match these filters.
        </div>
      )}

      {!isPending && !error && data && !data.empty && (
        <FadeIn
          key={listingKey}
          className="overflow-x-auto rounded-lg border border-slate-200 bg-white p-5 shadow-sm"
        >
          <table className="w-full text-left text-sm">
            <caption className="sr-only">User accounts and available status actions</caption>
            <thead className="text-xs uppercase tracking-wide text-slate-500">
              <tr>
                <th scope="col" className="py-2 pr-4 font-medium">Name</th>
                <th scope="col" className="py-2 pr-4 font-medium">Email</th>
                <th scope="col" className="py-2 pr-4 font-medium">Role</th>
                <th scope="col" className="py-2 pr-4 font-medium">Status</th>
                <th scope="col" className="py-2 pr-4 font-medium">Created</th>
                <th scope="col" className="py-2 font-medium">Change status</th>
              </tr>
            </thead>
            <tbody>
              {data.content.map((user) => {
                const isSelf = currentUserId !== null && user.id === currentUserId;

                return (
                  <tr key={user.id} className="border-t border-slate-100 align-top">
                    <td className="py-3 pr-4 font-medium text-slate-900">
                      {user.fullName}
                      {isSelf && <span className="ml-2 text-xs text-slate-500">(you)</span>}
                    </td>
                    <td className="py-3 pr-4 text-slate-700">
                      {user.email}
                      {user.phone && (
                        <p className="text-xs text-slate-500">{user.phone}</p>
                      )}
                    </td>
                    <td className="py-3 pr-4 text-slate-700">{formatRole(user.role)}</td>
                    <td className="py-3 pr-4">
                      <AdminUserStatusBadge status={user.status} />
                    </td>
                    <td className="py-3 pr-4 text-slate-600">{formatDateTime(user.createdAt)}</td>
                    <td className="py-3">
                      {isSelf ? (
                        <p className="text-xs text-slate-500">
                          An administrator cannot change their own account status.
                        </p>
                      ) : (
                        <div className="flex flex-wrap gap-2">
                          {USER_STATUSES.filter((status) => status !== user.status).map((status) => (
                            <button
                              key={status}
                              type="button"
                              onClick={() => openDialog(status, user)}
                              disabled={updateStatus.isPending}
                              className={`rounded-md border border-slate-300 bg-white px-2.5 py-1 text-xs font-medium text-slate-700 hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-50 ${PRESSABLE} ${FOCUS_RING}`}
                            >
                              {formatUserStatus(status)}
                            </button>
                          ))}
                        </div>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>

          <div className="mt-4">
            <Pagination
              page={data}
              disabled={updateStatus.isPending}
              onPageChange={(page) => setFilters((current) => ({ ...current, page }))}
            />
          </div>
        </FadeIn>
      )}

      {/* `AnimatePresence` gives the dialog its exit animation; `mode="wait"`
          keeps one dialog on screen at a time when the target changes. */}
      <AnimatePresence mode="wait">
        {pending && (
          <ReasonDialog
            key={`${pending.user.id}-${pending.status}`}
            heading={`Set ${pending.user.email} to ${formatUserStatus(pending.status).toLowerCase()}?`}
            description={`${pending.user.fullName} is currently ${pending.user.status.toLowerCase()}. Changing status also signs this account out everywhere.`}
            confirmLabel={`Set to ${formatUserStatus(pending.status)}`}
            requiresReason
            isSubmitting={updateStatus.isPending}
            error={actionError}
            onConfirm={(reason) => void confirmChange(reason)}
            onCancel={closeDialog}
          />
        )}
      </AnimatePresence>
    </div>
  );
}