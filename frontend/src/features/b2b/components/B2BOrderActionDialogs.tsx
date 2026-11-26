/**
 * Action dialogs for one B2B order (Stage 3 of the B2B module).
 *
 * Each dialog maps to exactly one backend handler and sends only what that
 * handler requires:
 *  - reject: mandatory reason (`B2BOrderActionRequest.reason`)
 *  - cancel: mandatory reason (`B2BOrderActionRequest.reason`)
 *  - ship: mandatory shipment reference (`B2BOrderActionRequest.shipmentReference`)
 *  - accept / deliver: no body
 *
 * The client never invents a status transition — it asks the backend to make
 * the next valid one and renders the result (or the 409/400 it refuses with).
 */

import { useState } from "react";
import { motion } from "framer-motion";
import { backdropVariants, dialogVariants } from "@/motion/tokens";
import { usePrefersReducedMotion } from "@/motion/use-reduced-motion";
import { FIELD_TRANSITION, FOCUS_RING, PRESSABLE } from "@/motion/pressable";

export interface B2BOrderActionDialogsProps {
  orderName: string;
  /** Which dialog is open, if any. */
  openAction: "reject" | "cancel" | "ship" | null;
  /** Submitting any action disables every dialog's confirm buttons. */
  isSubmitting: boolean;
  /** Normalised backend failure from the most recent attempt. */
  error: string | null;
  onConfirm: (payload: { reason?: string; shipmentReference?: string }) => void;
  onCancel: () => void;
}

/**
 * One modal that hosts the reject, cancel and ship forms. Only one is mounted
 * at a time, chosen by `openAction`, so the reason and shipment-reference rules
 * cannot disagree with the action the vendor actually picked.
 */
export function B2BOrderActionDialogs({
  orderName,
  openAction,
  isSubmitting,
  error,
  onConfirm,
  onCancel,
}: B2BOrderActionDialogsProps) {
  const reduceMotion = usePrefersReducedMotion();
  const [reason, setReason] = useState("");
  const [shipmentReference, setShipmentReference] = useState("");

  const canConfirm =
    openAction === "ship"
      ? shipmentReference.trim().length > 0
      : reason.trim().length > 0;

  return (
    <>
      {openAction && (
        <>
          <motion.div
            className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4"
            variants={backdropVariants}
            initial={reduceMotion ? false : "hidden"}
            animate={openAction !== null ? "visible" : "hidden"}
            exit={reduceMotion ? undefined : "exit"}
          />
          <motion.div
            role="dialog"
            aria-modal="true"
            className="w-full max-w-md rounded-lg border border-slate-200 bg-white p-5 shadow-lg"
            variants={dialogVariants}
            initial={reduceMotion ? false : "hidden"}
            animate={openAction !== null ? "visible" : "hidden"}
            exit={reduceMotion ? undefined : "exit"}
          >
            <form
              className="mt-4 space-y-4"
              noValidate
              onSubmit={(event: React.FormEvent) => {
                event.preventDefault();
                if (canConfirm) {
                  onConfirm({
                    ...(openAction === "ship"
                      ? { shipmentReference: shipmentReference.trim() }
                      : { reason: reason.trim() }),
                  });
                }
              }}
            >
              <h2 className="text-base font-semibold text-slate-900">
                {openAction === "reject" ? "Reject order" : openAction === "cancel" ? "Cancel order" : "Ship order"}
              </h2>
              <p className="mt-1 text-sm text-slate-600">
                {openAction === "reject" ? (
                  <>
                    Why are you rejecting <span className="font-medium">{orderName}</span>?
                  </>
                ) : openAction === "cancel" ? (
                  <>
                    Why are you cancelling <span className="font-medium">{orderName}</span>?
                  </>
                ) : (
                  <>
                    Shipment reference for <span className="font-medium">{orderName}</span>:
                  </>
                )}
              </p>

              {error && (
                <p className="text-sm text-red-800 bg-red-50 rounded-lg p-2 border border-red-100">
                  {error}
                </p>
              )}

              {openAction === "ship" ? (
                <>
                  <label className="block text-sm font-medium text-slate-700">
                    Shipment reference
                  </label>
                  <input
                    data-testid="shipment-reference"
                    type="text"
                    value={shipmentReference}
                    onChange={(event) => setShipmentReference(event.target.value)}
                    className={`mt-1 block w-full rounded-md border-slate-300 shadow-sm ${FIELD_TRANSITION} focus:border-brand-500 focus:ring-brand-500 sm:text-sm ${
                      error ? "border-red-500" : ""
                    }`}
                  />
                  <p className="text-xs text-slate-500">
                    The carrier or tracking reference shown to the buyer.
                  </p>
                </>
              ) : (
                <>
                  <label className="block text-sm font-medium text-slate-700">
                    Reason
                  </label>
                  <textarea
                    data-testid={`${openAction}-reason`}
                    rows={3}
                    value={reason}
                    onChange={(event) => setReason(event.target.value)}
                    className={`mt-1 block w-full rounded-md border-slate-300 shadow-sm ${FIELD_TRANSITION} focus:border-brand-500 focus:ring-brand-500 sm:text-sm ${
                      error ? "border-red-500" : ""
                    }`}
                  />
                  <p className="text-xs text-slate-500">
                    Stored on the order's status timeline.
                  </p>
                </>
              )}
              {!canConfirm && !error && (
                <p className="text-xs text-red-700">
                  {openAction === "ship" ? "A shipment reference is required." : "A reason is required."}
                </p>
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
                  {isSubmitting ? "Working…" : openAction === "ship" ? "Ship order" : openAction === "reject" ? "Reject order" : "Cancel order"}
                </button>
              </div>
            </form>
          </motion.div>
        </>
      )}
    </>
  );
}
