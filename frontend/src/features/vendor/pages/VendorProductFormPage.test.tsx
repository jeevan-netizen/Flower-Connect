import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Route, Routes } from "react-router-dom";
import { VendorProductFormPage } from "@/features/vendor/pages/VendorProductFormPage";
import { renderWithProviders } from "@/test/render";
import { makeCategory, makeInventory, makePage, makeProduct, makeProductImage } from "@/test/factories";
import { apiError, apiErrorFor } from "@/test/api-errors";
import { MAX_IMAGES_PER_PRODUCT } from "@/features/vendor/types";
import type { Product, ProductImage } from "@/features/vendor/types";

const mockFetchProduct = vi.hoisted(() => vi.fn());
const mockFetchCategories = vi.hoisted(() => vi.fn());
const mockFetchImages = vi.hoisted(() => vi.fn());
const mockFetchInventory = vi.hoisted(() => vi.fn());
const mockFetchMovements = vi.hoisted(() => vi.fn());
const mockCreate = vi.hoisted(() => vi.fn());
const mockUpdate = vi.hoisted(() => vi.fn());
const mockDeactivate = vi.hoisted(() => vi.fn());
const mockUpload = vi.hoisted(() => vi.fn());
const mockSetPrimary = vi.hoisted(() => vi.fn());
const mockReorder = vi.hoisted(() => vi.fn());
const mockDeleteImage = vi.hoisted(() => vi.fn());
const mockStockIn = vi.hoisted(() => vi.fn());
const mockUpdateThreshold = vi.hoisted(() => vi.fn());
const mockUpdateExpiry = vi.hoisted(() => vi.fn());

vi.mock("@/features/vendor/api", () => ({
  fetchProduct: mockFetchProduct,
  fetchCategories: mockFetchCategories,
  fetchProductImages: mockFetchImages,
  fetchInventory: mockFetchInventory,
  fetchStockMovements: mockFetchMovements,
  createProduct: mockCreate,
  updateProduct: mockUpdate,
  deactivateProduct: mockDeactivate,
  uploadProductImage: mockUpload,
  setPrimaryProductImage: mockSetPrimary,
  reorderProductImages: mockReorder,
  deleteProductImage: mockDeleteImage,
  stockIn: mockStockIn,
  stockOut: vi.fn(),
  adjustStock: vi.fn(),
  writeOffStock: vi.fn(),
  updateLowStockThreshold: mockUpdateThreshold,
  updateExpiryDate: mockUpdateExpiry,
}));

/** Renders the page at a real route so `useParams` resolves a genuine path segment. */
function renderPage(route: string) {
  return renderWithProviders(
    <Routes>
      <Route path="/vendor/catalog/new" element={<VendorProductFormPage />} />
      <Route path="/vendor/catalog/:productId" element={<VendorProductFormPage />} />
      <Route path="/vendor/catalog" element={<p>Catalog listing</p>} />
    </Routes>,
    { route },
  );
}

function primeProduct(product: Product = makeProduct()) {
  mockFetchProduct.mockResolvedValue(product);
  mockFetchImages.mockResolvedValue(product.images);
  mockFetchInventory.mockResolvedValue(product.inventory ?? makeInventory());
  mockFetchMovements.mockResolvedValue(makePage([]));
  return product;
}

