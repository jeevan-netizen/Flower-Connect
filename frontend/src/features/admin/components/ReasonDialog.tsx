import { useId } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { TextField, FormErrorSummary, SubmitButton } from "@/features/vendor/components/FormFields";
import { adminReasonSchema, type AdminReasonValues } from "@/features/admin/form-schema";
import { ADMIN_REASON_MAX_LENGTH } from "@/features/admin/types";
import type { ApiErrorInfo } from "@/shared/lib/api-error";

interface ReasonDialogProps {
  /** Question shown as the dialog heading, e.g. "Reject this vendor?". */
  heading: string;
  /** One line naming the record and what will happen to it. */
  description: string;
  confirmLabel: string;
  /**
   * `true` for `reject` and `suspend`, whose request bodies carry a mandatory
   * reason. `false` for `approve` and `reinstate`, whose routes take no body — so
   * no reason input is rendered and none is sent.
   */
  requiresReason: boolean;
  isSubmitting: boolean;
  /** Normalised backend failure from the previous attempt, if any. */
  error: ApiErrorInfo | null;
  onConfirm: (reason: string | undefined) => void;
  onCancel: () => void;
}

/**
 * The confirmation step for an admin action (plan task 2.10: "approve/reject/
 * suspend actions and reason dialog").
 *
 * A single dialog serves all four vendor transitions and the user status change,
 * because the only real difference is whether a reason is collected. Rendering a
 * reason box for `approve` would imply the backend records one — it does not, and
 * `approve`/`reinstate` take no request body at all.
 *
 * The reason bound mirrors `VendorAdminReasonRequest.reason` and
 * `UserStatusUpdateRequest.reason` (`@NotBlank`, `@Size(max = 500)`). A 400 from
 * the server is still shown if it arrives, with its per-field message attached to
 * the textarea.
 *
 * Callers render this only while an action is pending, and should give it a `key`
 * derived from the target id so switching targets remounts the form rather than
 * carrying a stale reason across.
 */
export function ReasonDialog({
  heading,
  description,
  confirmLabel,
  requiresReason,
  isSubmitting,
  error,
  onConfirm,
  onCancel,
}: ReasonDialogProps) {
  const headingId = useId();
  const descriptionId = useId();

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<AdminReasonValues>({
    resolver: zodResolver(adminReasonSchema),
    defaultValues: { reason: "" },
  });

  const submit = requiresReason
    ? handleSubmit((values) => onConfirm(values.reason))
    : () => onConfirm(undefined);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4">
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby={headingId}
        aria-describedby={descriptionId}
        className="w-full max-w-md rounded-lg border border-slate-200 bg-white p-5 shadow-lg"
      >
        <h2 id={headingId} className="text-base font-semibold text-slate-900">
          {heading}
        </h2>
        <p id={descriptionId} className="mt-1 text-sm text-slate-600">
          {description}
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

          {requiresReason && (
            <TextField
              label="Reason"
              hint={`Stored on the audit record. Maximum ${ADMIN_REASON_MAX_LENGTH} characters.`}
              multiline
              rows={3}
              error={errors.reason?.message ?? error?.validation.reason}
              registration={register("reason")}
            />
          )}

          <div className="flex justify-end gap-2">
            <button
              type="button"
              onClick={onCancel}
              disabled={isSubmitting}
              className="rounded-md border border-slate-300 bg-white px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-50"
            >
              Cancel
            </button>
            <SubmitButton
              isSubmitting={isSubmitting}
              idleLabel={confirmLabel}
              busyLabel="Working..."
            />
          </div>
        </form>
      </div>
    </div>
  );
}