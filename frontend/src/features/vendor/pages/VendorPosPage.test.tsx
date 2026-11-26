import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { screen, act } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { VendorPosPage } from "@/features/vendor/pages/VendorPosPage";
import { renderWithProviders } from "@/test/render";

const mockClearVendorCache = vi.hoisted(() => vi.fn());
const mockLogout = vi.hoisted(() => vi.fn());

vi.mock("@/features/vendor/queries", () => ({
  useVendorProfile: () => ({ data: { businessName: "Petal & Stem" } }),
  useUpdateVendorProfile: vi.fn(),
  clearVendorCache: mockClearVendorCache,
}));

vi.mock("@/features/auth/stores/auth-store", () => ({
  useAuthStore: vi.fn((selector?: (state: { logout: () => void }) => unknown) => {
    const state = { logout: mockLogout };
    return selector ? selector(state) : state;
  }),
}));

function renderPos() {
  return renderWithProviders(<VendorPosPage />);
}

describe("VendorPosPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("renders the POS header and payment sections", () => {
    renderPos();

    expect(screen.getByRole("heading", { name: /POS/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /logout/i })).toBeInTheDocument();
    expect(screen.getByPlaceholderText(/search products/i)).toBeInTheDocument();
  });

  it("starts with an empty ticket showing zero subtotal and total", () => {
    renderPos();

    const amounts = screen.getAllByText("$0.00");
    expect(amounts).toHaveLength(2);
    expect(screen.getByText("Total: $0.00")).toBeInTheDocument();
  });

  it("disables the charge button when the ticket is empty", () => {
    renderPos();

    const charge = screen.getByRole("button", { name: /charge/i });
    expect(charge).toBeDisabled();
  });

  it("selects a payment method via radio buttons", async () => {
    const user = userEvent.setup();
    renderPos();

    expect(screen.getByLabelText("CASH")).toBeChecked();
    await user.click(screen.getByLabelText("UPI"));
    expect(screen.getByLabelText("UPI")).toBeChecked();
    expect(screen.getByLabelText("CASH")).not.toBeChecked();
  });

  it("logs out and clears the vendor cache", async () => {
    const user = userEvent.setup();
    renderPos();

    await user.click(screen.getByRole("button", { name: /logout/i }));

    expect(mockLogout).toHaveBeenCalledTimes(1);
    expect(mockClearVendorCache).toHaveBeenCalledTimes(1);
  });

  it("does not print a receipt when the ticket is empty", () => {
    vi.useFakeTimers();

    try {
      renderPos();

      // The sale handler is gated behind ticket.items.length > 0. With an
      // empty ticket setTimeout is never scheduled, so advancing timers does
      // nothing and no receipt appears.
      act(() => {
        vi.advanceTimersByTime(800);
      });

      expect(screen.queryByRole("heading", { name: /receipt/i })).not.toBeInTheDocument();
    } finally {
      vi.useRealTimers();
    }
  });

  it("renders all three payment method options", () => {
    renderPos();

    expect(screen.getByLabelText("CASH")).toBeInTheDocument();
    expect(screen.getByLabelText("CARD")).toBeInTheDocument();
    expect(screen.getByLabelText("UPI")).toBeInTheDocument();
  });
});
