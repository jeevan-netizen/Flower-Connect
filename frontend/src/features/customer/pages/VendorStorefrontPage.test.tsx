import { describe, it, expect, vi, beforeEach } from "vitest";
import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createMemoryRouter, RouterProvider } from "react-router-dom";
import { QueryClientProvider } from "@tanstack/react-query";
import { VendorStorefrontPage } from "@/features/customer/pages/VendorStorefrontPage";
import { ProductDetailPage } from "@/features/customer/pages/ProductDetailPage";
import { PRODUCT_ROUTE_IDS } from "@/features/customer/navigation";
import { useLocationStore } from "@/features/location/stores/location-store";
import { createTestQueryClient } from "@/test/render";
import { makePublicProduct, makeServiceLocations, makeStorefront } from "@/test/factories";
import type { ServiceLocationGroup } from "@/features/location/types";

const mockFetchStorefront = vi.hoisted(() => vi.fn());
const mockFetchPublicProduct = vi.hoisted(() => vi.fn());

vi.mock("@/features/customer/api", () => ({
  fetchStorefront: mockFetchStorefront,
  fetchPublicProduct: mockFetchPublicProduct,
  fetchDiscovery: vi.fn(),
  fetchSearch: vi.fn(),
  fetchCategories: vi.fn(),
}));

