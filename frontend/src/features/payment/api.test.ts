import { describe, it, expect, vi, beforeEach } from "vitest";
import { fetchRazorpayKey, verifyRazorpayPayment } from "@/features/payment/api";
import type { RazorpayKeyResponse, RazorpayVerifyResponse } from "@/features/payment/types";

const mockGet = vi.hoisted(() => vi.fn());
const mockPost = vi.hoisted(() => vi.fn());

vi.mock("@/shared/lib/api", () => ({
  default: {
    get: mockGet,
    post: mockPost,
  },
}));

describe("payment Razorpay API", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe("fetchRazorpayKey", () => {
    it("returns the Razorpay key ID", async () => {
      const key: RazorpayKeyResponse = { keyId: "rzp_test_1234567890" };
      mockGet.mockResolvedValue({ data: key });

      const result = await fetchRazorpayKey();

      expect(mockGet).toHaveBeenCalledWith("/payments/razorpay/key", { signal: undefined });
      expect(result).toEqual(key);
    });
  });

  describe("verifyRazorpayPayment", () => {
    it("posts the Razorpay verification payload", async () => {
      const response: RazorpayVerifyResponse = {
        orderId: 5001,
        orderStatus: "PLACED",
        paymentStatus: "PAID",
        paymentGroupId: "pg-1001",
      };
      mockPost.mockResolvedValue({ data: response });

      const result = await verifyRazorpayPayment(5001, {
        razorpayOrderId: "order_test_123",
        razorpayPaymentId: "pay_test_456",
        razorpaySignature: "sig_abc",
      });

      expect(mockPost).toHaveBeenCalledWith(
        "/payments/razorpay/5001/verify",
        {
          razorpayOrderId: "order_test_123",
          razorpayPaymentId: "pay_test_456",
          razorpaySignature: "sig_abc",
        },
        { signal: undefined },
      );
      expect(result).toEqual(response);
    });
  });
});
