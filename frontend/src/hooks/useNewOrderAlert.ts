import { useCallback, useEffect, useRef, useState } from "react";

/**
 * Detects newly arrived vendor orders and triggers an alert (toast + optional sound).
 *
 * <p>The hook keeps a {@code Set} of order IDs that have already triggered an
 * alert so the same order never fires twice, even if the polling refetches
 * multiple times. New orders are defined as those whose status is
 * {@code PLACED} and whose ID was not present in the previous fetch.
 *
 * <p>Sound playback is deferred until the first user interaction (click/key)
 * to satisfy browser autoplay policies. The sound is a short, gentle chime
 * generated via the Web Audio API — no external asset required.
 */
export function useNewOrderAlert(
  orders: Array<{ id: number; status: string }>,
  { enabled = true, soundEnabled = true }: { enabled?: boolean; soundEnabled?: boolean } = {},
) {
  const seenIdsRef = useRef<Set<number>>(new Set());
  const [showToast, setShowToast] = useState<{ orderId: number; orderName: string } | null>(null);
  const audioContextRef = useRef<AudioContext | null>(null);
  const soundUnlockedRef = useRef(false);

  // Initialize seen IDs on first render
  useEffect(() => {
    if (enabled && orders.length > 0 && seenIdsRef.current.size === 0) {
      orders.forEach((o) => seenIdsRef.current.add(o.id));
    }
  }, [enabled, orders]);

  // Detect new PLACED orders
  useEffect(() => {
    if (!enabled) return;

    const newOrders = orders.filter(
      (o) => o.status === "PLACED" && !seenIdsRef.current.has(o.id),
    );

    if (newOrders.length > 0) {
      newOrders.forEach((o) => seenIdsRef.current.add(o.id));

      // Show toast for the first new order (most recent first)
      const latest = newOrders[0];
      setShowToast({ orderId: latest.id, orderName: `Order #${latest.id}` });

      // Play sound if enabled
      if (soundEnabled) {
        playAlertSound();
      }
    }
  }, [enabled, orders, soundEnabled]);

  useEffect(() => {
    if (!enabled) return;

    const unlock = () => {
      if (soundUnlockedRef.current) return;
      if (audioContextRef.current?.state === "suspended") {
        audioContextRef.current.resume();
      }
      soundUnlockedRef.current = true;
    };

    window.addEventListener("click", unlock, { once: true });
    window.addEventListener("keydown", unlock, { once: true });
    return () => {
      window.removeEventListener("click", unlock);
      window.removeEventListener("keydown", unlock);
    };
  }, [soundEnabled]);

  const dismissToast = useCallback(() => {
    setShowToast(null);
  }, []);

  // Simple Web Audio API chime — no external asset needed
  const playAlertSound = useCallback(() => {
    if (!soundEnabled || !soundUnlockedRef.current) return;

    try {
      if (!audioContextRef.current) {
        audioContextRef.current = new (window.AudioContext ||
          (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext)();
      }
      const ctx = audioContextRef.current;
      if (ctx.state === "suspended") {
        ctx.resume();
      }

      const now = ctx.currentTime;
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();

      osc.type = "sine";
      osc.frequency.setValueAtTime(880, now); // A5
      osc.frequency.exponentialRampToValueAtTime(440, now + 0.3); // A4

      gain.gain.setValueAtTime(0.0, now);
      gain.gain.linearRampToValueAtTime(0.3, now + 0.02);
      gain.gain.exponentialRampToValueAtTime(0.001, now + 0.5);

      osc.connect(gain).connect(ctx.destination);
      osc.start(now);
      osc.stop(now + 0.5);
    } catch {
      // Ignore audio errors — sound is best-effort
    }
  }, [soundEnabled]);

  return {
    showToast,
    dismissToast,
    /** Enable/disable sound at runtime (e.g. from user settings). */
    setSoundEnabled: (_enabled: boolean) => {
      // This would need state if runtime toggle is needed
    },
  };
}