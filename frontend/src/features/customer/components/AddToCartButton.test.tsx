import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createMemoryRouter, RouterProvider, useLocation } from "react-router-dom";
import { QueryClientProvider } from "@tanstack/react-query";
import { AddToCartButton } from "@/features/customer/components/AddToCartButton";
import { createTestQueryClient } from "@/test/render";
import type { UserResponse } from "@/features/auth/types";
const mockState = vi.hoisted(() => ({
  user: null as UserResponse | null,
}));

vi.mock("@/features/auth/stores/auth-store", () => ({
  useAuthStore: (selector?: (s: typeof mockState) => unknown) => {
    const state = { user: mockState.user };
    return selector ? selector(state) : state;
  },
}));

const mockAddCartItem = vi.hoisted(() => vi.fn());

vi.mock("@/features/cart/api", () => ({
  addCartItem: mockAddCartItem,
  fetchCart: vi.fn(),
  updateCartItem: vi.fn(),
  deleteCartItem: vi.fn(),
  clearCart: vi.fn(),
}));

/** Shows the `from` the login page would be given, so the redirect can be asserted. */
function FromProbe() {
  const location = useLocation();
  const state = location.state as { from?: string } | null;
  return <p data-testid="from">{state?.from ?? ""}</p>;
}

function renderButton(route = "/vendors/7/products/1001") {
  const router = createMemoryRouter(
    [
      {
        path: "/vendors/:vendorId/products/:productId",
        element: <AddToCartButton productId={1001} productName="Red Rose Bouquet" />,
      },
      { path: "/login", element: <FromProbe /> },
    ],
    { initialEntries: [route] },
  );

  return render(
    <QueryClientProvider client={createTestQueryClient()}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );
}

describe("AddToCartButton", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockState.user = null;
    mockAddCartItem.mockResolvedValue({ cartId: 1, totalItems: 1, subtotal: 899, shops: [], items: [] });
  });

  it("sends an anonymous visitor to the login page", async () => {
    const user = userEvent.setup();
    renderButton();

    await user.click(
      screen.getByRole("button", { name: "Sign in to add Red Rose Bouquet to your cart" }),
    );

    expect(await screen.findByTestId("from")).toBeInTheDocument();
  });

  it("preserves the page the customer was on, so login returns them to it", async () => {
    const user = userEvent.setup();
    renderButton("/vendors/7/products/1001");

    await user.click(
      screen.getByRole("button", { name: "Sign in to add Red Rose Bouquet to your cart" }),
    );

    await waitFor(() => {
      expect(screen.getByTestId("from")).toHaveTextContent("/vendors/7/products/1001");
    });
  });

  it("does not add anything to the cart for an anonymous visitor", async () => {
    const user = userEvent.setup();
    renderButton();

    await user.click(
      screen.getByRole("button", { name: "Sign in to add Red Rose Bouquet to your cart" }),
    );

    expect(mockAddCartItem).not.toHaveBeenCalled();
  });

  it("refuses the button to a signed-in account that is not a customer", () => {
    mockState.user = {
      id: 1,
      email: "petal@example.com",
      fullName: "Petal Owner",
      phone: null,
      role: "FLORIST",
      createdAt: "2026-09-01",
    };

    renderButton();

    const button = screen.getByRole("button", {
      name: "Only customer accounts can add Red Rose Bouquet to a cart",
    });
    expect(button).toBeDisabled();
  });

  it("adds the product for a customer, with the gift note", async () => {
    const user = userEvent.setup();
    mockState.user = {
      id: 2,
      email: "buyer@example.com",
      fullName: "Bea Buyer",
      phone: null,
      role: "CUSTOMER",
      createdAt: "2026-09-02",
    };

    renderButton();

    await user.click(screen.getByRole("button", { name: "Add Red Rose Bouquet to cart" }));

    await waitFor(() => {
      expect(mockAddCartItem).toHaveBeenCalledWith({
        productId: 1001,
        quantity: 1,
        note: null,
      });
    });
  });

  it("confirms the add once the cart has taken it", async () => {
    const user = userEvent.setup();
    mockState.user = {
      id: 2,
      email: "buyer@example.com",
      fullName: "Bea Buyer",
      phone: null,
      role: "CUSTOMER",
      createdAt: "2026-09-02",
    };

    renderButton();

    await user.click(screen.getByRole("button", { name: "Add Red Rose Bouquet to cart" }));

    expect(
      await screen.findByText("Red Rose Bouquet added to your cart."),
    ).toBeInTheDocument();
  });
});
