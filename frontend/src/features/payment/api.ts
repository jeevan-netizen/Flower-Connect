import api from "@/shared/lib/api";
import type {
  PaymentGroupStateResponse,
  PaymentResumeResponse,
  PaymentStateResponse,
  RazorpayKeyResponse,
  RazorpayVerifyRequest,
  RazorpayVerifyResponse,
} from "@/features/payment/types";

/**
 * Customer payment API (plan task 7.6).
 *
 * All endpoints are `hasRole("CUSTOMER")` and resolve the caller from the JWT
 * subject, so no route accepts a customer identifier and cross-customer access
 * is not expressible in the request at all. No provider secrets are ever
 * returned — only server-generated references (payment group id, intent
 * reference) that the frontend needs to poll and resume.
 */

/** `GET /api/v1/payments/{orderId}` — current payment state for one order. */
export async function fetchPaymentState(
  orderId: number,
  signal?: AbortSignal,
): Promise<PaymentStateResponse> {
  const response = await api.get<PaymentStateResponse>(`/payments/${orderId}`, { signal });
  return response.data;
}

/** `GET /api/v1/payments/group/{paymentGroupId}` — current state of a payment group. */
export async function fetchGroupState(
  paymentGroupId: string,
  signal?: AbortSignal,
): Promise<PaymentGroupStateResponse> {
  const response = await api.get<PaymentGroupStateResponse>(
    `/payments/group/${paymentGroupId}`,
    { signal },
  );
  return response.data;
}

/**
 * `POST /api/v1/payments/{orderId}/resume` — confirm payment for an unexpired
 * `PENDING_PAYMENT` order (simulated/test-mode).
 *
 * This is a *refresh* action, not a payment completion: it returns the current
 * pending state and marks the intent expired on timeout. Only a verified
 * webhook event (delivered through `POST /api/v1/payments/webhook`) may
 * transition a payment to `PAID`/`PLACED`. The frontend never submits card
 * data.
 */
export async function resumePayment(
  orderId: number,
  signal?: AbortSignal,
): Promise<PaymentResumeResponse> {
  const response = await api.post<PaymentResumeResponse>(
    `/payments/${orderId}/resume`,
    undefined,
    { signal },
  );
  return response.data;
}

/**
 * `GET /api/v1/payments/razorpay/key` — public Razorpay Key ID for
 * initialising Razorpay Standard Checkout. Never returns the Key Secret.
 */
export async function fetchRazorpayKey(
  signal?: AbortSignal,
): Promise<RazorpayKeyResponse> {
  const response = await api.get<RazorpayKeyResponse>(
    `/payments/razorpay/key`,
    { signal },
  );
  return response.data;
}

/**
 * `POST /api/v1/payments/razorpay/{orderId}/verify` — verify a Razorpay
 * checkout signature and, if valid, mark the payment `PAID` and transition
 * the order to `PLACED`.
 */
export async function verifyRazorpayPayment(
  orderId: number,
  payload: RazorpayVerifyRequest,
  signal?: AbortSignal,
): Promise<RazorpayVerifyResponse> {
  const response = await api.post<RazorpayVerifyResponse>(
    `/payments/razorpay/${orderId}/verify`,
    payload,
    { signal },
  );
  return response.data;
}
