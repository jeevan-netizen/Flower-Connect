import { formatMoney } from "@/features/vendor/format";

interface WeightDeductionPreviewProps {
  grossKg: number;
  deductionKg: number;
  netKg: number;
  pricePerKg: number;
  amount: number;
}

/**
 * Live preview of the weight-deduction calculation. Mirrors the backend's
 * `WeightDeductionPolicy` brackets (below 10 → 0, 10–<20 → 1, …, 50+ → 5) and
 * HALF_UP rounding so the vendor sees the result before submitting. The backend
 * result is authoritative.
 */
export function WeightDeductionPreview({
  grossKg,
  deductionKg,
  netKg,
  pricePerKg,
  amount,
}: WeightDeductionPreviewProps) {
  return (
    <div className="mt-4 rounded-lg border border-bolder-line bg-bolder-bg p-4">
      <h3 className="text-xs font-semibold uppercase tracking-wide text-bolder-muted mb-3">
        Preview
      </h3>
      <div className="grid grid-cols-2 gap-2 text-sm">
        <div className="flex justify-between">
          <span className="text-bolder-muted">Gross weight</span>
          <span className="font-mono text-bolder-text">{grossKg.toFixed(2)} kg</span>
        </div>
        <div className="flex justify-between">
          <span className="text-bolder-muted">Deduction</span>
          <span className="font-mono text-bolder-text">{deductionKg.toFixed(2)} kg</span>
        </div>
        <div className="flex justify-between">
          <span className="text-bolder-muted">Net weight</span>
          <span className="font-mono text-bolder-text">{netKg.toFixed(2)} kg</span>
        </div>
        <div className="flex justify-between">
          <span className="text-bolder-muted">Price / kg</span>
          <span className="font-mono text-bolder-text">{formatMoney(pricePerKg)}</span>
        </div>
        <div className="flex justify-between col-span-2 font-semibold text-bolder-text">
          <span className="text-bolder-muted">Amount</span>
          <span>{formatMoney(amount)}</span>
        </div>
      </div>
    </div>
  );
}
