import { useCallback, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useAdminDisputes, useResolveDispute } from "@/features/dispute/queries";
import { disputeKeys } from "@/features/dispute/queries";
import { DISPUTE_STATUSES, DISPUTE_STATUS_LABELS } from "@/features/dispute/types";
import type { DisputeListItem, DisputeStatus } from "@/features/dispute/types";
import type { ResolutionStatus } from "@/features/dispute/types";
import { PageHeading } from "@/features/vendor/components/StatCard";
import { AdminErrorState } from "@/features/admin/components/AdminErrorState";
import { Pagination } from "@/shared/components/Pagination";
import { FadeIn } from "@/motion/FadeIn";
import { toApiError, type ApiErrorInfo } from "@/shared/lib/api-error";
import { FOCUS_RING, PRESSABLE } from "@/motion/pressable";
import { formatMoney } from "@/features/vendor/format";
import { z } from "zod";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";

const resolveSchema = z.object({
  status: z.enum(["RESOLVED_REFUND", "RESOLVED_PARTIAL", "RESOLVED_REJECTED"] as const),
  resolutionNote: z.string().min(1, "Resolution note is required").max(500, "Max 500 characters"),
  refundAmount: z.string().optional(),
});

type ResolveForm = z.infer<typeof resolveSchema>;

