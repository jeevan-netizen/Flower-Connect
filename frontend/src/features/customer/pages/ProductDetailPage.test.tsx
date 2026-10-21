import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createMemoryRouter, Outlet, RouterProvider } from "react-router-dom";
import { QueryClientProvider } from "@tanstack/react-query";
import { ProductDetailPage } from "@/features/customer/pages/ProductDetailPage";
import { PRODUCT_ROUTE_IDS } from "@/features/customer/navigation";
import { useLocationStore } from "@/features/location/stores/location-store";
import { createTestQueryClient } from "@/test/render";
import {
  makePublicProduct,
  makePublicProductImage,
  makeServiceLocations,
} from "@/test/factories";
import type { ServiceLocationGroup } from "@/features/location/types";
import type { UserResponse } from "@/features/auth/types";

const mockFetchPublicProduct = vi.hoisted(() => vi.fn());
const mockAddCartItem = vi.hoisted(() => vi.fn());

vi.mock("@/features/customer/api", () => ({
  fetchPublicProduct: mockFetchPublicProduct,
  fetchDiscovery: vi.fn(),
  fetchSearch: vi.fn(),
  fetchStorefront: vi.fn(),
  fetchCategories: vi.fn(),
}));

vi.mock("@/features/cart/api", () => ({
  addCartItem: mockAddCartItem,
  fetchCart: vi.fn(),
  updateCartItem: vi.fn(),
  deleteCartItem: vi.fn(),
  clearCart: vi.fn(),
}));

const mockState = vi.hoisted(() => ({
  user: null as UserResponse | null,
}));

vi.mock("@/features/auth/stores/auth-store", () => ({
  useAuthStore: (selector?: (s: typeof mockState) => unknown) => {
    const state = { user: mockState.user };
    return selector ? selector(state) : state;
  },
}));

const mockQuery = vi.hoisted(() => ({
  cities: undefined as ServiceLocationGroup[] | undefined,
}));

vi.mock("@/features/location/queries", () => ({
  useServiceLocations: () => ({
    data: mockQuery.cities,
    isPending: mockQuery.cities === undefined,
    error: null,
    isSuccess: mockQuery.cities !== undefined,
    refetch: vi.fn(),
  }),
}));

const KORAMANGALA = {
  id: 4,
  city: "Bengaluru",
  area: "Koramangala",
  pincode: "560034",
  latitude: 12.9352,
  longitude: 77.6245,
};

const closeSpy = vi.hoisted(() => vi.fn());

function renderProduct(path = "/vendors/7/products/1001") {
  const router = createMemoryRouter(
    [{ path: "/vendors/:vendorId/products/:productId", element: <ProductDetailPage /> }],
    { initialEntries: [path] },
  );

  return render(
    <QueryClientProvider client={createTestQueryClient()}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );
}

/**
 * A surface for the product route, the way the storefront and the search
 * page host it: the product renders inside the surface's outlet and learns
 * from the outlet context whether it is an overlay.
 */
function renderWithinSurface({ overlay, path }: { overlay: boolean; path?: string }) {
  function TestSurface() {
    return (
      <Outlet
        context={{
          isOverlay: overlay,
          close: closeSpy,
          returnPath: "/vendors/7",
          returnLabel: "the shop",
        }}
      />
    );
  }

  const router = createMemoryRouter(
    [
      {
        path: "/vendors/:vendorId",
        element: <TestSurface />,
        children: [
          {
            path: "products/:productId",
            id: PRODUCT_ROUTE_IDS.storefront,
            element: <ProductDetailPage />,
          },
        ],
      },
    ],
    { initialEntries: [path ?? "/vendors/7/products/1001"] },
  );

  return render(
    <QueryClientProvider client={createTestQueryClient()}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );
}

