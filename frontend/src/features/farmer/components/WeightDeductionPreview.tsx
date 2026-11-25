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
    <div className="mt-4 rounded-lg border border-slate-200 bg-slate-50 p-4">
      <h3 className="text-xs font-semibold uppercase tracking-wide text-slate-500 mb-3">
        Preview
      </h3>
      <div className="grid grid-cols-2 gap-2 text-sm">
        <div className="flex justify-between">
          <span className="text-slate-600">Gross weight</span>
          <span className="font-mono text-slate-900">{grossKg.toFixed(2)} kg</span>
        </div>
        <div className="flex justify-between">
          <span className="text-slate-600">Deduction</span>
          <span className="font-mono text-slate-900">{deductionKg.toFixed(2)} kg</span>
        </div>
        <div className="flex justify-between">
          <span className="text-slate-600">Net weight</span>
          <span className="font-mono text-slate-900">{netKg.toFixed(2)} kg</span>
        </div>
        <div className="flex justify-between">
          <span className="text-slate-600">Price / kg</span>
          <span className="font-mono text-slate-900">{formatMoney(pricePerKg)}</span>
        </div>
        <div className="flex justify-between col-span-2 font-semibold text-brand-900">
          <span className="text-slate-600">Amount</span>
          <span>{formatMoney(amount)}</span>
        </div>
      </div>
    </div>
  );
}
