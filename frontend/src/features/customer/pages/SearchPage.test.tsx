import { describe, it, expect, vi, beforeEach } from "vitest";
import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createMemoryRouter, RouterProvider } from "react-router-dom";
import { QueryClientProvider } from "@tanstack/react-query";
import { SearchPage } from "@/features/customer/pages/SearchPage";
import { ProductDetailPage } from "@/features/customer/pages/ProductDetailPage";
import { PRODUCT_ROUTE_IDS } from "@/features/customer/navigation";
import { useSearchStore } from "@/features/customer/stores/search-store";
import { DEFAULT_SEARCH_FILTERS } from "@/features/customer/types";
import { useLocationStore } from "@/features/location/stores/location-store";
import { createTestQueryClient } from "@/test/render";
import {
  makeCategory,
  makePage,
  makePublicProduct,
  makeServiceLocations,
} from "@/test/factories";
import type { ServiceLocationGroup } from "@/features/location/types";

const mockFetchSearch = vi.hoisted(() => vi.fn());
const mockFetchCategories = vi.hoisted(() => vi.fn());
const mockFetchPublicProduct = vi.hoisted(() => vi.fn());

vi.mock("@/features/customer/api", () => ({
  fetchSearch: mockFetchSearch,
  fetchDiscovery: vi.fn(),
  fetchStorefront: vi.fn(),
  fetchPublicProduct: mockFetchPublicProduct,
  fetchCategories: mockFetchCategories,
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

/**
 * The search page is a route element, and the surface for its product child,
 * so it is rendered inside a data router — the same shape it has in the app.
 */
function renderSearch(path = "/search") {
  const router = createMemoryRouter(
    [
      {
        path: "/search",
        element: <SearchPage />,
        children: [
          { index: true, element: null },
          {
            path: "products/:productId",
            id: PRODUCT_ROUTE_IDS.search,
            element: <ProductDetailPage />,
          },
        ],
      },
    ],
    { initialEntries: [path] },
  );

  const view = render(
    <QueryClientProvider client={createTestQueryClient()}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );

  return { router, unmount: view.unmount };
}

describe("SearchPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    sessionStorage.clear();
    useLocationStore.setState({ selected: null });
    useSearchStore.setState({ filters: { ...DEFAULT_SEARCH_FILTERS } });
    mockQuery.cities = makeServiceLocations();
    mockFetchSearch.mockResolvedValue(makePage([makePublicProduct()]));
    mockFetchCategories.mockResolvedValue(makePage([makeCategory({ id: 1, name: "Roses" })]));
    mockFetchPublicProduct.mockResolvedValue(makePublicProduct());
  });

  it("asks for a delivery area and makes no search request without one", () => {
    renderSearch();

    expect(
      screen.getByRole("heading", { name: "Choose your delivery area" }),
    ).toBeInTheDocument();
    expect(mockFetchSearch).not.toHaveBeenCalled();
  });

  it("searches for the chosen area", async () => {
    useLocationStore.setState({ selected: KORAMANGALA });

    renderSearch();

    await waitFor(() => {
      expect(mockFetchSearch).toHaveBeenCalledWith(
        4,
        expect.objectContaining({ q: "", categoryId: null, sort: "distance", page: 0 }),
        expect.anything(),
      );
    });
  });

  it("shows the products that came back", async () => {
    useLocationStore.setState({ selected: KORAMANGALA });

    renderSearch();

    expect(await screen.findByRole("heading", { name: "Red Rose Bouquet" })).toBeInTheDocument();
  });

  it("sends the search term once it is submitted", async () => {
    const user = userEvent.setup();
    useLocationStore.setState({ selected: KORAMANGALA });

    renderSearch();
    await screen.findByRole("heading", { name: "Red Rose Bouquet" });

    await user.type(screen.getByLabelText("Search products"), "roses{Enter}");

    await waitFor(() => {
      expect(mockFetchSearch).toHaveBeenLastCalledWith(
        4,
        expect.objectContaining({ q: "roses" }),
        expect.anything(),
      );
    });
  });

  it("sends the chosen category and returns to the first page", async () => {
    const user = userEvent.setup();
    useLocationStore.setState({ selected: KORAMANGALA });

    renderSearch();
    await screen.findByRole("heading", { name: "Red Rose Bouquet" });

    await user.click(screen.getByRole("button", { name: "Roses" }));

    await waitFor(() => {
      expect(mockFetchSearch).toHaveBeenLastCalledWith(
        4,
        expect.objectContaining({ categoryId: 1, page: 0 }),
        expect.anything(),
      );
    });
  });

  it("offers the public categories as filter chips", async () => {
    useLocationStore.setState({ selected: KORAMANGALA });

    renderSearch();

    expect(await screen.findByRole("button", { name: "Roses" })).toBeInTheDocument();
  });

  it("refuses price bounds that cannot both be true, and does not send them", async () => {
    const user = userEvent.setup();
    useLocationStore.setState({ selected: KORAMANGALA });

    renderSearch();
    await screen.findByRole("heading", { name: "Red Rose Bouquet" });

    const min = screen.getByLabelText("Minimum price");
    const max = screen.getByLabelText("Maximum price");
    await user.clear(min);
    await user.type(min, "900");
    await user.clear(max);
    await user.type(max, "100");
    await user.tab();

    expect(
      await screen.findByText(
        "The minimum price must not be greater than the maximum price.",
      ),
    ).toBeInTheDocument();
    expect(mockFetchSearch).not.toHaveBeenLastCalledWith(
      4,
      expect.objectContaining({ priceMin: 900, priceMax: 100 }),
      expect.anything(),
    );
  });

  it("says so when nothing matches", async () => {
    useLocationStore.setState({ selected: KORAMANGALA });
    mockFetchSearch.mockResolvedValue(makePage([]));

    renderSearch();

    expect(await screen.findByText("No products match")).toBeInTheDocument();
  });

  it("shows an error state when the search fails", async () => {
    useLocationStore.setState({ selected: KORAMANGALA });
    mockFetchSearch.mockRejectedValue(new Error("boom"));

    renderSearch();

    expect(await screen.findByRole("alert")).toBeInTheDocument();
  });

  it("shows a product opened from the results over the results", async () => {
    const user = userEvent.setup();
    useLocationStore.setState({ selected: KORAMANGALA });
    renderSearch();

    await screen.findByRole("heading", { name: "Red Rose Bouquet" });

    await user.click(screen.getByRole("link", { name: "Red Rose Bouquet" }));

    await screen.findByRole("dialog");
    // The results are still on screen behind the product.
    expect(screen.getByRole("heading", { name: "Search products" })).toBeInTheDocument();
  });

  it("returns to the results when the product is dismissed", async () => {
    const user = userEvent.setup();
    useLocationStore.setState({ selected: KORAMANGALA });
    const { router } = renderSearch();

    await screen.findByRole("heading", { name: "Red Rose Bouquet" });
    await user.click(screen.getByRole("link", { name: "Red Rose Bouquet" }));
    await screen.findByRole("dialog");

    await user.click(screen.getByRole("button", { name: "Close product details" }));

    await waitFor(() => {
      expect(screen.queryByRole("dialog")).toBeNull();
    });
    expect(router.state.location.pathname).toBe("/search");
  });

  it("keeps the filters when the product is dismissed", async () => {
    const user = userEvent.setup();
    useLocationStore.setState({ selected: KORAMANGALA });
    renderSearch();

    await screen.findByRole("heading", { name: "Red Rose Bouquet" });
    await user.type(screen.getByLabelText("Search products"), "roses{Enter}");
    await waitFor(() => {
      expect(mockFetchSearch).toHaveBeenLastCalledWith(
        4,
        expect.objectContaining({ q: "roses" }),
        expect.anything(),
      );
    });

    await user.click(screen.getByRole("link", { name: "Red Rose Bouquet" }));
    await screen.findByRole("dialog");
    await user.click(screen.getByRole("button", { name: "Close product details" }));
    await waitFor(() => {
      expect(screen.queryByRole("dialog")).toBeNull();
    });

    // The filters live in the store, so they survive the round trip — and the
    // request that follows uses them rather than the defaults.
    expect(useSearchStore.getState().filters.q).toBe("roses");
    expect(screen.getByLabelText("Search products")).toHaveValue("roses");
    await waitFor(() => {
      expect(mockFetchSearch).toHaveBeenLastCalledWith(
        4,
        expect.objectContaining({ q: "roses" }),
        expect.anything(),
      );
    });
  });

  it("keeps the filters when the page is remounted", async () => {
    const user = userEvent.setup();
    useLocationStore.setState({ selected: KORAMANGALA });
    const { unmount } = renderSearch();

    await screen.findByRole("heading", { name: "Red Rose Bouquet" });
    await user.type(screen.getByLabelText("Search products"), "roses{Enter}");
    await waitFor(() => {
      expect(useSearchStore.getState().filters.q).toBe("roses");
    });

    // The app shell remounts the routed subtree on every pathname change, so
    // the filters have to outlive the component that holds them.
    unmount();
    renderSearch();

    expect(await screen.findByLabelText("Search products")).toHaveValue("roses");
  });

  it("shows the product over the results again on forward", async () => {
    const user = userEvent.setup();
    useLocationStore.setState({ selected: KORAMANGALA });
    const { router } = renderSearch();

    await screen.findByRole("heading", { name: "Red Rose Bouquet" });
    await user.click(screen.getByRole("link", { name: "Red Rose Bouquet" }));
    await screen.findByRole("dialog");

    await act(async () => { await router.navigate(-1); });
    await waitFor(() => {
      expect(screen.queryByRole("dialog")).toBeNull();
    });

    await act(async () => { await router.navigate(1); });
    await screen.findByRole("dialog");
  });

  it("renders a product reached directly on its own, with a way back to the results", async () => {
    useLocationStore.setState({ selected: KORAMANGALA });

    renderSearch("/search/products/1001");

    expect(
      await screen.findByRole("heading", { name: "Red Rose Bouquet", level: 1 }),
    ).toBeInTheDocument();
    expect(screen.queryByRole("dialog")).toBeNull();
    expect(screen.getByRole("link", { name: "Back to the results" })).toHaveAttribute(
      "href",
      "/search",
    );
    // The results are not behind the product, so they are not fetched.
    expect(mockFetchSearch).not.toHaveBeenCalled();
  });
});
