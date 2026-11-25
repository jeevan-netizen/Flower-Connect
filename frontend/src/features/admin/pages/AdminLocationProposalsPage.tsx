import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { motion, AnimatePresence } from "framer-motion";
import { FadeIn } from "@/motion/FadeIn";
import { FIELD_TRANSITION, FOCUS_RING, PRESSABLE } from "@/motion/pressable";
import { PageHeading } from "@/features/vendor/components/StatCard";
import { SuccessMessage } from "@/features/vendor/components/FormFields";
import { FormErrorSummary } from "@/features/vendor/components/FormFields";
import { AdminErrorState } from "@/features/admin/components/AdminErrorState";
import { ReasonDialog } from "@/features/admin/components/ReasonDialog";
import {
  useServiceLocationProposals,
  useApproveServiceLocationProposal,
  useRejectServiceLocationProposal,
} from "@/features/admin/queries";
import type { ServiceLocationProposal } from "@/features/admin/types";
import { toApiError, type ApiErrorInfo } from "@/shared/lib/api-error";

const approveSchema = z.object({
  latitude: z
    .string()
    .trim()
    .refine((val) => !isNaN(Number(val)) && Number(val) >= -90 && Number(val) <= 90, {
      message: "Latitude must be between -90 and 90",
    }),
  longitude: z
    .string()
    .trim()
    .refine((val) => !isNaN(Number(val)) && Number(val) >= -180 && Number(val) <= 180, {
      message: "Longitude must be between -180 and 180",
    }),
  timezone: z.string().min(1, "Timezone is required"),
});

interface ProposalAction {
  action: "approve" | "reject";
  proposal: ServiceLocationProposal;
}

/**
 * Admin review screen for service-location proposals (Phase 14 vendor
 * registration).
 *
 * Lists every PENDING proposal with the proposing vendor's email, the submitted
 * area and the submitted date. Approve requires administrator-supplied
 * coordinates and timezone (data the registration form cannot provide); reject
 * requires a reason. Both invalidate the proposal listing on success so the
 * resolved proposal drops out of view immediately.
 *
 * The table wraps in `overflow-x-auto` so it stays usable on a phone-sized
 * screen; on wider viewports it renders as a full-width table.
 */
