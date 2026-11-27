import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useRaiseDispute, useCustomerDispute } from "@/features/dispute/queries";
import { DISPUTE_REASON_CODES, REASON_CODE_LABELS } from "@/features/dispute/types";
import { formatMoney } from "@/features/vendor/format";
import { FOCUS_RING, PRESSABLE } from "@/motion/pressable";
import { toApiError, type ApiErrorInfo } from "@/shared/lib/api-error";
import { PageHeading } from "@/features/vendor/components/StatCard";

const raiseDisputeSchema = z.object({
  reasonCode: z.enum(DISPUTE_REASON_CODES, {
    errorMap: () => ({ message: "Select a reason" }),
  }),
  description: z.string().min(1, "Description is required").max(2000, "Max 2000 characters"),
});

type RaiseDisputeForm = z.infer<typeof raiseDisputeSchema>;

export function RaiseDisputePage() {
  const { orderId } = useParams();
  const id = orderId ? Number(orderId) : NaN;
  const [error, setError] = useState<ApiErrorInfo | null>(null);
  const [success, setSuccess] = useState(false);

  const raiseDispute = useRaiseDispute();

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
    reset,
  } = useForm<RaiseDisputeForm>({
    resolver: zodResolver(raiseDisputeSchema),
    defaultValues: { reasonCode: "OTHER" as const, description: "" },
  });

  const onSubmit = async (data: RaiseDisputeForm) => {
    setError(null);
    try {
      await raiseDispute.mutateAsync({
        orderId: id,
        payload: data,
      });
      setSuccess(true);
      reset();
    } catch (mutationError) {
      setError(toApiError(mutationError));
    }
  };

  if (success) {
    return (
      <div className="max-w-2xl mx-auto px-4 py-16 text-center">
        <PageHeading title="Dispute submitted" description="Your dispute has been filed." />
        <p className="mt-4 text-sm text-bolder-muted">
          The vendor will be notified and may respond. An administrator will review
          the dispute and determine the outcome.
        </p>
        <Link
          to={`/orders/${id}`}
          className={`mt-6 inline-flex items-center rounded-md border border-transparent bg-bolder-rose-action px-4 py-2 text-sm font-medium text-white hover:bg-bolder-rose-deep ${PRESSABLE} ${FOCUS_RING}`}
        >
          Back to order
        </Link>
      </div>
    );
  }

  return (
    <div className="max-w-2xl mx-auto px-4 py-8">
      <PageHeading title="Report a problem" description={`Order #${id}`} />

      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4 rounded-lg border bolder-line bg-bolder-surface p-6 shadow-sm">
        <div>
          <label htmlFor="reasonCode" className="block text-sm font-medium text-bolder-text">
            Reason
          </label>
          <select
            id="reasonCode"
            {...register("reasonCode")}
            className={`mt-1 block w-full rounded-md border-bolder-line-control text-sm shadow-sm ${FOCUS_RING}`}
          >
            {DISPUTE_REASON_CODES.map((code) => (
              <option key={code} value={code}>
                {REASON_CODE_LABELS[code]}
              </option>
            ))}
          </select>
          {errors.reasonCode && (
            <p className="mt-1 text-xs text-bolder-danger">{errors.reasonCode.message}</p>
          )}
        </div>

        <div>
          <label htmlFor="description" className="block text-sm font-medium text-bolder-text">
            Description
          </label>
          <textarea
            id="description"
            {...register("description")}
            rows={4}
            className={`mt-1 block w-full rounded-md border-bolder-line-control text-sm shadow-sm ${FOCUS_RING}`}
            placeholder="Describe the issue in detail..."
          />
          {errors.description && (
            <p className="mt-1 text-xs text-bolder-danger">{errors.description.message}</p>
          )}
        </div>

        {error && (
          <p className="text-sm text-bolder-danger bg-bolder-danger-surface rounded-lg p-2 border bolder-danger-border">
            {error.message}
          </p>
        )}

        <div className="flex justify-end gap-3">
          <Link
            to={`/orders/${id}`}
            className={`rounded-md border border-bolder-line-control bg-bolder-surface px-4 py-2 text-sm font-medium text-bolder-text hover:bg-bolder-bg ${PRESSABLE} ${FOCUS_RING}`}
          >
            Cancel
          </Link>
          <button
            type="submit"
            disabled={isSubmitting || raiseDispute.isPending}
            className={`rounded-md border border-transparent bg-bolder-rose-action px-4 py-2 text-sm font-medium text-white hover:bg-bolder-rose-deep disabled:opacity-50 disabled:cursor-not-allowed ${PRESSABLE} ${FOCUS_RING}`}
          >
            {isSubmitting || raiseDispute.isPending ? "Submitting…" : "Submit dispute"}
          </button>
        </div>
      </form>
    </div>
  );
}

