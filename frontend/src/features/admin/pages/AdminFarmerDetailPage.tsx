import { useParams } from "react-router-dom";
import { useState } from "react";
import { useAdminFarmerDetail, useUpdateFarmerStatus } from "@/features/admin/queries";
import type { FarmerStatus } from "@/features/admin/types";
import { formatDateTime, formatUserStatus } from "@/features/admin/format";
import { PageHeading } from "@/features/vendor/components/StatCard";
import { SuccessMessage } from "@/features/vendor/components/FormFields";
import { AdminErrorState } from "@/features/admin/components/AdminErrorState";
import { AdminUserStatusBadge } from "@/features/admin/components/AdminStatusBadge";
import { ReasonDialog } from "@/features/admin/components/ReasonDialog";
import { AnimatePresence } from "framer-motion";
import { FOCUS_RING, PRESSABLE } from "@/motion/pressable";
import { toApiError, type ApiErrorInfo } from "@/shared/lib/api-error";
import { formatMoney } from "@/features/vendor/format";
import type { PassbookEntry } from "@/features/farmer/types";

const ENTRY_TYPE_LABELS: Record<PassbookEntry["type"], string> = {
  DEPOSIT: "Flower deposit",
  COLLECTION: "Money collection",
};

/**
 * Admin farmer detail (Admin Farmer Management).
 *
 * Read-only: the profile, linked florists and each vendor's ledger overview are
 * reported exactly as the backend computes them. No balance adjustment, ledger
 * editing or transaction mutation is offered here — those remain with the farmer
 * and vendor portals. Authorization is server-side; the route guard is UX only.
 */
