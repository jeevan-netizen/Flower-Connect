import { describe, it, expect, vi } from "vitest";
import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { SearchFilters } from "@/features/customer/components/SearchFilters";
import { renderWithProviders } from "@/test/render";
import { makeCategory } from "@/test/factories";
import { DEFAULT_SEARCH_FILTERS } from "@/features/customer/types";

function renderFilters(overrides: Partial<Parameters<typeof SearchFilters>[0]> = {}) {
  const props = {
    search: "",
    onSearchChange: vi.fn(),
    onSearchSubmit: vi.fn(),
    categoryId: null,
    onCategoryChange: vi.fn(),
    categories: [makeCategory({ id: 1, name: "Roses" })],
    priceMin: "",
    priceMax: "",
    onPriceChange: vi.fn(),
    onPriceCommit: vi.fn(),
    priceError: null,
    sort: DEFAULT_SEARCH_FILTERS.sort,
    onSortChange: vi.fn(),
    onClear: vi.fn(),
    ...overrides,
  };

  renderWithProviders(<SearchFilters {...props} />);
  return props;
}

describe("SearchFilters", () => {
  it("reports what the customer types in the search box", async () => {
    const user = userEvent.setup();
    const props = renderFilters();

    await user.type(screen.getByLabelText("Search products"), "r");

    expect(props.onSearchChange).toHaveBeenCalledWith("r");
  });

  it("submits the search term rather than sending it on every keystroke", async () => {
    const user = userEvent.setup();
    const props = renderFilters();

    await user.type(screen.getByLabelText("Search products"), "roses{Enter}");

    expect(props.onSearchSubmit).toHaveBeenCalled();
  });

  it("offers the public categories as chips, and no child categories", () => {
    renderFilters({
      categories: [
        makeCategory({ id: 1, name: "Roses" }),
        makeCategory({ id: 2, name: "Bouquets" }),
      ],
    });

    const group = screen.getByRole("group", { name: "Filter by category" });
    expect(group).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Roses" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Bouquets" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "All categories" })).toBeInTheDocument();
  });

  it("reports the chosen category", async () => {
    const user = userEvent.setup();
    const props = renderFilters();

    await user.click(screen.getByRole("button", { name: "Roses" }));

    expect(props.onCategoryChange).toHaveBeenCalledWith(1);
  });

  it("clears the category when the chosen chip is pressed again", async () => {
    const user = userEvent.setup();
    const props = renderFilters({ categoryId: 1 });

    await user.click(screen.getByRole("button", { name: "Roses" }));

    expect(props.onCategoryChange).toHaveBeenCalledWith(null);
  });

  it("marks the chosen category chip as pressed", () => {
    renderFilters({ categoryId: 1 });

    expect(screen.getByRole("button", { name: "Roses" })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
    expect(screen.getByRole("button", { name: "All categories" })).toHaveAttribute(
      "aria-pressed",
      "false",
    );
  });

  it("uses labelled number inputs for the price bounds, not a slider", () => {
    renderFilters();

    const min = screen.getByLabelText("Minimum price");
    const max = screen.getByLabelText("Maximum price");
    expect(min).toHaveAttribute("type", "number");
    expect(max).toHaveAttribute("type", "number");
    expect(screen.queryByRole("slider")).toBeNull();
  });

  it("reports each price bound as it is typed", async () => {
    const user = userEvent.setup();
    const props = renderFilters();

    await user.type(screen.getByLabelText("Minimum price"), "1");

    expect(props.onPriceChange).toHaveBeenCalledWith("min", "1");
  });

  it("commits the price bounds on blur rather than on every keystroke", async () => {
    const user = userEvent.setup();
    const props = renderFilters();

    await user.click(screen.getByLabelText("Maximum price"));
    await user.tab();

    expect(props.onPriceCommit).toHaveBeenCalled();
  });

  it("shows the price error against both bounds", () => {
    renderFilters({
      priceError: "The minimum price must not be greater than the maximum price.",
    });

    expect(
      screen.getByText("The minimum price must not be greater than the maximum price."),
    ).toBeInTheDocument();
    expect(screen.getByLabelText("Minimum price")).toHaveAttribute("aria-invalid", "true");
  });

  it("offers exactly the sort keys the backend accepts", () => {
    renderFilters();

    const select = screen.getByLabelText("Sort by");
    const values = Array.from(select.querySelectorAll("option")).map(
      (option) => option.getAttribute("value"),
    );

    expect(values).toEqual(["distance", "price_asc", "price_desc", "name"]);
  });

  it("reports a changed sort", async () => {
    const user = userEvent.setup();
    const props = renderFilters();

    await user.selectOptions(screen.getByLabelText("Sort by"), "price_asc");

    expect(props.onSortChange).toHaveBeenCalledWith("price_asc");
  });

  it("offers to clear the filters only when there are some", async () => {
    const user = userEvent.setup();
    const props = renderFilters({ search: "roses" });

    await user.click(screen.getByRole("button", { name: "Clear filters" }));

    expect(props.onClear).toHaveBeenCalled();
  });

  it("has nothing to clear when no filter is set", () => {
    renderFilters();

    expect(screen.queryByRole("button", { name: "Clear filters" })).toBeNull();
  });
});
