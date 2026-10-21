import { describe, it, expect, vi } from "vitest";
import { screen, render, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createMemoryRouter, RouterProvider, useLocation } from "react-router-dom";
import { ProductCard } from "@/features/customer/components/ProductCard";
import { renderWithProviders } from "@/test/render";
import { makePublicProduct, makePublicProductImage } from "@/test/factories";

describe("ProductCard", () => {
  it("shows the product, its florist and its price", () => {
    renderWithProviders(<ProductCard product={makePublicProduct()} />);

    expect(screen.getByRole("heading", { name: "Red Rose Bouquet" })).toBeInTheDocument();
    expect(screen.getByText("Petal & Stem")).toBeInTheDocument();
    expect(screen.getByText("Roses")).toBeInTheDocument();
    expect(screen.getByText("In stock")).toBeInTheDocument();
  });

  it("counts down the last few units", () => {
    renderWithProviders(<ProductCard product={makePublicProduct({ available: 2 })} />);

    expect(screen.getByText("Only 2 left")).toBeInTheDocument();
  });

  it("renders a placeholder rather than an image, because no route serves the bytes", () => {
    const { container } = renderWithProviders(<ProductCard product={makePublicProduct()} />);

    // D-31 / known issue 020: a broken image reads as a failed upload, so the
    // customer area never renders an <img> at all.
    expect(container.querySelector("img")).toBeNull();
    expect(
      screen.getByRole("img", { name: /Photo of Red Rose Bouquet/ }),
    ).toBeInTheDocument();
  });

  it("says so when the product has no photo", () => {
    renderWithProviders(<ProductCard product={makePublicProduct({ images: [] })} />);

    expect(
      screen.getByRole("img", { name: /No photo available for Red Rose Bouquet/ }),
    ).toBeInTheDocument();
  });

  it("links to the product inside its florist's storefront", () => {
    renderWithProviders(
      <ProductCard product={makePublicProduct({ id: 1001, vendorId: 7 })} vendorId={7} />,
    );

    const links = screen.getAllByRole("link", { name: "Red Rose Bouquet" });
    expect(links[0]).toHaveAttribute("href", "/vendors/7/products/1001");
  });

  it("links into the search surface when the product is opened from the results", () => {
    renderWithProviders(
      <ProductCard
        product={makePublicProduct({ id: 1001, vendorId: 7 })}
        detailPathBase="/search/products"
      />,
    );

    const links = screen.getAllByRole("link", { name: "Red Rose Bouquet" });
    expect(links[0]).toHaveAttribute("href", "/search/products/1001");
  });

  it("marks the link as an overlay of the surface it was opened from", async () => {
    const user = userEvent.setup();

    function LocationProbe() {
      const location = useLocation();
      return <span data-testid="location-state">{JSON.stringify(location.state)}</span>;
    }

    const router = createMemoryRouter([
      { path: "/", element: <ProductCard product={makePublicProduct()} /> },
      {
        path: "/vendors/:vendorId/products/:productId",
        element: <LocationProbe />,
      },
    ]);

    render(<RouterProvider router={router} />);

    await user.click(screen.getAllByRole("link", { name: "Red Rose Bouquet" })[0]);

    await waitFor(() => {
      const state = JSON.parse(screen.getByTestId("location-state").textContent ?? "{}");
      expect(state).toMatchObject({ productOverlay: true });
      expect(typeof state.overlayToken).toBe("string");
    });
  });

  it("shows the primary image even when it is not the first one", () => {
    renderWithProviders(
      <ProductCard
        product={makePublicProduct({
          images: [
            makePublicProductImage({ id: 1, originalFilename: "first.jpg", primary: false }),
            makePublicProductImage({ id: 2, originalFilename: "cover.jpg", primary: true }),
          ],
        })}
      />,
    );

    expect(
      screen.getByRole("img", { name: /Photo of Red Rose Bouquet: cover\.jpg/ }),
    ).toBeInTheDocument();
  });

  it("falls back to the first image when none is marked primary", () => {
    renderWithProviders(
      <ProductCard
        product={makePublicProduct({
          images: [
            makePublicProductImage({ id: 1, originalFilename: "first.jpg", primary: false }),
            makePublicProductImage({ id: 2, originalFilename: "second.jpg", primary: false }),
          ],
        })}
      />,
    );

    expect(
      screen.getByRole("img", { name: /Photo of Red Rose Bouquet: first\.jpg/ }),
    ).toBeInTheDocument();
  });

  it("reports the product when the add-to-cart control is used", async () => {
    const user = userEvent.setup();
    const onAddToCart = vi.fn();
    const product = makePublicProduct();

    renderWithProviders(
      <ProductCard product={product} showAddToCart onAddToCart={onAddToCart} />,
    );

    await user.click(screen.getByRole("button", { name: "Add Red Rose Bouquet to cart" }));

    expect(onAddToCart).toHaveBeenCalledWith(product);
  });

  it("cannot be added when it is sold out", () => {
    renderWithProviders(
      <ProductCard
        product={makePublicProduct({ available: 0 })}
        showAddToCart
        onAddToCart={vi.fn()}
      />,
    );

    expect(screen.getByRole("button", { name: /Add Red Rose Bouquet to cart/ })).toBeDisabled();
  });
});
