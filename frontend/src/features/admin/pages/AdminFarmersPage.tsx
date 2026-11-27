import { useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { useAdminFarmers, useUpdateFarmerStatus } from "@/features/admin/queries";
import {
  FARMER_STATUSES,
  parseFarmerStatusParam,
  type AdminFarmer,
  type AdminFarmerFilters,
} from "@/features/admin/types";
import { formatDateTime, formatUserStatus } from "@/features/admin/format";
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
import { Link } from "react-router-dom";

const STATUS_FILTER_OPTIONS: { value: AdminFarmer["status"] | null; label: string }[] = [
  { value: null, label: "All statuses" },
  ...FARMER_STATUSES.map((status) => ({ value: status, label: formatUserStatus(status) })),
];

/** The status change awaiting confirmation, and the farmer it targets. */
interface PendingStatusChange {
  status: AdminFarmer["status"];
  farmer: AdminFarmer;
}

/**
 * Admin farmer management (Admin Farmer Management).
 *
 * **Authorization is server-side.** `SecurityConfig` matches
 * `/api/v1/admin/farmers/**` with `hasRole("ADMIN")`; the frontend
 * `ProtectedRoute roles={["ADMIN"]}` is routing UX only and never the
 * authority. Every row's actions and the detail view are scoped to the
 * requested farmer and return only fields the admin UI needs.
 *
 * The status filter is seeded from the query string so a dashboard deep link
 * such as `?status=SUSPENDED` selects that filter. Choosing a filter in the
 * input does not rewrite the URL: the query string is an entry point, not the
 * source of truth.
 */
export function AdminFarmersPage() {
  const [searchParams] = useSearchParams();
  const statusFromUrl = parseFarmerStatusParam(searchParams.get("status"));

  const [filters, setFilters] = useState<AdminFarmerFilters>({
    search: "",
    status: statusFromUrl,
    page: 0,
  });
  const [pending, setPending] = useState<PendingStatusChange | null>(null);
  const [actionError, setActionError] = useState<ApiErrorInfo | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  // Re-seed from the URL when it changes under a mounted page. `filters` is not
  // a dependency on purpose: this must react to the query string, not to the
  // operator picking a filter, which would otherwise immediately be undone.
  useEffect(() => {
    setFilters((current) => ({ ...current, status: statusFromUrl, page: 0 }));
  }, [statusFromUrl]);

  const { data, isPending, error, refetch } = useAdminFarmers(filters);
  const updateStatus = useUpdateFarmerStatus();

  const listingKey = data
    ? `${filters.search ?? ""}:${filters.status ?? "all"}:${data.page}:${data.content
        .map((farmer) => farmer.id)
        .join(",")}`
    : "";

  const openDialog = (status: AdminFarmer["status"], farmer: AdminFarmer) => {
    setActionError(null);
    setSuccess(null);
    setPending({ status, farmer });
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
        farmerId: pending.farmer.id,
        status: pending.status,
        reason: reason ?? "",
      });
      setSuccess(
        `${pending.farmer.fullName ?? pending.farmer.email} is now ${formatUserStatus(
          pending.status,
        ).toLowerCase()}.`,
      );
      setPending(null);
    } catch (mutationError) {
      setActionError(toApiError(mutationError));
    }
  };

  const applySearch = (value: string) => {
    setFilters((current) => ({ ...current, search: value, page: 0 }));
  };

  const applyFilter = (patch: Partial<AdminFarmerFilters>) => {
    setFilters((current) => ({ ...current, ...patch, page: 0 }));
  };

  /**
   * The actions the backend will accept for a farmer currently in `status`.
   * `ACTIVE` can be suspended; `SUSPENDED` or `DISABLED` can be reactivated.
   */
  const farmerActionsFor = (status: AdminFarmer["status"]) => {
    if (status === "ACTIVE") {
      return [{ status: "SUSPENDED" as const, label: "Suspend" }];
    }
    return [{ status: "ACTIVE" as const, label: "Reactivate" }];
  };

  return (
    <div className="space-y-6">
      <PageHeading
        title="Farmers"
        description="List farmer accounts and suspend or reactivate an account. Every change revokes that account's sessions."
      />

      <div className="flex flex-wrap items-end gap-3 rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
        <div className="min-w-[220px]">
          <label
            htmlFor="farmer-search"
            className="block text-sm font-medium text-slate-700"
          >
            Search
          </label>
          <input
            id="farmer-search"
            value={filters.search ?? ""}
            onChange={(event) => applySearch(event.target.value)}
            placeholder="Name, email or mobile"
            className={`mt-1 block w-full rounded-md border-slate-300 text-sm shadow-sm ${FIELD_TRANSITION} focus:border-brand-500 focus:ring-brand-500`}
          />
        </div>

        <div>
          <label
            htmlFor="farmer-status-filter"
            className="block text-sm font-medium text-slate-700"
          >
            Account status
          </label>
          <select
            id="farmer-status-filter"
            value={filters.status ?? ""}
            onChange={(event) =>
              applyFilter({
                status: event.target.value === "" ? null : (event.target.value as AdminFarmer["status"]),
              })
            }
            className={`mt-1 block rounded-md border-slate-300 text-sm shadow-sm ${FIELD_TRANSITION} focus:border-brand-500 focus:ring-brand-500`}
          >
            {STATUS_FILTER_OPTIONS.map((option) => (
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
          Loading farmers…
        </div>
      )}

      {!isPending && error && (
        <AdminErrorState error={error} onRetry={() => void refetch()} />
      )}

      {!isPending && !error && data && data.empty && (
        <div className="rounded-lg border border-slate-200 bg-white p-5 text-sm text-slate-600">
          {filters.search || filters.status
            ? "No farmer accounts match these filters."
            : "No farmer accounts exist yet."}
        </div>
      )}

      {!isPending && !error && data && !data.empty && (
        <FadeIn
          key={listingKey}
          className="overflow-x-auto rounded-lg border border-slate-200 bg-white p-5 shadow-sm"
        >
          <table className="w-full text-left text-sm">
            <caption className="sr-only">Farmer accounts and available status actions</caption>
            <thead className="text-xs uppercase tracking-wide text-slate-500">
              <tr>
                <th scope="col" className="py-2 pr-4 font-medium">Name</th>
                <th scope="col" className="py-2 pr-4 font-medium">Email</th>
                <th scope="col" className="py-2 pr-4 font-medium">Mobile</th>
                <th scope="col" className="py-2 pr-4 font-medium">Status</th>
                <th scope="col" className="py-2 pr-4 font-medium">Registered</th>
                <th scope="col" className="py-2 font-medium">Account status</th>
              </tr>
            </thead>
            <tbody>
              {data.content.map((farmer) => {
                const actions = farmerActionsFor(farmer.status);

                return (
                  <tr key={farmer.id} className="border-t border-slate-100 align-top">
                    <td className="py-3 pr-4 font-medium text-slate-900">
                      <Link
                        to={`/admin/farmers/${farmer.id}`}
                        className="text-brand-700 hover:text-brand-900 hover:underline"
                      >
                        {farmer.fullName}
                      </Link>
                    </td>
                    <td className="py-3 pr-4 text-slate-700">{farmer.email}</td>
                    <td className="py-3 pr-4 text-slate-700">
                      {farmer.phone ?? "—"}
                    </td>
                    <td className="py-3 pr-4">
                      <AdminUserStatusBadge status={farmer.status} />
                    </td>
                    <td className="py-3 pr-4 text-slate-600">
                      {formatDateTime(farmer.createdAt)}
                    </td>
                    <td className="py-3">
                      <div className="flex flex-wrap gap-2">
                        {actions.map((action) => (
                          <button
                            key={action.status}
                            type="button"
                            onClick={() => openDialog(action.status, farmer)}
                            disabled={updateStatus.isPending}
                            className={`rounded-md border border-slate-300 bg-white px-2.5 py-1 text-xs font-medium text-slate-700 hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-50 ${PRESSABLE} ${FOCUS_RING}`}
                          >
                            {action.label}
                          </button>
                        ))}
                      </div>
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

      <AnimatePresence mode="wait">
        {pending && (
          <ReasonDialog
            key={`${pending.farmer.id}-${pending.status}`}
            heading={`Set ${pending.farmer.fullName ?? pending.farmer.email} to ${formatUserStatus(
              pending.status,
            ).toLowerCase()}?`}
            description={
              pending.farmer.fullName
                ? `${pending.farmer.fullName} is currently ${pending.farmer.status.toLowerCase()}. Changing status also signs this account out everywhere.`
                : `Changing status also signs this account out everywhere.`
            }
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