describe("ProductDetailPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    sessionStorage.clear();
    useLocationStore.setState({ selected: null });
    mockState.user = null;
    mockQuery.cities = makeServiceLocations();
    mockFetchPublicProduct.mockResolvedValue(makePublicProduct());
    mockAddCartItem.mockResolvedValue({
      cartId: 1,
      totalItems: 1,
      subtotal: 899,
      shops: [],
      items: [],
    });
  });

  it("asks for a delivery area and makes no product request without one", () => {
    renderProduct();

    expect(
      screen.getByRole("heading", { name: "Choose your delivery area" }),
    ).toBeInTheDocument();
    expect(mockFetchPublicProduct).not.toHaveBeenCalled();
  });

  it("shows the product for the chosen area", async () => {
    useLocationStore.setState({ selected: KORAMANGALA });

    renderProduct();

    expect(
      await screen.findByRole("heading", { name: "Red Rose Bouquet", level: 1 }),
    ).toBeInTheDocument();
    expect(mockFetchPublicProduct).toHaveBeenCalledWith(1001, 4, expect.anything());
  });

  it("shows the florist, the category and the description", async () => {
    useLocationStore.setState({ selected: KORAMANGALA });

    renderProduct();

    await screen.findByRole("heading", { name: "Red Rose Bouquet", level: 1 });
    expect(screen.getByText("Petal & Stem")).toBeInTheDocument();
    expect(screen.getByText("Roses")).toBeInTheDocument();
    expect(screen.getByText("A dozen deep red roses.")).toBeInTheDocument();
  });

  it("renders a placeholder rather than an image", async () => {
    useLocationStore.setState({ selected: KORAMANGALA });

    const { container } = renderProduct();

    await screen.findByRole("heading", { name: "Red Rose Bouquet", level: 1 });
    expect(container.querySelector("img")).toBeNull();
    expect(screen.getByRole("img", { name: /Photo of Red Rose Bouquet/ })).toBeInTheDocument();
  });

  it("sends the gift note with the add", async () => {
    const user = userEvent.setup();
    useLocationStore.setState({ selected: KORAMANGALA });
    mockState.user = {
      id: 2,
      email: "buyer@example.com",
      fullName: "Bea Buyer",
      phone: null,
      role: "CUSTOMER",
      createdAt: "2026-09-02",
    };

    renderProduct();
    await screen.findByRole("heading", { name: "Red Rose Bouquet", level: 1 });

    await user.type(screen.getByLabelText("Gift note (optional)"), "Happy birthday!");
    await user.click(screen.getByRole("button", { name: "Add Red Rose Bouquet to cart" }));

    await waitFor(() => {
      expect(mockAddCartItem).toHaveBeenCalledWith({
        productId: 1001,
        quantity: 1,
        note: "Happy birthday!",
      });
    });
  });

  it("sends the quantity the customer chose", async () => {
    const user = userEvent.setup();
    useLocationStore.setState({ selected: KORAMANGALA });
    mockState.user = {
      id: 2,
      email: "buyer@example.com",
      fullName: "Bea Buyer",
      phone: null,
      role: "CUSTOMER",
      createdAt: "2026-09-02",
    };

    renderProduct();
    await screen.findByRole("heading", { name: "Red Rose Bouquet", level: 1 });

    await user.clear(screen.getByLabelText("Quantity"));
    await user.type(screen.getByLabelText("Quantity"), "3");
    await user.click(screen.getByRole("button", { name: "Add Red Rose Bouquet to cart" }));

    await waitFor(() => {
      expect(mockAddCartItem).toHaveBeenCalledWith(
        expect.objectContaining({ quantity: 3 }),
      );
    });
  });

  it("says a product that is not available is not available", async () => {
    useLocationStore.setState({ selected: KORAMANGALA });
    mockFetchPublicProduct.mockRejectedValue({
      isAxiosError: true,
      response: { status: 404, data: { code: "NOT_FOUND", message: "Product not found" } },
    });

    renderProduct();

    expect(
      await screen.findByRole("heading", { name: "This product is not available" }),
    ).toBeInTheDocument();
  });

  it("caps the gift note at the length the backend accepts", async () => {
    useLocationStore.setState({ selected: KORAMANGALA });

    renderProduct();

    await screen.findByRole("heading", { name: "Red Rose Bouquet", level: 1 });
    expect(screen.getByLabelText("Gift note (optional)")).toHaveAttribute(
      "maxlength",
      "1000",
    );
  });

  it("shows the product as a dialog when the surface presents it as an overlay", async () => {
    useLocationStore.setState({ selected: KORAMANGALA });

    renderWithinSurface({ overlay: true });

    await screen.findByRole("heading", { name: "Red Rose Bouquet", level: 1 });
    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Back to the shop" })).toBeNull();
  });

  it("offers a return link when the product is on its own", async () => {
    useLocationStore.setState({ selected: KORAMANGALA });

    renderWithinSurface({ overlay: false });

    await screen.findByRole("heading", { name: "Red Rose Bouquet", level: 1 });
    expect(screen.queryByRole("dialog")).toBeNull();
    expect(screen.getByRole("link", { name: "Back to the shop" })).toHaveAttribute(
      "href",
      "/vendors/7",
    );
  });

  it("dismisses the overlay with the close control", async () => {
    const user = userEvent.setup();
    useLocationStore.setState({ selected: KORAMANGALA });

    renderWithinSurface({ overlay: true });
    await screen.findByRole("dialog");

    await user.click(screen.getByRole("button", { name: "Close product details" }));

    expect(closeSpy).toHaveBeenCalled();
  });

  it("dismisses the overlay with Escape", async () => {
    const user = userEvent.setup();
    useLocationStore.setState({ selected: KORAMANGALA });

    renderWithinSurface({ overlay: true });
    await screen.findByRole("dialog");

    await user.keyboard("{Escape}");

    expect(closeSpy).toHaveBeenCalled();
  });

  it("caps the quantity at the cart's maximum", async () => {
    const user = userEvent.setup();
    useLocationStore.setState({ selected: KORAMANGALA });
    mockState.user = {
      id: 2,
      email: "buyer@example.com",
      fullName: "Bea Buyer",
      phone: null,
      role: "CUSTOMER",
      createdAt: "2026-09-02",
    };

    renderProduct();
    await screen.findByRole("heading", { name: "Red Rose Bouquet", level: 1 });

    expect(screen.getByLabelText("Quantity")).toHaveAttribute("max", "99");

    await user.clear(screen.getByLabelText("Quantity"));
    await user.type(screen.getByLabelText("Quantity"), "100");
    await user.click(screen.getByRole("button", { name: "Add Red Rose Bouquet to cart" }));

    await waitFor(() => {
      expect(mockAddCartItem).toHaveBeenCalledWith(
        expect.objectContaining({ quantity: 99 }),
      );
    });
  });

  it("keeps an empty quantity editable instead of snapping it back", async () => {
    const user = userEvent.setup();
    useLocationStore.setState({ selected: KORAMANGALA });

    renderProduct();
    await screen.findByRole("heading", { name: "Red Rose Bouquet", level: 1 });

    const quantity = screen.getByLabelText("Quantity");
    await user.clear(quantity);

    // The field keeps the customer's own text, so the caret is not fought.
    expect(quantity).toHaveValue(null);
    await user.type(quantity, "3");
    expect(quantity).toHaveValue(3);
  });

  it("shows the primary image even when it is not the first one", async () => {
    useLocationStore.setState({ selected: KORAMANGALA });
    mockFetchPublicProduct.mockResolvedValue(
      makePublicProduct({
        images: [
          makePublicProductImage({ id: 1, originalFilename: "first.jpg", primary: false }),
          makePublicProductImage({ id: 2, originalFilename: "cover.jpg", primary: true }),
        ],
      }),
    );

    renderProduct();

    await screen.findByRole("heading", { name: "Red Rose Bouquet", level: 1 });
    expect(
      screen.getByRole("img", { name: /Photo of Red Rose Bouquet: cover\.jpg/ }),
    ).toBeInTheDocument();
  });

  it("falls back to the first image when none is marked primary", async () => {
    useLocationStore.setState({ selected: KORAMANGALA });
    mockFetchPublicProduct.mockResolvedValue(
      makePublicProduct({
        images: [
          makePublicProductImage({ id: 1, originalFilename: "first.jpg", primary: false }),
          makePublicProductImage({ id: 2, originalFilename: "second.jpg", primary: false }),
        ],
      }),
    );

    renderProduct();

    await screen.findByRole("heading", { name: "Red Rose Bouquet", level: 1 });
    expect(
      screen.getByRole("img", { name: /Photo of Red Rose Bouquet: first\.jpg/ }),
    ).toBeInTheDocument();
  });

  it("makes no request for a malformed product id", async () => {
    useLocationStore.setState({ selected: KORAMANGALA });

    renderProduct("/vendors/7/products/not-a-number");

    expect(
      await screen.findByRole("heading", { name: "This product is not available" }),
    ).toBeInTheDocument();
    expect(mockFetchPublicProduct).not.toHaveBeenCalled();
  });
});
