import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { zodResolver } from "@hookform/resolvers/zod";
import { useAddDeposit } from "@/features/farmer/queries";
import { FieldMessage } from "@/motion/FieldMessage";
import { PRESSABLE } from "@/motion/pressable";
import { WeightDeductionPreview } from "@/features/farmer/components/WeightDeductionPreview";

const DEPOSIT_SCHEMA = z.object({
  flowerType: z.string().min(1, "Flower type is required").max(100, "Flower type must not exceed 100 characters"),
  grossKg: z.number({ required_error: "Gross kg is required" }).positive("Gross kg must be positive"),
  pricePerKg: z.number({ required_error: "Price per kg is required" }).positive("Price per kg must be positive"),
  depositedAt: z.string().optional(),
});

const CONTROL_CLASS = `mt-1 block w-full rounded-md border-slate-300 shadow-sm focus:border-brand-500 focus:ring-brand-500 sm:text-sm`;

type DepositFormValues = z.infer<typeof DEPOSIT_SCHEMA>;

interface DepositFormProps {
  linkId: number;
  onSuccess?: () => void;
}

/**
 * Vendor's "Add deposit" form: flower type, gross kg, price per kg and optional
 * date, with a live preview of deduction, net and amount. The backend's
 * weight-deduction rule and HALF_UP rounding are authoritative; the preview
 * mirrors them so the vendor sees the result before submitting.
 */
export function DepositForm({ linkId, onSuccess }: DepositFormProps) {
  const [submitError, setSubmitError] = useState<string | null>(null);
  const addDeposit = useAddDeposit();

  const {
    register,
    handleSubmit,
    watch,
    reset,
    formState: { errors, isValid },
  } = useForm<DepositFormValues>({
    resolver: zodResolver(DEPOSIT_SCHEMA),
    mode: "onChange",
    defaultValues: { flowerType: "", grossKg: 0, pricePerKg: 0, depositedAt: undefined },
  });

  const values = watch();

  const onSubmit = async (formValues: DepositFormValues) => {
    setSubmitError(null);
    try {
      await addDeposit.mutateAsync({
        linkId,
        payload: {
          flowerType: formValues.flowerType,
          grossKg: formValues.grossKg,
          pricePerKg: formValues.pricePerKg,
          depositedAt: formValues.depositedAt || undefined,
        },
      });
      reset();
      onSuccess?.();
    } catch (e) {
      const err = e as { response?: { data?: { message?: string } } };
      setSubmitError(err?.response?.data?.message ?? "Deposit failed");
    }
  };

  const isPending = addDeposit.isPending;

  // Live preview mirrors the backend's WeightDeductionPolicy brackets:
  // below 10 → 0, 10–<20 → 1, 20–<30 → 2, 30–<40 → 3, 40–<50 → 4, 50+ → 5.
  const grossKg = Number.isFinite(values.grossKg) ? values.grossKg : 0;
  const pricePerKg = Number.isFinite(values.pricePerKg) ? values.pricePerKg : 0;
  let deductionKg = 0;
  if (grossKg >= 50) deductionKg = 5;
  else if (grossKg >= 40) deductionKg = 4;
  else if (grossKg >= 30) deductionKg = 3;
  else if (grossKg >= 20) deductionKg = 2;
  else if (grossKg >= 10) deductionKg = 1;

  const netKg = grossKg - deductionKg;
  const amount = netKg * pricePerKg;

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="space-y-3">
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div>
          <label htmlFor="flowerType" className="block text-sm font-medium text-slate-700">
            Flower type
          </label>
          <input
            id="flowerType"
            type="text"
            placeholder="e.g. Roses"
            required
            aria-invalid={errors.flowerType ? true : undefined}
            aria-describedby={errors.flowerType ? "flowerType-note" : undefined}
            className={`${CONTROL_CLASS} ${errors.flowerType ? "border-red-500" : ""}`}
            {...register("flowerType")}
          />
          <FieldMessage id="flowerType-note" message={errors.flowerType?.message} reserve />
        </div>
        <div>
          <label htmlFor="grossKg" className="block text-sm font-medium text-slate-700">
            Gross (kg)
          </label>
          <input
            id="grossKg"
            type="number"
            step="0.01"
            min="0"
            required
            aria-invalid={errors.grossKg ? true : undefined}
            aria-describedby={errors.grossKg ? "grossKg-note" : undefined}
            className={`${CONTROL_CLASS} ${errors.grossKg ? "border-red-500" : ""}`}
            {...register("grossKg", { valueAsNumber: true })}
          />
          <FieldMessage id="grossKg-note" message={errors.grossKg?.message} reserve />
        </div>
        <div>
          <label htmlFor="pricePerKg" className="block text-sm font-medium text-slate-700">
            Price (/kg)
          </label>
          <input
            id="pricePerKg"
            type="number"
            step="0.01"
            min="0"
            required
            aria-invalid={errors.pricePerKg ? true : undefined}
            aria-describedby={errors.pricePerKg ? "pricePerKg-note" : undefined}
            className={`${CONTROL_CLASS} ${errors.pricePerKg ? "border-red-500" : ""}`}
            {...register("pricePerKg", { valueAsNumber: true })}
          />
          <FieldMessage id="pricePerKg-note" message={errors.pricePerKg?.message} reserve />
        </div>
      </div>
      <div>
        <label htmlFor="depositedAt" className="block text-sm font-medium text-slate-700">
          Deposited at (optional)
        </label>
        <input
          id="depositedAt"
          type="datetime-local"
          aria-describedby={errors.depositedAt ? "depositedAt-note" : undefined}
          className={`${CONTROL_CLASS} ${errors.depositedAt ? "border-red-500" : ""}`}
          {...register("depositedAt")}
        />
        <FieldMessage id="depositedAt-note" message={errors.depositedAt?.message} reserve />
      </div>

      <WeightDeductionPreview
        grossKg={grossKg}
        deductionKg={deductionKg}
        netKg={netKg}
        pricePerKg={pricePerKg}
        amount={amount}
      />

      {submitError && (
        <div role="alert" className="rounded-md bg-red-50 p-3 text-sm text-red-700">
          {submitError}
        </div>
      )}
      <button
        type="submit"
        disabled={isPending || !isValid}
        className={`w-full rounded-md border border-transparent bg-brand-600 py-2.5 px-4 text-sm font-medium text-white hover:bg-brand-700 focus:outline-none focus:ring-2 focus:ring-brand-500 focus:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 ${PRESSABLE}`}
      >
        {isPending ? "Recording…" : "Record deposit"}
      </button>
    </form>
  );
}
