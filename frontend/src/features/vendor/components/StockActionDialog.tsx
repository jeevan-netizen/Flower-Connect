import { useId } from "react";
import { useForm, type Resolver } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { motion } from "framer-motion";
import {
  STOCK_REASON_MAX_LENGTH,
  type InventorySummary,
  type StockActionKind,
  type StockActionRequest,
} from "@/features/vendor/types";
import { adjustmentSchema, stockInSchema, writeOffSchema } from "@/features/vendor/form-schema";
import {
  FormErrorSummary,
  NumberField,
  SubmitButton,
  TextField,
} from "@/features/vendor/components/FormFields";
import { backdropVariants, dialogVariants } from "@/motion/tokens";
import { usePrefersReducedMotion } from "@/motion/use-reduced-motion";
import { FOCUS_RING, PRESSABLE } from "@/motion/pressable";
import { toApiError } from "@/shared/lib/api-error";

/**
 * Per-action copy and rules.
 *
 * `requiresReason` is the load-bearing entry: it is `true` only for the two actions
 * whose DTO carries `@NotBlank`, and it is what decides whether a reason box is
 * rendered *and* which resolver runs. One table, so the two cannot disagree.
 *
 * `signedQuantity` marks the single action whose quantity may be negative.
 */
const ACTION_CONTENT: Record<
  StockActionKind,
  {
    heading: string;
    description: string;
    confirmLabel: string;
    quantityLabel: string;
    quantityHint: string;
    requiresReason: boolean;
    signedQuantity: boolean;
    reasonLabel: string;
    reasonHint: string;
  }
> = {
  "stock-in": {
    heading: "Add stock",
    description: "Record units you have received. This raises the quantity on hand.",
    confirmLabel: "Add stock",
    quantityLabel: "Quantity to add",
    quantityHint: "Whole units greater than zero.",
    requiresReason: false,
    signedQuantity: false,
    reasonLabel: "Reason (optional)",
    reasonHint: "A supplier or batch reference makes reconciling easier later.",
  },
  "stock-out": {
    heading: "Remove stock",
    description: "Record units that left your shelf without a sale, such as damage or a transfer.",
    confirmLabel: "Remove stock",
    quantityLabel: "Quantity to remove",
    quantityHint:
      "Whole units greater than zero. Stock reserved for placed orders cannot be taken this way.",
    requiresReason: false,
    signedQuantity: false,
    reasonLabel: "Reason (optional)",
    reasonHint: "A supplier or batch reference makes reconciling easier later.",
  },
  adjustment: {
    heading: "Adjust stock",
    description: "Correct a miscount — positive to add units, negative to remove them.",
    confirmLabel: "Save adjustment",
    quantityLabel: "Signed change",
    quantityHint: "Any non-zero whole number, for example 5 or -2.",
    requiresReason: true,
    signedQuantity: true,
    reasonLabel: "Reason",
    reasonHint: `Required for a stock adjustment, and stored on the movement record. Maximum ${STOCK_REASON_MAX_LENGTH} characters.`,
  },
  "write-off": {
    heading: "Write off stock",
    description: "Remove units that cannot be sold, such as spoiled stems. Recorded as waste.",
    confirmLabel: "Write off",
    quantityLabel: "Quantity to write off",
    quantityHint: "Whole units greater than zero.",
    requiresReason: true,
    signedQuantity: false,
    reasonLabel: "Reason",
    reasonHint: `Required for a write-off, and stored on the movement record. Maximum ${STOCK_REASON_MAX_LENGTH} characters.`,
  },
};

/** Labels for the four buttons that open this dialog, shared with both callers. */
export const STOCK_ACTION_LABELS: Record<StockActionKind, string> = {
  "stock-in": "Add stock",
  "stock-out": "Remove stock",
  adjustment: "Adjust",
  "write-off": "Write off",
};

interface StockFormValues {
  quantity: string;
  reason: string;
}

function resolverFor(action: StockActionKind): Resolver<StockFormValues> {
  switch (action) {
    case "adjustment":
      return zodResolver(adjustmentSchema);
    case "write-off":
      return zodResolver(writeOffSchema);
    case "stock-in":
    case "stock-out":
      return zodResolver(stockInSchema);
  }
}

interface StockActionDialogProps {
  action: StockActionKind;
  productName: string;
  /** Current stock, so the dialog can state what it is about to change. */
  inventory: InventorySummary | null;
  isSubmitting: boolean;
  /** Normalised backend failure from a previous attempt, rendered above the fields. */
  errorMessage: string | null;
  /** Must reject on failure; the dialog maps the rejection onto the inputs. */
  onConfirm: (payload: StockActionRequest) => Promise<void>;
  onCancel: () => void;
}

