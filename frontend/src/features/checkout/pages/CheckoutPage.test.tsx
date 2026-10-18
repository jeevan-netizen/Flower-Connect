import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { CheckoutPage, getBrowserLocalDate } from "@/features/checkout/pages/CheckoutPage";
import { renderWithProviders } from "@/test/render";
import {
  makeCartItem,
  makeCartResponse,
  makeCartShopView,
  makeAddressResponse,
  makeSlotResponse,
  makeCheckoutQuoteResponse,
  makeCheckoutShopQuote,
  makeCheckoutResponse,
  makeOrderSummary,
} from "@/test/factories";
import { apiError } from "@/test/api-errors";
import type { CartResponse } from "@/features/cart/types";
import type { AddressResponse, SlotResponse } from "@/features/checkout/types";

const mockFetchCart = vi.hoisted(() => vi.fn());
const mockFetchAddresses = vi.hoisted(() => vi.fn());
const mockFetchSlots = vi.hoisted(() => vi.fn());
const mockFetchCheckoutQuote = vi.hoisted(() => vi.fn());
const mockPlaceCheckout = vi.hoisted(() => vi.fn());

vi.mock("@/features/cart/api", () => ({
  fetchCart: mockFetchCart,
  addCartItem: vi.fn(),
  updateCartItem: vi.fn(),
  deleteCartItem: vi.fn(),
  clearCart: vi.fn(),
}));

vi.mock("@/features/checkout/api", () => ({
  fetchCheckoutQuote: mockFetchCheckoutQuote,
  placeCheckout: mockPlaceCheckout,
  fetchSlots: mockFetchSlots,
  fetchAddresses: mockFetchAddresses,
}));

function renderCheckout() {
  return renderWithProviders(<CheckoutPage />);
}

function primeCart(cart: CartResponse = makeCartResponse()) {
  mockFetchCart.mockResolvedValue(cart);
  return cart;
}

function primeAddresses(addresses: AddressResponse[] = [makeAddressResponse()]) {
  mockFetchAddresses.mockResolvedValue({
    content: addresses,
    page: 0,
    size: 50,
    totalElements: addresses.length,
    totalPages: 1,
    first: true,
    last: true,
    empty: addresses.length === 0,
  });
}

function primeSlots(slots: SlotResponseMap) {
  Object.entries(slots).forEach(([, slotList]) => {
    mockFetchSlots.mockResolvedValueOnce(slotList);
  });
}

type SlotResponseMap = Record<string, SlotResponse[]>;

