import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ProductForm } from "@/features/vendor/components/ProductForm";
import { PRODUCT_STATUS_HINTS } from "@/features/vendor/format";
import { renderWithProviders } from "@/test/render";
import { makeCategory, makeProduct } from "@/test/factories";
import { validationError } from "@/test/api-errors";
import type { Category, Product } from "@/features/vendor/types";

const onSubmit = vi.hoisted(() => vi.fn());
const onError = vi.hoisted(() => vi.fn());

const CATEGORIES: Category[] = [
  makeCategory({ id: 1, name: "Roses" }),
  makeCategory({ id: 2, name: "Bouquets", displayOrder: 2 }),
];

/**
 * `ProductForm` is exercised through the DOM rather than by calling its resolver:
 * the rules under test are bound to `ProductRequest` (the backend DTO) and the whole
 * point of the screen is that a merchant is stopped before a request is made.
 */
function renderForm(overrides: { product?: Product | null; categories?: Category[] } = {}) {
  return renderWithProviders(
    <ProductForm
      product={overrides.product ?? null}
      categories={overrides.categories ?? CATEGORIES}
      isSubmitting={false}
      onSubmit={onSubmit}
      onError={onError}
    />,
  );
}

async function fillValidForm(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText(/product name/i), "Red Rose Bouquet");
  await user.selectOptions(screen.getByLabelText(/category/i), "1");
  await user.type(screen.getByLabelText(/base price/i), "899");
}

describe("ProductForm", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    onSubmit.mockResolvedValue(undefined);
  });

  it("refuses to submit an empty form and says why on the field", async () => {
    const user = userEvent.setup();
    renderForm();

    await user.click(screen.getByRole("button", { name: /create product/i }));

    expect(await screen.findByText(/product name is required/i)).toBeInTheDocument();
    expect(screen.getByText(/category is required/i)).toBeInTheDocument();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it("refuses a zero price, matching the backend's exclusive minimum", async () => {
    const user = userEvent.setup();
    renderForm();

    await user.type(screen.getByLabelText(/product name/i), "Red Rose Bouquet");
    await user.selectOptions(screen.getByLabelText(/category/i), "1");
    await user.type(screen.getByLabelText(/base price/i), "0");
    await user.click(screen.getByRole("button", { name: /create product/i }));

    // `@DecimalMin(value = "0", inclusive = false)` — the exclusive minimum is what
    // makes 0 illegal rather than merely free.
    expect(await screen.findByText("Base price must be greater than 0")).toBeInTheDocument();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it("refuses a price with more than the two decimals the column stores", async () => {
    const user = userEvent.setup();
    renderForm();

    await user.type(screen.getByLabelText(/product name/i), "Red Rose Bouquet");
    await user.selectOptions(screen.getByLabelText(/category/i), "1");
    await user.type(screen.getByLabelText(/base price/i), "12.345");
    await user.click(screen.getByRole("button", { name: /create product/i }));

    // `base_price` is `DECIMAL(10,2)`: a third decimal would be silently rounded by
    // the server, so the client refuses it rather than letting the price change.
    expect(await screen.findByText("Base price must have at most 2 decimal places")).toBeInTheDocument();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it("submits the DTO shape, sending an empty description as null", async () => {
    const user = userEvent.setup();
    renderForm();

    await fillValidForm(user);
    await user.click(screen.getByRole("button", { name: /create product/i }));

    await waitFor(() => {
      expect(onSubmit).toHaveBeenCalledWith({
        name: "Red Rose Bouquet",
        categoryId: 1,
        description: null,
        basePrice: 899,
        status: "DRAFT",
      });
    });
  });

  it("attaches a server field error to the matching input and passes the message up", async () => {
    const user = userEvent.setup();
    onSubmit.mockRejectedValue(
      validationError({ name: "size must be between 0 and 160", basePrice: "price is not allowed" }),
    );
    renderForm();

    await fillValidForm(user);
    await user.click(screen.getByRole("button", { name: /create product/i }));

    expect(await screen.findByText("size must be between 0 and 160")).toBeInTheDocument();
    expect(screen.getByText("price is not allowed")).toBeInTheDocument();
    expect(onError).toHaveBeenCalledWith(
      expect.objectContaining({ message: "Validation failed" }),
    );
  });

  it("seeds the form from an existing product and shows its generated slug", async () => {
    renderForm({ product: makeProduct({ name: "Red Rose Bouquet", slug: "red-rose-bouquet-3" }) });

    await waitFor(() => {
      expect(screen.getByLabelText(/product name/i)).toHaveValue("Red Rose Bouquet");
    });
    expect(screen.getByLabelText(/category/i)).toHaveValue("1");
    expect(screen.getByText("red-rose-bouquet-3")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /save changes/i })).toBeInTheDocument();
  });

  it("explains the selected status inline instead of implying a transition rule", async () => {
    const user = userEvent.setup();
    renderForm();

    expect(screen.getByText(PRODUCT_STATUS_HINTS.DRAFT)).toBeInTheDocument();

    await user.selectOptions(screen.getByLabelText(/status/i), "ACTIVE");

    expect(screen.getByText(PRODUCT_STATUS_HINTS.ACTIVE)).toBeInTheDocument();
    expect(screen.queryByText(PRODUCT_STATUS_HINTS.DRAFT)).not.toBeInTheDocument();
  });

  it("counts description characters against the stored maximum", async () => {
    const user = userEvent.setup();
    renderForm();

    await user.type(screen.getByLabelText(/description/i), "Seasonal stems.");

    expect(screen.getByText(/15 \/ 2000 characters/i)).toBeInTheDocument();
  });

  it("offers only the categories it was given and no empty placeholder choice", () => {
    renderForm();

    const select = screen.getByLabelText(/category/i) as HTMLSelectElement;
    const options = Array.from(select.options).map((option) => option.textContent);

    expect(options).toEqual(["Choose a category", "Roses", "Bouquets"]);
  });
});