export function AdminFarmerDetailPage() {
  const { farmerId } = useParams<{ farmerId: string }>();
  const id = farmerId ? Number(farmerId) : NaN;

  const { data, isPending, error, refetch } = useAdminFarmerDetail(
    Number.isFinite(id) ? id : null,
  );
  const updateStatus = useUpdateFarmerStatus();

  const [pending, setPending] = useState<{
    status: FarmerStatus;
    reason: string;
  } | null>(null);
  const [actionError, setActionError] = useState<ApiErrorInfo | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  if (isPending) {
    return (
      <div className="rounded-lg border border-slate-200 bg-white p-6 text-sm text-slate-600">
        Loading farmer…
      </div>
    );
  }

  if (error) {
    return <AdminErrorState error={error} onRetry={() => void refetch()} />;
  }

  if (!data) {
    return (
      <div className="rounded-lg border border-slate-200 bg-white p-6 text-sm text-slate-600">
        Farmer not found.
      </div>
    );
  }

  const actions =
    data.status === "ACTIVE"
      ? [{ status: "SUSPENDED" as FarmerStatus, label: "Suspend" }]
      : [{ status: "ACTIVE" as FarmerStatus, label: "Reactivate" }];

  const openDialog = (status: FarmerStatus) => {
    setActionError(null);
    setSuccess(null);
    setPending({ status, reason: "" });
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
        farmerId: data.id,
        status: pending.status,
        reason: reason ?? "",
      });
      setSuccess(
        `${data.fullName ?? data.email} is now ${formatUserStatus(
          pending.status,
        ).toLowerCase()}.`,
      );
      setPending(null);
    } catch (mutationError) {
      setActionError(toApiError(mutationError));
    }
  };

  return (
    <div className="space-y-6">
      <PageHeading
        title="Farmer account"
        description={
          data.fullName
            ? `Profile, linked florists and a vendor-separated read-only ledger overview for ${data.fullName}.`
            : "Profile, linked florists and a vendor-separated read-only ledger overview."
        }
      />

      <SuccessMessage message={success} />

      <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
        <div className="flex flex-wrap items-start gap-4 md:flex-nowrap md:items-center">
          <div>
            <h2 className="text-lg font-bold text-slate-900">
              {data.fullName ?? "Unnamed farmer"}
            </h2>
            <p className="mt-0.5 text-sm text-slate-500">{data.email}</p>
            {data.phone && (
              <p className="mt-0.5 text-sm text-slate-500">{data.phone}</p>
            )}
            {data.address && (
              <p className="mt-0.5 text-sm text-slate-500">{data.address}</p>
            )}
          </div>

          <div className="ml-auto flex flex-col gap-2 sm:ml-0 sm:flex-row">
            <span className="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold">
              <AdminUserStatusBadge status={data.status} />
            </span>
            {actions.map((action) => (
              <button
                key={action.status}
                type="button"
                onClick={() => openDialog(action.status)}
                disabled={updateStatus.isPending}
                className={`rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-50 ${PRESSABLE} ${FOCUS_RING}`}
              >
                {action.label}
              </button>
            ))}
          </div>
        </div>

        <dl className="mt-4 grid grid-cols-1 gap-x-4 gap-y-2 sm:grid-cols-2 sm:gap-y-4 sm:gap-x-8">
          <div className="sm:col-span-2">
            <dt className="text-xs uppercase tracking-wide text-slate-500">
              Account status
            </dt>
            <dd className="mt-0.5 text-sm font-medium text-slate-900">
              {formatUserStatus(data.status)}
            </dd>
          </div>
          <div className="sm:col-span-2">
            <dt className="text-xs uppercase tracking-wide text-slate-500">
              Registered
            </dt>
            <dd className="mt-0.5 text-sm text-slate-600">
              {formatDateTime(data.createdAt)}
            </dd>
          </div>
        </dl>
      </div>

      {(data.linkedVendors?.length ?? 0) > 0 && (
        <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
          <h3 className="text-base font-semibold text-slate-900 mb-4">
            Ledger by florist
          </h3>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {data.linkedVendors.map((vendor) => (
              <div
                key={vendor.vendorId}
                className="rounded-lg border border-slate-200 bg-white p-4"
              >
                <div className="flex items-start justify-between mb-2">
                  <div>
                    <h4 className="font-medium text-slate-900">
                      {vendor.vendorName}
                    </h4>
                    <p className="mt-0.5 text-sm text-slate-500">
                      Linked {formatDateTime(vendor.linkCreatedAt)}
                    </p>
                  </div>
                  <span
                    className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold ${
                      vendor.linkStatus === "ACTIVE"
                        ? "bg-brand-100 text-brand-900"
                        : vendor.linkStatus === "SUSPENDED"
                          ? "bg-amber-100 text-amber-800"
                          : "bg-slate-200 text-slate-800"
                    }`}
                  >
                    {vendor.linkStatus}
                  </span>
                </div>

                <div className="mt-3 mb-3">
                  <dt className="text-xs uppercase tracking-wide text-slate-500">
                    Outstanding balance
                  </dt>
                  <dd className="mt-0.5 text-sm font-medium text-slate-900">
                    {formatMoney(Number(vendor.outstandingBalance ?? 0))}
                  </dd>
                </div>

                <table className="w-full text-left text-xs">
                  <thead>
                    <tr>
                      <th className="pb-2 pr-2 font-medium text-slate-500">Date</th>
                      <th className="pb-2 pr-2 font-medium text-slate-500">Type</th>
                      <th className="pb-2 pr-2 font-medium text-slate-500 text-right">
                        Amount
                      </th>
                      <th className="pb-2 font-medium text-slate-500 text-right">
                        Running balance
                      </th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-50">
                    {(vendor.entries ?? []).map((entry) => (
                      <tr key={entry.id} className="bg-white">
                        <td className="py-1.5 pr-2 text-slate-600">
                          {formatDateTime(entry.date)}
                        </td>
                        <td className="py-1.5 pr-2 text-slate-700">
                          {ENTRY_TYPE_LABELS[entry.type]}
                        </td>
                        <td className="py-1.5 pr-2 text-right text-slate-700">
                          {formatMoney(Number(entry.amount ?? 0))}
                        </td>
                        <td className="py-1.5 text-right text-slate-700">
                          {formatMoney(Number(entry.runningBalance ?? 0))}
                        </td>
                      </tr>
                    ))}
                    {(vendor.entries?.length ?? 0) === 0 && (
                      <tr>
                        <td colSpan={4} className="py-3 text-center text-slate-500">
                          No entries yet.
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
            ))}
          </div>
        </div>
      )}

      {(data.linkedVendors?.length ?? 0) === 0 && (
        <div className="rounded-lg border border-slate-200 bg-white p-5 text-sm text-slate-600">
          This farmer has no linked florists yet, so there is no ledger to show.
        </div>
      )}

      <AnimatePresence mode="wait">
        {pending && (
          <ReasonDialog
            key={data.id}
            heading={`Set ${data.fullName ?? data.email} to ${formatUserStatus(
              pending.status,
            ).toLowerCase()}?`}
            description={
              `${data.fullName ?? data.email} is currently ${data.status.toLowerCase()}. Changing status also signs this account out everywhere.`
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