export function DisputeDetailPage() {
  const { disputeId } = useParams();
  const id = disputeId ? Number(disputeId) : NaN;
  const { data: dispute, isPending, error } = useCustomerDispute(id);

  if (isPending) {
    return (
        <div className="rounded-lg border bolder-line bg-bolder-surface p-6 text-sm text-bolder-muted">
          Loading dispute…
        </div>
    );
  }

  if (error || !dispute) {
    return (
      <div className="max-w-4xl mx-auto px-4 py-16 text-center">
        <p className="text-sm text-bolder-danger">Failed to load dispute.</p>
        <Link to="/orders" className={`mt-4 inline-flex items-center rounded-md border border-transparent bg-bolder-rose-action px-4 py-2 text-sm font-medium text-white hover:bg-bolder-rose-deep ${PRESSABLE} ${FOCUS_RING}`}>
          Back to orders
        </Link>
      </div>
    );
  }

  return (
    <div className="space-y-6 max-w-4xl mx-auto px-4 py-8">
      <PageHeading title={`Dispute #${dispute.id}`} description={`Order #${dispute.orderId}`} />

      <div className="rounded-lg border bolder-line bg-bolder-surface p-5 shadow-sm space-y-3">
        <div className="flex items-center gap-2">
          <span className="text-xs font-medium text-bolder-muted">Status:</span>
          <span className="inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium border border-bolder-neutral-border bg-bolder-neutral-surface text-bolder-neutral">
            {dispute.status.replace(/_/g, " ")}
          </span>
        </div>
        <div>
          <p className="text-xs text-bolder-muted">Reason</p>
          <p className="text-sm text-bolder-text">{dispute.reasonCode.replace(/_/g, " ")}</p>
        </div>
        <div>
          <p className="text-xs text-bolder-muted">Description</p>
          <p className="text-sm text-bolder-text">{dispute.description}</p>
        </div>
        {dispute.vendorResponse && (
          <div>
            <p className="text-xs text-bolder-muted">Vendor response</p>
            <p className="text-sm text-bolder-text">{dispute.vendorResponse}</p>
          </div>
        )}
        {dispute.resolutionNote && (
          <div>
            <p className="text-xs text-bolder-muted">Resolution note</p>
            <p className="text-sm text-bolder-text">{dispute.resolutionNote}</p>
          </div>
        )}
        {dispute.refundAmount != null && (
          <div>
            <p className="text-xs text-bolder-muted">Refund amount</p>
            <p className="text-sm font-medium text-bolder-text">{formatMoney(dispute.refundAmount)}</p>
          </div>
        )}
        {dispute.resolvedAt && (
          <div>
            <p className="text-xs text-bolder-muted">Resolved at</p>
            <p className="text-sm text-bolder-text">{new Date(dispute.resolvedAt).toLocaleString()}</p>
          </div>
        )}
      </div>

      <div className="mt-6 flex justify-end">
        <Link
          to="/orders"
          className={`rounded-md border border-bolder-line-control bg-bolder-surface px-4 py-2 text-sm font-medium text-bolder-text hover:bg-bolder-bg ${PRESSABLE} ${FOCUS_RING}`}
        >
          Back to orders
        </Link>
      </div>
    </div>
  );
}

export function DisputeEligibilityBanner({
  orderId,
  isDelivered,
  hasDispute,
  windowOpen,
}: {
  orderId: number;
  isDelivered: boolean;
  hasDispute: boolean;
  windowOpen: boolean;
}) {
  if (!isDelivered) return null;
  if (hasDispute) {
    return (
      <div className="rounded-lg border bolder-neutral-border bg-bolder-neutral-surface p-4 text-sm text-bolder-neutral">
        A dispute has already been filed for this order.
      </div>
    );
  }
  if (!windowOpen) {
    return (
      <div className="rounded-lg border bolder-neutral-border bg-bolder-neutral-surface p-4 text-sm text-bolder-neutral">
        The dispute window has closed for this order.
      </div>
    );
  }
  return (
    <Link
      to={`/orders/${orderId}/dispute`}
      className={`inline-flex items-center rounded-md border border-transparent bg-bolder-warning-surface px-4 py-2 text-sm font-medium text-bolder-warning hover:bg-bolder-warning-surface ${PRESSABLE} ${FOCUS_RING}`}
    >
      Report a problem
    </Link>
  );
}