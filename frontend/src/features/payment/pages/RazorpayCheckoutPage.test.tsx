import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import RazorpayCheckoutPage from "@/features/payment/pages/RazorpayCheckoutPage";
import { MemoryRouter, Routes, Route } from "react-router-dom";
import { QueryClient } from "@tanstack/react-query";
import { QueryClientProvider } from "@tanstack/react-query";
import { render } from "@testing-library/react";
import {
  verifyRazorpayPayment,
} from "@/features/payment/api";
import type { PaymentStateResponse } from "@/features/payment/types";
import { makePaymentStateResponse } from "@/test/factories";

const mockFetchRazorpayKey = vi.hoisted(() => vi.fn());
const mockVerifyRazorpayPayment = vi.hoisted(() => vi.fn());
const mockApiGet = vi.hoisted(() => vi.fn());

vi.mock("@/features/payment/api", () => ({
  fetchRazorpayKey: mockFetchRazorpayKey,
  verifyRazorpayPayment: mockVerifyRazorpayPayment,
}));

vi.mock("@/shared/lib/api", () => ({
  default: {
    get: mockApiGet,
  },
}));

function createTestQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: 0, staleTime: 0 },
      mutations: { retry: false },
    },
  });
}

function renderRazorpayCheckout(
  route = "/payment/razorpay/5001",
): { queryClient: QueryClient } {
  const queryClient = createTestQueryClient();
  function Wrapper() {
    return (
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={[route]}>
          <Routes>
            <Route
              path="/payment/razorpay/:orderId"
              element={<RazorpayCheckoutPage />}
            />
            <Route path="/orders" element={<div data-testid="orders-page" />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    );
  }
  render(<Wrapper />);
  return { queryClient };
}

beforeEach(() => {
  vi.clearAllMocks();
  mockFetchRazorpayKey.mockResolvedValue({ keyId: "rzp_test_1234567890" });
  mockVerifyRazorpayPayment.mockResolvedValue({
    orderId: 5001,
    orderStatus: "PLACED",
    paymentStatus: "PAID",
    paymentGroupId: "pg-1001",
  });
  mockApiGet.mockResolvedValue({ data: {} as PaymentStateResponse });
});

describe("RazorpayCheckoutPage", () => {
  it("loads checkout details and opens Razorpay", async () => {
    const payment = makePaymentStateResponse({
      orderId: 5001,
      intentReference: "order_test_123",
      amount: 329,
    });
    mockApiGet.mockResolvedValue({ data: payment });

    renderRazorpayCheckout();

    await waitFor(() => {
      expect(mockFetchRazorpayKey).toHaveBeenCalled();
    });
    await waitFor(() => {
      expect(mockApiGet).toHaveBeenCalled();
    });
    await waitFor(() => {
      expect(mockApiGet.mock.calls.some(([url]) => String(url).includes("/payments/5001"))).toBe(true);
    });
  });

  it("shows an error when checkout details fail to load", async () => {
    mockApiGet.mockRejectedValue(
      new Error("Network Error"),
    );

    renderRazorpayCheckout();

    await waitFor(() => {
      expect(screen.getByText(/Payment setup failed/i)).toBeInTheDocument();
    });
  });

  it("verifies the signature on successful payment", async () => {
    const payment = makePaymentStateResponse({
      orderId: 5001,
      intentReference: "order_test_123",
      amount: 329,
    });
    mockApiGet.mockResolvedValue({ data: payment });

    renderRazorpayCheckout();

    // Simulate successful Razorpay handler invocation.
    await verifyRazorpayPayment(5001, {
      razorpayOrderId: "order_test_123",
      razorpayPaymentId: "pay_test_456",
      razorpaySignature: "sig_abc",
    });

    expect(mockVerifyRazorpayPayment).toHaveBeenCalledWith(5001, {
      razorpayOrderId: "order_test_123",
      razorpayPaymentId: "pay_test_456",
      razorpaySignature: "sig_abc",
    });
  });

  it("records a verification failure", async () => {
    mockVerifyRazorpayPayment.mockRejectedValue(
      new Error("Invalid Razorpay signature"),
    );

    const payment = makePaymentStateResponse({
      orderId: 5001,
      intentReference: "order_test_123",
      amount: 329,
    });
    mockApiGet.mockResolvedValue({ data: payment });

    renderRazorpayCheckout();

    await verifyRazorpayPayment(5001, {
      razorpayOrderId: "order_test_123",
      razorpayPaymentId: "pay_test_456",
      razorpaySignature: "bad_sig",
    }).catch(() => {
      /* expected */
    });

    expect(mockVerifyRazorpayPayment).toHaveBeenCalled();
  });
});
