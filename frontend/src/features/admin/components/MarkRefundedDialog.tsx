/**
 * Mark-as-refunded dialog for the admin refund queue (plan Phase 8, task 8.3).
 *
 * Marking a refund complete requires a non-blank reference note of at most 500
 * characters. The dialog only enables confirm when the note is valid, sends it
 * to `POST /api/v1/admin/refunds/{orderId}/mark`, and shows the backend's
 * error on failure (409 when the payment is not `REFUND_PENDING`, 404 when the
 * order is unknown). The dialog stays open on failure so the operator can
 * correct the note or see the current state.
 */

import { useId } from "react";
import { motion } from "framer-motion";
import { TextField, FormErrorSummary, SubmitButton } from "@/features/vendor/components/FormFields";
import { adminReasonSchema, type AdminReasonValues } from "@/features/admin/form-schema";
import { ADMIN_REASON_MAX_LENGTH } from "@/features/admin/types";
import { backdropVariants, dialogVariants } from "@/motion/tokens";
import { usePrefersReducedMotion } from "@/motion/use-reduced-motion";
import { FOCUS_RING, PRESSABLE } from "@/motion/pressable";
import type { ApiErrorInfo } from "@/shared/lib/api-error";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";

interface MarkRefundedDialogProps {
  orderName: string;
  open: boolean;
  isSubmitting: boolean;
  error: ApiErrorInfo | null;
  onConfirm: (referenceNote: string) => void;
  onCancel: () => void;
}

export function MarkRefundedDialog({
  orderName,
  open,
  isSubmitting,
  error,
  onConfirm,
  onCancel,
}: MarkRefundedDialogProps) {
  const headingId = useId();
  const descriptionId = useId();
  const reduceMotion = usePrefersReducedMotion();
  const enterState = reduceMotion ? false : "hidden";
  const exitState = reduceMotion ? undefined : "exit";

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<AdminReasonValues>({
    resolver: zodResolver(adminReasonSchema),
    defaultValues: { reason: "" },
  });

  const submit = handleSubmit((values) => onConfirm(values.reason));

  return (
    <motion.div
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4"
      variants={backdropVariants}
      initial={enterState}
      animate={open ? "visible" : "hidden"}
      exit={exitState}
    >
      <motion.div
        role="dialog"
        aria-modal="true"
        aria-labelledby={headingId}
        aria-describedby={descriptionId}
        className="w-full max-w-md rounded-lg border border-slate-200 bg-white p-5 shadow-lg"
        variants={dialogVariants}
        initial={enterState}
        animate="visible"
        exit={exitState}
      >
        <h2 id={headingId} className="text-base font-semibold text-slate-900">
          Mark refund as complete
        </h2>
        <p id={descriptionId} className="mt-1 text-sm text-slate-600">
          Mark the refund for <span className="font-medium">{orderName}</span> as complete.
          A reference note (e.g. the gateway refund id) is required and is stored
          on the audit record.
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

          <TextField
            label="Reference note"
            hint={`Maximum ${ADMIN_REASON_MAX_LENGTH} characters. Stored on the audit record.`}
            multiline
            rows={3}
            error={errors.reason?.message ?? error?.validation.reason}
            registration={register("reason")}
          />

          <div className="flex justify-end gap-2">
            <button
              type="button"
              onClick={onCancel}
              disabled={isSubmitting}
              className={`rounded-md border border-slate-300 bg-white px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-50 ${PRESSABLE} ${FOCUS_RING}`}
            >
              Cancel
            </button>
            <SubmitButton
              isSubmitting={isSubmitting}
              idleLabel="Mark refunded"
              busyLabel="Working..."
            />
          </div>
        </form>
      </motion.div>
    </motion.div>
  );
}
