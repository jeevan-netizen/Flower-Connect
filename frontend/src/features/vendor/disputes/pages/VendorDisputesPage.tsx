import { useState } from "react";
import { useVendorDisputes, useRespondToDispute } from "@/features/dispute/queries";
import { DISPUTE_STATUS_LABELS } from "@/features/dispute/types";
import { formatMoney } from "@/features/vendor/format";
import { PageHeading } from "@/features/vendor/components/StatCard";
import { AdminErrorState } from "@/features/admin/components/AdminErrorState";
import { Pagination } from "@/shared/components/Pagination";
import { FadeIn } from "@/motion/FadeIn";
import { toApiError, type ApiErrorInfo } from "@/shared/lib/api-error";
import { FOCUS_RING, PRESSABLE } from "@/motion/pressable";
import { z } from "zod";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";

const respondSchema = z.object({
  response: z.string().min(1, "Response is required").max(500, "Max 500 characters"),
});

type RespondForm = z.infer<typeof respondSchema>;

export function VendorDisputesPage() {
  const [page, setPage] = useState(0);
  const [respondTarget, setRespondTarget] = useState<number | null>(null);
  const [actionError, setActionError] = useState<ApiErrorInfo | null>(null);

  const { data, isPending, error } = useVendorDisputes(page);
  const respondToDispute = useRespondToDispute();

  const items = data?.content ?? [];

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
    reset,
  } = useForm<RespondForm>({
    resolver: zodResolver(respondSchema),
    defaultValues: { response: "" },
  });

  const onSubmitResponse = async (formData: RespondForm) => {
    if (respondTarget === null) return;
    setActionError(null);
    try {
      await respondToDispute.mutateAsync({
        disputeId: respondTarget,
        payload: { response: formData.response },
      });
      setRespondTarget(null);
      reset();
    } catch (mutationError) {
      setActionError(toApiError(mutationError));
    }
  };

  if (error) {
    return (
      <div className="max-w-5xl mx-auto px-4 py-16">
        <PageHeading title="Disputes" description="Disputes on your orders." />
        <AdminErrorState error={error} onRetry={() => setPage(0)} />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <PageHeading title="Disputes" description="Customer disputes on your orders." />

      {isPending && (
        <div className="rounded-lg border bolder-line bg-bolder-surface p-5 text-sm text-bolder-muted">
          Loading disputes...
        </div>
      )}

      {!isPending && data && data.empty && (
        <div className="rounded-lg border bolder-line bg-bolder-surface p-5 text-sm text-bolder-muted">
          <h3 className="text-base font-semibold text-bolder-text">No disputes</h3>
          <p className="mt-2 text-sm text-bolder-muted">
            No customers have raised disputes on your orders yet.
          </p>
        </div>
      )}

      {!isPending && data && !data.empty && (
        <FadeIn className="overflow-x-auto rounded-lg border bolder-line bg-bolder-surface p-5 shadow-sm">
          <table className="w-full text-left text-sm">
            <thead className="text-xs uppercase tracking-wide text-bolder-muted">
              <tr>
                <th scope="col" className="py-2 pr-4 font-medium">Dispute</th>
                <th scope="col" className="py-2 pr-4 font-medium">Order</th>
                <th scope="col" className="py-2 pr-4 font-medium">Status</th>
                <th scope="col" className="py-2 pr-4 font-medium">Reason</th>
                <th scope="col" className="py-2 pr-4 font-medium">Total</th>
                <th scope="col" className="py-2 font-medium">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-bolder-line">
              {items.map((item) => (
                <tr key={item.id} className="align-top">
                  <td className="py-3 pr-4 font-medium text-bolder-text">
                    #{item.id}
                  </td>
                  <td className="py-3 pr-4 text-bolder-text">Order #{item.orderId}</td>
                  <td className="py-3 pr-4">
                    <span className="inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium border border-bolder-line bg-bolder-bg text-bolder-text">
                      {DISPUTE_STATUS_LABELS[item.status]}
                    </span>
                  </td>
                  <td className="py-3 pr-4 text-bolder-text">
                    {item.reasonCode.replace(/_/g, " ")}
                  </td>
                  <td className="py-3 pr-4 text-right font-medium text-bolder-text">
                    {formatMoney(item.orderTotal)}
                  </td>
                  <td className="py-3">
                    {item.status === "OPEN" && (
                      <button
                        type="button"
                        disabled={respondToDispute.isPending}
                        onClick={() => {
                          setActionError(null);
                          setRespondTarget(item.id);
                        }}
                        className={`rounded-md border border-transparent bg-bolder-rose-action px-3 py-1.5 text-xs font-medium text-white hover:bg-bolder-rose-deep disabled:opacity-50 disabled:cursor-not-allowed ${PRESSABLE} ${FOCUS_RING}`}
                      >
                        Respond
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>

          <div className="mt-4">
            <Pagination
              page={data}
              disabled={respondToDispute.isPending}
              onPageChange={(p) => setPage(p)}
            />
          </div>
        </FadeIn>
      )}

      {respondTarget !== null && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-bolder-overlay">
          <div className="w-full max-w-md rounded-lg bg-bolder-surface p-6 shadow-lg">
            <h2 className="text-lg font-semibold text-bolder-text mb-4">
              Respond to dispute
            </h2>
            <form onSubmit={handleSubmit(onSubmitResponse)} className="space-y-4">
              <div>
                <label htmlFor="response" className="block text-sm font-medium text-bolder-text">
                  Response
                </label>
                <textarea
                  id="response"
                  {...register("response")}
                  rows={4}
                  className={`mt-1 block w-full rounded-md border-bolder-line-control text-sm shadow-sm ${FOCUS_RING}`}
                  placeholder="Your response to the customer's dispute..."
                />
                {errors.response && (
                  <p className="mt-1 text-xs text-bolder-danger">{errors.response.message}</p>
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
                  onClick={() => {
                    setRespondTarget(null);
                    reset();
                    setActionError(null);
                  }}
                  className={`rounded-md border bolder-line-control bg-bolder-surface px-4 py-2 text-sm font-medium text-bolder-text hover:bg-bolder-bg ${PRESSABLE} ${FOCUS_RING}`}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={isSubmitting || respondToDispute.isPending}
                  className={`rounded-md border border-transparent bg-bolder-rose-action px-4 py-2 text-sm font-medium text-white hover:bg-bolder-rose-deep disabled:opacity-50 disabled:cursor-not-allowed ${PRESSABLE} ${FOCUS_RING}`}
                >
                  {isSubmitting || respondToDispute.isPending ? "Submitting…" : "Submit response"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}