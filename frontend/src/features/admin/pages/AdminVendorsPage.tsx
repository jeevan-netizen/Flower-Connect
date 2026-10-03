import { useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import type { VendorProfile, VendorStatus } from "@/features/vendor/types";
import { VENDOR_STATUSES } from "@/features/vendor/types";
import { useAdminVendors, useVendorAdminAction } from "@/features/admin/queries";
import {
  parseVendorStatusParam,
  vendorActionRequiresReason,
  vendorActionsFor,
  type VendorAdminAction,
  type VendorAdminMutation,
  type VendorListFilters,
} from "@/features/admin/types";
import {
  formatDateTime,
  VENDOR_ACTION_HEADINGS,
  VENDOR_ACTION_LABELS,
  VENDOR_ACTION_PAST_TENSE,
} from "@/features/admin/format";
import { PageHeading } from "@/features/vendor/components/StatCard";
import { SuccessMessage } from "@/features/vendor/components/FormFields";
import { AdminErrorState } from "@/features/admin/components/AdminErrorState";
import { AdminVendorStatusBadge } from "@/features/admin/components/AdminStatusBadge";
import { Pagination } from "@/features/admin/components/Pagination";
import { ReasonDialog } from "@/features/admin/components/ReasonDialog";
import { AnimatePresence } from "framer-motion";
import { FadeIn } from "@/motion/FadeIn";
import { FIELD_TRANSITION, FOCUS_RING, PRESSABLE } from "@/motion/pressable";
import { toApiError, type ApiErrorInfo } from "@/shared/lib/api-error";

const STATUS_FILTER_OPTIONS: { value: VendorStatus | null; label: string }[] = [
  { value: null, label: "All statuses" },
  ...VENDOR_STATUSES.map((status) => ({ value: status, label: status.replace(/_/g, " ") })),
];

/** The action currently awaiting confirmation, and the profile it targets. */
interface PendingAction {
  action: VendorAdminAction;
  vendor: VendorProfile;
}

/**
 * Admin vendor management (plan task 2.10, backend task 2.6).
 *
 * The four buttons offered per row are derived from `vendorActionsFor(status)`,
 * which mirrors `VendorAdminService.requireStatus` exactly — so the UI offers only
 * transitions the backend will accept, and a rejected action is a race with another
 * administrator rather than a button this screen chose to expose.
 *
 * Two behaviours worth stating explicitly:
 *
 *  - Every action goes through the shared `ReasonDialog`. `approve` and `reinstate`
 *    show no reason input because their routes take no request body; `reject` and
 *    `suspend` collect one because `@NotBlank` requires it.
 *  - The dialog stays open on failure so the operator sees the backend's message
 *    (409 for a transition someone else already made, 404 for a deleted profile)
 *    and can retry, instead of the failure vanishing with the dialog.
 *
 * **The status filter is seeded from the query string.** The admin dashboard's
 * "View all N pending applications" link is a deep link into this page, so
 * `?status=PENDING_APPROVAL` has to actually select that filter. The URL is read
 * once on mount and again whenever it changes — the page stays mounted when only
 * the query string changes, so initialisation alone would miss a later
 * navigation. Choosing a filter in the select deliberately does *not* rewrite the
 * URL: the query string is an entry point, not the source of truth, and pushing
 * every click into the address bar would be a second, conflicting state store.
 */
export function AdminVendorsPage() {
  const [searchParams] = useSearchParams();
  const statusFromUrl = parseVendorStatusParam(searchParams.get("status"));

  const [filters, setFilters] = useState<VendorListFilters>({ status: statusFromUrl, page: 0 });
  const [pending, setPending] = useState<PendingAction | null>(null);
  const [actionError, setActionError] = useState<ApiErrorInfo | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  // Re-seed from the URL when it changes under a mounted page. `filters` is not
  // a dependency on purpose: this must react to the query string, not to the
  // operator picking a filter, which would otherwise immediately be undone.
  useEffect(() => {
    setFilters({ status: statusFromUrl, page: 0 });
  }, [statusFromUrl]);

  const { data, isPending, error, refetch } = useAdminVendors(filters);
  const runAction = useVendorAdminAction();

  /**
   * When the table replays its entry animation: the filter, the page, and the rows
   * themselves. A refetch that comes back with the same rows produces the same
   * key, so a background refetch, a window focus or an invalidation that changed
   * nothing never replays it — only a genuinely different result set does. Row
   * order is part of the key for the same reason.
   */
  const listingKey = data
    ? `${filters.status ?? "all"}:${data.page}:${data.content.map((vendor) => vendor.id).join(",")}`
    : "";

  const openDialog = (action: VendorAdminAction, vendor: VendorProfile) => {
    setActionError(null);
    setSuccess(null);
    setPending({ action, vendor });
  };

  const closeDialog = () => {
    setPending(null);
    setActionError(null);
  };

  const confirmAction = async (reason?: string) => {
    if (!pending) {
      return;
    }
    setActionError(null);

    // The reason-bearing branch is what makes a missing reason a compile error
    // rather than a 400: `vendorActionRequiresReason` narrows `action`, so the
    // object literal below is checked against the reason-bearing union member.
    const variables: VendorAdminMutation = vendorActionRequiresReason(pending.action)
      ? { action: pending.action, profileId: pending.vendor.id, reason: reason ?? "" }
      : { action: pending.action, profileId: pending.vendor.id };

    try {
      await runAction.mutateAsync(variables);
      setSuccess(
        `${VENDOR_ACTION_PAST_TENSE[pending.action]} ${pending.vendor.businessName}.`,
      );
      setPending(null);
    } catch (mutationError) {
      setActionError(toApiError(mutationError));
    }
  };

  return (
    <div className="space-y-6">
      <PageHeading
        title="Vendors"
        description="List vendor applications and move them between approval states."
      />

      <div className="flex flex-wrap items-end gap-3 rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
        <div>
          <label
            htmlFor="vendor-status-filter"
            className="block text-sm font-medium text-slate-700"
          >
            Approval status
          </label>
          <select
            id="vendor-status-filter"
            value={filters.status ?? ""}
            onChange={(event) => {
              const value = event.target.value;
              setFilters({
                status: value === "" ? null : (value as VendorStatus),
                // Changing the filter resets to the first page: the previous page
                // index belongs to a different result set.
                page: 0,
              });
            }}
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
          Loading vendors...
        </div>
      )}

      {!isPending && error && (
        <AdminErrorState error={error} onRetry={() => void refetch()} />
      )}

      {!isPending && !error && data && data.empty && (
        <div className="rounded-lg border border-slate-200 bg-white p-5 text-sm text-slate-600">
          {filters.status
            ? `No vendors have status ${filters.status.replace(/_/g, " ")}.`
            : "No vendor profiles exist yet."}
        </div>
      )}

      {!isPending && !error && data && !data.empty && (
        <FadeIn
          key={listingKey}
          className="overflow-x-auto rounded-lg border border-slate-200 bg-white p-5 shadow-sm"
        >
          <table className="w-full text-left text-sm">
            <caption className="sr-only">Vendor profiles and available admin actions</caption>
            <thead className="text-xs uppercase tracking-wide text-slate-500">
              <tr>
                <th scope="col" className="py-2 pr-4 font-medium">Business</th>
                <th scope="col" className="py-2 pr-4 font-medium">Owner</th>
                <th scope="col" className="py-2 pr-4 font-medium">Service area</th>
                <th scope="col" className="py-2 pr-4 font-medium">Status</th>
                <th scope="col" className="py-2 pr-4 font-medium">Applied</th>
                <th scope="col" className="py-2 font-medium">Actions</th>
              </tr>
            </thead>
            <tbody>
              {data.content.map((vendor) => {
                const actions = vendorActionsFor(vendor.status);

                return (
                  <tr key={vendor.id} className="border-t border-slate-100 align-top">
                    <td className="py-3 pr-4">
                      <p className="font-medium text-slate-900">{vendor.businessName}</p>
                      <p className="text-xs text-slate-500">{vendor.addressLine1}</p>
                    </td>
                    <td className="py-3 pr-4 text-slate-700">{vendor.ownerEmail}</td>
                    <td className="py-3 pr-4 text-slate-700">
                      {vendor.area}, {vendor.city} — {vendor.pincode}
                    </td>
                    <td className="py-3 pr-4">
                      <AdminVendorStatusBadge status={vendor.status} />
                    </td>
                    <td className="py-3 pr-4 text-slate-600">{formatDateTime(vendor.createdAt)}</td>
                    <td className="py-3">
                      {actions.length === 0 ? (
                        <span className="text-xs text-slate-500">
                          No further action available
                        </span>
                      ) : (
                        <div className="flex flex-wrap gap-2">
                          {actions.map((action) => (
                            <button
                              key={action}
                              type="button"
                              onClick={() => openDialog(action, vendor)}
                              // Every row's buttons are disabled while any action is
                              // in flight, so one submission cannot race another and
                              // a double click cannot fire the same transition twice.
                              disabled={runAction.isPending}
                              className={`rounded-md border border-slate-300 bg-white px-2.5 py-1 text-xs font-medium text-slate-700 hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-50 ${PRESSABLE} ${FOCUS_RING}`}
                            >
                              {VENDOR_ACTION_LABELS[action]}
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
              disabled={runAction.isPending}
              onPageChange={(page) => setFilters((current) => ({ ...current, page }))}
            />
          </div>
        </FadeIn>
      )}

      {/*
        `AnimatePresence` gives the dialog its exit animation when it closes.
        `mode="wait"` keeps one dialog on screen at a time, so switching the
        pending target cannot show two overlapping modals.
      */}
      <AnimatePresence mode="wait">
        {pending && (
          <ReasonDialog
            key={`${pending.vendor.id}-${pending.action}`}
            heading={VENDOR_ACTION_HEADINGS[pending.action]}
            description={`${pending.vendor.businessName} (${pending.vendor.ownerEmail}) is currently ${pending.vendor.status.replace(/_/g, " ").toLowerCase()}.`}
            confirmLabel={VENDOR_ACTION_LABELS[pending.action]}
            requiresReason={vendorActionRequiresReason(pending.action)}
            isSubmitting={runAction.isPending}
            error={actionError}
            onConfirm={(reason) => void confirmAction(reason)}
            onCancel={closeDialog}
          />
        )}
      </AnimatePresence>
    </div>
  );
}