describe("CheckoutPage", () => {
beforeEach(() => {
  vi.clearAllMocks();
  mockFetchCheckoutQuote.mockResolvedValue(makeCheckoutQuoteResponse());
  mockPlaceCheckout.mockResolvedValue(makeCheckoutResponse());
  mockFetchAddresses.mockResolvedValue({
    content: [],
    page: 0,
    size: 50,
    totalElements: 0,
    totalPages: 0,
    first: true,
    last: true,
    empty: true,
  });
});

  it("shows an empty state when the cart is empty", async () => {
    primeCart(makeCartResponse({ items: [], shops: [], totalItems: 0, subtotal: 0 }));

    renderCheckout();

    expect(await screen.findByText(/your cart is empty/i)).toBeInTheDocument();
  });

  it("flows through address, slots, review and confirmation", async () => {
    const user = userEvent.setup();
    primeCart();
    primeAddresses();
    primeSlots({
      "7": [makeSlotResponse()],
    });
    mockFetchCheckoutQuote.mockResolvedValue(makeCheckoutQuoteResponse());
    mockPlaceCheckout.mockResolvedValue(makeCheckoutResponse());

    renderCheckout();

    // Step 1: address
    await screen.findByRole("heading", { name: "Delivery address" });
    await user.click(screen.getByRole("radio", { name: /Home/ }));
    await user.click(screen.getByRole("button", { name: /choose delivery slots/i }));

    // Step 2: slots
    await waitFor(() => {
      expect(screen.getByText("Petal & Stem")).toBeInTheDocument();
    });
    await user.click(screen.getByRole("radio", { name: /10:00 AM — 11:00 AM/i }));
    await user.click(screen.getByRole("button", { name: /review order/i }));

    // Step 3: review
    await waitFor(() => {
      expect(screen.getByText("Order total")).toBeInTheDocument();
    });
    await user.click(screen.getByRole("button", { name: /place order/i }));

    // Step 4: confirmation
    await waitFor(() => {
      expect(screen.getByRole("heading", { name: "Order placed" })).toBeInTheDocument();
    });

    expect(mockPlaceCheckout).toHaveBeenCalledWith({
      addressId: 42,
      clientOrderId: expect.any(String),
      confirmPriceChanges: false,
      items: [{ slotStart: "2026-10-03T10:00:00", vendorId: 7 }],
      quoteHash: "abc123def456",
    });
  });

  it("requires price change confirmation when the quote has drifted", async () => {
    const user = userEvent.setup();
    primeCart();
    primeAddresses();
    primeSlots({ "7": [makeSlotResponse()] });
    mockFetchCheckoutQuote.mockResolvedValue(
      makeCheckoutQuoteResponse({ hasPriceChanges: true }),
    );

    renderCheckout();

    await screen.findByRole("heading", { name: "Delivery address" });
    await user.click(screen.getByRole("radio", { name: /Home/ }));
    await user.click(screen.getByRole("button", { name: /choose delivery slots/i }));

    await waitFor(() => {
      expect(screen.getByText("Petal & Stem")).toBeInTheDocument();
    });
    await user.click(screen.getByRole("radio", { name: /10:00 AM — 11:00 AM/i }));
    await user.click(screen.getByRole("button", { name: /review order/i }));

    await waitFor(() => {
      expect(screen.getByText(/some prices have changed/i)).toBeInTheDocument();
    });

    // Place order is disabled until confirmed
    expect(screen.getByRole("button", { name: /place order/i })).toBeDisabled();

    // Confirm price changes
    await user.click(screen.getByRole("checkbox", { name: /i confirm the updated prices and totals/i }));
    await user.click(screen.getByRole("button", { name: /place order/i }));

    await waitFor(() => {
      expect(mockPlaceCheckout).toHaveBeenCalledWith(
        expect.objectContaining({ confirmPriceChanges: true }),
      );
    });
  });

  it("surfaces a failed address read with a retry", async () => {
    mockFetchAddresses.mockRejectedValue(apiError(500, { message: "An unexpected error occurred" }));
    primeCart();

    renderCheckout();

    expect(await screen.findByText(/something went wrong/i)).toBeInTheDocument();

    primeAddresses();
    await userEvent.click(screen.getByRole("button", { name: /try again/i }));

    expect(await screen.findByRole("radio", { name: /Home/ })).toBeInTheDocument();
  });

  it("generates the idempotency key once per checkout attempt (stable across rerenders)", async () => {
    const uuidSpy = vi.spyOn(crypto, "randomUUID").mockReturnValue("stable-test-key" as `${string}-${string}-${string}-${string}-${string}`);
    try {
      const user = userEvent.setup();
      primeCart();
      primeAddresses();
      mockFetchSlots.mockResolvedValue([makeSlotResponse()]);

      renderCheckout();

      await screen.findByRole("heading", { name: "Delivery address" });
      await user.click(screen.getByRole("radio", { name: /Home/ }));
      await user.click(screen.getByRole("button", { name: /choose delivery slots/i }));

      await waitFor(() => expect(screen.getByText("Petal & Stem")).toBeInTheDocument());
      await user.click(screen.getByRole("radio", { name: /10:00 AM — 11:00 AM/i }));
      await user.click(screen.getByRole("button", { name: /review order/i }));

      await waitFor(() => expect(screen.getByText("Order total")).toBeInTheDocument());

      // Navigate back and forward again to force rerenders; the key must not change.
      await user.click(screen.getByRole("button", { name: /change slots/i }));
      await waitFor(() => expect(screen.getByText("Petal & Stem")).toBeInTheDocument());
      await user.click(screen.getByRole("radio", { name: /10:00 AM — 11:00 AM/i }));
      await user.click(screen.getByRole("button", { name: /review order/i }));

      await waitFor(() => expect(screen.getByText("Order total")).toBeInTheDocument());
      await user.click(screen.getByRole("button", { name: /place order/i }));

      await waitFor(() => expect(screen.getByRole("heading", { name: "Order placed" })).toBeInTheDocument());

      expect(mockPlaceCheckout).toHaveBeenCalledWith(
        expect.objectContaining({ clientOrderId: "stable-test-key" })
      );
      expect(uuidSpy).toHaveBeenCalledTimes(1);
    } finally {
      uuidSpy.mockRestore();
    }
  });

  it("prevents duplicate placement while a request is in flight", async () => {
    const user = userEvent.setup();
    primeCart();
    primeAddresses();
    primeSlots({ "7": [makeSlotResponse()] });

    // A controllable promise simulates a slow placement that stays in flight.
    let resolvePlaceCheckout: (value: unknown) => void = () => {};
    const placePromise = new Promise<unknown>(resolve => {
      resolvePlaceCheckout = resolve;
    });
    mockPlaceCheckout.mockReturnValue(placePromise);

    renderCheckout();

    await screen.findByRole("heading", { name: "Delivery address" });
    await user.click(screen.getByRole("radio", { name: /Home/ }));
    await user.click(screen.getByRole("button", { name: /choose delivery slots/i }));

    await waitFor(() => expect(screen.getByText("Petal & Stem")).toBeInTheDocument());
    await user.click(screen.getByRole("radio", { name: /10:00 AM — 11:00 AM/i }));
    await user.click(screen.getByRole("button", { name: /review order/i }));

    await waitFor(() => expect(screen.getByText("Order total")).toBeInTheDocument());

    const placeButton = screen.getByRole("button", { name: /place order/i });
    await user.click(placeButton);

    await waitFor(() => expect(placeButton).toBeDisabled());

    // A second click on the now-disabled button must not trigger another call.
    await userEvent.click(placeButton);

    expect(mockPlaceCheckout).toHaveBeenCalledTimes(1);

    // Release the in-flight request so the component can settle.
    resolvePlaceCheckout(makeCheckoutResponse());
    await waitFor(() => expect(screen.getByRole("heading", { name: "Order placed" })).toBeInTheDocument());
  });

  it("surfaces a placement error instead of failing silently", async () => {
    const user = userEvent.setup();
    primeCart();
    primeAddresses();
    primeSlots({ "7": [makeSlotResponse()] });
    mockPlaceCheckout.mockRejectedValueOnce(
      apiError(409, { code: "CONFLICT", message: "Checkout already in progress" })
    );

    renderCheckout();

    await screen.findByRole("heading", { name: "Delivery address" });
    await user.click(screen.getByRole("radio", { name: /Home/ }));
    await user.click(screen.getByRole("button", { name: /choose delivery slots/i }));

    await waitFor(() => expect(screen.getByText("Petal & Stem")).toBeInTheDocument());
    await user.click(screen.getByRole("radio", { name: /10:00 AM — 11:00 AM/i }));
    await user.click(screen.getByRole("button", { name: /review order/i }));

    await waitFor(() => expect(screen.getByText("Order total")).toBeInTheDocument());

    await user.click(screen.getByRole("button", { name: /place order/i }));

    await waitFor(() => expect(screen.getByRole("alert")).toBeInTheDocument());

    // The idempotency key is a valid UUID, preserved for retry.
    expect(mockPlaceCheckout.mock.calls[0][0].clientOrderId).toMatch(/^[0-9a-f-]{36}$/);
  });

  it("re-queries the quote when the customer changes slots", async () => {
    const user = userEvent.setup();
    primeCart();
    primeAddresses();
    mockFetchSlots.mockResolvedValue([makeSlotResponse()]);
    mockFetchCheckoutQuote.mockResolvedValue(makeCheckoutQuoteResponse());

    renderCheckout();

    await screen.findByRole("heading", { name: "Delivery address" });
    await user.click(screen.getByRole("radio", { name: /Home/ }));
    await user.click(screen.getByRole("button", { name: /choose delivery slots/i }));

    await waitFor(() => expect(screen.getByText("Petal & Stem")).toBeInTheDocument());
    await user.click(screen.getByRole("radio", { name: /10:00 AM — 11:00 AM/i }));
    await user.click(screen.getByRole("button", { name: /review order/i }));

    await waitFor(() => expect(screen.getByText("Order total")).toBeInTheDocument());
    expect(mockFetchCheckoutQuote).toHaveBeenCalledTimes(1);

    await user.click(screen.getByRole("button", { name: /change slots/i }));

    await waitFor(() => expect(screen.getByText("Petal & Stem")).toBeInTheDocument());
    await user.click(screen.getByRole("radio", { name: /10:00 AM — 11:00 AM/i }));
    await user.click(screen.getByRole("button", { name: /review order/i }));

    await waitFor(() => expect(screen.getByText("Order total")).toBeInTheDocument());
    expect(mockFetchCheckoutQuote).toHaveBeenCalledTimes(2);
  });

  it("clears the cart cache after a successful checkout", async () => {
    const user = userEvent.setup();
    primeCart();
    primeAddresses();
    primeSlots({ "7": [makeSlotResponse()] });

    const { queryClient } = renderCheckout();
    const removeQueriesSpy = vi.spyOn(queryClient, "removeQueries");

    await screen.findByRole("heading", { name: "Delivery address" });
    await user.click(screen.getByRole("radio", { name: /Home/ }));
    await user.click(screen.getByRole("button", { name: /choose delivery slots/i }));

    await waitFor(() => expect(screen.getByText("Petal & Stem")).toBeInTheDocument());
    await user.click(screen.getByRole("radio", { name: /10:00 AM — 11:00 AM/i }));
    await user.click(screen.getByRole("button", { name: /review order/i }));

    await waitFor(() => expect(screen.getByText("Order total")).toBeInTheDocument());
    await user.click(screen.getByRole("button", { name: /place order/i }));

    await waitFor(() => expect(screen.getByRole("heading", { name: "Order placed" })).toBeInTheDocument());

    expect(removeQueriesSpy).toHaveBeenCalledWith({ queryKey: ["cart"] });
  });

  it("maps each vendor's selected slot in a multi-vendor checkout", async () => {
    const user = userEvent.setup();
    primeCart(makeCartResponse({
      shops: [
        makeCartShopView({ vendorId: 7, vendorName: "Petal & Stem" }),
        makeCartShopView({ vendorId: 8, vendorName: "Blossom & Co" }),
      ],
      items: [
        makeCartItem({ vendorId: 7, vendorName: "Petal & Stem", productId: 101 }),
        makeCartItem({ vendorId: 8, vendorName: "Blossom & Co", productId: 102 }),
      ],
    }));
    primeAddresses();
    mockFetchSlots.mockResolvedValue([makeSlotResponse()]);
    mockFetchCheckoutQuote.mockResolvedValue(makeCheckoutQuoteResponse({
      shops: [
        makeCheckoutShopQuote({ vendorId: 7, vendorName: "Petal & Stem" }),
        makeCheckoutShopQuote({ vendorId: 8, vendorName: "Blossom & Co" }),
      ],
    }));

    renderCheckout();

    await screen.findByRole("heading", { name: "Delivery address" });
    await user.click(screen.getByRole("radio", { name: /Home/ }));
    await user.click(screen.getByRole("button", { name: /choose delivery slots/i }));

    await waitFor(() => expect(screen.getByText("Petal & Stem")).toBeInTheDocument());
    await waitFor(() => expect(screen.getByText("Blossom & Co")).toBeInTheDocument());

    const slotRadios = screen.getAllByRole("radio", { name: /10:00 AM — 11:00 AM/i });
    expect(slotRadios).toHaveLength(2);
    await user.click(slotRadios[0]);
    await user.click(slotRadios[1]);

    await user.click(screen.getByRole("button", { name: /review order/i }));

    await waitFor(() => expect(screen.getByText("Order total")).toBeInTheDocument());
    await user.click(screen.getByRole("button", { name: /place order/i }));

    await waitFor(() => expect(screen.getByRole("heading", { name: "Order placed" })).toBeInTheDocument());

    expect(mockPlaceCheckout).toHaveBeenCalledWith({
      addressId: 42,
      clientOrderId: expect.any(String),
      confirmPriceChanges: false,
      items: [
        { slotStart: "2026-10-03T10:00:00", vendorId: 7 },
        { slotStart: "2026-10-03T10:00:00", vendorId: 8 },
      ],
      quoteHash: "abc123def456",
    });
  });

  it("renders every order from a multi-vendor checkout", async () => {
    const user = userEvent.setup();
    primeCart(makeCartResponse({
      shops: [
        makeCartShopView({ vendorId: 7, vendorName: "Petal & Stem" }),
        makeCartShopView({ vendorId: 8, vendorName: "Blossom & Co" }),
      ],
      items: [
        makeCartItem({ vendorId: 7, vendorName: "Petal & Stem", productId: 101 }),
        makeCartItem({ vendorId: 8, vendorName: "Blossom & Co", productId: 102 }),
      ],
    }));
    primeAddresses();
    mockFetchSlots.mockResolvedValue([makeSlotResponse()]);
    mockFetchCheckoutQuote.mockResolvedValue(makeCheckoutQuoteResponse({
      shops: [
        makeCheckoutShopQuote({ vendorId: 7, vendorName: "Petal & Stem" }),
        makeCheckoutShopQuote({ vendorId: 8, vendorName: "Blossom & Co" }),
      ],
    }));
    mockPlaceCheckout.mockResolvedValue(makeCheckoutResponse({
      orders: [
        makeOrderSummary({ vendorName: "Petal & Stem", orderId: 5001 }),
        makeOrderSummary({ vendorName: "Blossom & Co", orderId: 5002 }),
      ],
    }));

    renderCheckout();

    await screen.findByRole("heading", { name: "Delivery address" });
    await user.click(screen.getByRole("radio", { name: /Home/ }));
    await user.click(screen.getByRole("button", { name: /choose delivery slots/i }));

    await waitFor(() => expect(screen.getByText("Petal & Stem")).toBeInTheDocument());
    await waitFor(() => expect(screen.getByText("Blossom & Co")).toBeInTheDocument());

    const slotRadios = screen.getAllByRole("radio", { name: /10:00 AM — 11:00 AM/i });
    await user.click(slotRadios[0]);
    await user.click(slotRadios[1]);

    await user.click(screen.getByRole("button", { name: /review order/i }));

    await waitFor(() => expect(screen.getByText("Order total")).toBeInTheDocument());
    await user.click(screen.getByRole("button", { name: /place order/i }));

    await waitFor(() => expect(screen.getByRole("heading", { name: "Order placed" })).toBeInTheDocument());

    expect(screen.getByText("Petal & Stem")).toBeInTheDocument();
    expect(screen.getByText("Blossom & Co")).toBeInTheDocument();
  });

  it("derives the date picker min from the browser-local date in YYYY-MM-DD format", async () => {
    const user = userEvent.setup();
    primeCart();
    primeAddresses();
    primeSlots({ "7": [makeSlotResponse()] });

    renderCheckout();

    await screen.findByRole("heading", { name: "Delivery address" });
    await user.click(screen.getByRole("radio", { name: /Home/ }));
    await user.click(screen.getByRole("button", { name: /choose delivery slots/i }));

    await waitFor(() => expect(screen.getByText("Petal & Stem")).toBeInTheDocument());

    const dateInput = screen.getByLabelText(/deliver on/i);
    const expectedMin = getBrowserLocalDate();
    expect(dateInput).toHaveAttribute("min", expectedMin);
    expect(expectedMin).toMatch(/^\d{4}-\d{2}-\d{2}$/);

    // Verify browser local date formatter helper with fixed local dates
    const sampleDate = new Date(2026, 9, 21, 14, 30);
    expect(getBrowserLocalDate(sampleDate)).toBe("2026-10-21");
  });

  it("renders guidance and blocks progression when no addresses are saved", async () => {
    primeCart();

    renderCheckout();

    expect(await screen.findByText("You have no saved addresses yet.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /choose delivery slots/i })).not.toBeInTheDocument();
  });

  it("disables proceeding to review when delivery slots are not selected", async () => {
    const user = userEvent.setup();
    primeCart();
    primeAddresses();
    primeSlots({ "7": [makeSlotResponse()] });

    renderCheckout();

    await screen.findByRole("heading", { name: "Delivery address" });
    await user.click(screen.getByRole("radio", { name: /Home/ }));
    await user.click(screen.getByRole("button", { name: /choose delivery slots/i }));

    await waitFor(() => expect(screen.getByText("Petal & Stem")).toBeInTheDocument());

    const reviewButton = screen.getByRole("button", { name: /review order/i });
    expect(reviewButton).toBeDisabled();
    await user.click(reviewButton);
    expect(mockFetchCheckoutQuote).not.toHaveBeenCalled();
  });

  it("shows an empty message and prevents proceeding when no slots are available", async () => {
    const user = userEvent.setup();
    primeCart();
    primeAddresses();
    mockFetchSlots.mockResolvedValue([]);

    renderCheckout();

    await screen.findByRole("heading", { name: "Delivery address" });
    await user.click(screen.getByRole("radio", { name: /Home/ }));
    await user.click(screen.getByRole("button", { name: /choose delivery slots/i }));

    await waitFor(() => expect(screen.getByText("Petal & Stem")).toBeInTheDocument());

    expect(await screen.findByText(/no available slots on/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /review order/i })).toBeDisabled();
  });
});
