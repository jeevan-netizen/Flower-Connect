/**
 * Order status stepper (plan Phase 6, task 6.6).
 *
 * Shows the lifecycle steps an order passes through and marks each one
 * completed, current or pending relative to the order's current status.
 * Renders one step per status so the test suite can cover every status.
 */

import type { OrderStatus } from "@/features/customer/orders/types";

interface OrderStatusStepperProps {
  /** The order's current status. */
  status: OrderStatus;
  /** Human-readable label shown above the stepper. */
  heading?: string;
}

const STEP_ORDER: OrderStatus[] = [
  "PLACED",
  "CONFIRMED",
  "PREPARING",
  "OUT_FOR_DELIVERY",
  "DELIVERED",
];

const STEP_LABELS: Record<"PLACED" | "CONFIRMED" | "PREPARING" | "OUT_FOR_DELIVERY" | "DELIVERED", string> = {
  PLACED: "Order placed",
  CONFIRMED: "Confirmed",
  PREPARING: "Preparing",
  OUT_FOR_DELIVERY: "Out for delivery",
  DELIVERED: "Delivered",
};

function isCompleted(step: OrderStatus, current: OrderStatus): boolean {
  return STEP_ORDER.indexOf(step) < STEP_ORDER.indexOf(current);
}

function isCurrent(step: OrderStatus, current: OrderStatus): boolean {
  return step === current;
}

function stepTone(step: OrderStatus, current: OrderStatus): string {
  if (isCompleted(step, current)) {
    return "text-brand-600 bg-brand-50 border-brand-200";
  }
  if (isCurrent(step, current)) {
    return "text-slate-900 border-brand-600 bg-white";
  }
  return "text-slate-400 border-slate-200 bg-slate-50";
}

export function OrderStatusStepper({ status, heading }: OrderStatusStepperProps) {
  return (
    <div>
      {heading && (
        <h2 className="text-base font-semibold text-slate-900 mb-2">{heading}</h2>
      )}
      <ol className="flex items-center gap-1 text-xs list-none p-0 m-0">
        {STEP_ORDER.map((step, i) => {
          const completed = isCompleted(step, status);
          return (
            <li key={step} className="flex items-center gap-2">
              <span
                className={`w-6 h-6 rounded-full flex items-center justify-center text-xs font-medium border ${stepTone(
                  step,
                  status,
                )}`}
              >
                {i + 1}
              </span>
              {i < STEP_ORDER.length - 1 && (
                <div
                  className={`h-0.5 w-4 ${completed ? "bg-brand-600" : "bg-slate-200"}`}
                />
              )}
              <span
                className={`font-medium ${stepTone(step, status)}`}
              >
                {STEP_LABELS[step as keyof typeof STEP_LABELS]}
              </span>
            </li>
          );
        })}
      </ol>
    </div>
  );
}
