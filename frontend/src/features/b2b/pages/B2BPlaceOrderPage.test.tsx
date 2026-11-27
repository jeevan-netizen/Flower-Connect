import { describe, expect, it, vi, beforeEach } from "vitest";
import { fireEvent, screen, waitFor } from "@testing-library/react";

import { B2BPlaceOrderPage } from "@/features/b2b/pages/B2BPlaceOrderPage";
import * as b2bQueries from "@/features/b2b/queries";
import * as b2bApi from "@/features/b2b/api";
import { renderWithProviders } from "@/test/render";
import { makeSellerSummary, makeB2BProductSummary } from "@/test/factories";
import type { SellerSummary, B2BProductSummary } from "@/features/b2b/types";
import type { UseQueryResult } from "@tanstack/react-query";

vi.mock("@/features/b2b/queries");
vi.mock("@/features/b2b/api");

const mockUseSellers = vi.mocked(b2bQueries.useSellers);
const mockUseSellerProducts = vi.mocked(b2bQueries.useSellerProducts);
const mockPlaceB2BOrder = vi.mocked(b2bApi.placeB2BOrder);

const sellers: SellerSummary[] = [
  makeSellerSummary({ vendorId: 11, businessName: "Seller Florist" }),
  makeSellerSummary({ vendorId: 12, businessName: "Other Florist" }),
];
const products: B2BProductSummary[] = [
  makeB2BProductSummary({ productId: 101, productName: "Red Rose Bouquet", unitPrice: 899, availableStock: 24 }),
  makeB2BProductSummary({ productId: 102, productName: "Lily Bouquet", unitPrice: 599, availableStock: 10 }),
];

describe("B2BPlaceOrderPage", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    // Resolved synchronously: no React Query timing to wait for.
    mockUseSellers.mockReturnValue({
      data: sellers,
      isPending: false,
      error: null,
    } as unknown as UseQueryResult<SellerSummary[], Error>);
    mockUseSellerProducts.mockReturnValue({
      data: products,
      isPending: false,
      error: null,
    } as unknown as UseQueryResult<B2BProductSummary[], Error>);
    mockPlaceB2BOrder.mockResolvedValue({
      id: 1,
      orderNo: "B2B-1",
      buyerId: 7,
      buyerName: "Buyer",
      sellerId: 11,
      sellerName: "Seller Florist",
      status: "PLACED",
      paymentStatus: "UNPAID",
      total: 1823,
      orderNote: null,
      shipmentReference: null,
      shippedAt: null,
      deliveredAt: null,
      billNumber: null,
      placedAt: "2026-11-25T10:00:00",
      updatedAt: "2026-11-25T10:00:00",
      items: [],
    } as never);
  });

  it("renders the seller search", () => {
    renderWithProviders(<B2BPlaceOrderPage />, { route: "/vendor/b2b/place-order" });

    expect(screen.getByPlaceholderText(/search sellers by name, area or pincode/i)).toBeInTheDocument();
  });

  it("selects a seller and shows its products", () => {
    renderWithProviders(<B2BPlaceOrderPage />, { route: "/vendor/b2b/place-order" });

    fireEvent.click(screen.getByText("Seller Florist"));
    expect(screen.getByText("Red Rose Bouquet")).toBeInTheDocument();
    expect(screen.getByText("Lily Bouquet")).toBeInTheDocument();
  });

  it("adjusts quantity and computes the subtotal", () => {
    renderWithProviders(<B2BPlaceOrderPage />, { route: "/vendor/b2b/place-order" });

    fireEvent.click(screen.getByText("Seller Florist"));

    const quantityInput = screen.getAllByRole("spinbutton")[0];
    fireEvent.change(quantityInput, { target: { value: "3" } });

    expect(screen.getByText(/Total:.*2,697/)).toBeInTheDocument();
  });

  it("disables place order when no quantity is chosen", () => {
    renderWithProviders(<B2BPlaceOrderPage />, { route: "/vendor/b2b/place-order" });

    fireEvent.click(screen.getByText("Seller Florist"));

    expect(screen.getByRole("button", { name: /place order/i })).toBeDisabled();
  });

  it("places the order", () => {
    renderWithProviders(<B2BPlaceOrderPage />, { route: "/vendor/b2b/place-order" });

    fireEvent.click(screen.getByText("Seller Florist"));

    const quantityInput = screen.getAllByRole("spinbutton")[0];
    fireEvent.change(quantityInput, { target: { value: "2" } });

    fireEvent.click(screen.getByRole("button", { name: /place order/i }));

    expect(mockPlaceB2BOrder).toHaveBeenCalledWith({
      sellerId: 11,
      items: [{ productId: 101, quantity: 2 }],
      orderNote: null,
    });
  });

  it("shows an API error when placement fails", async () => {
    mockPlaceB2BOrder.mockRejectedValueOnce(new Error("boom"));

    renderWithProviders(<B2BPlaceOrderPage />, { route: "/vendor/b2b/place-order" });

    fireEvent.click(screen.getByText("Seller Florist"));

    const quantityInput = screen.getAllByRole("spinbutton")[0];
    fireEvent.change(quantityInput, { target: { value: "1" } });

    fireEvent.click(screen.getByRole("button", { name: /place order/i }));

    await waitFor(() =>
      expect(screen.getByText(/could not place the order/i)).toBeInTheDocument(),
    );
  });
});
