import { useEffect, useRef, useState } from "react";

/**
 * Detects status changes on a customer order and triggers a toast notification.
 *
 * <p>The hook compares the current order status with the previous one and
 * triggers a toast when a transition occurs. It avoids duplicate toasts for
 * the same transition by tracking the last seen status.
 */
export function useOrderStatusToast(
  order: { status: string; id: number } | null,
  { enabled = true }: { enabled?: boolean } = {},
) {
  const prevStatusRef = useRef<string | null>(null);
  const [toast, setToast] = useState<{ message: string; type: "info" | "success" | "warning" | "error" } | null>(null);

  useEffect(() => {
    if (!enabled || !order) return;

    const currentStatus = order.status;
    const prevStatus = prevStatusRef.current;

    if (prevStatus !== null && prevStatus !== currentStatus) {
      const type = getStatusChangeType(prevStatus, currentStatus);
      const message = formatStatusChangeMessage(prevStatus, currentStatus);
      setToast({ message, type });
    }

    prevStatusRef.current = currentStatus;
  }, [enabled, order?.status]);

  // Initialize prevStatus on first render
  useEffect(() => {
    if (order) {
      prevStatusRef.current = order.status;
    }
  }, [order?.id]);

  const dismissToast = () => setToast(null);

  return {
    toast,
    dismissToast,
  };
}

function getStatusChangeType(
  _from: string,
  to: string,
): "info" | "success" | "warning" | "error" {
  if (to === "DELIVERED") return "success";
  if (to === "OUT_FOR_DELIVERY") return "info";
  if (to === "PREPARING" || to === "CONFIRMED") return "info";
  if (to === "CANCELLED" || to === "REJECTED") return "warning";
  return "info";
}

function formatStatusChangeMessage(from: string, to: string): string {
  const labels: Record<string, string> = {
    PLACED: "placed",
    CONFIRMED: "confirmed",
    PREPARING: "preparing",
    OUT_FOR_DELIVERY: "out for delivery",
    DELIVERED: "delivered",
    CANCELLED: "cancelled",
    REJECTED: "rejected",
    PENDING_PAYMENT: "awaiting payment",
  };

  const fromLabel = from === "null" ? "created" : labels[from] ?? from.toLowerCase();
  const toLabel = labels[to] ?? to.toLowerCase();

  return `Order status changed: ${fromLabel} → ${toLabel}`;
}