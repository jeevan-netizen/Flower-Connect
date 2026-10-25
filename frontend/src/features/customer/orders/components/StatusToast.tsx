import { useEffect, useState } from "react";
import { PRESSABLE } from "@/motion/pressable";

interface StatusToastProps {
  message: string;
  type: "info" | "success" | "warning" | "error";
  onDismiss: () => void;
}

const TYPE_STYLES: Record<
  "info" | "success" | "warning" | "error",
  { bg: string; border: string; text: string; icon: React.ReactNode }
> = {
  info: {
    bg: "bg-blue-50",
    border: "border-blue-200",
    text: "text-blue-900",
    icon: (
      <svg className="shrink-0 text-blue-600" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} aria-hidden="true">
        <path strokeLinecap="round" strokeLinejoin="round" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
      </svg>
    ),
  },
  success: {
    bg: "bg-green-50",
    border: "border-green-200",
    text: "text-green-900",
    icon: (
      <svg className="shrink-0 text-green-600" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} aria-hidden="true">
        <path strokeLinecap="round" strokeLinejoin="round" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
      </svg>
    ),
  },
  warning: {
    bg: "bg-amber-50",
    border: "border-amber-200",
    text: "text-amber-900",
    icon: (
      <svg className="shrink-0 text-amber-600" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} aria-hidden="true">
        <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
      </svg>
    ),
  },
  error: {
    bg: "bg-red-50",
    border: "border-red-200",
    text: "text-red-900",
    icon: (
      <svg className="shrink-0 text-red-600" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} aria-hidden="true">
        <path strokeLinecap="round" strokeLinejoin="round" d="M10 14l2-2m0 0l2-2m-2 2l-2-2m2 2l2 2m-2 2l-2 2m-2 2l2 2" />
      </svg>
    ),
  },
};

/**
 * Generic toast notification for status changes and alerts.
 * Auto-dismisses after 6 seconds but can be dismissed manually.
 */
export function StatusToast({ message, type, onDismiss }: StatusToastProps) {
  const [visible, setVisible] = useState(true);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setVisible(false);
      onDismiss();
    }, 6000);

    return () => window.clearTimeout(timer);
  }, [onDismiss]);

  if (!visible) return null;

  const { bg, border, text, icon } = TYPE_STYLES[type];

  return (
    <div
      className="fixed bottom-4 right-4 z-50 animate-slide-up"
      role="alert"
      aria-live="polite"
    >
      <div className={`flex items-center gap-3 rounded-lg border ${border} ${bg} p-4 shadow-lg min-w-[280px] max-w-md`}>
        <span className="shrink-0" aria-hidden="true">
          {icon}
        </span>
        <p className={`text-sm ${text}`}>{message}</p>
        <button
          type="button"
          onClick={() => onDismiss()}
          className={`shrink-0 inline-flex items-center justify-center rounded-md p-1.5 text-current/60 hover:bg-current/10 transition-colors ${PRESSABLE}`}
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