import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { PaymentStatusPage } from "@/features/payment/pages/PaymentStatusPage";
import { MemoryRouter, Routes, Route } from "react-router-dom";
import { QueryClient } from "@tanstack/react-query";
import { QueryClientProvider } from "@tanstack/react-query";
import { render, type RenderResult } from "@testing-library/react";
import { makePaymentGroupStateResponse, makePaymentResumeResponse } from "@/test/factories";
import { apiError } from "@/test/api-errors";
import type { PaymentGroupStateResponse } from "@/features/payment/types";

const mockFetchGroupState = vi.hoisted(() => vi.fn());
const mockResumePayment = vi.hoisted(() => vi.fn());

vi.mock("@/features/payment/api", () => ({
  fetchGroupState: mockFetchGroupState,
  resumePayment: mockResumePayment,
}));

function createTestQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: 0, staleTime: 0 },
      mutations: { retry: false },
    },
  });
}

function renderPaymentStatus(route = "/payment/pg-1001"): RenderResult & { queryClient: QueryClient } {
  const queryClient = createTestQueryClient();
  function Wrapper() {
    return (
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={[route]}>
          <Routes>
            <Route path="/payment/:paymentGroupId" element={<PaymentStatusPage />} />
            <Route path="/orders" element={<div />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    );
  }
  return Object.assign(render(<Wrapper />), { queryClient });
}

function primeGroup(group: PaymentGroupStateResponse) {
  mockFetchGroupState.mockResolvedValue(group);
}

beforeEach(() => {
  vi.clearAllMocks();
  mockResumePayment.mockResolvedValue(makePaymentResumeResponse());
});

describe("PaymentStatusPage", () => {
  it("shows the awaiting-payment state for a CREATED group", async () => {
    primeGroup(makePaymentGroupStateResponse({ status: "CREATED" }));

    renderPaymentStatus();

    await screen.findByText("Payment group");
    await screen.findByText("Awaiting payment");
    expect(screen.queryByText("Paid")).not.toBeInTheDocument();
    expect(screen.queryByText("Payment failed")).not.toBeInTheDocument();
  });

  it("transitions to paid when polling returns PAID and stops polling", async () => {
    const paidGroup = makePaymentGroupStateResponse({ status: "PAID" });
    mockFetchGroupState
      .mockResolvedValueOnce(makePaymentGroupStateResponse({ status: "CREATED" }))
      .mockResolvedValueOnce(paidGroup);

    const { queryClient } = renderPaymentStatus();

    await screen.findByText("Awaiting payment");

    // Simulate a poll: refetch the group query with the new (PAID) result.
    await queryClient.refetchQueries({ queryKey: ["payment", "group", "pg-1001"] });

    await waitFor(() => {
      expect(screen.getByText("PAID")).toBeInTheDocument();
    });

    // After reaching PAID, no further fetchGroupState calls should occur.
    const callsAfterTerminal = mockFetchGroupState.mock.calls.length;
    await new Promise((r) => setTimeout(r, 100));
    expect(mockFetchGroupState.mock.calls.length).toBe(callsAfterTerminal);
  });

  it("transitions to failed when polling returns FAILED", async () => {
    const failedGroup = makePaymentGroupStateResponse({ status: "FAILED" });
    mockFetchGroupState
      .mockResolvedValueOnce(makePaymentGroupStateResponse({ status: "CREATED" }))
      .mockResolvedValueOnce(failedGroup);

    const { queryClient } = renderPaymentStatus();

    await screen.findByText("Awaiting payment");

    await queryClient.refetchQueries({ queryKey: ["payment", "group", "pg-1001"] });

    await waitFor(() => {
      expect(screen.getByText("FAILED")).toBeInTheDocument();
    });
  });

  it("shows resume button for a pending order and calls the resume API", async () => {
    const pendingGroup = makePaymentGroupStateResponse({
      status: "CREATED",
      orders: [
        {
          ...makePaymentGroupStateResponse().orders[0],
          orderId: 5001,
          paymentStatus: "PENDING",
        },
      ],
    });
    primeGroup(pendingGroup);

    renderPaymentStatus();

    await screen.findByText("Awaiting payment");
    const resumeButton = screen.getByRole("button", { name: /resume/i });
    expect(resumeButton).toBeInTheDocument();

    await userEvent.click(resumeButton);

    await waitFor(() => {
      expect(mockResumePayment).toHaveBeenCalledWith(5001);
    });
  });

  it("hides the resume button for terminal states", async () => {
    const paidGroup = makePaymentGroupStateResponse({
      status: "PAID",
      orders: [
        {
          ...makePaymentGroupStateResponse().orders[0],
          orderId: 5001,
          paymentStatus: "PAID",
        },
      ],
    });
    primeGroup(paidGroup);

    renderPaymentStatus();

    await waitFor(() => {
      expect(screen.getByText("Paid")).toBeInTheDocument();
    });
    expect(screen.queryByRole("button", { name: /resume/i })).not.toBeInTheDocument();
  });

  it("surfaces a group-state error with a retry option", async () => {
    mockFetchGroupState.mockRejectedValue(
      apiError(500, { code: "INTERNAL_SERVER_ERROR", message: "Payment service unavailable" }),
    );

    renderPaymentStatus();

    expect(await screen.findByText(/something went wrong/i));
    expect(screen.getByText("Payment service unavailable")).toBeInTheDocument();

    primeGroup(makePaymentGroupStateResponse({ status: "CREATED" }));
    await userEvent.click(screen.getByRole("button", { name: /try again/i }));

    await screen.findByText("Awaiting payment");
  });

  it("navigates back to orders", async () => {
    const user = userEvent.setup();
    primeGroup(makePaymentGroupStateResponse({ status: "CREATED" }));

    renderPaymentStatus();

    await screen.findByText("Awaiting payment");
    await user.click(screen.getByRole("link", { name: /back to your orders/i }));

    await waitFor(() => {
      expect(screen.queryByText("Awaiting payment")).not.toBeInTheDocument();
    });
  });
});
