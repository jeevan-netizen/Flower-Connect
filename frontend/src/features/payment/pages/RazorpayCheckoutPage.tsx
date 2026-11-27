"use strict";

/**
 * Razorpay Standard Checkout page (test mode).
 *
 * Loads the Razorpay SDK, fetches the public Key ID and the Razorpay order
 * reference for the given order, opens the Razorpay Checkout modal on a
 * customer click, and on
 * successful payment verifies the signature server-side before marking the
 * payment `PAID` and transitioning the order to `PLACED`.
 *
 * No Key Secret is ever sent to the frontend. The amount is taken from the
 * server-calculated payment state, never from the browser.
 */
import { useEffect, useState, useCallback, useRef } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { fetchRazorpayKey, verifyRazorpayPayment } from "@/features/payment/api";
import type { PaymentStateResponse } from "@/features/payment/types";
import api from "@/shared/lib/api";
import { PageHeading } from "@/features/vendor/components/StatCard";

interface RazorpayOptions {
  key: string;
  amount: number; // INR paise
  currency: string;
  order_id: string;
  name: string;
  description: string;
  image?: string;
  handler: (response: {
    razorpay_payment_id: string;
    razorpay_order_id: string;
    razorpay_signature: string;
  }) => void;
  modal?: { escape: boolean; backdrop: boolean; ondismiss?: () => void };
  theme?: { color: string };
}

declare global {
  interface Window {
    Razorpay: new (
      options: RazorpayOptions,
    ) => { open: () => void };
  }
}

export default function RazorpayCheckoutPage() {
  const navigate = useNavigate();
  const { orderId } = useParams<{ orderId: string }>();
  const orderIdNum = orderId ? Number(orderId) : NaN;

  const [keyId, setKeyId] = useState<string | null>(null);
  const [razorpayOrderId, setRazorpayOrderId] = useState<string | null>(null);
  const [amountPaise, setAmountPaise] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [checkoutDismissed, setCheckoutDismissed] = useState(false);
  const checkoutOpenedRef = useRef(false);

  const loadRazorpaySdk = useCallback((): Promise<void> => {
    return new Promise((resolve, reject) => {
      if (window.Razorpay) {
        resolve();
        return;
      }
      const script = document.createElement("script");
      script.src = "https://checkout.razorpay.com/v1/checkout.js";
      script.async = true;
      script.onload = () => resolve();
      script.onerror = () => reject(new Error("Failed to load Razorpay SDK"));
      document.head.appendChild(script);
    });
  }, []);

  const openCheckout = useCallback(() => {
    if (!keyId || !razorpayOrderId || amountPaise === null) {
      setError("Missing checkout details");
      return;
    }
    if (!window.Razorpay) {
      setError("Razorpay Checkout did not load. Refresh the page and try again.");
      return;
    }
    try {
      const options: RazorpayOptions = {
        key: keyId,
        amount: amountPaise,
        currency: "INR",
        order_id: razorpayOrderId,
        name: "FlowerConnect",
        description: "Flower order payment",
        theme: { color: "#8a3149" },
        handler: async (response) => {
          try {
            await verifyRazorpayPayment(orderIdNum, {
              razorpayOrderId: response.razorpay_order_id,
              razorpayPaymentId: response.razorpay_payment_id,
              razorpaySignature: response.razorpay_signature,
            });
            navigate("/orders");
          } catch (err: unknown) {
            const message =
              err instanceof Error ? err.message : "Verification failed";
            setError(message);
          }
        },
        modal: {
          escape: false,
          backdrop: false,
          ondismiss: () => setCheckoutDismissed(true),
        },
      };

      const instance = new window.Razorpay(options);
      instance.open();
    } catch (err: unknown) {
      setError(
        err instanceof Error ? err.message : "Failed to open Razorpay Checkout",
      );
    }
  }, [
    keyId,
    razorpayOrderId,
    amountPaise,
    orderIdNum,
    navigate,
  ]);

  useEffect(() => {
    if (
      !loading &&
      !error &&
      keyId &&
      razorpayOrderId &&
      amountPaise !== null &&
      !checkoutOpenedRef.current
    ) {
      checkoutOpenedRef.current = true;
      openCheckout();
    }
  }, [loading, error, keyId, razorpayOrderId, amountPaise, openCheckout]);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const [keyResp, paymentResp] = await Promise.all([
          fetchRazorpayKey(),
          api.get<PaymentStateResponse>(`/payments/${orderIdNum}`),
          loadRazorpaySdk(),
        ]);
        if (cancelled) return;
        setKeyId(keyResp.keyId);
        setRazorpayOrderId(paymentResp.data.intentReference ?? null);
        setAmountPaise(Math.round(paymentResp.data.amount * 100));
      } catch (err: unknown) {
        if (cancelled) return;
        setError(
          err instanceof Error ? err.message : "Failed to load checkout details",
        );
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [orderIdNum, fetchRazorpayKey, loadRazorpaySdk]);

  if (loading) {
    return (
      <div className="max-w-3xl mx-auto px-4 py-16 text-center">
        <PageHeading
          title="Preparing payment"
          description="Loading secure Razorpay checkout…"
        />
      </div>
    );
  }

  if (error) {
    return (
      <div className="max-w-3xl mx-auto px-4 py-16 text-center">
        <PageHeading title="Payment setup failed" description={error} />
        <button
          onClick={() => navigate("/orders")}
          className="px-4 py-2 bg-bolder-rose-deep text-white rounded hover:bg-bolder-rose-strong"
        >
          Back to orders
        </button>
      </div>
    );
  }

  return (
    <div className="max-w-3xl mx-auto px-4 py-16 text-center">
      <PageHeading
        title="Pay with Razorpay"
        description="Continue to Razorpay’s secure test checkout to complete your payment."
      />
      {checkoutDismissed ? (
        <button
          type="button"
          onClick={() => {
            setCheckoutDismissed(false);
            openCheckout();
          }}
          className="mt-6 rounded-md bg-bolder-rose-action px-5 py-3 text-sm font-semibold text-white hover:bg-bolder-rose-deep"
        >
          Retry payment
        </button>
      ) : null}
    </div>
  );
}
