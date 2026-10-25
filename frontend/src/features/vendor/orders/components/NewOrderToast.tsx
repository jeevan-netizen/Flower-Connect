import { useEffect, useState } from "react";
import { PRESSABLE } from "@/motion/pressable";

interface NewOrderToastProps {
  orderName: string;
  onDismiss: () => void;
}

/**
 * Toast notification for a newly arrived vendor order.
 * Auto-dismisses after 8 seconds but can be dismissed manually.
 */
export function NewOrderToast({ orderName, onDismiss }: NewOrderToastProps) {
  const [visible, setVisible] = useState(true);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setVisible(false);
      onDismiss();
    }, 8000);

    return () => window.clearTimeout(timer);
  }, [onDismiss]);

  if (!visible) return null;

  return (
    <div
      className="fixed bottom-4 right-4 z-50 animate-slide-up"
      role="alert"
      aria-live="polite"
    >
      <div className="flex items-center gap-3 rounded-lg border border-amber-200 bg-amber-50 p-4 shadow-lg min-w-[280px] max-w-md">
        <svg
          className="shrink-0 text-amber-600"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth={2}
          aria-hidden="true"
        >
          <path strokeLinecap="round" strokeLinejoin="round" d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.381C7.72 5.48 6 7.562 6 10v11a2 2 0 002 2h5" />
        </svg>
        <div className="flex-1 min-w-0">
          <p className="text-sm font-semibold text-amber-900">New order received</p>
          <p className="mt-0.5 text-sm text-amber-800 truncate">{orderName}</p>
        </div>
        <button
          type="button"
          onClick={() => onDismiss()}
          className={`shrink-0 inline-flex items-center justify-center rounded-md p-1.5 text-amber-600 hover:bg-amber-100 transition-colors ${PRESSABLE}`}
          aria-label="Dismiss"
        >
          <svg
            className="h-5 w-5"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth={2}
            aria-hidden="true"
          >
            <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>
      </div>
    </div>
  );
}