export function AdminLocationProposalsPage() {
  const { data, isPending, error, refetch } = useServiceLocationProposals();
  const approveProposal = useApproveServiceLocationProposal();
  const rejectProposal = useRejectServiceLocationProposal();

  const [pending, setPending] = useState<ProposalAction | null>(null);
  const [actionError, setActionError] = useState<ApiErrorInfo | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const openDialog = (action: "approve" | "reject", proposal: ServiceLocationProposal) => {
    setActionError(null);
    setSuccess(null);
    setPending({ action, proposal });
  };

  const closeDialog = () => {
    setPending(null);
    setActionError(null);
  };

  const confirmApprove = async (values: { latitude: number; longitude: number; timezone: string }) => {
    if (!pending || pending.action !== "approve") {
      return;
    }
    setActionError(null);

    try {
      await approveProposal.mutateAsync({
        proposalId: pending.proposal.id,
        latitude: values.latitude,
        longitude: values.longitude,
        timezone: values.timezone,
      });
      setSuccess(`Approved ${pending.proposal.area}, ${pending.proposal.city}.`);
      setPending(null);
    } catch (mutationError) {
      setActionError(toApiError(mutationError));
    }
  };

  const confirmReject = async (reason: string | undefined) => {
    if (!pending || pending.action !== "reject") {
      return;
    }
    setActionError(null);

    try {
      await rejectProposal.mutateAsync({
        proposalId: pending.proposal.id,
        reason: reason ?? "",
      });
      setSuccess(`Rejected ${pending.proposal.area}, ${pending.proposal.city}.`);
      setPending(null);
    } catch (mutationError) {
      setActionError(toApiError(mutationError));
    }
  };

  const proposals = data ?? [];

  return (
    <div className="space-y-6">
      <PageHeading
        title="Service area proposals"
        description="Review and approve or reject new service areas proposed by vendors during registration."
      />

      <SuccessMessage message={success} />

      {isPending && (
        <div className="rounded-lg border border-slate-200 bg-white p-5 text-sm text-slate-600">
          Loading proposals...
        </div>
      )}

      {!isPending && error && (
        <AdminErrorState error={error} onRetry={() => void refetch()} />
      )}

      {!isPending && !error && proposals.length === 0 && (
        <div className="rounded-lg border border-slate-200 bg-white p-5 text-sm text-slate-600">
          No pending proposals.
        </div>
      )}

      {!isPending && !error && proposals.length > 0 && (
        <FadeIn className="overflow-x-auto rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
          <table className="w-full text-left text-sm">
            <caption className="sr-only">Pending service-area proposals awaiting admin review</caption>
            <thead className="text-xs uppercase tracking-wide text-slate-500">
              <tr>
                <th scope="col" className="py-3 pr-4 font-medium">Area</th>
                <th scope="col" className="py-3 pr-4 font-medium">Proposing vendor</th>
                <th scope="col" className="py-3 pr-4 font-medium">Submitted</th>
                <th scope="col" className="py-3 font-medium">Action</th>
              </tr>
            </thead>
            <tbody>
              {proposals.map((proposal) => (
                <tr
                  key={proposal.id}
                  className="border-t border-slate-100 align-top"
                >
                  <td className="py-3 pr-4">
                    <p className="font-medium text-slate-900">
                      {proposal.area}, {proposal.city}
                    </p>
                    <p className="text-xs text-slate-500">{proposal.pincode}</p>
                  </td>
                  <td className="py-3 pr-4 text-slate-700">
                    {proposal.proposedByEmail ?? "unknown"}
                  </td>
                  <td className="py-3 pr-4 text-slate-600">
                    {proposal.createdAt}
                  </td>
                  <td className="py-3">
                    <div className="flex flex-wrap gap-2">
                      <button
                        type="button"
                        onClick={() => openDialog("approve", proposal)}
                        disabled={
                          approveProposal.isPending || rejectProposal.isPending
                        }
                        className={`rounded-md border border-brand-600 bg-brand-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-brand-700 disabled:opacity-50 disabled:cursor-not-allowed ${PRESSABLE} ${FOCUS_RING}`}
                      >
                        Approve
                      </button>
                      <button
                        type="button"
                        onClick={() => openDialog("reject", proposal)}
                        disabled={
                          approveProposal.isPending || rejectProposal.isPending
                        }
                        className={`rounded-md border border-slate-300 bg-white px-3 py-1.5 text-xs font-medium text-slate-700 hover:bg-slate-100 disabled:opacity-50 disabled:cursor-not-allowed ${PRESSABLE} ${FOCUS_RING}`}
                      >
                        Reject
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </FadeIn>
      )}

      <AnimatePresence mode="wait">
        {pending && pending.action === "approve" && (
          <ProposalApproveDialog
            proposal={pending.proposal}
            isSubmitting={approveProposal.isPending}
            error={actionError}
            onConfirm={confirmApprove}
            onCancel={closeDialog}
          />
        )}
        {pending && pending.action === "reject" && (
          <ReasonDialog
            key={`reject-${pending.proposal.id}`}
            heading="Reject this service-area proposal?"
            description={`${pending.proposal.area}, ${pending.proposal.city} proposed by ${pending.proposal.proposedByEmail}.`}
            confirmLabel="Reject"
            requiresReason
            isSubmitting={rejectProposal.isPending}
            error={actionError}
            onConfirm={confirmReject}
            onCancel={closeDialog}
          />
        )}
      </AnimatePresence>
    </div>
  );
}

/**
 * Approve dialog for a service-location proposal. Collects the administrator's
 * latitude, longitude and timezone — the only fields the backend needs to
 * create the real service location row and that the registration form cannot
 * supply.
 */
function ProposalApproveDialog({
  proposal,
  isSubmitting,
  error,
  onConfirm,
  onCancel,
}: {
  proposal: ServiceLocationProposal;
  isSubmitting: boolean;
  error: ApiErrorInfo | null;
  onConfirm: (values: { latitude: number; longitude: number; timezone: string }) => void;
  onCancel: () => void;
}) {
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<z.infer<typeof approveSchema>>({
    resolver: zodResolver(approveSchema),
    defaultValues: { latitude: "", longitude: "", timezone: "Asia/Kolkata" },
  });

  const submit = handleSubmit((values) => {
    onConfirm({
      latitude: Number(values.latitude),
      longitude: Number(values.longitude),
      timezone: values.timezone,
    });
  });

  return (
    <motion.div
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4"
      initial="hidden"
      animate="visible"
      exit="exit"
    >
      <motion.div
        role="dialog"
        aria-modal="true"
        className="w-full max-w-md rounded-lg border border-slate-200 bg-white p-5 shadow-lg"
        initial="hidden"
        animate="visible"
        exit="exit"
      >
        <h2 className="text-base font-semibold text-slate-900">
          Approve service-area proposal
        </h2>
        <p className="mt-1 text-sm text-slate-600">
          {proposal.area}, {proposal.city} — proposed by {proposal.proposedByEmail}.
          Supply the coordinates and timezone for this area.
        </p>

        <form className="mt-4 space-y-4" onSubmit={submit} noValidate>
          <FormErrorSummary
            message={
              error && Object.keys(error.validation).length === 0
                ? error.message
                : error
                  ? "Some fields need attention."
                  : null
            }
          />

          <div className="space-y-2">
            <label className="block text-sm font-medium text-slate-700">
              Latitude
            </label>
            <input
              type="number"
              step="any"
              {...register("latitude")}
              className={`mt-1 block w-full rounded-md border-slate-300 shadow-sm sm:text-sm ${FIELD_TRANSITION} focus:border-brand-500 focus:ring-brand-500 ${
                errors.latitude ? "border-red-500" : ""
              }`}
            />
            {errors.latitude && (
              <p className="text-xs text-red-600">{errors.latitude.message}</p>
            )}
          </div>

          <div className="space-y-2">
            <label className="block text-sm font-medium text-slate-700">
              Longitude
            </label>
            <input
              type="number"
              step="any"
              {...register("longitude")}
              className={`mt-1 block w-full rounded-md border-slate-300 shadow-sm sm:text-sm ${FIELD_TRANSITION} focus:border-brand-500 focus:ring-brand-500 ${
                errors.longitude ? "border-red-500" : ""
              }`}
            />
            {errors.longitude && (
              <p className="text-xs text-red-600">{errors.longitude.message}</p>
            )}
          </div>

          <div className="space-y-2">
            <label className="block text-sm font-medium text-slate-700">
              Timezone
            </label>
            <input
              type="text"
              {...register("timezone")}
              className={`mt-1 block w-full rounded-md border-slate-300 shadow-sm sm:text-sm ${FIELD_TRANSITION} focus:border-brand-500 focus:ring-brand-500 ${
                errors.timezone ? "border-red-500" : ""
              }`}
            />
            {errors.timezone && (
              <p className="text-xs text-red-600">{errors.timezone.message}</p>
            )}
          </div>

          <div className="flex justify-end gap-2">
            <button
              type="button"
              onClick={onCancel}
              disabled={isSubmitting}
              className={`rounded-md border border-slate-300 bg-white px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-50 ${PRESSABLE} ${FOCUS_RING}`}
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={isSubmitting}
              className={`rounded-md border border-brand-600 bg-brand-600 px-3 py-2 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-50 disabled:cursor-not-allowed ${PRESSABLE} ${FOCUS_RING}`}
            >
              {isSubmitting ? "Approving…" : "Approve"}
            </button>
          </div>
        </form>
      </motion.div>
    </motion.div>
  );
}