vi.mock("@/features/cart/api", () => ({
  addCartItem: vi.fn(),
  fetchCart: vi.fn(),
  updateCartItem: vi.fn(),
  deleteCartItem: vi.fn(),
  clearCart: vi.fn(),
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

function renderStorefront(path: string | { pathname: string; state?: unknown } = "/vendors/7") {
  const router = createMemoryRouter(
    [
      {
        path: "/vendors/:vendorId",
        element: <VendorStorefrontPage />,
        children: [
          { index: true, element: null },
          {
            path: "products/:productId",
            id: PRODUCT_ROUTE_IDS.storefront,
            element: <ProductDetailPage />,
          },
        ],
      },
    ],
    { initialEntries: [path] },
  );

  render(
    <QueryClientProvider client={createTestQueryClient()}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );

  return router;
}

describe("VendorStorefrontPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    sessionStorage.clear();
    useLocationStore.setState({ selected: null });
    mockQuery.cities = makeServiceLocations();
    mockFetchStorefront.mockResolvedValue(makeStorefront());
    mockFetchPublicProduct.mockResolvedValue(makePublicProduct());
  });

  it("shows the shop's identity, hours and products", async () => {
    renderStorefront();

    expect(
      await screen.findByRole("heading", { name: "Petal & Stem", level: 1 }),
    ).toBeInTheDocument();
    expect(await screen.findByText("Monday")).toBeInTheDocument();
    expect(await screen.findByText("09:00 – 18:00")).toBeInTheDocument();
    expect(await screen.findByRole("heading", { name: "Red Rose Bouquet" })).toBeInTheDocument();
  });

  it("reads the shop without a delivery area, because the endpoint takes none", async () => {
    renderStorefront();

    expect(
      await screen.findByRole("heading", { name: "Petal & Stem", level: 1 }),
    ).toBeInTheDocument();
    expect(mockFetchStorefront).toHaveBeenCalledWith(7, 0, 20, expect.anything());
  });

  it("advises about the delivery area without blocking the shop", async () => {
    renderStorefront();

    expect(await screen.findByText("Set your delivery area")).toBeInTheDocument();
    // The shop itself is still shown: the advisory is not a gate.
    expect(screen.getByRole("heading", { name: "Petal & Stem", level: 1 })).toBeInTheDocument();
  });

  it("drops the advisory once an area is chosen", async () => {
    useLocationStore.setState({ selected: KORAMANGALA });

    renderStorefront();

    await screen.findByRole("heading", { name: "Petal & Stem", level: 1 });
    expect(screen.queryByText("Set your delivery area")).toBeNull();
  });

  it("says a shop that is not available is not available", async () => {
    mockFetchStorefront.mockRejectedValue({
      isAxiosError: true,
      response: { status: 404, data: { code: "NOT_FOUND", message: "Vendor not found" } },
    });

    renderStorefront();

    expect(
      await screen.findByRole("heading", { name: "This shop is not available" }),
    ).toBeInTheDocument();
  });

  it("renders a product opened from the shop over the shop", async () => {
    const user = userEvent.setup();
    useLocationStore.setState({ selected: KORAMANGALA });
    renderStorefront();

    await screen.findByRole("heading", { name: "Petal & Stem", level: 1 });

    // The product link carries the overlay marker, so the shop stays behind
    // the product.
    await user.click(screen.getByRole("link", { name: "Red Rose Bouquet" }));

    await waitFor(() => {
      expect(screen.getByRole("dialog")).toBeInTheDocument();
    });
    // The shop is still on screen behind it.
    expect(screen.getByRole("heading", { name: "Petal & Stem", level: 1 })).toBeInTheDocument();
  });

  it("returns to the shop when the overlay is closed, without a duplicate entry", async () => {
    const user = userEvent.setup();
    useLocationStore.setState({ selected: KORAMANGALA });
    const router = renderStorefront();

    await screen.findByRole("heading", { name: "Petal & Stem", level: 1 });
    await user.click(screen.getByRole("link", { name: "Red Rose Bouquet" }));
    await screen.findByRole("dialog");

    await user.click(screen.getByRole("button", { name: "Close product details" }));

    await waitFor(() => {
      expect(screen.queryByRole("dialog")).toBeNull();
    });
    expect(router.state.location.pathname).toBe("/vendors/7");

    // The overlay entry was popped, not pushed over: going back does not
    // bring the product back.
    await act(async () => { await router.navigate(-1); });
    await waitFor(() => {
      expect(screen.queryByRole("dialog")).toBeNull();
    });
  });

  it("returns to the shop on Escape, the same as the close control", async () => {
    const user = userEvent.setup();
    useLocationStore.setState({ selected: KORAMANGALA });
    const router = renderStorefront();

    await screen.findByRole("heading", { name: "Petal & Stem", level: 1 });
    await user.click(screen.getByRole("link", { name: "Red Rose Bouquet" }));
    await screen.findByRole("dialog");

    await user.keyboard("{Escape}");

    await waitFor(() => {
      expect(screen.queryByRole("dialog")).toBeNull();
    });
    expect(router.state.location.pathname).toBe("/vendors/7");
  });

  it("goes back to the shop with the browser back button", async () => {
    const user = userEvent.setup();
    useLocationStore.setState({ selected: KORAMANGALA });
    const router = renderStorefront();

    await screen.findByRole("heading", { name: "Petal & Stem", level: 1 });
    await user.click(screen.getByRole("link", { name: "Red Rose Bouquet" }));
    await screen.findByRole("dialog");

    await act(async () => { await router.navigate(-1); });

    await waitFor(() => {
      expect(screen.queryByRole("dialog")).toBeNull();
    });
    expect(screen.getByRole("heading", { name: "Petal & Stem", level: 1 })).toBeInTheDocument();
  });

  it("shows the overlay again on forward, rather than a standalone product", async () => {
    const user = userEvent.setup();
    useLocationStore.setState({ selected: KORAMANGALA });
    const router = renderStorefront();

    await screen.findByRole("heading", { name: "Petal & Stem", level: 1 });
    await user.click(screen.getByRole("link", { name: "Red Rose Bouquet" }));
    await screen.findByRole("dialog");

    await act(async () => { await router.navigate(-1); });
    await waitFor(() => {
      expect(screen.queryByRole("dialog")).toBeNull();
    });

    await act(async () => { await router.navigate(1); });

    await waitFor(() => {
      expect(screen.getByRole("dialog")).toBeInTheDocument();
    });
  });

  it("renders a product reached directly on its own, with no shop behind it", async () => {
    useLocationStore.setState({ selected: KORAMANGALA });

    renderStorefront("/vendors/7/products/1001");

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: "Red Rose Bouquet", level: 1 })).toBeInTheDocument();
    });
    expect(screen.queryByRole("dialog")).toBeNull();
    expect(screen.queryByRole("heading", { name: "Petal & Stem", level: 1 })).toBeNull();
  });

  it("does not fetch the shop for a standalone product entry", async () => {
    useLocationStore.setState({ selected: KORAMANGALA });

    renderStorefront("/vendors/7/products/1001");

    await screen.findByRole("heading", { name: "Red Rose Bouquet", level: 1 });
    // The shop is not behind the product, so the request would be discarded.
    expect(mockFetchStorefront).not.toHaveBeenCalled();
  });

  it("offers a way back from a standalone product", async () => {
    useLocationStore.setState({ selected: KORAMANGALA });

    renderStorefront("/vendors/7/products/1001");

    expect(
      await screen.findByRole("link", { name: "Back to the shop" }),
    ).toHaveAttribute("href", "/vendors/7");
  });

  it("makes no request for a malformed florist id", async () => {
    renderStorefront("/vendors/not-a-number");

    expect(
      await screen.findByRole("heading", { name: "This shop is not available" }),
    ).toBeInTheDocument();
    expect(mockFetchStorefront).not.toHaveBeenCalled();
  });

  it("renders on its own when the overlay marker is from another page load", async () => {
    useLocationStore.setState({ selected: KORAMANGALA });

    // A reload restores the history entry's navigation state, but the page
    // load is new and so is the token — the marker no longer matches.
    renderStorefront({
      pathname: "/vendors/7/products/1001",
      state: { productOverlay: true, overlayToken: "a-stale-token" },
    });

    await screen.findByRole("heading", { name: "Red Rose Bouquet", level: 1 });
    expect(screen.queryByRole("dialog")).toBeNull();
  });
});