/**
 * One dialog for the four stock mutations (plan task 3.6 / 3.9).
 *
 * The four actions share a shape but not their rules, and getting a rule wrong
 * sends a vendor to a 400 they cannot interpret:
 *
 * | Action    | Quantity             | Reason       |
 * |-----------|----------------------|--------------|
 * | stock-in  | positive             | optional     |
 * | stock-out | positive             | optional     |
 * | adjustment| **signed, non-zero** | **required** |
 * | write-off | positive             | **required** |
 *
 * So the quantity rule is chosen from the action rather than being one field with a
 * soft warning, and the reason box is rendered — and a reason sent — only for the
 * two actions whose DTO carries `@NotBlank`. A required reason box on `stock-in`
 * would imply the backend stores one; an optional one on `write-off` would offer a
 * way to be rejected.
 *
 * Two details that are easy to get wrong:
 *
 *  - **The adjustment box is a text input, not a number input.** `<input
 *    type="number">` discards a lone `-` as an invalid intermediate value, which
 *    makes a signed correction untypeable in several browsers. The positive
 *    actions keep the number input, where it belongs.
 *  - **A blank reason is sent as `null`, not `""`.** `InventoryService.normalize`
 *    stores `null` for a blank reason precisely so "no reason given" stays
 *    distinguishable in the append-only movement log.
 *
 * The reserved floor is stated rather than checked. `available = quantity −
 * reservedQuantity` and a change breaching it is `409 INSUFFICIENT_STOCK`; the
 * dialog says so instead of pretending to know the reserved count for a delta the
 * vendor has not typed yet.
 */
export function StockActionDialog({
  action,
  productName,
  inventory,
  isSubmitting,
  errorMessage,
  onConfirm,
  onCancel,
}: StockActionDialogProps) {
  const content = ACTION_CONTENT[action];
  const headingId = useId();
  const descriptionId = useId();
  const reduceMotion = usePrefersReducedMotion();
  const enterState = reduceMotion ? false : "hidden";
  const exitState = reduceMotion ? undefined : "exit";

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<StockFormValues>({
    resolver: resolverFor(action),
    defaultValues: { quantity: "", reason: "" },
  });

  const submit = handleSubmit(async (values) => {
    const quantity = Number(values.quantity.trim());
    const reason = values.reason.trim();

    let payload: StockActionRequest;
    switch (action) {
      case "stock-in":
      case "stock-out":
        payload = { quantity, reason: reason === "" ? null : reason };
        break;
      case "adjustment":
      case "write-off":
        payload = { quantity, reason };
        break;
    }

    try {
      await onConfirm(payload);
    } catch (caught) {
      const info = toApiError(caught);
      Object.entries(info.validation).forEach(([field, message]) => {
        if (field === "quantity" || field === "reason") {
          setError(field, { type: "server", message });
        }
      });
      // The failure is already shown by the caller's error banner; swallowing here
      // avoids an unhandled rejection from inside the form's submit handler.
    }
  });

  return (
    <motion.div
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4"
      variants={backdropVariants}
      initial={enterState}
      animate="visible"
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
          {content.heading}
        </h2>
        <p id={descriptionId} className="mt-1 text-sm text-slate-600">
          {productName} — {content.description}
        </p>

        <form className="mt-4 space-y-4" onSubmit={submit} noValidate>
          <FormErrorSummary message={errorMessage} />

          {inventory && (
            <p className="text-xs text-slate-500">
              Currently {inventory.quantity} on hand, {inventory.available} available
              {inventory.reservedQuantity > 0 ? `, ${inventory.reservedQuantity} reserved` : ""}.
            </p>
          )}

          {content.signedQuantity ? (
            <TextField
              label={content.quantityLabel}
              hint={content.quantityHint}
              error={errors.quantity?.message}
              registration={register("quantity")}
            />
          ) : (
            <NumberField
              label={content.quantityLabel}
              step="1"
              min="1"
              hint={content.quantityHint}
              error={errors.quantity?.message}
              registration={register("quantity")}
            />
          )}

          <TextField
            label={content.reasonLabel}
            hint={content.reasonHint}
            multiline
            rows={3}
            error={errors.reason?.message}
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
              idleLabel={content.confirmLabel}
              busyLabel="Working..."
            />
          </div>
        </form>
      </motion.div>
    </motion.div>
  );
}

/**
 * Explains a refused stock write in the vendor's terms.
 *
 * `INSUFFICIENT_STOCK` is its own `ErrorCode` rather than a generic `CONFLICT`
 * precisely so this case can be told apart from "this resource already exists" and
 * explained, instead of being rendered as an opaque conflict.
 */
export function explainStockFailure(
  error: { code: string | null; message: string },
  action: StockActionKind,
): string {
  if (error.code === "INSUFFICIENT_STOCK") {
    return `${error.message} Units reserved for placed orders cannot be taken by a ${ACTION_CONTENT[action].heading.toLowerCase()}.`;
  }
  return error.message;
}