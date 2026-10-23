/**
 * Reject-reason dialog for the vendor order board (plan Phase 6, task 6.7).
 *
 * Rejecting an order requires a reason on the backend, so the dialog collects
 * one and only enables confirm when it is non-blank. The reason is sent to
 * `POST /api/v1/vendor/orders/{orderId}/reject` — the client never invents a
 * client-side status transition.
 */

import { useState } from "react";
import { motion } from "framer-motion";
import { backdropVariants, dialogVariants } from "@/motion/tokens";
import { usePrefersReducedMotion } from "@/motion/use-reduced-motion";
import { FIELD_TRANSITION, FOCUS_RING, PRESSABLE } from "@/motion/pressable";

interface RejectReasonDialogProps {
  orderName: string;
  open: boolean;
  isSubmitting: boolean;
  error: string | null;
  onConfirm: (reason: string) => void;
  onCancel: () => void;
}

export function RejectReasonDialog({
  orderName,
  open,
  isSubmitting,
  error,
  onConfirm,
  onCancel,
}: RejectReasonDialogProps) {
  const [reason, setReason] = useState("");
  const reduceMotion = usePrefersReducedMotion();
  const enterState = reduceMotion ? false : "hidden";
  const exitState = reduceMotion ? undefined : "exit";

  const canConfirm = reason.trim().length > 0;

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
        className="w-full max-w-md rounded-lg border border-slate-200 bg-white p-5 shadow-lg"
        variants={dialogVariants}
        initial={enterState}
        animate={open ? "visible" : "hidden"}
        exit={exitState}
      >
        <h2 className="text-base font-semibold text-slate-900">Reject order</h2>
        <p className="mt-1 text-sm text-slate-600">
          Why are you rejecting{" "}
          <span className="font-medium">{orderName}</span>
          ?
        </p>

        <form
          className="mt-4 space-y-4"
          noValidate
          onSubmit={(event: React.FormEvent) => {
            event.preventDefault();
            if (canConfirm) {
              onConfirm(reason.trim());
            }
          }}
        >
          {error && (
            <p className="text-sm text-red-800 bg-red-50 rounded-lg p-2 border border-red-100">
              {error}
            </p>
          )}
          <label className="block text-sm font-medium text-slate-700">Reason</label>
          <textarea
            data-testid="reject-reason"
            rows={3}
            className={`mt-1 block w-full rounded-md border-slate-300 shadow-sm ${FIELD_TRANSITION} focus:border-brand-500 focus:ring-brand-500 sm:text-sm ${
              error ? "border-red-500" : ""
            }`}
            value={reason}
            onChange={(event) => setReason(event.target.value)}
          />
          <p className="text-xs text-slate-500">
            Stored on the order's status timeline.
          </p>
          {!canConfirm && !error && (
            <p className="text-xs text-red-700">A reason is required.</p>
          )}
          <div className="flex justify-end gap-2">
            <button
              type="button"
              disabled={isSubmitting}
              onClick={onCancel}
              className={`rounded-md border border-slate-300 bg-white px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-50 ${PRESSABLE} ${FOCUS_RING}`}
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={!canConfirm || isSubmitting}
              className={`rounded-md border border-transparent bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-50 disabled:cursor-not-allowed ${PRESSABLE} ${FOCUS_RING}`}
            >
              {isSubmitting ? "Rejecting…" : "Reject order"}
            </button>
          </div>
        </form>
      </motion.div>
    </motion.div>
  );
}
