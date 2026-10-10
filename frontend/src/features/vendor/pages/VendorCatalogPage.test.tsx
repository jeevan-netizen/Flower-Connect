import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { VendorCatalogPage } from "@/features/vendor/pages/VendorCatalogPage";
import { renderWithProviders } from "@/test/render";
import { makeCategory, makeInventory, makePage, makeProduct } from "@/test/factories";
import { apiError } from "@/test/api-errors";
import { formatMoney } from "@/features/vendor/format";
import type { PageResponse } from "@/shared/types";
import type { Category, Product } from "@/features/vendor/types";

const mockFetchProducts = vi.hoisted(() => vi.fn());
const mockFetchCategories = vi.hoisted(() => vi.fn());

vi.mock("@/features/vendor/api", () => ({
  fetchProducts: mockFetchProducts,
  fetchCategories: mockFetchCategories,
}));

/** `makePage` is generic; naming the fixture type keeps the categories page honest. */
function categoryPage(categories: Category[]): PageResponse<Category> {
  return makePage(categories);
}

function pageOf(products: Product[], overrides: Partial<PageResponse<Product>> = {}) {
  return makePage(products, { size: 20, ...overrides });
}

/**
 * The catalog page is asserted through its query calls rather than through rendered
 * rows alone: the filters live in React state and are only observable as the filter
 * object handed to `fetchProducts`. That is the whole contract of the screen — the
 * server does the filtering, so "the filter reached the query" is the behaviour.
 */
function renderCatalog() {
  return renderWithProviders(<VendorCatalogPage />);
}