export function AdminDisputesPage() {
  const [filters, setFilters] = useState<{
    status: DisputeStatus | null;
    from: string | null;
    to: string | null;
    page: number;
  }>({
    status: null,
    from: null,
    to: null,
    page: 0,
  });
  const [resolveTarget, setResolveTarget] = useState<DisputeListItem | null>(null);
  const [actionError, setActionError] = useState<ApiErrorInfo | null>(null);

  const queryClient = useQueryClient();
  const { data, isPending, error } = useAdminDisputes(filters.status, filters.from, filters.to, filters.page);
  const resolveDispute = useResolveDispute();

  const {
    register,
    handleSubmit,
    watch,
    formState: { errors, isSubmitting },
    reset,
    setValue,
  } = useForm<ResolveForm>({
    resolver: zodResolver(resolveSchema),
    defaultValues: { status: "RESOLVED_REFUND", resolutionNote: "", refundAmount: "" },
  });

  const selectedStatus = watch("status");

  const refetch = useCallback(() => {
    void queryClient.invalidateQueries({ queryKey: disputeKeys.admin.all });
  }, [queryClient]);

  const openResolveDialog = (item: DisputeListItem) => {
    setActionError(null);
    setResolveTarget(item);
    reset({ status: "RESOLVED_REFUND", resolutionNote: "", refundAmount: String(item.orderTotal) });
  };

  const closeResolveDialog = () => {
    setResolveTarget(null);
    setActionError(null);
    reset();
  };

  const onSubmitResolve = async (formData: ResolveForm) => {
    if (!resolveTarget) return;
    setActionError(null);

    // Validate refund amount for refund outcomes
    let refundAmount: number | null = null;
    if (formData.status === "RESOLVED_REFUND" || formData.status === "RESOLVED_PARTIAL") {
      const parsed = parseFloat(formData.refundAmount ?? "");
      if (isNaN(parsed) || parsed <= 0) {
        setActionError({ status: 400, code: null, message: "Refund amount must be greater than zero", validation: {} });
        return;
      }
      if (parsed > resolveTarget.orderTotal) {
        setActionError({
          status: 400,
          code: null,
          message: `Refund amount cannot exceed order total (${resolveTarget.orderTotal})`,
          validation: {},
        });
        return;
      }
      refundAmount = Math.round(parsed * 100) / 100;
    }

    if (formData.status === "RESOLVED_REJECTED") {
      refundAmount = null;
    }

    try {
      await resolveDispute.mutateAsync({
        disputeId: resolveTarget.id,
        payload: {
          status: formData.status as ResolutionStatus,
          resolutionNote: formData.resolutionNote,
          refundAmount,
        },
      });
      setResolveTarget(null);
      reset();
    } catch (mutationError) {
      setActionError(toApiError(mutationError));
    }
  };

  const items = data?.content ?? [];

  if (error) {
    return (
      <div className="max-w-5xl mx-auto px-4 py-16">
        <PageHeading title="Disputes" description="Customer disputes awaiting resolution." />
        <AdminErrorState error={error} onRetry={() => void refetch()} />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <PageHeading title="Disputes" description="Customer disputes awaiting resolution." />

      <div className="flex flex-wrap items-end gap-3 rounded-lg border border-bolder-line bg-bolder-surface p-4 shadow-sm">
        <div className="flex-1 min-w-[200px]">
          <label htmlFor="dispute-status-filter" className="block text-sm font-medium text-bolder-text">
            Status
          </label>
          <select
            id="dispute-status-filter"
            value={filters.status ?? ""}
            onChange={(e) => {
              const v = e.target.value;
              setFilters({ ...filters, status: v === "" ? null : (v as DisputeStatus), page: 0 });
            }}
            className={`mt-1 block rounded-md border-bolder-line-control text-sm shadow-sm ${FOCUS_RING}`}
          >
            <option value="">All statuses</option>
            {DISPUTE_STATUSES.map((s) => (
              <option key={s} value={s}>
                {DISPUTE_STATUS_LABELS[s]}
              </option>
            ))}
          </select>
        </div>
        <div className="flex-1 min-w-[200px]">
          <label htmlFor="dispute-from" className="block text-sm font-medium text-bolder-text">
            From date
          </label>
          <input
            id="dispute-from"
            type="date"
            value={filters.from ?? ""}
            onChange={(e) => setFilters({ ...filters, from: e.target.value || null, page: 0 })}
            className={`mt-1 block rounded-md border-bolder-line-control text-sm shadow-sm ${FOCUS_RING}`}
          />
        </div>
        <div className="flex-1 min-w-[200px]">
          <label htmlFor="dispute-to" className="block text-sm font-medium text-bolder-text">
            To date
          </label>
          <input
            id="dispute-to"
            type="date"
            value={filters.to ?? ""}
            onChange={(e) => setFilters({ ...filters, to: e.target.value || null, page: 0 })}
            className={`mt-1 block rounded-md border-bolder-line-control text-sm shadow-sm ${FOCUS_RING}`}
          />
        </div>
      </div>

      {isPending && (
        <div className="rounded-lg border border-bolder-line bg-bolder-surface p-5 text-sm text-bolder-muted">
          Loading disputes...
        </div>
      )}

      {!isPending && data && data.empty && (
        <div className="rounded-lg border border-bolder-line bg-bolder-surface p-5 text-sm text-bolder-muted">
          <h3 className="text-base font-semibold text-bolder-text">No disputes</h3>
          <p className="mt-2 text-sm text-bolder-muted">
            No disputes match the current filters.
          </p>
        </div>
      )}

      {!isPending && data && !data.empty && (
        <FadeIn className="overflow-x-auto rounded-lg border border-bolder-line bg-bolder-surface p-5 shadow-sm">
          <table className="w-full text-left text-sm">
            <thead className="text-xs uppercase tracking-wide text-bolder-muted">
              <tr>
                <th scope="col" className="py-2 pr-4 font-medium">Dispute</th>
                <th scope="col" className="py-2 pr-4 font-medium">Order</th>
                <th scope="col" className="py-2 pr-4 font-medium">Customer</th>
                <th scope="col" className="py-2 pr-4 font-medium">Shop</th>
                <th scope="col" className="py-2 pr-4 font-medium">Status</th>
                <th scope="col" className="py-2 pr-4 font-medium">Total</th>
                <th scope="col" className="py-2 font-medium">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-bolder-line">
              {items.map((item) => (
                <tr key={item.id} className="align-top">
                  <td className="py-3 pr-4 font-medium text-bolder-text">#{item.id}</td>
                  <td className="py-3 pr-4 text-bolder-text">Order #{item.orderId}</td>
                  <td className="py-3 pr-4 text-bolder-text">{item.customerName}</td>
                  <td className="py-3 pr-4 text-bolder-text">{item.vendorName}</td>
                  <td className="py-3 pr-4">
                    <span className="inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium border border-bolder-line bg-bolder-bg text-bolder-text">
                      {DISPUTE_STATUS_LABELS[item.status]}
                    </span>
                  </td>
                  <td className="py-3 pr-4 text-right font-medium text-bolder-text">
                    {formatMoney(item.orderTotal)}
                  </td>
                  <td className="py-3">
                    {(item.status === "OPEN" || item.status === "UNDER_REVIEW") && (
                      <button
                        type="button"
                        disabled={resolveDispute.isPending}
                        onClick={() => openResolveDialog(item)}
                        className={`rounded-md border border-transparent bg-bolder-rose-action px-3 py-1.5 text-xs font-medium text-white hover:bg-bolder-rose-deep disabled:opacity-50 disabled:cursor-not-allowed ${PRESSABLE} ${FOCUS_RING}`}
                      >
                        Resolve
                      </button>
                    )}
                    {item.status.startsWith("RESOLVED_") && item.refundAmount != null && (
                      <span className="text-xs text-bolder-muted">
                        Refund: {formatMoney(item.refundAmount)}
                      </span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>

          <div className="mt-4">
            <Pagination
              page={data}
              disabled={resolveDispute.isPending}
              onPageChange={(p) => setFilters((c) => ({ ...c, page: p }))}
            />
          </div>
        </FadeIn>
      )}

      {resolveTarget && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-bolder-overlay">
          <div className="w-full max-w-md rounded-lg border border-bolder-line bg-bolder-surface p-6 shadow-lg">
            <h2 className="text-lg font-semibold text-bolder-text mb-4">
              Resolve dispute #{resolveTarget.id}
            </h2>
            <form onSubmit={handleSubmit(onSubmitResolve)} className="space-y-4">
              <div>
                <label htmlFor="resolve-status" className="block text-sm font-medium text-bolder-text">
                  Outcome
                </label>
                <select
                  id="resolve-status"
                  {...register("status")}
                  onChange={(e) => {
                    setValue("status", e.target.value as ResolutionStatus);
                    if (e.target.value === "RESOLVED_REJECTED") {
                      setValue("refundAmount", "");
                    } else if (e.target.value === "RESOLVED_REFUND") {
                      setValue("refundAmount", String(resolveTarget.orderTotal));
                    }
                  }}
                  className={`mt-1 block w-full rounded-md border-bolder-line-control text-sm shadow-sm ${FOCUS_RING}`}
                >
                  <option value="RESOLVED_REFUND">Full refund</option>
                  <option value="RESOLVED_PARTIAL">Partial refund</option>
                  <option value="RESOLVED_REJECTED">Rejected</option>
                </select>
              </div>

              {(selectedStatus === "RESOLVED_REFUND" || selectedStatus === "RESOLVED_PARTIAL") && (
                <div>
                  <label htmlFor="refundAmount" className="block text-sm font-medium text-bolder-text">
                    Refund amount (INR)
                  </label>
                  <input
                    id="refundAmount"
                    type="number"
                    step="0.01"
                    {...register("refundAmount")}
                    className={`mt-1 block w-full rounded-md border-bolder-line-control text-sm shadow-sm ${FOCUS_RING}`}
                  />
                  {errors.refundAmount && (
                    <p className="mt-1 text-xs text-bolder-danger">{errors.refundAmount.message}</p>
                  )}
                  <p className="mt-1 text-xs text-bolder-muted">
                    Order total: {formatMoney(resolveTarget.orderTotal)}. Max: {formatMoney(resolveTarget.orderTotal)}.
                  </p>
                </div>
              )}

              <div>
                <label htmlFor="resolutionNote" className="block text-sm font-medium text-bolder-text">
                  Resolution note
                </label>
                <textarea
                  id="resolutionNote"
                  {...register("resolutionNote")}
                  rows={3}
                  className={`mt-1 block w-full rounded-md border-bolder-line-control text-sm shadow-sm ${FOCUS_RING}`}
                  placeholder="Explain the resolution..."
                />
                {errors.resolutionNote && (
                  <p className="mt-1 text-xs text-bolder-danger">{errors.resolutionNote.message}</p>
                )}
              </div>

              {actionError && (
                <p className="text-sm text-bolder-danger bg-bolder-danger-surface rounded-lg p-2 border bolder-danger-border">
                  {actionError.message}
                </p>
              )}

              <div className="flex justify-end gap-3">
                <button
                  type="button"
                  onClick={closeResolveDialog}
                  className={`rounded-md border border-bolder-line-control bg-bolder-surface px-4 py-2 text-sm font-medium text-bolder-text hover:bg-bolder-bg ${PRESSABLE} ${FOCUS_RING}`}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={isSubmitting || resolveDispute.isPending}
                  className={`rounded-md border border-transparent bg-bolder-rose-action px-4 py-2 text-sm font-medium text-white hover:bg-bolder-rose-deep disabled:opacity-50 disabled:cursor-not-allowed ${PRESSABLE} ${FOCUS_RING}`}
                >
                  {isSubmitting || resolveDispute.isPending ? "Resolving…" : "Resolve dispute"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
