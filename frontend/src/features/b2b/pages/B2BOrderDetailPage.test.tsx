import { describe, expect, it, vi, beforeEach } from "vitest";
import { fireEvent, screen } from "@testing-library/react";

import { B2BOrderDetailPage } from "@/features/b2b/pages/B2BOrderDetailPage";
import * as b2bQueries from "@/features/b2b/queries";
import * as vendorQueries from "@/features/vendor/queries";
import { renderWithProviders } from "@/test/render";
import { makeB2BOrderResponse } from "@/test/factories";
import type { B2BOrderResponse } from "@/features/b2b/types";
import type { VendorProfile } from "@/features/vendor/types";
import type { UseQueryResult } from "@tanstack/react-query";

vi.mock("@/features/b2b/queries");
vi.mock("@/features/vendor/queries");

const mockUseB2BOrder = vi.mocked(b2bQueries.useB2BOrder);
const mockUseVendorProfile = vi.mocked(vendorQueries.useVendorProfile);
const mockUseB2BOrderAction = vi.mocked(b2bQueries.useB2BOrderAction);

const order: B2BOrderResponse = makeB2BOrderResponse({
  id: 1,
  orderNo: "B2B-1001",
  buyerId: 7,
  buyerName: "Buyer Florist",
  sellerId: 11,
  sellerName: "Seller Florist",
  status: "PLACED",
  total: 1823,
  items: [{ id: 1, productId: 101, productName: "Red Rose Bouquet", unit: "bunch", quantity: 2, unitPrice: 899, lineTotal: 1798 }],
});

const sellerProfile: VendorProfile = {
  id: 11,
  ownerEmail: "seller@example.com",
  businessName: "Seller Florist",
  status: "APPROVED",
} as VendorProfile;

const buyerProfile: VendorProfile = {
  id: 7,
  ownerEmail: "buyer@example.com",
  businessName: "Buyer Florist",
  status: "APPROVED",
} as VendorProfile;

describe("B2BOrderDetailPage", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    mockUseB2BOrder.mockReturnValue({
      data: order,
      isPending: false,
      error: null,
    } as unknown as UseQueryResult<B2BOrderResponse, Error>);
    (mockUseB2BOrderAction as unknown as { mockReturnValue: (_: unknown) => void }).mockReturnValue({
      mutateAsync: vi.fn().mockResolvedValue(order),
      isPending: false,
    });
  });

  it("renders order items, total and timeline for the seller", () => {
    mockUseVendorProfile.mockReturnValue({
      data: sellerProfile,
      isPending: false,
      error: null,
    } as unknown as UseQueryResult<VendorProfile, Error>);

    renderWithProviders(<B2BOrderDetailPage />, { route: "/vendor/b2b/orders/1" });

    expect(screen.getByText("#B2B-1001")).toBeInTheDocument();
    expect(screen.getByText("Red Rose Bouquet")).toBeInTheDocument();
    expect(screen.getByText(/1,823\.00/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /accept order/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /reject order/i })).toBeInTheDocument();
  });

  it("shows cancel for the buyer on a PLACED order", () => {
    mockUseVendorProfile.mockReturnValue({
      data: buyerProfile,
      isPending: false,
      error: null,
    } as unknown as UseQueryResult<VendorProfile, Error>);

    renderWithProviders(<B2BOrderDetailPage />, { route: "/vendor/b2b/orders/1" });

    expect(screen.getByText("#B2B-1001")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /cancel order/i })).toBeInTheDocument();
  });

  it("shows the e-bill download only for DELIVERED orders", () => {
    const deliveredOrder = makeB2BOrderResponse({
      ...order,
      status: "DELIVERED",
      billNumber: "EB-1001",
    });
    mockUseB2BOrder.mockReturnValue({
      data: deliveredOrder,
      isPending: false,
      error: null,
    } as unknown as UseQueryResult<B2BOrderResponse, Error>);
    mockUseVendorProfile.mockReturnValue({
      data: sellerProfile,
      isPending: false,
      error: null,
    } as unknown as UseQueryResult<VendorProfile, Error>);

    renderWithProviders(<B2BOrderDetailPage />, { route: "/vendor/b2b/orders/1" });

    expect(screen.getByText("#B2B-1001")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /download e-bill/i })).toBeInTheDocument();
  });

  it("does not show e-bill download for a non-delivered order", () => {
    mockUseVendorProfile.mockReturnValue({
      data: sellerProfile,
      isPending: false,
      error: null,
    } as unknown as UseQueryResult<VendorProfile, Error>);

    renderWithProviders(<B2BOrderDetailPage />, { route: "/vendor/b2b/orders/1" });

    expect(screen.getByText("#B2B-1001")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /download e-bill/i })).not.toBeInTheDocument();
  });

  it("enables the Ship order dialog submit button when a shipment reference is entered", async () => {
    mockUseB2BOrder.mockReturnValue({
      data: makeB2BOrderResponse({
        id: 1,
        orderNo: "B2B-1001",
        status: "ACCEPTED",
        buyerId: 20,
        sellerId: 10,
      }),
      isPending: false,
      error: null,
      refetch: vi.fn(),
    } as unknown as UseQueryResult<ReturnType<typeof makeB2BOrderResponse>, Error>);
    mockUseVendorProfile.mockReturnValue({
      data: sellerProfile,
      isPending: false,
      error: null,
    } as unknown as UseQueryResult<VendorProfile, Error>);

    renderWithProviders(<B2BOrderDetailPage />, { route: "/vendor/b2b/orders/1" });

    fireEvent.click(screen.getByRole("button", { name: "Ship order" }));
    const dialog = await screen.findByRole("dialog");
    const input = screen.getByTestId("shipment-reference");
    fireEvent.change(input, { target: { value: "TRACK-123" } });

    const submitBtn = Array.from(dialog.querySelectorAll("button")).find(
      (b) => b.getAttribute("type") === "submit",
    );
    expect(submitBtn).toBeEnabled();
    expect(submitBtn).toHaveTextContent("Ship order");
  });
});
