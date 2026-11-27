/**
 * Cancellation confirmation dialog for a customer's own order
 * (plan Phase 8, task 8.6).
 *
 * The customer can cancel a `PLACED` or `CONFIRMED` order. The reason is
 * optional for a customer (the backend does not require it), so the confirm
 * button is always enabled once the dialog is open. A 409 from the backend
 * means the order has already moved on — the dialog stays open with the error
 * shown so the user can dismiss it and see the refreshed state.
 */

import { useState } from "react";
import { motion } from "framer-motion";
import { backdropVariants, dialogVariants } from "@/motion/tokens";
import { usePrefersReducedMotion } from "@/motion/use-reduced-motion";
import { FOCUS_RING, PRESSABLE } from "@/motion/pressable";
import type { ApiErrorInfo } from "@/shared/lib/api-error";

interface CancelOrderDialogProps {
  orderName: string;
  open: boolean;
  isSubmitting: boolean;
  error: ApiErrorInfo | null;
  onConfirm: (reason?: string) => void;
  onCancel: () => void;
}

export function CancelOrderDialog({
  orderName,
  open,
  isSubmitting,
  error,
  onConfirm,
  onCancel,
}: CancelOrderDialogProps) {
  const [reason, setReason] = useState("");
  const reduceMotion = usePrefersReducedMotion();
  const enterState = reduceMotion ? false : "hidden";
  const exitState = reduceMotion ? undefined : "exit";

  return (
    <motion.div
      className="fixed inset-0 z-50 flex items-center justify-center bg-bolder-overlay p-4"
      variants={backdropVariants}
      initial={enterState}
      animate={open ? "visible" : "hidden"}
      exit={exitState}
    >
      <motion.div
        role="dialog"
        aria-modal="true"
        className="w-full max-w-md rounded-lg border bolder-line bg-bolder-surface p-5 shadow-lg"
        variants={dialogVariants}
        initial={enterState}
        animate={open ? "visible" : "hidden"}
        exit={exitState}
      >
        <h2 className="text-base font-semibold text-bolder-text">Cancel order</h2>
        <p className="mt-1 text-sm text-bolder-muted">
          Canceling <span className="font-medium">{orderName}</span> will release any reserved stock.
          An optional reason is stored on the order's status timeline.
        </p>

        <form
          className="mt-4 space-y-4"
          noValidate
          onSubmit={(event: React.FormEvent) => {
            event.preventDefault();
            onConfirm(reason.trim() || undefined);
          }}
        >
          {error && (
            <p className="text-sm text-bolder-danger bg-bolder-danger-surface rounded-lg p-2 border bolder-danger-border">
              {error.message}
            </p>
          )}
          <label className="block text-sm font-medium text-bolder-text">Reason (optional)</label>
          <textarea
            data-testid="cancel-reason"
            rows={3}
            className={`mt-1 block w-full rounded-md border-bolder-line-control shadow-sm focus:border-bolder-rose focus:ring-bolder-rose sm:text-sm ${
              error ? "border-bolder-danger-border" : ""
            }`}
            value={reason}
            onChange={(event) => setReason(event.target.value)}
          />
          <p className="text-xs text-bolder-muted">
            Stored on the order's status timeline.
          </p>
          <div className="flex justify-end gap-2">
            <button
              type="button"
              disabled={isSubmitting}
              onClick={onCancel}
              className={`rounded-md border border-bolder-line-control bg-bolder-surface px-3 py-2 text-sm font-medium text-bolder-text hover:bg-bolder-bg disabled:cursor-not-allowed disabled:opacity-50 ${PRESSABLE} ${FOCUS_RING}`}
            >
              Keep order
            </button>
            <button
              type="submit"
              disabled={isSubmitting}
              className={`rounded-md border border-transparent bg-bolder-rose-action px-4 py-2 text-sm font-medium text-white hover:bg-bolder-rose-deep disabled:opacity-50 disabled:cursor-not-allowed ${PRESSABLE} ${FOCUS_RING}`}
            >
              {isSubmitting ? "Canceling…" : "Cancel order"}
            </button>
          </div>
        </form>
      </motion.div>
    </motion.div>
  );
}
