import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { CartPage } from "@/features/cart/pages/CartPage";
import { renderWithProviders } from "@/test/render";
import { makeCartResponse, makeCartItem, makeCartShopView } from "@/test/factories";
import { apiError } from "@/test/api-errors";
import type { CartResponse } from "@/features/cart/types";

const mockFetchCart = vi.hoisted(() => vi.fn());
const mockUpdateCartItem = vi.hoisted(() => vi.fn());
const mockDeleteCartItem = vi.hoisted(() => vi.fn());

vi.mock("@/features/cart/api", () => ({
  fetchCart: mockFetchCart,
  addCartItem: vi.fn(),
  updateCartItem: mockUpdateCartItem,
  deleteCartItem: mockDeleteCartItem,
  clearCart: vi.fn(),
}));

function renderCart() {
  return renderWithProviders(<CartPage />);
}

function primeCart(cart: CartResponse = makeCartResponse()) {
  mockFetchCart.mockResolvedValue(cart);
  return cart;
}

describe("CartPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUpdateCartItem.mockResolvedValue(makeCartResponse());
    mockDeleteCartItem.mockResolvedValue(makeCartResponse());
  });

  it("lists cart items grouped by vendor with totals", async () => {
    primeCart(
      makeCartResponse({
        shops: [
          makeCartShopView({ vendorId: 7, vendorName: "Petal & Stem", itemCount: 2, subtotal: 1798 }),
          makeCartShopView({ vendorId: 8, vendorName: "Rose & Co", itemCount: 1, subtotal: 549 }),
        ],
        items: [
          makeCartItem({ id: 1, productName: "Red Rose Bouquet", vendorId: 7, vendorName: "Petal & Stem", quantity: 2, priceSnapshot: 899, subtotal: 1798 }),
          makeCartItem({ id: 2, productName: "Mixed Bouquet", vendorId: 7, vendorName: "Petal & Stem", quantity: 1, priceSnapshot: 449, subtotal: 449 }),
          makeCartItem({ id: 3, productName: "Lily Bunch", vendorId: 8, vendorName: "Rose & Co", quantity: 1, priceSnapshot: 549, subtotal: 549 }),
        ],
      }),
    );

    renderCart();

    await screen.findByText("Petal & Stem");
    await screen.findByText("Rose & Co");
    expect(await screen.findByText("Red Rose Bouquet")).toBeInTheDocument();
    expect(await screen.findByText("Mixed Bouquet")).toBeInTheDocument();
    expect(await screen.findByText("Lily Bunch")).toBeInTheDocument();
  });

  it("increases quantity through the update API", async () => {
    const user = userEvent.setup();
    primeCart();
    renderCart();

    await screen.findByText("Red Rose Bouquet");
    await user.click(screen.getByRole("button", { name: /increase red rose bouquet quantity/i }));

    await waitFor(() => {
      expect(mockUpdateCartItem).toHaveBeenCalledWith(1, { quantity: 3 });
    });
  });

  it("decreases quantity through the update API", async () => {
    const user = userEvent.setup();
    primeCart(makeCartResponse({ items: [makeCartItem({ id: 1, quantity: 2 })] }));
    renderCart();

    await screen.findByText("Red Rose Bouquet");
    await user.click(screen.getByRole("button", { name: /reduce red rose bouquet quantity/i }));

    await waitFor(() => {
      expect(mockUpdateCartItem).toHaveBeenCalledWith(1, { quantity: 1 });
    });
  });

  it("does not decrease below 1", async () => {
    primeCart(makeCartResponse({ items: [makeCartItem({ id: 1, quantity: 1 })] }));

    renderCart();

    await screen.findByText("Red Rose Bouquet");
    const reduce = screen.getByRole("button", { name: /reduce red rose bouquet quantity/i });
    expect(reduce).toBeDisabled();
  });

  it("removes an item through the delete API", async () => {
    const user = userEvent.setup();
    primeCart();
    renderCart();

    await screen.findByText("Red Rose Bouquet");
    await user.click(screen.getByRole("button", { name: /remove red rose bouquet from cart/i }));

    await waitFor(() => {
      expect(mockDeleteCartItem).toHaveBeenCalledWith(1);
    });
  });

  it("shows price drift when the current price differs from the snapshot", async () => {
    primeCart(
      makeCartResponse({
        items: [
          makeCartItem({
            id: 1,
            productName: "Red Rose Bouquet",
            priceSnapshot: 899,
            currentPrice: 949,
            priceChanged: true,
          }),
        ],
      }),
    );

    renderCart();

    expect(await screen.findByText(/price changed since you added this/i)).toBeInTheDocument();
  });

  it("shows a reason when an item is not purchasable", async () => {
    primeCart(
      makeCartResponse({
        shops: [makeCartShopView({ purchasable: false })],
        items: [
          makeCartItem({
            id: 1,
            productName: "Red Rose Bouquet",
            purchasable: false,
            notPurchasableReason: "Out of stock",
          }),
        ],
      }),
    );

    renderCart();

    expect(await screen.findByText("Out of stock")).toBeInTheDocument();
  });

  it("disables checkout when any item is not purchasable", async () => {
    primeCart(
      makeCartResponse({
        shops: [makeCartShopView({ purchasable: false })],
        items: [makeCartItem({ purchasable: false })],
      }),
    );

    renderCart();

    await screen.findByText("Red Rose Bouquet");
    expect(screen.getByRole("button", { name: /proceed to checkout/i })).toBeDisabled();
  });

  it("shows an empty state when the cart has no items", async () => {
    primeCart(makeCartResponse({ items: [], shops: [], totalItems: 0, subtotal: 0 }));

    renderCart();

    expect(await screen.findByText(/your cart is empty/i)).toBeInTheDocument();
    expect(await screen.findByRole("link", { name: /start browsing/i })).toHaveAttribute(
      "href",
      "/browse",
    );
  });

  it("surfaces a failed cart read with a retry", async () => {
    mockFetchCart.mockRejectedValue(apiError(500, { message: "An unexpected error occurred" }));

    renderCart();

    expect(await screen.findByText(/something went wrong/i)).toBeInTheDocument();

    primeCart();
    await userEvent.click(screen.getByRole("button", { name: /try again/i }));

    expect(await screen.findByText("Red Rose Bouquet")).toBeInTheDocument();
  });
});