describe("VendorCatalogPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockFetchCategories.mockResolvedValue(
      categoryPage([makeCategory({ id: 1, name: "Roses" }), makeCategory({ id: 2, name: "Bouquets" })]),
    );
    mockFetchProducts.mockResolvedValue(pageOf([makeProduct()]));
  });

  it("asks for an unfiltered first page and lists the returned products", async () => {
    renderCatalog();

    await waitFor(() => {
      expect(mockFetchProducts).toHaveBeenCalledWith(
        { status: null, categoryId: null, name: "", page: 0 },
        expect.anything(),
      );
    });

    expect(await screen.findByRole("link", { name: "Red Rose Bouquet" })).toHaveAttribute(
      "href",
      "/vendor/catalog/101",
    );
    // The price comes from the shared INR formatter rather than a literal here; the
    // formatter's own output is covered in `format.test.ts`.
    expect(screen.getByText(formatMoney(makeProduct().basePrice))).toBeInTheDocument();
  });

  it("renders the backend's low-stock flag rather than recomputing it", async () => {
    mockFetchProducts.mockResolvedValue(
      pageOf([
        makeProduct({
          id: 1,
          name: "Nearly Gone",
          inventory: makeInventory({ productId: 1, quantity: 3, available: 3, lowStockThreshold: 4, lowStock: true }),
        }),
        makeProduct({
          id: 2,
          name: "Plenty",
          inventory: makeInventory({ productId: 2, lowStock: false }),
        }),
      ]),
    );

    renderCatalog();

    const low = await screen.findByText("Nearly Gone");
    const plenty = await screen.findByText("Plenty");

    expect(within(low.closest("tr")!).getByTestId("low-stock-badge")).toHaveTextContent(/low stock/i);
    expect(within(plenty.closest("tr")!).queryByTestId("low-stock-badge")).not.toBeInTheDocument();
  });

  it("names an out-of-stock product differently from one merely at its threshold", async () => {
    mockFetchProducts.mockResolvedValue(
      pageOf([
        makeProduct({
          id: 1,
          name: "Sold Out",
          inventory: makeInventory({ productId: 1, quantity: 0, available: 0, lowStockThreshold: 4, lowStock: true }),
        }),
      ]),
    );

    renderCatalog();

    expect(await screen.findByTestId("low-stock-badge")).toHaveTextContent(/out of stock/i);
  });

  it("submits a search term on Enter rather than on every keystroke", async () => {
    const user = userEvent.setup();
    renderCatalog();

    await screen.findByRole("link", { name: "Red Rose Bouquet" });
    const box = screen.getByLabelText(/search products/i);

    await user.type(box, "rose");
    expect(mockFetchProducts).toHaveBeenCalledTimes(1);

    await user.type(box, "{Enter}");

    await waitFor(() => {
      expect(mockFetchProducts).toHaveBeenCalledWith(
        { status: null, categoryId: null, name: "rose", page: 0 },
        expect.anything(),
      );
    });
  });

  it("trims the search term so a trailing space does not become a filter", async () => {
    const user = userEvent.setup();
    renderCatalog();

    await screen.findByRole("link", { name: "Red Rose Bouquet" });
    await user.type(screen.getByLabelText(/search products/i), "  rose  {Enter}");

    await waitFor(() => {
      expect(mockFetchProducts).toHaveBeenLastCalledWith(
        { status: null, categoryId: null, name: "rose", page: 0 },
        expect.anything(),
      );
    });
  });

  it("filters by status and resets to the first page", async () => {
    const user = userEvent.setup();
    renderCatalog();

    await screen.findByRole("link", { name: "Red Rose Bouquet" });
    await user.selectOptions(screen.getByLabelText(/status/i), "ACTIVE");

    await waitFor(() => {
      expect(mockFetchProducts).toHaveBeenLastCalledWith(
        { status: "ACTIVE", categoryId: null, name: "", page: 0 },
        expect.anything(),
      );
    });
  });

  it("filters by category", async () => {
    const user = userEvent.setup();
    renderCatalog();

    await screen.findByRole("link", { name: "Red Rose Bouquet" });
    await user.selectOptions(screen.getByLabelText(/category/i), "2");

    await waitFor(() => {
      expect(mockFetchProducts).toHaveBeenLastCalledWith(
        { status: null, categoryId: 2, name: "", page: 0 },
        expect.anything(),
      );
    });
  });

  it("pages without disturbing the active filters", async () => {
    const user = userEvent.setup();
    mockFetchProducts.mockResolvedValue(
      pageOf([makeProduct()], { page: 0, size: 20, totalElements: 45, totalPages: 3, last: false }),
    );
    renderCatalog();

    await screen.findByRole("link", { name: "Red Rose Bouquet" });
    await user.selectOptions(screen.getByLabelText(/status/i), "ACTIVE");
    await waitFor(() => {
      expect(mockFetchProducts).toHaveBeenLastCalledWith(
        { status: "ACTIVE", categoryId: null, name: "", page: 0 },
        expect.anything(),
      );
    });

    await user.click(screen.getByRole("button", { name: /next/i }));

    await waitFor(() => {
      expect(mockFetchProducts).toHaveBeenLastCalledWith(
        { status: "ACTIVE", categoryId: null, name: "", page: 1 },
        expect.anything(),
      );
    });
  });

  it("returns to the unfiltered listing through Clear filters", async () => {
    const user = userEvent.setup();
    renderCatalog();

    await screen.findByRole("link", { name: "Red Rose Bouquet" });
    await user.selectOptions(screen.getByLabelText(/status/i), "DRAFT");
    await waitFor(() => {
      expect(mockFetchProducts).toHaveBeenLastCalledWith(
        { status: "DRAFT", categoryId: null, name: "", page: 0 },
        expect.anything(),
      );
    });

    await user.click(screen.getByRole("button", { name: /clear filters/i }));

    await waitFor(() => {
      expect(mockFetchProducts).toHaveBeenLastCalledWith(
        { status: null, categoryId: null, name: "", page: 0 },
        expect.anything(),
      );
    });
  });

  it("offers no clear button until a filter is set", async () => {
    renderCatalog();

    await screen.findByRole("link", { name: "Red Rose Bouquet" });
    expect(screen.queryByRole("button", { name: /clear filters/i })).not.toBeInTheDocument();
  });

  it("says an empty result means the filters matched nothing", async () => {
    mockFetchProducts.mockResolvedValue(pageOf([]));
    renderCatalog();

    expect(await screen.findByText(/no products match these filters/i)).toBeInTheDocument();
  });

  it("keeps the listing up when only the categories fail to load", async () => {
    mockFetchCategories.mockRejectedValue(apiError(500, { message: "An unexpected error occurred" }));

    renderCatalog();

    expect(await screen.findByRole("link", { name: "Red Rose Bouquet" })).toBeInTheDocument();
    expect(screen.getByRole("alert")).toHaveTextContent(/categories could not be loaded/i);
    // The filter itself degrades to "all categories" rather than disappearing.
    expect(screen.getByLabelText(/category/i)).toBeInTheDocument();
  });

  it("surfaces a failed listing read with a retry", async () => {
    mockFetchProducts.mockRejectedValue(apiError(500, { message: "An unexpected error occurred" }));

    renderCatalog();

    expect(await screen.findByText(/something went wrong/i)).toBeInTheDocument();

    mockFetchProducts.mockResolvedValue(pageOf([makeProduct()]));
    await userEvent.click(screen.getByRole("button", { name: /try again/i }));

    expect(await screen.findByRole("link", { name: "Red Rose Bouquet" })).toBeInTheDocument();
  });

  it("links to the create form", async () => {
    renderCatalog();

    expect(await screen.findByRole("link", { name: /add product/i })).toHaveAttribute(
      "href",
      "/vendor/catalog/new",
    );
  });
});