describe("VendorProductFormPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockFetchCategories.mockResolvedValue(
      makePage([makeCategory({ id: 1, name: "Roses" }), makeCategory({ id: 2, name: "Bouquets" })]),
    );
    primeProduct();
    mockUpdate.mockResolvedValue(makeProduct());
    mockDeactivate.mockResolvedValue(undefined);
    mockUpdateThreshold.mockResolvedValue(makeInventory());
    mockUpdateExpiry.mockResolvedValue(makeInventory());
  });

  it("asks for no product at all on the create route", async () => {
    renderPage("/vendor/catalog/new");

    expect(await screen.findByRole("heading", { name: /add product/i })).toBeInTheDocument();
    expect(mockFetchProduct).not.toHaveBeenCalled();
    expect(screen.queryByRole("heading", { name: /^images$/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: /^inventory$/i })).not.toBeInTheDocument();
  });

  it("creates the product from the form and moves to its own URL", async () => {
    const user = userEvent.setup();
    mockCreate.mockResolvedValue(makeProduct({ id: 555 }));
    renderPage("/vendor/catalog/new");

    await user.type(await screen.findByLabelText(/product name/i), "Red Rose Bouquet");
    await user.selectOptions(screen.getByLabelText(/category/i), "1");
    await user.type(screen.getByLabelText(/base price/i), "899");
    await user.click(screen.getByRole("button", { name: /create product/i }));

    await waitFor(() => {
      expect(mockCreate).toHaveBeenCalledWith({
        name: "Red Rose Bouquet",
        categoryId: 1,
        description: null,
        basePrice: 899,
        status: "DRAFT",
      });
    });

    // The route change re-renders this page for the new id, so the product is read.
    await waitFor(() => {
      expect(mockFetchProduct).toHaveBeenCalledWith(555);
    });
  });

  it("loads the product, its images, its stock and its history for an edit route", async () => {
    primeProduct(makeProduct({ id: 101, name: "Red Rose Bouquet" }));
    renderPage("/vendor/catalog/101");

    expect(await screen.findByRole("heading", { name: /edit red rose bouquet/i })).toBeInTheDocument();
    expect(mockFetchImages).toHaveBeenCalledWith(101);
    expect(mockFetchInventory).toHaveBeenCalledWith(101);
    expect(mockFetchMovements).toHaveBeenCalledWith(101, 0, 20, expect.anything());
  });

  it("saves an edit through the full DTO", async () => {
    const user = userEvent.setup();
    primeProduct(makeProduct({ id: 101 }));
    renderPage("/vendor/catalog/101");

    const name = await screen.findByLabelText(/product name/i);
    await user.clear(name);
    await user.type(name, "Renamed Bouquet");
    await user.click(screen.getByRole("button", { name: /save changes/i }));

    await waitFor(() => {
      expect(mockUpdate).toHaveBeenCalledWith(101, {
        name: "Renamed Bouquet",
        categoryId: 1,
        description: "A dozen deep red roses.",
        basePrice: 899,
        status: "DRAFT",
      });
    });
    expect(await screen.findByText(/product saved/i)).toBeInTheDocument();
  });

  it("refuses a non-numeric product id without asking the server", async () => {
    renderPage("/vendor/catalog/banana");

    expect(
      await screen.findByText(/that product link is not valid/i),
    ).toBeInTheDocument();
    expect(mockFetchProduct).not.toHaveBeenCalled();
    expect(screen.getByRole("link", { name: /back to catalog/i })).toHaveAttribute(
      "href",
      "/vendor/catalog",
    );
  });

  it("reports a missing product as a missing product, not as a missing account", async () => {
    mockFetchProduct.mockRejectedValue(
      apiErrorFor("/vendors/products/101", 404, { code: "NOT_FOUND", message: "Product not found" }),
    );
    renderPage("/vendor/catalog/101");

    expect(await screen.findByText(/product not found/i)).toBeInTheDocument();
    expect(screen.queryByText(/no vendor profile/i)).not.toBeInTheDocument();
  });

  it("surfaces a failed product read with a retry", async () => {
    mockFetchProduct.mockRejectedValue(apiError(500, { message: "An unexpected error occurred" }));
    renderPage("/vendor/catalog/101");

    expect(await screen.findByText(/something went wrong/i)).toBeInTheDocument();

    primeProduct();
    await userEvent.click(screen.getByRole("button", { name: /try again/i }));

    expect(await screen.findByRole("heading", { name: /edit red rose bouquet/i })).toBeInTheDocument();
  });

  it("deactivates behind a confirmation and returns to the catalog", async () => {
    const user = userEvent.setup();
    renderPage("/vendor/catalog/101");

    await screen.findByRole("heading", { name: /edit red rose bouquet/i });
    await user.click(screen.getByRole("button", { name: /deactivate product/i }));

    expect(screen.getByRole("dialog", { name: /deactivate this product/i })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /yes, deactivate/i }));

    await waitFor(() => {
      expect(mockDeactivate).toHaveBeenCalledWith(101);
    });
    expect(await screen.findByText("Catalog listing")).toBeInTheDocument();
  });

  it("leaves the product untouched when the deactivation is cancelled", async () => {
    const user = userEvent.setup();
    renderPage("/vendor/catalog/101");

    await screen.findByRole("heading", { name: /edit red rose bouquet/i });
    await user.click(screen.getByRole("button", { name: /deactivate product/i }));
    await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: /cancel/i }));

    expect(mockDeactivate).not.toHaveBeenCalled();
    expect(screen.queryByRole("dialog", { name: /deactivate this product/i })).not.toBeInTheDocument();
  });

  it("uploads a chosen file with the cover flag the section was set to", async () => {
    const user = userEvent.setup();
    renderPage("/vendor/catalog/101");

    await screen.findByLabelText(/upload a product image/i);
    await user.click(screen.getByLabelText(/make the next upload the cover/i));

    const file = new File(["bytes"], "bouquet.jpg", { type: "image/jpeg" });
    await user.upload(screen.getByLabelText(/upload a product image/i), file);

    await waitFor(() => {
      expect(mockUpload).toHaveBeenCalledWith(101, file, true);
    });
  });

  it("sends a whole reordered id list, because the endpoint demands a permutation", async () => {
    const images: ProductImage[] = [
      makeProductImage({ id: 1, primary: true, sortOrder: 0, originalFilename: "one.jpg" }),
      makeProductImage({ id: 2, primary: false, sortOrder: 1, originalFilename: "two.jpg" }),
      makeProductImage({ id: 3, primary: false, sortOrder: 2, originalFilename: "three.jpg" }),
    ];
    primeProduct(makeProduct({ id: 101, images }));
    mockReorder.mockResolvedValue(images);

    const user = userEvent.setup();
    renderPage("/vendor/catalog/101");

    await screen.findByText("one.jpg");
    await user.click(screen.getByRole("button", { name: /move three\.jpg earlier/i }));

    await waitFor(() => {
      expect(mockReorder).toHaveBeenCalledWith(101, [1, 3, 2]);
    });
  });

  it("marks the current cover instead of offering to set it", async () => {
    primeProduct(
      makeProduct({
        images: [
          makeProductImage({ id: 1, primary: true, originalFilename: "cover.jpg" }),
          makeProductImage({ id: 2, primary: false, originalFilename: "second.jpg" }),
        ],
      }),
    );

    renderPage("/vendor/catalog/101");

    const coverRow = (await screen.findByText("cover.jpg")).closest("li")!;
    expect(within(coverRow).getByText("Cover")).toBeInTheDocument();
    expect(within(coverRow).queryByRole("button", { name: /make cover/i })).not.toBeInTheDocument();

    const secondRow = screen.getByText("second.jpg").closest("li")!;
    await userEvent.click(within(secondRow).getByRole("button", { name: /make cover/i }));

    await waitFor(() => {
      expect(mockSetPrimary).toHaveBeenCalledWith(101, 2);
    });
  });

  it("shows an image failure in place and keeps the rest of the editor usable", async () => {
    mockUpload.mockRejectedValue(
      apiError(415, { code: "UNSUPPORTED_MEDIA_TYPE", message: "Unsupported image format." }),
    );

    const user = userEvent.setup();
    renderPage("/vendor/catalog/101");

    await screen.findByLabelText(/upload a product image/i);
    // An allowed MIME type, so the file reaches the handler and it is the *server's*
    // refusal that is under test — a `.txt` would be dropped by the input's `accept`
    // filter before any handler ran, proving nothing.
    const file = new File(["bytes"], "photo.png", { type: "image/png" });
    await user.upload(screen.getByLabelText(/upload a product image/i), file);

    expect(await screen.findByText("Unsupported image format.")).toBeInTheDocument();
    expect(screen.getByLabelText(/product name/i)).toBeEnabled();
  });

  it("reports the image budget and refuses another upload at the limit", async () => {
    primeProduct(
      makeProduct({
        images: Array.from({ length: MAX_IMAGES_PER_PRODUCT }, (_, index) =>
          makeProductImage({ id: index + 1, primary: index === 0 }),
        ),
      }),
    );

    renderPage("/vendor/catalog/101");

    expect(await screen.findByText(`${MAX_IMAGES_PER_PRODUCT} of ${MAX_IMAGES_PER_PRODUCT} used`)).toBeInTheDocument();
    expect(screen.getByLabelText(/upload a product image/i)).toBeDisabled();
  });

  it("runs a stock action from the inventory panel with the current level stated", async () => {
    primeProduct(makeProduct({ id: 101, inventory: makeInventory({ quantity: 12, available: 12 }) }));
    mockStockIn.mockResolvedValue(makeInventory({ quantity: 20, available: 20 }));

    const user = userEvent.setup();
    renderPage("/vendor/catalog/101");

    await user.click(await screen.findByRole("button", { name: /^add stock$/i }));

    expect(await screen.findByText(/currently 12 on hand, 12 available/i)).toBeInTheDocument();
    await user.type(screen.getByLabelText(/quantity to add/i), "8");
    await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: /add stock/i }));

    await waitFor(() => {
      expect(mockStockIn).toHaveBeenCalledWith(101, { quantity: 8, reason: null });
    });
    expect(await screen.findByText(/recorded in the movement history/i)).toBeInTheDocument();
  });

  it("saves the low-stock threshold and the expiry date", async () => {
    const user = userEvent.setup();
    renderPage("/vendor/catalog/101");

    const threshold = await screen.findByLabelText(/low stock threshold/i);
    await user.clear(threshold);
    await user.type(threshold, "6");
    await user.click(within(threshold.closest("div")!).getByRole("button", { name: /save/i }));

    await waitFor(() => {
      expect(mockUpdateThreshold).toHaveBeenCalledWith(101, { lowStockThreshold: 6 });
    });

    const expiry = screen.getByLabelText(/expiry date/i);
    await user.type(expiry, "2026-10-20");
    await user.click(within(expiry.closest("div")!).getByRole("button", { name: /save/i }));

    await waitFor(() => {
      expect(mockUpdateExpiry).toHaveBeenCalledWith(101, { expiryDate: "2026-10-20" });
    });
  });

  it("clears the expiry date by sending null rather than an empty string", async () => {
    primeProduct(makeProduct({ id: 101, inventory: makeInventory({ expiryDate: "2026-10-20" }) }));

    const user = userEvent.setup();
    renderPage("/vendor/catalog/101");

    const expiry = await screen.findByLabelText(/expiry date/i);
    expect(expiry).toHaveValue("2026-10-20");
    await user.clear(expiry);
    await user.click(within(expiry.closest("div")!).getByRole("button", { name: /clear/i }));

    await waitFor(() => {
      expect(mockUpdateExpiry).toHaveBeenCalledWith(101, { expiryDate: null });
    });
  });

  it("keeps the editor usable when only the images fail to load", async () => {
    mockFetchImages.mockRejectedValue(apiError(500, { message: "An unexpected error occurred" }));

    renderPage("/vendor/catalog/101");

    expect(await screen.findByRole("heading", { name: /edit red rose bouquet/i })).toBeInTheDocument();
    expect(screen.getByLabelText(/product name/i)).toHaveValue("Red Rose Bouquet");
  });
});