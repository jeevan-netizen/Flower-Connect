import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { zodResolver } from "@hookform/resolvers/zod";
import { useRequestCollection } from "@/features/farmer/queries";
import type { FarmerCollectionRequest } from "@/features/farmer/types";
import { COLLECTION_MODES, COLLECTION_MODE_LABELS } from "@/features/farmer/types";
import { FieldMessage } from "@/motion/FieldMessage";
import { FadeIn } from "@/motion/FadeIn";
import { PRESSABLE } from "@/motion/pressable";

const COLLECTION_SCHEMA = z.object({
  amount: z.number({ required_error: "Amount is required" }).positive("Amount must be positive"),
  mode: z.enum(["CASH", "UPI"]),
  note: z.string().max(500, "Note must not exceed 500 characters").optional().or(z.literal("")),
});

const CONTROL_CLASS = `mt-1 block w-full rounded-md border-slate-300 shadow-sm focus:border-brand-500 focus:ring-brand-500 sm:text-sm`;

interface CollectionFormProps {
  linkId: number;
  /** When true the link is not `ACTIVE` or a collection is already pending. */
  disabled?: boolean;
  onSuccess?: () => void;
}

/**
 * Farmer's collection request: amount, payment mode and optional note.
 *
 * Submission is disabled when the link cannot take a new request (not `ACTIVE`
 * or a request already pending); the caller renders the pending badge.
 */
export function CollectionForm({
  linkId,
  disabled = false,
  onSuccess,
}: CollectionFormProps) {
  const [submitError, setSubmitError] = useState<string | null>(null);
  const requestCollection = useRequestCollection();

  const {
    register,
    handleSubmit,
    formState: { errors, isValid },
  } = useForm<z.infer<typeof COLLECTION_SCHEMA>>({
    resolver: zodResolver(COLLECTION_SCHEMA),
    defaultValues: { amount: 0, mode: "CASH", note: "" },
  });

  const onSubmit = async (values: z.infer<typeof COLLECTION_SCHEMA>) => {
    setSubmitError(null);
    try {
      await requestCollection.mutateAsync({
        linkId,
        payload: values as FarmerCollectionRequest,
      });
      onSuccess?.();
    } catch (e) {
      const err = e as { response?: { data?: { message?: string } } };
      setSubmitError(err?.response?.data?.message ?? "Collection request failed");
    }
  };

  const isPending = requestCollection.isPending;

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="space-y-3">
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <div>
          <label htmlFor="amount" className="block text-sm font-medium text-slate-700">
            Amount (INR)
          </label>
          <input
            id="amount"
            type="number"
            step="0.01"
            min="0"
            placeholder="0.00"
            required
            aria-invalid={errors.amount ? true : undefined}
            aria-describedby={errors.amount ? "amount-note" : undefined}
            className={`${CONTROL_CLASS} ${errors.amount ? "border-red-500" : ""}`}
            {...register("amount")}
          />
          <FieldMessage id="amount-note" message={errors.amount?.message} reserve />
        </div>
        <div>
          <label htmlFor="mode" className="block text-sm font-medium text-slate-700">
            Mode
          </label>
          <select
            id="mode"
            required
            aria-invalid={errors.mode ? true : undefined}
            aria-describedby={errors.mode ? "mode-note" : undefined}
            className={`${CONTROL_CLASS} ${errors.mode ? "border-red-500" : ""}`}
            {...register("mode")}
          >
            {COLLECTION_MODES.map((mode) => (
              <option key={mode} value={mode}>
                {COLLECTION_MODE_LABELS[mode]}
              </option>
            ))}
          </select>
          <FieldMessage id="mode-note" message={errors.mode?.message} reserve />
        </div>
      </div>
      <div>
        <label htmlFor="note" className="block text-sm font-medium text-slate-700">
          Note
        </label>
        <textarea
          id="note"
          rows={2}
          placeholder="Optional note"
          aria-invalid={errors.note ? true : undefined}
          aria-describedby={errors.note ? "note-note" : undefined}
          className={`${CONTROL_CLASS} ${errors.note ? "border-red-500" : ""}`}
          {...register("note")}
        />
        <FieldMessage id="note-note" message={errors.note?.message} reserve />
      </div>
      {submitError && (
        <FadeIn role="alert" className="rounded-md bg-red-50 p-3 text-sm text-red-700">
          {submitError}
        </FadeIn>
      )}
      <button
        type="submit"
        disabled={disabled || isPending || !isValid}
        className={`w-full rounded-md border border-transparent bg-brand-600 py-2.5 px-4 text-sm font-medium text-white hover:bg-brand-700 focus:outline-none focus:ring-2 focus:ring-brand-500 focus:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 ${PRESSABLE}`}
      >
        {isPending ? "Requesting…" : disabled ? "Cannot request" : "Request collection"}
      </button>
    </form>
  );
}
